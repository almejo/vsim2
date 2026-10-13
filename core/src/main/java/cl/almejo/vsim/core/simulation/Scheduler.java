package cl.almejo.vsim.core.simulation;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Set;

/** Discrete event queue (port of VSim's Heap + Scheduler; ties run in insertion order). */
final class Scheduler {

    /** {@code owner}: the component whose behavior scheduled the event (M6.1-B), null for nobody's. */
    private record Entry(long time, long sequence, ComponentInstance owner, Runnable action) {
    }

    private final PriorityQueue<Entry> queue = new PriorityQueue<>(
            Comparator.comparingLong(Entry::time).thenComparingLong(Entry::sequence));
    private long now;
    private long nextSequence;

    long now() {
        return now;
    }

    void schedule(long delay, Runnable action) {
        schedule(delay, null, action);
    }

    /** Schedules {@code action} as an event of {@code owner} (null: nobody's), see {@link #ownersAt(long)}. */
    void schedule(long delay, ComponentInstance owner, Runnable action) {
        if (delay < 0) {
            throw new IllegalArgumentException("negative delay: " + delay);
        }
        queue.add(new Entry(now + delay, nextSequence++, owner, action));
    }

    /**
     * The owners of the events due at {@code time} (M6.1-B pending outline), each once; events without an owner are
     * left out. It walks the whole queue, so it is meant for a paused simulation.
     */
    Set<ComponentInstance> ownersAt(long time) {
        Set<ComponentInstance> owners = new LinkedHashSet<>();
        for (Entry entry : queue) {
            if (entry.time() == time && entry.owner() != null) {
                owners.add(entry.owner());
            }
        }
        return owners;
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

    /** The time of the earliest pending event (it may equal {@link #now()}); empty when nothing is pending. */
    OptionalLong nextEventTime() {
        Entry next = queue.peek();
        return next == null ? OptionalLong.empty() : OptionalLong.of(next.time());
    }

    /**
     * {@link #runUntil(long)} with a limit (M6.1-B «Avanzar»): before running event number {@code maximumEvents + 1}
     * it throws, because a zero-delay loop would never let time move.
     *
     * @throws IllegalStateException "too many events at t = …" with the time of the event that was not run
     */
    void runUntil(long until, int maximumEvents) {
        if (until < now) {
            throw new IllegalArgumentException("cannot go back in time from " + now + " to " + until);
        }
        int ran = 0;
        while (!queue.isEmpty() && queue.peek().time() <= until) {
            if (ran == maximumEvents) {
                throw new IllegalStateException("too many events at t = " + queue.peek().time());
            }
            Entry entry = queue.poll();
            now = entry.time();
            entry.action().run();
            ran++;
        }
        now = until;
    }

    /** «Reiniciar» (M6.1-B): drops every pending event and goes back to time 0. */
    void reset() {
        queue.clear();
        now = 0;
    }

    int pendingEvents() {
        return queue.size();
    }
}
