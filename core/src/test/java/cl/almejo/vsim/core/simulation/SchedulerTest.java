package cl.almejo.vsim.core.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
}
