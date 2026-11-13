package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresentingWindowsTest {

    private final List<Boolean> heard = new ArrayList<>();
    private final PresentingWindows<String> windows = new PresentingWindows<>(heard::add);

    @Test
    void theInterfaceStaysLargeUntilTheLastPresentingWindowLeaves() {
        windows.changed("A", true);
        windows.changed("B", true);
        windows.changed("A", false);
        windows.changed("B", false);

        assertThat(heard).containsExactly(true, true, true, false);
    }

    @Test
    void enteringTwiceIsOneWindow() {
        windows.changed("A", true);
        windows.changed("A", true);
        windows.changed("A", false);

        assertThat(heard).containsExactly(true, true, false);
    }

    @Test
    void aClosedWindowThatNeverLeftIsForgotten() {
        windows.changed("A", true);
        windows.closed("A");
        windows.closed("A");
        windows.closed("B");

        assertThat(heard).containsExactly(true, false);
    }
}
