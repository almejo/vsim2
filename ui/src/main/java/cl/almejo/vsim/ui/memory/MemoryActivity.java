package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.ui.paint.MemoryLights;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Which lights of each memory a view shows (memory spec §3): the RD light is lit when the memory read since the frame
 * before, the WR light when it wrote, both when it did both — a counter that moves the address right after each write
 * would otherwise hide every write behind the next read. A light that comes on stays on at least {@link #HOLD_NANOS}
 * of wall time while the simulation runs, so an access never shows as a flicker shorter than a frame, and while the
 * simulation time does not move (paused, after a step) until the next step.
 *
 * <p>It compares each memory's read and write tallies ({@link VisibleState.LastRead}, {@link VisibleState.LastWrite})
 * with the ones of the frame before, so it costs nothing on the simulation thread. One per view (the canvas, the
 * content editor), used on the EDT; a view that sees a memory for the first time lights nothing.
 */
public final class MemoryActivity {

    /** How long a light stays on at least while the simulation runs: 200 ms. */
    public static final long HOLD_NANOS = 200_000_000L;
    /** Above this many memories remembered the oldest are forgotten (the ids of deleted memories). */
    private static final int MAXIMUM_REMEMBERED = 1024;

    private final LongSupplier nanoClock;
    private final Map<String, Remembered> remembered = new HashMap<>();

    /** One kind of access of one memory as last seen, and until when its light stays on. */
    private static final class Hold {
        private long count;
        private long time;
        private boolean lit;
        private long litUntilNanos;
        private long litAtSimulationTime;

        Hold(long count, long time) {
            this.count = count;
            this.time = time;
        }

        /** Whether the light is on in this frame, after taking in the tally seen now. */
        boolean update(long seenCount, long seenTime, long address, long nowNanos, long simulationTime) {
            if (seenCount != count || seenTime != time) {
                count = seenCount;
                time = seenTime;
                lit = address >= 0;
                litUntilNanos = nowNanos + HOLD_NANOS;
                litAtSimulationTime = simulationTime;
            }
            if (address < 0) {
                lit = false;
            }
            if (lit && nowNanos - litUntilNanos >= 0 && simulationTime != litAtSimulationTime) {
                lit = false;
            }
            return lit;
        }
    }

    private record Remembered(Hold reads, Hold writes) {
    }

    /** A tracker on the system's clock. */
    public MemoryActivity() {
        this(System::nanoTime);
    }

    /** A tracker on {@code nanoClock} (a fake one in tests). */
    public MemoryActivity(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /**
     * The lights of memory {@code componentId} in this frame, whose {@code state} comes from a snapshot at
     * {@code simulationTime}. Each call is one frame of this view.
     */
    public MemoryLights lights(String componentId, VisibleState state, long simulationTime) {
        if (!(state instanceof VisibleState.MemoryState memory)) {
            remembered.remove(componentId);
            return MemoryLights.IDLE;
        }
        VisibleState.LastRead read = memory.lastRead();
        VisibleState.LastWrite write = memory.lastWrite();
        Remembered known = remembered.get(componentId);
        if (known == null) {
            if (remembered.size() >= MAXIMUM_REMEMBERED) {
                remembered.clear();
            }
            remembered.put(componentId, new Remembered(new Hold(read.count(), read.time()),
                    new Hold(write.count(), write.time())));
            return MemoryLights.IDLE;
        }
        long now = nanoClock.getAsLong();
        boolean reading = known.reads().update(read.count(), read.time(), read.address(), now, simulationTime);
        boolean writing = known.writes().update(write.count(), write.time(), write.address(), now, simulationTime);
        return new MemoryLights(reading, reading ? read.address() : -1, writing, writing ? write.address() : -1);
    }
}
