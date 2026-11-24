package cl.almejo.vsim.ui.panels;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.FocusListener;
import org.junit.jupiter.api.Test;

class FocusEventsTest {

    @Test
    void lostRunsOnlyWhenTheFocusIsLost() {
        int[] runs = {0};
        FocusListener listener = FocusEvents.onLost(() -> runs[0]++);

        listener.focusGained(null);
        assertThat(runs[0]).isZero();
        listener.focusLost(null);
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void gainedRunsOnlyWhenTheFocusIsGained() {
        int[] runs = {0};
        FocusListener listener = FocusEvents.onGained(() -> runs[0]++);

        listener.focusLost(null);
        assertThat(runs[0]).isZero();
        listener.focusGained(null);
        assertThat(runs[0]).isEqualTo(1);
    }
}
