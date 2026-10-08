package cl.almejo.vsim.core.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * Runs a {@link SimulationEngine} on its own thread. Every request is queued and applied there;
 * other threads only read the immutable {@link #latestSnapshot()}.
 */
public final class SimulationThread implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationThread.class);
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final double MAXIMUM_MILLISECONDS_PER_TICK = 250.0;
    private static final long PARK_NANOS = 2 * NANOS_PER_MILLISECOND;

    private final SimulationEngine engine;
    private final LongSupplier nanoClock;
    private final Queue<Runnable> requests = new ConcurrentLinkedQueue<>();
    private volatile SimulationSnapshot latestSnapshot = SimulationSnapshot.EMPTY;
    private volatile boolean stopRequested;
    private Thread thread;

    // Only touched on the simulation thread (or by tick() in tests).
    private boolean running;
    private double speed = 1.0;
    private double pendingUnits;
    private long lastTickNanos = -1;

    public SimulationThread(SimulationEngine engine) {
        this(engine, System::nanoTime);
    }

    SimulationThread(SimulationEngine engine, LongSupplier nanoClock) {
        this.engine = engine;
        this.nanoClock = nanoClock;
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
        requests.add(() -> engine.update(circuit));
    }

    public void interact(String componentId) {
        requests.add(() -> engine.interact(componentId));
    }

    public void resume() {
        requests.add(() -> running = true);
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

    public SimulationSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    void tick() {
        long now = nanoClock.getAsLong();
        long elapsedNanos = lastTickNanos < 0 ? 0 : now - lastTickNanos;
        lastTickNanos = now;
        try {
            Runnable request;
            while ((request = requests.poll()) != null) {
                request.run();
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
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Simulation failed; pausing", exception);
            running = false;
        }
        latestSnapshot = engine.snapshot().withRunning(running);
    }

    private void loop() {
        while (!stopRequested) {
            tick();
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
                current.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
