package cl.almejo.vsim.core.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The event budget every run of the scheduler honours while one is set (IA.1: the AI's runs never hang). */
class SchedulerEventBudgetTest {

    /** An event that schedules itself again with no delay: time never moves. */
    private static void zeroDelayLoop(Scheduler scheduler) {
        scheduler.schedule(0, new Runnable() {
            @Override
            public void run() {
                scheduler.schedule(0, this);
            }
        });
    }

    @Test
    void aZeroDelayLoopStopsAtTheBudgetOfThePlainRun() {
        Scheduler scheduler = new Scheduler();
        scheduler.schedule(3, () -> zeroDelayLoop(scheduler));
        scheduler.limitEvents(50);

        assertThatThrownBy(() -> scheduler.runUntil(10)).isInstanceOf(EventBudgetExceededException.class);

        assertThat(scheduler.eventsRun()).isEqualTo(50);
        assertThat(scheduler.now()).as("the time reached, not the time asked for").isEqualTo(3);
        assertThat(scheduler.pendingEvents()).as("the event that was not run stays queued").isEqualTo(1);
    }

    @Test
    void theBudgetCoversEveryRunUntilItIsLifted() {
        Scheduler scheduler = new Scheduler();
        List<Long> times = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            scheduler.schedule(index, () -> times.add(scheduler.now()));
        }
        scheduler.limitEvents(3);
        scheduler.runUntil(2);
        assertThatThrownBy(() -> scheduler.runUntil(5, 100)).isInstanceOf(EventBudgetExceededException.class);
        assertThat(times).containsExactly(1L, 2L, 3L);

        scheduler.unlimitEvents();
        scheduler.runUntil(5);

        assertThat(times).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(scheduler.eventsRun()).isEqualTo(5);
    }

    @Test
    void aBudgetThatIsNotReachedChangesNothing() {
        Scheduler scheduler = new Scheduler();
        scheduler.schedule(1, () -> { });
        scheduler.schedule(2, () -> { });
        scheduler.limitEvents(2);

        scheduler.runUntil(10);

        assertThat(scheduler.now()).isEqualTo(10);
        assertThat(scheduler.eventsRun()).isEqualTo(2);
    }
}
