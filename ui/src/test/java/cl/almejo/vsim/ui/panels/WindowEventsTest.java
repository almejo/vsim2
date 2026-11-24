package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import java.awt.event.WindowListener;

import static org.assertj.core.api.Assertions.assertThat;

class WindowEventsTest {

    @Test
    void closingRunsOnlyOnClosing() {
        int[] runs = {0};
        WindowListener listener = WindowEvents.onClosing(() -> runs[0]++);

        listener.windowOpened(null);
        listener.windowClosed(null);
        assertThat(runs[0]).isZero();
        listener.windowClosing(null);
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void openedRunsOnlyOnOpened() {
        int[] runs = {0};
        WindowListener listener = WindowEvents.onOpened(() -> runs[0]++);

        listener.windowClosing(null);
        listener.windowClosed(null);
        assertThat(runs[0]).isZero();
        listener.windowOpened(null);
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void closedRunsOnlyOnClosed() {
        int[] runs = {0};
        WindowListener listener = WindowEvents.onClosed(() -> runs[0]++);

        listener.windowClosing(null);
        listener.windowOpened(null);
        assertThat(runs[0]).isZero();
        listener.windowClosed(null);
        assertThat(runs[0]).isEqualTo(1);
    }
}
