package cl.almejo.vsim.core.simulation;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Discrete event queue (port of VSim's Heap + Scheduler; ties run in insertion order). The pending events are grouped
 * by time: each time with events has one {@link Moment}, the list of them in scheduling order, and the moments sit in
 * an array sorted by time. A circuit has few times pending at once (the gate delays ahead of now and the next clock
 * edge), so finding a moment is a short binary search and running an event moves nothing.
 */
final class Scheduler {

    /** A pending event: what to run and, for {@link #ownersAt(long)}, whose it is. Each one is scheduled once. */
    abstract static class Event {

        /** The component whose behavior scheduled the event (M6.1-B), null for nobody's. */
        final ComponentInstance owner;
        /** The event scheduled after this one for the same time. */
        private Event next;

        Event(ComponentInstance owner) {
            this.owner = owner;
        }

        abstract void run();
    }

    /** An event that runs a {@link Runnable}. */
    private static final class ActionEvent extends Event {

        private final Runnable action;

        ActionEvent(ComponentInstance owner, Runnable action) {
            super(owner);
            this.action = action;
        }

        @Override
        void run() {
            action.run();
        }
    }

    /** The events due at one time, in scheduling order. A moment is in the queue exactly while it has events. */
    private static final class Moment {

        final long time;
        Event first;
        Event last;

        Moment(long time) {
            this.time = time;
        }

        void append(Event event) {
            if (last == null) {
                first = event;
            } else {
                last.next = event;
            }
            last = event;
        }

        Event removeFirst() {
            Event event = first;
            first = event.next;
            if (first == null) {
                last = null;
            }
            event.next = null;
            return event;
        }
    }

    /** The moments with events, sorted by time, in {@code moments[head..end)}: the earliest one at {@code head}. */
    private Moment[] moments = new Moment[16];
    private int head;
    private int end;
    private int pendingEvents;
    private long now;
    private long eventsRun;
    /** The value of {@link #eventsRun} at which the budget is used up; {@code Long.MAX_VALUE}: no budget. */
    private long eventLimit = Long.MAX_VALUE;

    long now() {
        return now;
    }

    /** Schedules {@code action} as nobody's event. */
    void schedule(long delay, Runnable action) {
        schedule(delay, new ActionEvent(null, action));
    }

    /** Schedules {@code event} for now + {@code delay}, after every event already due then. */
    void schedule(long delay, Event event) {
        if (delay < 0) {
            throw new IllegalArgumentException("negative delay: " + delay);
        }
        momentAt(now + delay).append(event);
        pendingEvents++;
    }

    /**
     * The owners of the events due at {@code time} (M6.1-B pending outline), in scheduling order, each once; events
     * without an owner are left out.
     */
    Set<ComponentInstance> ownersAt(long time) {
        Set<ComponentInstance> owners = new LinkedHashSet<>();
        int index = indexOf(time);
        if (index >= 0) {
            for (Event event = moments[index].first; event != null; event = event.next) {
                if (event.owner != null) {
                    owners.add(event.owner);
                }
            }
        }
        return owners;
    }

    /**
     * Runs every event with time &lt;= {@code until} (including ones scheduled meanwhile), then sets now = until. With a
     * budget set ({@link #limitEvents}) it throws {@link EventBudgetExceededException} instead of going over it,
     * leaving now at the time of the last event it ran.
     */
    void runUntil(long until) {
        if (until < now) {
            throw new IllegalArgumentException("cannot go back in time from " + now + " to " + until);
        }
        while (head < end && moments[head].time <= until) {
            runNext();
        }
        now = until;
    }

    /** The time of the earliest pending event (it may equal {@link #now()}); empty when nothing is pending. */
    OptionalLong nextEventTime() {
        return head < end ? OptionalLong.of(moments[head].time) : OptionalLong.empty();
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
        while (head < end && moments[head].time <= until) {
            if (ran == maximumEvents) {
                throw new IllegalStateException("too many events at t = " + moments[head].time);
            }
            runNext();
            ran++;
        }
        now = until;
    }

    /** «Reiniciar» (M6.1-B): drops every pending event and goes back to time 0. */
    void reset() {
        Arrays.fill(moments, head, end, null);
        head = 0;
        end = 0;
        pendingEvents = 0;
        now = 0;
    }

    int pendingEvents() {
        return pendingEvents;
    }

    /**
     * From now on every run (both {@code runUntil}s) stops with {@link EventBudgetExceededException} before it would
     * run more than {@code maximumEvents} further events, until {@link #unlimitEvents()} (IA.1: the AI's runs).
     */
    void limitEvents(long maximumEvents) {
        if (maximumEvents < 0) {
            throw new IllegalArgumentException("negative event budget: " + maximumEvents);
        }
        eventLimit = maximumEvents > Long.MAX_VALUE - eventsRun ? Long.MAX_VALUE : eventsRun + maximumEvents;
    }

    /** Takes back {@link #limitEvents}: the runs are unlimited again, as the user's always are. */
    void unlimitEvents() {
        eventLimit = Long.MAX_VALUE;
    }

    /** How many events ran since this scheduler was made (never reset, not even by {@link #reset()}). */
    long eventsRun() {
        return eventsRun;
    }

    /** The event limit in force (an {@link #eventsRun()} value), {@code Long.MAX_VALUE} when there is none. */
    long eventLimit() {
        return eventLimit;
    }

    /** Restores a limit read with {@link #eventLimit()}. */
    void restoreEventLimit(long limit) {
        eventLimit = limit;
    }

    /**
     * Runs the earliest event, counting it, or throws when the budget is used up. Its moment leaves the queue before
     * the event runs when that was its last event, so an event the run schedules for the same time starts a new one.
     */
    private void runNext() {
        Moment moment = moments[head];
        if (eventsRun >= eventLimit) {
            throw new EventBudgetExceededException(moment.time);
        }
        eventsRun++;
        Event event = moment.removeFirst();
        if (moment.first == null) {
            moments[head] = null;
            head++;
            if (head == end) {
                head = 0;
                end = 0;
            }
        }
        pendingEvents--;
        now = moment.time;
        event.run();
    }

    /** The moment of {@code time}, made and sorted in when there was none. */
    private Moment momentAt(long time) {
        int index = indexOf(time);
        if (index >= 0) {
            return moments[index];
        }
        Moment moment = new Moment(time);
        insert(-index - 1, moment);
        return moment;
    }

    /** The index of the moment of {@code time}, or {@code -(insertion point) - 1} when there is none. */
    private int indexOf(long time) {
        int low = head;
        int high = end - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long middleTime = moments[middle].time;
            if (middleTime < time) {
                low = middle + 1;
            } else if (middleTime > time) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -low - 1;
    }

    /** Puts {@code moment} at {@code index}, the insertion point that keeps the array sorted. */
    private void insert(int index, Moment moment) {
        if (index == head && head > 0) {
            head--;
            moments[head] = moment;
            return;
        }
        if (end == moments.length) {
            if (head > 0) {
                int count = end - head;
                System.arraycopy(moments, head, moments, 0, count);
                Arrays.fill(moments, count, end, null);
                index -= head;
                head = 0;
                end = count;
            } else {
                moments = Arrays.copyOf(moments, moments.length * 2);
            }
        }
        System.arraycopy(moments, index, moments, index + 1, end - index);
        moments[index] = moment;
        end++;
    }
}
