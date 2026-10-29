package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.standard.MemoryBehavior;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Runs a {@link SimulationEngine} on its own thread. Every request is queued and applied there;
 * other threads only read the immutable {@link #latestSnapshot()}.
 */
public final class SimulationThread implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationThread.class);
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final double MAXIMUM_MILLISECONDS_PER_TICK = 250.0;
    private static final long PARK_NANOS = 2 * NANOS_PER_MILLISECOND;
    /** Waveform snapshots are republished at most this often (about 60 per second). */
    private static final long WAVEFORM_PUBLISH_INTERVAL_NANOS = 16 * NANOS_PER_MILLISECOND;
    /** Memory map frames are republished at most this often, like the waveforms. */
    private static final long MEMORY_MAP_PUBLISH_INTERVAL_NANOS = 16 * NANOS_PER_MILLISECOND;
    /** A running tick advances the engine for at most this long of real time, then publishes and serves requests. */
    private static final long TICK_BUDGET_NANOS = 10 * NANOS_PER_MILLISECOND;
    /** While running, the snapshot is rebuilt at most this often (about 60 per second: what a frame can show). */
    private static final long SNAPSHOT_INTERVAL_NANOS = 16 * NANOS_PER_MILLISECOND;
    /** Nor more often than this many times its own cost, so a huge circuit's snapshot takes at most a fifth. */
    private static final long SNAPSHOT_COST_SHARE = 5;
    /** The achieved speed is averaged over windows this long. */
    private static final long SPEED_WINDOW_NANOS = 250 * NANOS_PER_MILLISECOND;
    /** The engine runs in slices that aim at this long of real time each, so a tick can stop near its budget. */
    private static final long SLICE_TARGET_NANOS = 2 * NANOS_PER_MILLISECOND;
    /** The units of a slice before the engine's pace is known, and after a request (the circuit may have changed). */
    private static final long INITIAL_SLICE_UNITS = 1_000;
    /** A slice never asks for more than this, however idle the circuit, so the simulated time stays sane. */
    private static final long MAXIMUM_SLICE_UNITS = 1_000_000_000L;

    private final SimulationEngine engine;
    private final LongSupplier nanoClock;
    private final long closeTimeoutMillis;
    private final Queue<Runnable> requests = new ConcurrentLinkedQueue<>();
    /** The receipts of the budgeted requests not answered yet: failed with {@link ClosedException} on close. */
    private final Set<CompletableFuture<RequestReport>> waitingReports = ConcurrentHashMap.newKeySet();
    /** The {@link #requestMemoryContents} answers not given yet: completed empty on close. */
    private final Set<CompletableFuture<Optional<MemoryContents>>> waitingContents = ConcurrentHashMap.newKeySet();
    private volatile SimulationSnapshot latestSnapshot = SimulationSnapshot.EMPTY;
    private volatile WaveformSnapshot latestWaveforms = WaveformSnapshot.EMPTY;
    /** The last memory map frame for the current request; null without a request or a memory to answer it. */
    private volatile MemoryMapFrame latestMemoryMap;
    /** The last «Avanzar» or paused interaction (M6.1-B); null until the first one. */
    private volatile StepReport latestStep;
    /** The engine's next event time, refreshed with every published snapshot (M6.1-B). */
    private volatile OptionalLong latestNextEventTime = OptionalLong.empty();

    /** The engine's pending components while paused, empty while running (M6.1-B), written with the next event time. */
    private volatile Set<String> latestPendingComponents = Set.of();
    private volatile boolean stopRequested;
    private Thread thread;

    // Only touched on the simulation thread (or by tick() in tests).
    private boolean running;
    private double speed = 1.0;
    private double pendingUnits;
    private long sliceUnits = INITIAL_SLICE_UNITS;
    /** True when a request, a pause or a failure must publish at once, whatever the snapshot cadence. */
    private boolean snapshotDue = true;
    private long lastSnapshotNanos;
    /** How long the last rebuild of the snapshot took. */
    private long snapshotCostNanos;
    /** True when the last tick used up its budget: the engine is behind the speed asked for. */
    private boolean saturated;
    /** The engine's {@link SimulationEngine#eventsRun()} when the snapshot was last rebuilt. */
    private long eventsAtSnapshot;
    private long speedWindowStartNanos;
    private long speedWindowUnits;
    private volatile double achievedUnitsPerMillisecond;
    private boolean firstTick = true;
    private long lastTickNanos;
    private String failure;
    private boolean pauseOnShortCircuit;
    private Set<String> shortCircuitNets = Set.of();
    /** True when the engine or the run state changed since the last published snapshot. */
    private boolean snapshotStale = true;
    private boolean waveformsPublished;
    private long lastWaveformPublishNanos;
    /** What the memory map asks for; null when it asks for nothing. */
    private MemoryMapRequest memoryMapRequest;
    /** True when something ran since the last memory map frame (a request, a running tick). */
    private boolean memoryMapStale;
    private boolean memoryMapPublished;
    private long lastMemoryMapPublishNanos;
    private long stepSequence;
    /** The {@link #published()} futures whose request ran in this tick, completed once it has published. */
    private final List<CompletableFuture<SimulationSnapshot>> publicationWaiters = new ArrayList<>();

    public SimulationThread(SimulationEngine engine) {
        this(engine, System::nanoTime);
    }

    SimulationThread(SimulationEngine engine, LongSupplier nanoClock) {
        this(engine, nanoClock, TimeUnit.SECONDS.toMillis(2));
    }

    SimulationThread(SimulationEngine engine, LongSupplier nanoClock, long closeTimeoutMillis) {
        this.engine = engine;
        this.nanoClock = nanoClock;
        this.closeTimeoutMillis = closeTimeoutMillis;
    }

    public synchronized void start() {
        if (thread != null) {
            return;
        }
        thread = new Thread(this::loop, "vsim-simulation");
        thread.setDaemon(true);
        thread.start();
    }

    public void update(FlatCircuit circuit) {
        requests.add(() -> {
            engine.update(circuit);
            failure = null;
        });
    }

    /**
     * Replaces the circuit and binds the waveform channels (as {@link #setProbes}) in one request, so no tick runs
     * between them (spec §3.4). The channels are bound even when the circuit is rejected.
     */
    public void update(FlatCircuit circuit, Map<String, String> channels) {
        Map<String, String> copy = Map.copyOf(channels);
        requests.add(() -> {
            engine.update(circuit, copy);
            failure = null;
        });
    }

    /**
     * «Reiniciar» (M6.1-B): the simulation of {@code circuit} starts again at time 0 with the switches where they are
     * ({@link SimulationEngine#restart}); running or paused stays as it was, a failure is cleared.
     */
    public void restart(FlatCircuit circuit, Map<String, String> channels) {
        Map<String, String> copy = Map.copyOf(channels);
        requests.add(() -> {
            engine.restart(circuit, copy);
            latestStep = null; // the old timeline's last jump must not come back as «(+N)»
            failure = null;
            pendingUnits = 0;
        });
    }

    /**
     * The circuit could not be prepared ({@code cause}): pauses and publishes the failure, keeping the last circuit
     * until the next {@link #update}.
     */
    public void reportFailure(Throwable cause) {
        requests.add(() -> publishFailure(cause));
    }

    /**
     * Push button {@code componentId} is pressed: it drives 1 from now on. Like a click, while paused it publishes a
     * {@link #latestStep()} with the nets that changed. A component that is gone or is not an input is skipped.
     */
    public void pressButton(String componentId) {
        driveButton(componentId, Value.ONE);
    }

    /** Push button {@code componentId} is released: it drives 0 again (see {@link #pressButton}). */
    public void releaseButton(String componentId) {
        driveButton(componentId, Value.ZERO);
    }

    private void driveButton(String componentId, Value value) {
        requests.add(() -> {
            try {
                if (running) {
                    engine.setInput(componentId, value);
                } else {
                    publishStep(engine.setInputTracked(componentId, value));
                }
            } catch (IllegalArgumentException exception) {
                LOGGER.debug("Button {} not driven: {}", componentId, exception.getMessage());
            }
        });
    }

    /** Clicks a component. While paused it also publishes a {@link #latestStep()} with the nets that changed. */
    public void interact(String componentId) {
        requests.add(() -> {
            if (running) {
                engine.interact(componentId);
            } else {
                publishStep(engine.interactTracked(componentId));
            }
        });
    }

    /**
     * New live values for running components ({@link SimulationEngine#takeLiveValues}: a Switch's «Valor» changed by a
     * click, its undo or redo), without a new circuit or token. While paused, one that changes something publishes a
     * step like a click, so «Paso a paso» shows an undone click too.
     */
    public void takeLiveValues(Map<String, Map<String, Object>> parametersById) {
        Map<String, Map<String, Object>> copy = Map.copyOf(parametersById);
        requests.add(() -> {
            if (running) {
                engine.takeLiveValues(copy);
            } else {
                engine.takeLiveValuesTracked(copy).ifPresent(this::publishStep);
            }
        });
    }

    /** Clicks bit {@code bit} of a multi-bit switch; like {@link #interact}, a paused click publishes a step. */
    public void interactBit(String componentId, int bit) {
        requests.add(() -> {
            if (running) {
                engine.interactBit(componentId, bit);
            } else {
                publishStep(engine.interactBitTracked(componentId, bit));
            }
        });
    }

    /**
     * «Avanzar» (M6.1-B): runs every event of the next simulation time and publishes the result as
     * {@link #latestStep()}. Ignored while running. A zero-delay loop fails like any engine error: paused, with the
     * failure in the snapshot.
     */
    public void advanceToNextEvent() {
        requests.add(() -> {
            if (!running) {
                publishStep(engine.advanceToNextEvent());
            }
        });
    }

    /**
     * «Avanzar al próximo flanco de reloj» ({@link ClockEdgeMode#ANY}) and «Avanzar un ciclo de reloj»
     * ({@link ClockEdgeMode#FALLING}): see {@link SimulationEngine#advanceToNextClockEdge(ClockEdgeMode)}; published
     * as {@link #latestStep()} like «Avanzar». Ignored while running; failures pause like any engine error.
     */
    public void advanceToNextClockEdge(ClockEdgeMode mode) {
        Objects.requireNonNull(mode, "mode");
        requests.add(() -> {
            if (!running) {
                publishStep(engine.advanceToNextClockEdge(mode));
            }
        });
    }

    /**
     * The last step published by {@link #advanceToNextEvent()}, {@link #advanceToNextClockEdge}, a paused
     * {@link #step} or a paused {@link #interact}; empty before the first.
     */
    public Optional<StepReport> latestStep() {
        return Optional.ofNullable(latestStep);
    }

    /**
     * The time of the earliest pending event (empty when the circuit is stable), as of the latest published snapshot.
     * Read it after {@link #latestSnapshot()}: it is written first.
     */
    public OptionalLong latestNextEventTime() {
        return latestNextEventTime;
    }

    /**
     * The components with an event at {@link #latestNextEventTime()} (flat ids, see
     * {@link SimulationEngine#pendingComponents()}), as of the latest published snapshot; empty while running.
     */
    public Set<String> latestPendingComponents() {
        return latestPendingComponents;
    }

    private void publishStep(StepResult result) {
        stepSequence++;
        latestStep = new StepReport(stepSequence, result);
    }

    /**
     * Sets input components (switches, input pins) to the given values like clicks do (M6.1-C: a truth-table row tried
     * in the circuit): queued, applied at the current time with their zero-delay effects; a running simulation goes on
     * from there. An id that is gone or is not an input is skipped, since the circuit may change before it applies.
     */
    public void setInputs(Map<String, Value> values) {
        Map<String, Value> copy = Map.copyOf(values);
        requests.add(() -> copy.forEach((componentId, value) -> {
            try {
                engine.setInput(componentId, value);
            } catch (IllegalArgumentException exception) {
                LOGGER.debug("Input {} not set: {}", componentId, exception.getMessage());
            }
        }));
    }

    /**
     * «Restaurar contenido inicial»: the memory simulated as {@code componentId} goes back to its initial contents;
     * the time and every other part go on (see {@link SimulationEngine#restoreInitialState}). Not an edit of the
     * document, so not an undo step.
     */
    public void restoreMemory(String componentId) {
        requests.add(() -> engine.restoreInitialState(componentId));
    }

    /**
     * The words the memory simulated as {@code componentId} holds now, writes included (for «Volcar», the editor's live
     * grid and the AI): completed on the simulation thread once the earlier requests ran, empty when there is no such
     * memory, and at once with empty when the thread is closed. Never wait for it on the EDT.
     */
    public CompletableFuture<Optional<MemoryContents>> requestMemoryContents(String componentId) {
        CompletableFuture<Optional<MemoryContents>> future = new CompletableFuture<>();
        if (stopRequested) {
            future.complete(Optional.empty());
            return future;
        }
        waitingContents.add(future);
        future.whenComplete((contents, failure) -> waitingContents.remove(future));
        requests.add(() -> future.complete(engine.memory(componentId).map(MemoryBehavior::liveContents)));
        if (stopRequested) {
            future.complete(Optional.empty());
        }
        return future;
    }

    public void resume() {
        requests.add(() -> {
            running = true;
            failure = null;
        });
    }

    public void pause() {
        requests.add(() -> running = false);
    }

    /** «Avanzar N t»: ignored while running; while paused it is published as a {@link #latestStep()} (M6.1-B). */
    public void step(long duration) {
        requests.add(() -> {
            if (!running) {
                publishStep(engine.advanceBy(duration));
            }
        });
    }

    /**
     * Simulated units per real millisecond to aim at; {@link Double#POSITIVE_INFINITY} runs as fast as the engine
     * can. When the engine cannot keep up with a finite speed, the simulation goes as fast as it can instead and
     * never owes the difference: see {@link #achievedUnitsPerMillisecond()}.
     */
    public void setSpeed(double unitsPerMillisecond) {
        if (!(unitsPerMillisecond > 0)) {
            throw new IllegalArgumentException("speed must be positive: " + unitsPerMillisecond);
        }
        requests.add(() -> speed = unitsPerMillisecond);
    }

    /** The units simulated per real millisecond lately (over about a quarter of a second); 0 while paused. */
    public double achievedUnitsPerMillisecond() {
        return achievedUnitsPerMillisecond;
    }

    /** When on, a running simulation pauses as soon as a net that was not shorted becomes shorted (spec §6.12). */
    public void setPauseOnShortCircuit(boolean enabled) {
        requests.add(() -> pauseOnShortCircuit = enabled);
    }

    /** Binds waveform channels (channel id → net id; "" or an unknown id records a gap), applied at the next tick. */
    public void setProbes(Map<String, String> channels) {
        Map<String, String> copy = Map.copyOf(channels);
        requests.add(() -> engine.setProbes(copy));
    }

    /** «Limpiar trazas»: drops every recorded event; the channels go on recording from their current value. */
    public void clearWaveforms() {
        requests.add(engine::clearWaveforms);
    }

    /**
     * What the «Mapa de memoria» shows (spec §6): from now on {@link #latestMemoryMap()} answers {@code request} after
     * something ran, at most every 16 ms while running; empty stops the frames. Asking for another memory forgets the
     * accesses it made before (they are not recent for the map); its counts stay.
     */
    public void setMemoryMapRequest(Optional<MemoryMapRequest> request) {
        MemoryMapRequest wanted = request.orElse(null);
        requests.add(() -> {
            if (wanted != null && (memoryMapRequest == null
                    || !memoryMapRequest.componentId().equals(wanted.componentId()))) {
                engine.memory(wanted.componentId()).ifPresent(memory -> memory.accessLog().drainRecent());
            }
            memoryMapRequest = wanted;
            memoryMapStale = true;
            memoryMapPublished = false;
            if (wanted == null) {
                latestMemoryMap = null;
            }
        });
    }

    /**
     * The latest memory map frame: empty without a request, or when what it names is not a memory being simulated.
     * Its {@link MemoryMapFrame#request()} may be an earlier request than the last one set; compare before using it.
     */
    public Optional<MemoryMapFrame> latestMemoryMap() {
        return Optional.ofNullable(latestMemoryMap);
    }

    /**
     * The recorded waveforms, republished at most every 16 ms when a trace changes or, with channels, when the
     * simulation time moves.
     */
    public WaveformSnapshot latestWaveforms() {
        return latestWaveforms;
    }

    /**
     * Completes with the published snapshot once every request queued before this call has been applied and its
     * result published: the snapshot and, written before it, the next event time, the pending components and the
     * step. A request that fails publishes the failure first; the ones after it are applied in the next tick.
     * Lets a caller (tests, mainly) wait for a known state instead of polling for one the running simulation may
     * already have moved past. Never completes once the thread is closed; wait with a timeout.
     */
    public CompletableFuture<SimulationSnapshot> published() {
        CompletableFuture<SimulationSnapshot> future = new CompletableFuture<>();
        requests.add(() -> publicationWaiters.add(future));
        return future;
    }

    // --- Requests with an event budget and a receipt (IA.1 Task 4, A§7) ---
    //
    // Each one is queued like the others and runs on this thread within its budget; its future completes there, right
    // after it ran, with one immutable RequestReport (never a mix of separately published fields). A request that
    // runs out of budget stops at the time it reached and pauses the simulation, so the events left over cannot run
    // away in the free run; nothing is rolled back. The user's own requests above keep their behaviour.

    /**
     * Sets input components one after another in the order given, each with its zero-delay effects before the next
     * (sequential, not simultaneous: A§7), all of them within {@code eventBudget} events. Unlike {@link #setInputs}
     * every input is reported accepted or rejected (an id that is gone, a component that is not an input, a value of
     * another width). Running or paused stays as it was, unless the budget runs out. After the budget runs out the
     * inputs not yet reached are reported rejected.
     */
    public CompletableFuture<RequestReport> setInputsWithin(List<InputAssignment> inputs, long eventBudget) {
        List<InputAssignment> copy = List.copyOf(inputs);
        requirePositiveBudget(eventBudget);
        List<InputOutcome> outcomes = new ArrayList<>();
        return budgeted(eventBudget, outcomes, () -> {
            for (InputAssignment input : copy) {
                try {
                    engine.setInput(input.componentId(), input.value());
                    outcomes.add(InputOutcome.accepted(input.componentId()));
                } catch (IllegalArgumentException exception) {
                    outcomes.add(InputOutcome.rejected(input.componentId(), String.valueOf(exception.getMessage())));
                } catch (EventBudgetExceededException exception) {
                    outcomes.add(new InputOutcome(input.componentId(), true,
                            "set, but the event budget ran out during its zero-delay effects"));
                    for (int index = outcomes.size(); index < copy.size(); index++) {
                        outcomes.add(InputOutcome.rejected(copy.get(index).componentId(),
                                "not set: the event budget ran out before it"));
                    }
                    throw exception;
                }
            }
        });
    }

    /**
     * {@code count} times {@link #advanceToNextEvent()}, all within {@code eventBudget} events (and no other per-time
     * limit), stopping early once nothing is pending; paused only, published as one step.
     *
     * @throws IllegalArgumentException when {@code count} or {@code eventBudget} is not positive
     */
    public CompletableFuture<RequestReport> advanceToNextEventWithin(int count, long eventBudget) {
        requirePositive(count, eventBudget);
        return budgetedStep(eventBudget, () -> repeated(count, () -> engine.advanceToNextEvent(Integer.MAX_VALUE)));
    }

    /**
     * {@code count} times {@link #advanceToNextClockEdge(ClockEdgeMode)}, all within {@code eventBudget} events,
     * stopping early when one moves nothing (no clock, nothing pending); paused only, published as one step that names
     * the last edge reached.
     *
     * @throws IllegalArgumentException when {@code count} or {@code eventBudget} is not positive
     */
    public CompletableFuture<RequestReport> advanceToNextClockEdgeWithin(ClockEdgeMode mode, int count,
                                                                        long eventBudget) {
        Objects.requireNonNull(mode, "mode");
        requirePositive(count, eventBudget);
        return budgetedStep(eventBudget, () -> repeated(count, () -> engine.advanceToNextClockEdge(mode,
                SimulationEngine.CLOCK_EDGE_TIME_BUDGET, Integer.MAX_VALUE)));
    }

    private static void requirePositive(int count, long eventBudget) {
        if (count <= 0) {
            throw new IllegalArgumentException("the count must be positive: " + count);
        }
        requirePositiveBudget(eventBudget);
    }

    /**
     * {@code count} steps as one: from the first one's start to the last one's end, with every net that changed and
     * the last one's clock edge.
     */
    private static StepResult repeated(int count, Supplier<StepResult> step) {
        StepResult first = step.get();
        StepResult last = first;
        Set<String> changed = new LinkedHashSet<>(first.changedNets());
        for (int done = 1; done < count && (last.time() != last.fromTime() || !last.changedNets().isEmpty())
                && last.nextEventTime().isPresent(); done++) {
            last = step.get();
            changed.addAll(last.changedNets());
        }
        return new StepResult(first.fromTime(), last.time(), changed, last.nextEventTime(), last.edge());
    }

    /**
     * «Avanzar N t» ({@link #step}) within {@code eventBudget} events; paused only. A time past the largest
     * simulation time is refused.
     *
     * @throws IllegalArgumentException when {@code duration} or {@code eventBudget} is not positive
     */
    public CompletableFuture<RequestReport> stepWithin(long duration, long eventBudget) {
        if (duration <= 0) {
            throw new IllegalArgumentException("the duration must be positive: " + duration);
        }
        requirePositiveBudget(eventBudget);
        CompletableFuture<RequestReport> future = waitingReport();
        requests.add(() -> {
            if (duration > Long.MAX_VALUE - engine.now()) {
                future.complete(reportOf(RequestReport.Status.REFUSED_TIME_OVERFLOW, engine.now(), 0, List.of()));
                return;
            }
            runStep(future, eventBudget, () -> engine.advanceBy(duration));
        });
        return future;
    }

    /** {@link #restart} within {@code eventBudget} events (the components' initialization included). */
    public CompletableFuture<RequestReport> restartWithin(FlatCircuit circuit, Map<String, String> channels,
                                                          long eventBudget) {
        Map<String, String> copy = Map.copyOf(channels);
        requirePositiveBudget(eventBudget);
        return budgeted(eventBudget, List.of(), () -> {
            latestStep = null; // the old timeline's last jump must not come back as «(+N)»
            failure = null;
            pendingUnits = 0;
            engine.restart(circuit, copy);
        });
    }

    /** A receipt of the state once every earlier request ran: nothing runs (a reading of one instant). */
    public CompletableFuture<RequestReport> report() {
        CompletableFuture<RequestReport> future = waitingReport();
        requests.add(() -> future.complete(reportOf(RequestReport.Status.COMPLETED, engine.now(), 0, List.of())));
        return future;
    }

    private static void requirePositiveBudget(long eventBudget) {
        if (eventBudget <= 0) {
            throw new IllegalArgumentException("the event budget must be positive: " + eventBudget);
        }
    }

    /**
     * Queues {@code work} to run within the budget, running or paused; {@code outcomes} is what the work reports per
     * input (it fills it as it goes). A budget that runs out pauses the simulation.
     */
    /**
     * Thrown into the receipt of a budgeted request that will never run: the thread was closed, or it died, before
     * its turn came.
     */
    public static final class ClosedException extends IllegalStateException {
        public ClosedException() {
            super("The simulation was closed before the request ran");
        }
    }

    /** A receipt future answered when its request runs, or failed with {@link ClosedException} if it never will. */
    private CompletableFuture<RequestReport> waitingReport() {
        CompletableFuture<RequestReport> future = new CompletableFuture<>();
        if (stopRequested) {
            future.completeExceptionally(new ClosedException());
            return future;
        }
        waitingReports.add(future);
        future.whenComplete((report, failure) -> waitingReports.remove(future));
        if (stopRequested) {
            future.completeExceptionally(new ClosedException());
        }
        return future;
    }

    private void failWaitingReports() {
        for (CompletableFuture<RequestReport> future : Set.copyOf(waitingReports)) {
            future.completeExceptionally(new ClosedException());
        }
        for (CompletableFuture<Optional<MemoryContents>> future : Set.copyOf(waitingContents)) {
            future.complete(Optional.empty());
        }
    }

    private CompletableFuture<RequestReport> budgeted(long eventBudget, List<InputOutcome> outcomes, Runnable work) {
        CompletableFuture<RequestReport> future = waitingReport();
        requests.add(() -> {
            long fromTime = engine.now();
            EventBudgetRun run;
            try {
                run = engine.runWithEventBudget(eventBudget, work);
            } catch (RuntimeException exception) {
                LOGGER.error("Simulation request failed; pausing", exception);
                publishFailure(exception);
                future.complete(reportOf(RequestReport.Status.FAILED, fromTime, 0, outcomes));
                return;
            }
            if (run.exceeded()) {
                running = false;
            }
            future.complete(reportOf(run.exceeded() ? RequestReport.Status.BUDGET_EXCEEDED
                    : RequestReport.Status.COMPLETED, fromTime, run.eventsProcessed(), outcomes));
        });
        return future;
    }

    private CompletableFuture<RequestReport> budgetedStep(long eventBudget, Supplier<StepResult> step) {
        CompletableFuture<RequestReport> future = waitingReport();
        requests.add(() -> runStep(future, eventBudget, step));
        return future;
    }

    /**
     * Runs one step on this thread within the budget: refused while running; otherwise published as
     * {@link #latestStep()} like the user's «Avanzar» (when the budget ran out, without the nets that changed).
     */
    private void runStep(CompletableFuture<RequestReport> future, long eventBudget,
                         Supplier<StepResult> step) {
        long fromTime = engine.now();
        if (running) {
            future.complete(reportOf(RequestReport.Status.REFUSED_WHILE_RUNNING, fromTime, 0, List.of()));
            return;
        }
        StepResult[] result = new StepResult[1];
        EventBudgetRun run;
        try {
            run = engine.runWithEventBudget(eventBudget, () -> result[0] = step.get());
        } catch (RuntimeException exception) {
            LOGGER.error("Simulation step failed; pausing", exception);
            publishFailure(exception);
            future.complete(reportOf(RequestReport.Status.FAILED, fromTime, 0, List.of()));
            return;
        }
        StepResult published = result[0] == null
                ? new StepResult(fromTime, engine.now(), Set.of(), engine.nextEventTime())
                : result[0];
        publishStep(published);
        future.complete(reportOf(run.exceeded() ? RequestReport.Status.BUDGET_EXCEEDED
                : RequestReport.Status.COMPLETED, fromTime, run.eventsProcessed(), List.of())
                .withClockEdge(published.edge()));
    }

    /** The receipt of the engine's state now; called on this thread right after a request. */
    private RequestReport reportOf(RequestReport.Status status, long fromTime, long eventsProcessed,
                                   List<InputOutcome> inputs) {
        SimulationSnapshot snapshot = engine.snapshot().withRunning(running).withFailure(Optional.ofNullable(failure));
        return new RequestReport(status, snapshot, engine.nextEventTime(), fromTime, eventsProcessed, inputs);
    }

    /** For tests: requests queued and not yet applied. */
    int pendingRequests() {
        return requests.size();
    }

    public SimulationSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    void tick() {
        long now = nanoClock.getAsLong();
        long elapsedNanos = firstTick ? 0 : now - lastTickNanos;
        firstTick = false;
        lastTickNanos = now;
        try {
            Runnable request;
            while ((request = requests.poll()) != null) {
                request.run();
                snapshotStale = true;
                snapshotDue = true;
                sliceUnits = INITIAL_SLICE_UNITS;
            }
            if (running) {
                advanceFor(elapsedNanos);
                snapshotStale = true;
            } else {
                stopMeasuringSpeed(now);
            }
            if (snapshotStale) {
                publishSnapshot(now);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Simulation failed; pausing", exception);
            publishFailure(exception);
        }
        // Also after a failure: the channels of a rejected circuit are still bound and shown.
        publishWaveforms(now);
        publishMemoryMap(now);
        if (!publicationWaiters.isEmpty()) {
            SimulationSnapshot published = latestSnapshot;
            publicationWaiters.forEach(waiter -> waiter.complete(published));
            publicationWaiters.clear();
        }
    }

    /**
     * Advances the engine by the units owed for {@code elapsedNanos} at the current speed (all it can at an unlimited
     * one), in slices, until they are done or the tick's real-time budget is spent. What the engine could not run in
     * time is dropped rather than owed: the simulation then goes as fast as the engine can, and every tick stays
     * short enough to publish a frame and serve the requests.
     */
    private void advanceFor(long elapsedNanos) {
        boolean unlimited = Double.isInfinite(speed);
        if (!unlimited) {
            double elapsedMilliseconds = Math.min(elapsedNanos / (double) NANOS_PER_MILLISECOND,
                    MAXIMUM_MILLISECONDS_PER_TICK);
            pendingUnits += elapsedMilliseconds * speed;
        }
        long sliceStart = nanoClock.getAsLong();
        long deadline = sliceStart + TICK_BUDGET_NANOS;
        long advanced = 0;
        saturated = false;
        while (unlimited || pendingUnits >= 1) {
            // A slice is sized for SLICE_TARGET_NANOS; the last one, for the time left before the deadline.
            long units = Math.max(1, Math.min(sliceUnits, sliceUnits * (deadline - sliceStart) / SLICE_TARGET_NANOS));
            if (!unlimited) {
                units = Math.min((long) pendingUnits, units);
            }
            engine.advance(units);
            advanced += units;
            if (!unlimited) {
                pendingUnits -= units;
            }
            long sliceEnd = nanoClock.getAsLong();
            adaptSlice(units, sliceEnd - sliceStart);
            sliceStart = sliceEnd;
            if (sliceEnd >= deadline) {
                saturated = unlimited || pendingUnits >= 1;
                break;
            }
        }
        if (pendingUnits >= 1) {
            pendingUnits -= Math.floor(pendingUnits);
        }
        measureSpeed(advanced, sliceStart);
    }

    /**
     * Re-aims the next slice at {@link #SLICE_TARGET_NANOS} of real time from how long the last one took. It shrinks
     * as much as needed but grows at most twofold: a circuit's work comes in bursts (a clock edge and its cascade),
     * so a slice that happened to hold none says little about the next one.
     */
    private void adaptSlice(long units, long tookNanos) {
        double aimed = units * (double) SLICE_TARGET_NANOS / Math.max(1, tookNanos);
        long ceiling = Math.min(2 * sliceUnits, MAXIMUM_SLICE_UNITS);
        sliceUnits = (long) Math.max(1, Math.min(aimed, ceiling));
    }

    /** Averages the units run per real millisecond over {@link #SPEED_WINDOW_NANOS}. */
    private void measureSpeed(long advanced, long now) {
        speedWindowUnits += advanced;
        long window = now - speedWindowStartNanos;
        if (window >= SPEED_WINDOW_NANOS) {
            achievedUnitsPerMillisecond = speedWindowUnits * (double) NANOS_PER_MILLISECOND / window;
            speedWindowUnits = 0;
            speedWindowStartNanos = now;
        }
    }

    private void stopMeasuringSpeed(long now) {
        achievedUnitsPerMillisecond = 0;
        speedWindowUnits = 0;
        speedWindowStartNanos = now;
    }

    /**
     * Publishes the engine's state. While running, the snapshot is rebuilt at most every
     * {@link #SNAPSHOT_INTERVAL_NANOS}, and only when some event ran since the last one (a frame could not show
     * more); between rebuilds only its time moves. A request, a pause and a failure publish at once what they did.
     */
    private void publishSnapshot(long now) {
        long interval = Math.max(SNAPSHOT_INTERVAL_NANOS, SNAPSHOT_COST_SHARE * snapshotCostNanos);
        boolean due = snapshotDue || !running || now - lastSnapshotNanos >= interval;
        boolean changed = snapshotDue || !running || engine.eventsRun() != eventsAtSnapshot;
        if (due && changed) {
            long before = nanoClock.getAsLong();
            SimulationSnapshot snapshot = engine.snapshot();
            snapshotCostNanos = nanoClock.getAsLong() - before;
            pauseOnNewShortCircuit(snapshot);
            latestNextEventTime = engine.nextEventTime();
            latestPendingComponents = running ? Set.of() : engine.pendingComponents();
            latestSnapshot = snapshot.withRunning(running).withFailure(Optional.ofNullable(failure));
            snapshotStale = false;
            snapshotDue = false;
            lastSnapshotNanos = now;
            eventsAtSnapshot = engine.eventsRun();
            memoryMapStale = true;
        } else if (latestSnapshot.time() != engine.now()) {
            latestSnapshot = latestSnapshot.withTime(engine.now());
            latestNextEventTime = engine.nextEventTime();
        }
    }

    private void pauseOnNewShortCircuit(SimulationSnapshot snapshot) {
        Set<String> current = snapshot.shortCircuits().stream()
                .map(ShortCircuit::netId)
                .collect(Collectors.toUnmodifiableSet());
        boolean appeared = !shortCircuitNets.containsAll(current);
        shortCircuitNets = current;
        if (appeared && pauseOnShortCircuit && running) {
            running = false;
            LOGGER.info("Short circuit detected at t = {}; pausing", snapshot.time());
        }
    }

    private void publishWaveforms(long nowNanos) {
        if (waveformsPublished && nowNanos - lastWaveformPublishNanos < WAVEFORM_PUBLISH_INTERVAL_NANOS) {
            return;
        }
        WaveformSnapshot published = latestWaveforms;
        boolean moved = engine.hasWaveformChannels() && engine.now() != published.time();
        if (engine.waveformVersion() != published.version() || moved) {
            latestWaveforms = engine.waveforms();
            waveformsPublished = true;
            lastWaveformPublishNanos = nowNanos;
        }
    }

    /**
     * Publishes a frame when something ran since the last one: while running at most every 16 ms (the snapshots go on
     * being republished, so the map catches up); while paused at once, with the snapshot of the step or click that
     * changed it, since no later snapshot may come to make the map look again.
     */
    private void publishMemoryMap(long nowNanos) {
        MemoryMapRequest request = memoryMapRequest;
        if (request == null || !memoryMapStale || running && memoryMapPublished
                && nowNanos - lastMemoryMapPublishNanos < MEMORY_MAP_PUBLISH_INTERVAL_NANOS) {
            return;
        }
        Optional<MemoryBehavior> memory = engine.memory(request.componentId());
        latestMemoryMap = memory.map(found -> MemoryMapFrame.capture(found, request,
                found.accessLog().drainRecent(), engine.now())).orElse(null);
        memoryMapStale = false;
        memoryMapPublished = true;
        lastMemoryMapPublishNanos = nowNanos;
    }

    private void publishFailure(Throwable throwable) {
        running = false;
        failure = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        latestNextEventTime = engine.nextEventTime();
        latestPendingComponents = engine.pendingComponents();
        latestSnapshot = latestSnapshot.withRunning(false).withFailure(Optional.of(failure));
    }

    private void loop() {
        while (!stopRequested) {
            try {
                tick();
            } catch (Throwable throwable) {
                LOGGER.error("Simulation thread failed", throwable);
                publishFailure(throwable);
                if (throwable instanceof Error) {
                    failWaitingReports();
                    return;
                }
            }
            if (!saturated) {
                LockSupport.parkNanos(PARK_NANOS); // an idle engine leaves the processor alone; a saturated one goes on
            }
        }
    }

    boolean isAlive() {
        Thread current;
        synchronized (this) {
            current = thread;
        }
        return current != null && current.isAlive();
    }

    @Override
    public void close() {
        stopRequested = true;
        Thread current;
        synchronized (this) {
            current = thread;
        }
        if (current != null) {
            try {
                current.join(closeTimeoutMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (current.isAlive()) {
                LOGGER.warn("Simulation thread did not stop within {} ms; interrupting it", closeTimeoutMillis);
                current.interrupt();
            }
        }
        failWaitingReports();
    }
}
