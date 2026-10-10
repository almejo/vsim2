package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;
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

    private final SimulationEngine engine;
    private final LongSupplier nanoClock;
    private final long closeTimeoutMillis;
    private final Queue<Runnable> requests = new ConcurrentLinkedQueue<>();
    private volatile SimulationSnapshot latestSnapshot = SimulationSnapshot.EMPTY;
    private volatile WaveformSnapshot latestWaveforms = WaveformSnapshot.EMPTY;
    private volatile boolean stopRequested;
    private Thread thread;

    // Only touched on the simulation thread (or by tick() in tests).
    private boolean running;
    private double speed = 1.0;
    private double pendingUnits;
    private boolean firstTick = true;
    private long lastTickNanos;
    private String failure;
    private boolean pauseOnShortCircuit;
    private Set<String> shortCircuitNets = Set.of();
    /** True when the engine or the run state changed since the last published snapshot. */
    private boolean snapshotStale = true;
    private boolean waveformsPublished;
    private long lastWaveformPublishNanos;

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
     * The circuit could not be prepared ({@code cause}): pauses and publishes the failure, keeping the last circuit
     * until the next {@link #update}.
     */
    public void reportFailure(Throwable cause) {
        requests.add(() -> publishFailure(cause));
    }

    public void interact(String componentId) {
        requests.add(() -> engine.interact(componentId));
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

    public void step(long duration) {
        requests.add(() -> {
            if (!running) {
                engine.advance(duration);
            }
        });
    }

    public void setSpeed(double unitsPerMillisecond) {
        if (!(unitsPerMillisecond > 0)) {
            throw new IllegalArgumentException("speed must be positive: " + unitsPerMillisecond);
        }
        requests.add(() -> speed = unitsPerMillisecond);
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
     * The recorded waveforms, republished at most every 16 ms when a trace changes or, with channels, when the
     * simulation time moves.
     */
    public WaveformSnapshot latestWaveforms() {
        return latestWaveforms;
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
            }
            if (running) {
                double elapsedMilliseconds = Math.min(elapsedNanos / (double) NANOS_PER_MILLISECOND,
                        MAXIMUM_MILLISECONDS_PER_TICK);
                pendingUnits += elapsedMilliseconds * speed;
                long units = (long) Math.floor(pendingUnits);
                pendingUnits -= units;
                if (units > 0) {
                    engine.advance(units);
                }
                snapshotStale = true;
            }
            if (snapshotStale) {
                SimulationSnapshot snapshot = engine.snapshot();
                pauseOnNewShortCircuit(snapshot);
                latestSnapshot = snapshot.withRunning(running).withFailure(Optional.ofNullable(failure));
                snapshotStale = false;
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Simulation failed; pausing", exception);
            publishFailure(exception);
        }
        // Also after a failure: the channels of a rejected circuit are still bound and shown.
        publishWaveforms(now);
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

    private void publishFailure(Throwable throwable) {
        running = false;
        failure = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
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
                    return;
                }
            }
            LockSupport.parkNanos(PARK_NANOS);
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
    }
}
