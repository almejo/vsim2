package cl.almejo.vsim.core.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulerTest {

    @Test
    void runsEventsInTimeThenInsertionOrder() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        scheduler.schedule(5, () -> log.add("late"));
        scheduler.schedule(1, () -> log.add("first"));
        scheduler.schedule(1, () -> log.add("second"));
        scheduler.runUntil(10);
        assertThat(log).containsExactly("first", "second", "late");
        assertThat(scheduler.now()).isEqualTo(10);
    }

    @Test
    void stopsAtTheRequestedTimeAndKeepsLaterEvents() {
        Scheduler scheduler = new Scheduler();
        List<Long> times = new ArrayList<>();
        scheduler.schedule(3, () -> times.add(scheduler.now()));
        scheduler.schedule(8, () -> times.add(scheduler.now()));
        scheduler.runUntil(5);
        assertThat(times).containsExactly(3L);
        assertThat(scheduler.pendingEvents()).isEqualTo(1);
        scheduler.runUntil(8);
        assertThat(times).containsExactly(3L, 8L);
    }

    @Test
    void zeroDelayEventsScheduledWhileRunningRunInTheSameCall() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        scheduler.schedule(0, () -> scheduler.schedule(0, () -> log.add("chained")));
        scheduler.runUntil(0);
        assertThat(log).containsExactly("chained");
    }

    @Test
    void rejectsNegativeDelayAndGoingBackInTime() {
        Scheduler scheduler = new Scheduler();
        assertThatThrownBy(() -> scheduler.schedule(-1, () -> { })).isInstanceOf(IllegalArgumentException.class);
        scheduler.runUntil(5);
        assertThatThrownBy(() -> scheduler.runUntil(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextEventTimeIsTheEarliestPendingTimeAndEmptyWhenNothingIsPending() {
        Scheduler scheduler = new Scheduler();
        assertThat(scheduler.nextEventTime()).isEmpty();
        scheduler.schedule(5, () -> { });
        scheduler.schedule(2, () -> { });
        scheduler.schedule(2, () -> { });
        assertThat(scheduler.nextEventTime()).hasValue(2);
        scheduler.runUntil(2);
        assertThat(scheduler.nextEventTime()).hasValue(5);
        scheduler.runUntil(5);
        assertThat(scheduler.nextEventTime()).isEmpty();
    }

    @Test
    void aZeroDelayEventIsPendingAtTheCurrentTime() {
        Scheduler scheduler = new Scheduler();
        scheduler.runUntil(7);
        scheduler.schedule(0, () -> { });
        assertThat(scheduler.nextEventTime()).hasValue(7);
    }

    @Test
    void eventsWithoutAnOwnerAreNeverListedAsPending() {
        Scheduler scheduler = new Scheduler();
        scheduler.schedule(2, () -> { });

        assertThat(scheduler.nextEventTime()).hasValue(2);
        assertThat(scheduler.ownersAt(2)).isEmpty();
        assertThat(scheduler.ownersAt(3)).as("a time with nothing pending").isEmpty();
    }

    @Test
    void aZeroDelayEventScheduledWhileRunningGoesAfterTheOnesAlreadyDueThen() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        scheduler.schedule(1, () -> {
            log.add("first");
            scheduler.schedule(0, () -> log.add("chained"));
        });
        scheduler.schedule(1, () -> log.add("second"));
        scheduler.runUntil(1);
        assertThat(log).containsExactly("first", "second", "chained");
    }

    @Test
    void anEventForATimeEarlierThanEveryPendingOneGoesFirst() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        scheduler.schedule(5, () -> log.add("5"));
        scheduler.schedule(3, () -> log.add("3a"));
        scheduler.schedule(8, () -> log.add("8"));
        scheduler.schedule(1, () -> log.add("1"));
        scheduler.schedule(3, () -> log.add("3b"));
        scheduler.runUntil(2);
        scheduler.schedule(0, () -> log.add("2"));
        scheduler.runUntil(10);
        assertThat(log).containsExactly("1", "2", "3a", "3b", "5", "8");
    }

    @Test
    void keepsTimeThenInsertionOrderWithManyTimesPendingAcrossRuns() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        List<long[]> expected = new ArrayList<>();
        int sequence = 0;
        for (int index = 0; index < 120; index++) {
            long time = (index * 37) % 23 + 1;
            expected.add(new long[] {time, sequence});
            String entry = time + "#" + sequence++;
            scheduler.schedule(time, () -> log.add(entry));
        }
        scheduler.runUntil(10);
        for (int index = 0; index < 120; index++) {
            long time = 10 + (index * 53) % 41;
            expected.add(new long[] {time, sequence});
            String entry = time + "#" + sequence++;
            scheduler.schedule(time - 10, () -> log.add(entry));
        }
        scheduler.runUntil(100);
        expected.sort(Comparator.comparingLong(entry -> entry[0]));
        assertThat(log).containsExactlyElementsOf(expected.stream().map(entry -> entry[0] + "#" + entry[1]).toList());
        assertThat(scheduler.pendingEvents()).isZero();
    }

    @Test
    void anEventThatFailsLeavesTheOthersPending() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        scheduler.schedule(1, () -> {
            throw new IllegalStateException("boom");
        });
        scheduler.schedule(1, () -> log.add("same time"));
        scheduler.schedule(2, () -> log.add("later"));
        assertThatThrownBy(() -> scheduler.runUntil(5)).hasMessage("boom");
        assertThat(scheduler.now()).isEqualTo(1);
        assertThat(scheduler.pendingEvents()).isEqualTo(2);
        assertThat(scheduler.nextEventTime()).hasValue(1);
        scheduler.runUntil(5);
        assertThat(log).containsExactly("same time", "later");
    }

    @Test
    void resetDropsEveryPendingEventAndGoesBackToZero() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        scheduler.schedule(2, () -> log.add("dropped"));
        scheduler.schedule(4, () -> log.add("dropped too"));
        scheduler.runUntil(1);
        scheduler.reset();
        assertThat(scheduler.pendingEvents()).isZero();
        assertThat(scheduler.nextEventTime()).isEmpty();
        assertThat(scheduler.now()).isZero();
        scheduler.schedule(1, () -> log.add("fresh"));
        scheduler.runUntil(4);
        assertThat(log).containsExactly("fresh");
    }

    @Test
    void anEventRunsItself() {
        Scheduler scheduler = new Scheduler();
        List<String> log = new ArrayList<>();
        scheduler.schedule(3, new Scheduler.Event(null) {
            @Override
            void run() {
                log.add("ran at " + scheduler.now());
            }
        });
        scheduler.runUntil(3);
        assertThat(log).containsExactly("ran at 3");
    }
}
