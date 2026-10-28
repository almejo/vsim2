package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MemoryBehavior;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Event-driven simulator. Single-threaded: call it only from one thread
 * (see {@link SimulationThread}). State of unchanged components survives {@link #update}.
 */
public final class SimulationEngine {

    private final Scheduler scheduler = new Scheduler();
    private Map<String, ComponentInstance> instances = new LinkedHashMap<>();
    private Map<String, RuntimeNet> nets = new LinkedHashMap<>();
    private long revision;
    private long token;
    private final WaveformRecorder recorder = new WaveformRecorder();
    private int waveformRebinds;
    /** Alias id → the root net that carries it (FlatNet#aliases), rebuilt with the nets. */
    private Map<String, RuntimeNet> aliasRoots = Map.of();
    /** The wiring problems of the tunnels of the circuit last applied (FlatCircuit#tunnelProblems). */
    private List<TunnelProblem> tunnelProblems = List.of();
    private List<ConnectivityProblem> connectivityProblems = List.of();

    /** At most this many events run in one {@link #advanceToNextEvent()} (a zero-delay loop never lets time move). */
    public static final int STEP_EVENT_BUDGET = 100_000;

    /** At most this many event times run in one {@link #advanceToNextClockEdge()}. */
    public static final int CLOCK_EDGE_TIME_BUDGET = 1_000_000;
    /** Ids (with aliases) of the nets that changed since tracking started; null while not tracking (M6.1-B). */
    private Set<String> changedNets;

    Scheduler scheduler() {
        return scheduler;
    }

    public long now() {
        return scheduler.now();
    }

    public long revision() {
        return revision;
    }

    /**
     * Replaces the simulated circuit, keeping the state of unchanged components.
     * An invalid circuit throws IllegalArgumentException and leaves the engine untouched.
     */
    public void update(FlatCircuit circuit) {
        validate(circuit);
        Map<String, ComponentInstance> nextInstances = new LinkedHashMap<>();
        List<ComponentInstance> created = new ArrayList<>();
        for (FlatComponent component : circuit.components()) {
            ComponentInstance existing = instances.get(component.id());
            if (existing != null && existing.matches(component)) {
                nextInstances.put(component.id(), existing);
            } else {
                ComponentInstance instance = new ComponentInstance(this, component);
                nextInstances.put(component.id(), instance);
                created.add(instance);
            }
        }
        for (ComponentInstance previous : instances.values()) {
            if (nextInstances.get(previous.id) != previous) {
                previous.removed = true;
            }
        }
        instances = nextInstances;
        try {
            rewireAndInitialize(circuit, created);
        } catch (RuntimeException exception) {
            created.forEach(instance -> instance.initializationFailed = true);
            throw exception;
        } finally {
            // The nets may have been replaced even when initialization failed: the traces follow the live ones.
            rebindWaveforms();
        }
        token = circuit.token();
        tunnelProblems = circuit.tunnelProblems();
        connectivityProblems = circuit.connectivityProblems();
        revision++;
    }

    /**
     * Replaces the circuit and sets the waveform channels with a single rebind (as {@link #update(FlatCircuit)} then
     * {@link #setProbes}, which would rebind twice). The channels are bound even when the circuit is rejected.
     */
    public void update(FlatCircuit circuit, Map<String, String> channels) {
        recorder.setChannels(channels);
        int before = waveformRebinds;
        try {
            update(circuit);
        } finally {
            if (waveformRebinds == before) {
                rebind();
            }
        }
    }

    /**
     * Makes input component {@code componentId} (a switch or an input pin) drive {@code value} and applies the
     * zero-delay effects now, like {@link #interact}. Used by the truth-table evaluator (M6.1-C) on its own engines.
     * An unknown id, a component that is not an input or a value of another width throws IllegalArgumentException.
     */
    public void setInput(String componentId, Value value) {
        ComponentInstance instance = instances.get(componentId);
        if (instance == null) {
            throw new IllegalArgumentException("no component " + componentId);
        }
        if (!instance.behavior.setValue(instance, value)) {
            throw new IllegalArgumentException(componentId + " (" + instance.type.id() + ") is not an input");
        }
        scheduler.runUntil(now());
    }

    /**
     * «Reiniciar» (M6.1-B): simulates {@code circuit} from scratch at time 0 — every component new (flip-flops at
     * Q = 0, clocks from their start), no event left from before, the waveforms cleared and started again at t = 0 —
     * except that every switch and input pin keeps the value it has now ({@link Behavior#setValue}). The channels are
     * bound as in {@link #update(FlatCircuit, Map)}. A circuit rejected by validation throws IllegalArgumentException
     * and leaves the engine untouched; a failure while initializing the components leaves it reset (time 0, switches
     * not restored).
     */
    public void restart(FlatCircuit circuit, Map<String, String> channels) {
        validate(circuit);
        Map<String, Value> inputs = new LinkedHashMap<>();
        for (ComponentInstance instance : instances.values()) {
            if (instance.behavior.visibleState() instanceof VisibleState.SwitchState state) {
                inputs.put(instance.id, state.value());
            }
            instance.removed = true;
        }
        instances = new LinkedHashMap<>();
        nets = new LinkedHashMap<>();
        aliasRoots = Map.of();
        scheduler.reset();
        recorder.clear();
        update(circuit, channels);
        inputs.forEach((id, value) -> {
            ComponentInstance instance = instances.get(id);
            if (instance != null && instance.behavior.visibleState() instanceof VisibleState.SwitchState state
                    && state.value().width() == value.width()) {
                instance.behavior.setValue(instance, value);
            }
        });
        scheduler.runUntil(now());
        // the traces start at t = 0 with the switches already in place
        recorder.clear();
        rebind();
    }

    private void rebindWaveforms() {
        if (recorder.hasChannels()) {
            rebind();
        }
    }

    private void rebind() {
        recorder.rebind(this::netWithId, nets.values(), now());
        waveformRebinds++;
    }

    /** For tests: how many times the waveform channels were attached to the nets again. */
    int waveformRebinds() {
        return waveformRebinds;
    }

    private void rewireAndInitialize(FlatCircuit circuit, List<ComponentInstance> created) {
        for (ComponentInstance instance : instances.values()) {
            for (PinState pin : instance.pins) {
                pin.net = null;
            }
        }

        Map<String, RuntimeNet> previousNets = nets;
        Map<String, RuntimeNet> nextNets = new LinkedHashMap<>();
        Map<String, RuntimeNet> nextAliases = new HashMap<>();
        for (FlatNet flatNet : circuit.nets()) {
            RuntimeNet net = new RuntimeNet(flatNet.id(), flatNet.width(), flatNet.widthMismatch());
            net.aliases = flatNet.aliases();
            for (String alias : flatNet.aliases()) {
                nextAliases.put(alias, net);
            }
            RuntimeNet previous = previousNets.get(flatNet.id());
            if (previous != null) {
                net.conflictSince = previous.conflictSince;
            }
            for (FlatPinRef reference : flatNet.pins()) {
                ComponentInstance instance = instances.get(reference.componentId());
                PinState pin = instance.pins[reference.pinIndex()];
                pin.net = net;
                net.pins.add(pin);
            }
            nextNets.put(net.id, net);
        }
        for (ComponentInstance instance : instances.values()) {
            for (PinState pin : instance.pins) {
                if (pin.net == null) {
                    RuntimeNet privateNet = new RuntimeNet("p:" + instance.id + "#" + pin.index, pin.spec.width(), false);
                    pin.net = privateNet;
                    privateNet.pins.add(pin);
                    nextNets.put(privateNet.id, privateNet);
                }
            }
        }
        nets = nextNets;
        aliasRoots = nextAliases;
        nets.values().forEach(net -> net.resolve(now()));
        for (ComponentInstance instance : created) {
            for (PinState pin : instance.pins) {
                pin.lastSeen = instance.input(pin.index);
            }
            instance.behavior.initialize(instance);
        }
        // Apply zero-delay drives due now (initial values of new components) before notifying inputs,
        // so replacing a component never exposes a transient Z to its neighbours.
        scheduler.runUntil(now());
        nets.values().forEach(this::deliver);
    }

    private static void validate(FlatCircuit circuit) {
        Map<String, FlatComponent> componentsById = new HashMap<>();
        for (FlatComponent component : circuit.components()) {
            if (componentsById.putIfAbsent(component.id(), component) != null) {
                throw new IllegalArgumentException("duplicate component id " + component.id());
            }
        }
        for (FlatNet net : circuit.nets()) {
            for (FlatPinRef reference : net.pins()) {
                FlatComponent component = componentsById.get(reference.componentId());
                if (component == null) {
                    throw new IllegalArgumentException("net " + net.id() + " references unknown component "
                            + reference.componentId());
                }
                if (reference.pinIndex() < 0 || reference.pinIndex() >= component.type().pinCount()) {
                    throw new IllegalArgumentException("net " + net.id() + " references pin " + reference.pinIndex()
                            + " of " + component.id() + ", which has " + component.type().pinCount() + " pins");
                }
            }
        }
    }

    public void runUntil(long time) {
        scheduler.runUntil(time);
    }

    /**
     * The time of the earliest pending event, empty when the circuit is stable. Used by «Avanzar» (M6.1-B) and by the
     * truth-table evaluator (M6.1-C) to run one simulation time at a time with {@link #runUntil(long)}.
     */
    public OptionalLong nextEventTime() {
        return scheduler.nextEventTime();
    }

    public void advance(long duration) {
        runUntil(now() + duration);
    }

    public void interact(String componentId) {
        ComponentInstance instance = instances.get(componentId);
        if (instance != null && instance.behavior.isInteractive()) {
            instance.behavior.interact(instance);
            // Apply zero-delay effects now so a paused simulation shows a consistent picture.
            scheduler.runUntil(now());
        }
    }

    /**
     * «Restaurar contenido inicial»: component {@code componentId} (a RAM) goes back to its initial contents
     * ({@link Behavior#restoreInitialState}) and its zero-delay effects apply now; the rest of the circuit and the
     * time go on. An unknown id or a component without contents changes nothing.
     */
    public void restoreInitialState(String componentId) {
        ComponentInstance instance = instances.get(componentId);
        if (instance != null) {
            instance.behavior.restoreInitialState(instance);
            scheduler.runUntil(now());
        }
    }

    /** The memory simulated as {@code componentId} (flat id), empty when there is none or it is not a RAM or ROM. */
    public Optional<MemoryBehavior> memory(String componentId) {
        ComponentInstance instance = instances.get(componentId);
        return instance != null && instance.behavior instanceof MemoryBehavior memory
                ? Optional.of(memory) : Optional.empty();
    }

    /**
     * «Avanzar» (M6.1-B): runs every event of the next pending time, zero-delay effects at that time included, and
     * reports the nets that changed. When nothing is pending nothing runs and the result is idle.
     *
     * @throws IllegalStateException when more than {@link #STEP_EVENT_BUDGET} events would run at that time
     */
    public StepResult advanceToNextEvent() {
        return advanceToNextEvent(STEP_EVENT_BUDGET);
    }

    /**
     * «Avanzar al próximo flanco de reloj» (M6.1-B): runs event times, each like {@link #advanceToNextEvent()}, until
     * one at which the output of a clock (any clock, also inside a user component) falls from 1 to 0 — the edge the D
     * flip-flops latch on — and stops right after that time, so the next «Avanzar» shows the flip-flops reacting. The
     * result goes from the time before the first one to the edge time and carries the nets that changed at the edge
     * time only. Without a clock nothing runs.
     *
     * @throws IllegalStateException when no such edge comes within {@link #CLOCK_EDGE_TIME_BUDGET} event times, or
     *                               when one event time runs too many events ({@link #advanceToNextEvent()})
     */
    public StepResult advanceToNextClockEdge() {
        return advanceToNextClockEdge(CLOCK_EDGE_TIME_BUDGET);
    }

    StepResult advanceToNextClockEdge(int maximumEventTimes) {
        return advanceToNextClockEdge(maximumEventTimes, STEP_EVENT_BUDGET);
    }

    /** {@link #advanceToNextClockEdge(int)} with each event time limited to {@code maximumEventsPerTime} events. */
    StepResult advanceToNextClockEdge(int maximumEventTimes, int maximumEventsPerTime) {
        long fromTime = now();
        List<RuntimeNet> clockOutputs = clockOutputs();
        if (clockOutputs.isEmpty()) {
            return new StepResult(fromTime, fromTime, Set.of(), nextEventTime());
        }
        for (int eventTimes = 0; eventTimes < maximumEventTimes; eventTimes++) {
            List<Value> before = clockOutputs.stream().map(net -> net.value).toList();
            StepResult step = advanceToNextEvent(maximumEventsPerTime);
            if (step.isIdle()) {
                return new StepResult(fromTime, now(), Set.of(), OptionalLong.empty());
            }
            for (int index = 0; index < clockOutputs.size(); index++) {
                if (before.get(index).equals(Value.ONE) && clockOutputs.get(index).value.equals(Value.ZERO)) {
                    return new StepResult(fromTime, step.time(), step.changedNets(), step.nextEventTime());
                }
            }
        }
        throw new IllegalStateException("no clock edge within " + maximumEventTimes + " event times");
    }

    /** The nets of the clocks' outputs, each once (also nested clocks, and an unconnected clock's private net). */
    private List<RuntimeNet> clockOutputs() {
        List<RuntimeNet> outputs = new ArrayList<>();
        for (ComponentInstance instance : instances.values()) {
            if (instance.type.id().equals(StandardComponentTypes.CLOCK) && !outputs.contains(instance.pins[0].net)) {
                outputs.add(instance.pins[0].net);
            }
        }
        return outputs;
    }

    /**
     * «Avanzar N t» while paused (M6.1-B, spec §4.4): {@link #advance(long)} that also reports every net that changed
     * anywhere in the jump. No event budget, like a running simulation.
     */
    public StepResult advanceBy(long duration) {
        long fromTime = now();
        Set<String> changed = startTracking();
        try {
            advance(duration);
        } finally {
            changedNets = null;
        }
        return new StepResult(fromTime, now(), changed, nextEventTime());
    }

    /** {@link #interact} that also reports the nets that changed (a switch clicked while paused, M6.1-B). */
    public StepResult interactTracked(String componentId) {
        Set<String> changed = startTracking();
        try {
            interact(componentId);
        } finally {
            changedNets = null;
        }
        return new StepResult(now(), now(), changed, nextEventTime());
    }

    /**
     * Runs {@code work} (any calls on this engine: inputs, clicks, steps, an update or a restart) allowing at most
     * {@code maximumEvents} events in all, zero-delay effects and component initialization included (IA.1, A§7: the
     * AI's requests never hang the simulation thread). When the next event would go over the budget the work stops
     * there: the engine stays at the time it reached with that event pending, and nothing is rolled back. Outside this
     * call the engine is unlimited, as for the user. A failure other than the budget reaches the caller.
     *
     * @throws IllegalArgumentException when {@code maximumEvents} is not positive
     */
    public EventBudgetRun runWithEventBudget(long maximumEvents, Runnable work) {
        if (maximumEvents <= 0) {
            throw new IllegalArgumentException("the event budget must be positive: " + maximumEvents);
        }
        long previousLimit = scheduler.eventLimit();
        long start = scheduler.eventsRun();
        scheduler.limitEvents(maximumEvents);
        if (previousLimit < scheduler.eventLimit()) {
            scheduler.restoreEventLimit(previousLimit); // a budget inside another never widens it
        }
        try {
            work.run();
            return new EventBudgetRun(scheduler.eventsRun() - start, false);
        } catch (EventBudgetExceededException exception) {
            return new EventBudgetRun(scheduler.eventsRun() - start, true);
        } finally {
            scheduler.restoreEventLimit(previousLimit);
        }
    }

    /**
     * {@link #advanceToNextEvent()} with another per-time event limit: {@link SimulationThread}'s budgeted step passes
     * {@code Integer.MAX_VALUE} so that only its budget stops a zero-delay loop.
     */
    StepResult advanceToNextEvent(int maximumEvents) {
        long fromTime = now();
        OptionalLong next = nextEventTime();
        if (next.isEmpty()) {
            return StepResult.idle(fromTime);
        }
        Set<String> changed = startTracking();
        try {
            scheduler.runUntil(next.getAsLong(), maximumEvents);
        } finally {
            changedNets = null;
        }
        return new StepResult(fromTime, now(), changed, nextEventTime());
    }

    private Set<String> startTracking() {
        Set<String> changed = new LinkedHashSet<>();
        changedNets = changed;
        return changed;
    }

    /**
     * The components with an event at {@link #nextEventTime()} (M6.1-B): the gates whose output is about to change and
     * the clocks about to tick, by flat id (a nested one as {@code instance/child}). Empty when nothing is pending.
     * Walks the event queue: call it while paused.
     */
    public Set<String> pendingComponents() {
        OptionalLong next = nextEventTime();
        if (next.isEmpty()) {
            return Set.of();
        }
        Set<String> pending = new LinkedHashSet<>();
        for (ComponentInstance owner : scheduler.ownersAt(next.getAsLong())) {
            if (!owner.removed) {
                pending.add(owner.id);
            }
        }
        return Collections.unmodifiableSet(pending);
    }

    /**
     * Binds waveform channels: channel id → net id (a top-level id, an alias or a nested id; an empty or unknown id
     * records a gap). Channels left out are dropped with their history; the others keep it, even when their net id
     * changed (spec §3.3). Every channel is sampled now.
     */
    public void setProbes(Map<String, String> channels) {
        recorder.setChannels(channels);
        rebind();
    }

    /** «Limpiar trazas»: drops every recorded event; each channel starts again at its current value. */
    public void clearWaveforms() {
        recorder.clear();
        rebind();
    }

    /** Grows whenever a trace or the set of channels changes. */
    public long waveformVersion() {
        return recorder.version();
    }

    public boolean hasWaveformChannels() {
        return recorder.hasChannels();
    }

    public WaveformSnapshot waveforms() {
        return recorder.snapshot(now());
    }

    /**
     * The value net {@code netId} (or one of its aliases) carries now, as {@link #snapshot()} would give it, without
     * copying every net: high impedance for an unknown id.
     */
    public Value netValue(String netId) {
        RuntimeNet net = netWithId(netId);
        return net == null ? Value.HIGH_IMPEDANCE : net.value;
    }

    private RuntimeNet netWithId(String id) {
        RuntimeNet net = nets.get(id);
        return net != null ? net : aliasRoots.get(id);
    }

    void netChanged(RuntimeNet net) {
        if (net != null && net.resolve(now())) {
            if (!net.recorders.isEmpty()) {
                recorder.record(net, now());
            }
            if (changedNets != null) {
                changedNets.add(net.id);
                changedNets.addAll(net.aliases);
            }
            deliver(net);
        }
    }

    private void deliver(RuntimeNet net) {
        for (PinState pin : List.copyOf(net.pins)) {
            if (!pin.spec.isInput() || pin.instance.removed || pin.net != net) {
                continue;
            }
            Value seen = net.valueFor(pin);
            if (!seen.equals(pin.lastSeen)) {
                pin.lastSeen = seen;
                pin.instance.behavior.inputChanged(pin.instance, pin.index);
            }
        }
    }

    /**
     * The soft warning of a 7-segment display whose bus input and at least one bit input are both joined to a driver
     * pin (a wiring check: what the drivers drive does not matter).
     */
    private static Optional<Problem> displayBusOverride(ComponentInstance instance) {
        if (!StandardComponentTypes.isSevenSegmentsDisplay(instance.type.id())) {
            return Optional.empty();
        }
        int busPin = StandardComponentTypes.sevenSegmentsBusPin(instance.type);
        RuntimeNet bus = instance.pins[busPin].net;
        if (!hasDriver(bus)) {
            return Optional.empty();
        }
        for (int bit = 0; bit < busPin; bit++) {
            if (hasDriver(instance.pins[bit].net)) {
                return Optional.of(new DisplayBusOverride(bus.id, instance.id));
            }
        }
        return Optional.empty();
    }

    private static boolean hasDriver(RuntimeNet net) {
        return net != null && net.pins.stream().anyMatch(pin -> pin.spec.isDriver());
    }

    public SimulationSnapshot snapshot() {
        Map<String, Value> netValues = new HashMap<>();
        List<Problem> problems = new ArrayList<>();
        for (RuntimeNet net : nets.values()) {
            netValues.put(net.id, net.value);
            for (String alias : net.aliases) {
                netValues.put(alias, net.value);
            }
            if (net.widthMismatch) {
                problems.add(new WidthMismatch(net.id, net.pins.stream()
                        .map(pin -> new FlatPinRef(pin.instance.id, pin.index))
                        .toList()));
            } else if (net.value.conflict()) {
                problems.add(new ShortCircuit(net.id, net.pins.stream()
                        .filter(pin -> pin.spec.isDriver() && !pin.driven.isFullyHighImpedance())
                        .map(pin -> new DriverRef(pin.instance.id, pin.index, pin.driven))
                        .toList(), net.conflictSince));
            }
        }
        problems.addAll(tunnelProblems);
        problems.addAll(connectivityProblems);
        Map<String, VisibleState> states = new HashMap<>();
        for (ComponentInstance instance : instances.values()) {
            displayBusOverride(instance).ifPresent(problems::add);
            VisibleState state = instance.behavior.visibleState();
            if (!(state instanceof VisibleState.None)) {
                states.put(instance.id, state);
            }
        }
        return new SimulationSnapshot(revision, token, now(), false, netValues, states, problems, Optional.empty());
    }
}
