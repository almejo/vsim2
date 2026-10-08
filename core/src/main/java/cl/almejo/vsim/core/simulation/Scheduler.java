package cl.almejo.vsim.core.simulation;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Discrete event queue (port of VSim's Heap + Scheduler; ties run in insertion order). */
final class Scheduler {

    private record Entry(long time, long sequence, Runnable action) {
    }

    private final PriorityQueue<Entry> queue = new PriorityQueue<>(
            Comparator.comparingLong(Entry::time).thenComparingLong(Entry::sequence));
    private long now;
    private long nextSequence;

    long now() {
        return now;
    }

    void schedule(long delay, Runnable action) {
        if (delay < 0) {
            throw new IllegalArgumentException("negative delay: " + delay);
        }
        queue.add(new Entry(now + delay, nextSequence++, action));
    }

    /** Runs every event with time &lt;= {@code until} (including ones scheduled meanwhile), then sets now = until. */
    void runUntil(long until) {
        if (until < now) {
            throw new IllegalArgumentException("cannot go back in time from " + now + " to " + until);
        }
        while (!queue.isEmpty() && queue.peek().time() <= until) {
            Entry entry = queue.poll();
            now = entry.time();
            entry.action().run();
        }
        now = until;
    }

    int pendingEvents() {
        return queue.size();
    }
}
