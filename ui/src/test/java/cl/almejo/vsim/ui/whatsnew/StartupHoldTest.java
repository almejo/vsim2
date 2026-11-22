package cl.almejo.vsim.ui.whatsnew;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What waits for the start's recovery question and its documents. */
class StartupHoldTest {

    private final StartupHold hold = new StartupHold();
    private final List<String> events = new ArrayList<>();

    @Test
    void withoutAHoldWhatWaitsRunsAtOnce() {
        hold.whenReleased(() -> events.add("now"));

        assertThat(events).containsExactly("now");
    }

    @Test
    void whileHeldNothingRunsAndTheReleaseRunsItInOrderOnce() {
        hold.hold();
        hold.whenReleased(() -> events.add("first"));
        hold.whenReleased(() -> events.add("second"));
        assertThat(events).isEmpty();

        hold.release();
        hold.release();

        assertThat(events).containsExactly("first", "second");
    }

    @Test
    void afterTheReleaseWhatWaitsRunsAtOnceAgain() {
        hold.hold();
        hold.release();

        hold.whenReleased(() -> events.add("late"));

        assertThat(events).containsExactly("late");
    }

    @Test
    void aFailingWaiterDoesNotStopTheOthers() {
        hold.hold();
        hold.whenReleased(() -> {
            throw new IllegalStateException("broken");
        });
        hold.whenReleased(() -> events.add("second"));

        hold.release();

        assertThat(events).containsExactly("second");
    }
}
