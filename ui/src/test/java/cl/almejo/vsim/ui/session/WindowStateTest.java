package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowStateTest {

    private static final ViewState VIEW = new ViewState(1.5, 10, -4);
    private static final Rectangle LAPTOP = new Rectangle(0, 0, 1920, 1080);
    private static final Rectangle RIGHT_MONITOR = new Rectangle(1920, 0, 2560, 1440);

    @Test
    void aWindowOnAScreenStaysWhereItWas() {
        WindowState state = new WindowState(100, 50, 1280, 820, false, VIEW);
        assertThat(state.clampedTo(List.of(LAPTOP))).isEqualTo(state);
    }

    @Test
    void aWindowOnASecondMonitorStaysThereOnlyWhileThatMonitorIsConnected() {
        WindowState state = new WindowState(2200, 100, 1280, 820, true, VIEW);
        assertThat(state.clampedTo(List.of(LAPTOP, RIGHT_MONITOR))).isEqualTo(state);
        assertThat(state.clampedTo(List.of(LAPTOP))).isEqualTo(new WindowState(320, 130, 1280, 820, true, VIEW));
    }

    @Test
    void aWindowMostlyOffScreenStaysWhileEnoughOfItsTitleCanBeGrabbed() {
        WindowState grabbable = new WindowState(1920 - 120, 1080 - 80, 1280, 820, false, VIEW);
        assertThat(grabbable.clampedTo(List.of(LAPTOP))).isEqualTo(grabbable);

        WindowState tooFarRight = new WindowState(1920 - 119, 0, 1280, 820, false, VIEW);
        assertThat(tooFarRight.clampedTo(List.of(LAPTOP))).isEqualTo(new WindowState(320, 130, 1280, 820, false, VIEW));

        WindowState titleAboveTheScreen = new WindowState(100, -61, 1280, 820, false, VIEW);
        assertThat(titleAboveTheScreen.clampedTo(List.of(LAPTOP)).y()).isEqualTo(130);
    }

    @Test
    void twentyPixelsOfTheTitleBandAreEnough() {
        WindowState slightlyAbove = new WindowState(100, -4, 1280, 820, false, VIEW);
        assertThat(slightlyAbove.clampedTo(List.of(LAPTOP))).isEqualTo(slightlyAbove);
        WindowState twentyAbove = new WindowState(100, -60, 1280, 820, false, VIEW);
        assertThat(twentyAbove.clampedTo(List.of(LAPTOP))).isEqualTo(twentyAbove);
        WindowState twentyAtTheBottom = new WindowState(100, 1080 - 20, 1280, 820, false, VIEW);
        assertThat(twentyAtTheBottom.clampedTo(List.of(LAPTOP))).isEqualTo(twentyAtTheBottom);
        WindowState nineteenAtTheBottom = new WindowState(100, 1080 - 19, 1280, 820, false, VIEW);
        assertThat(nineteenAtTheBottom.clampedTo(List.of(LAPTOP)).y()).isEqualTo(130);
    }

    @Test
    void aWindowAcrossStackedMonitorsKeepsItsPlace() {
        Rectangle upperMonitor = new Rectangle(0, -1080, 1920, 1080);
        WindowState straddling = new WindowState(300, -40, 1280, 820, false, VIEW);
        assertThat(straddling.clampedTo(List.of(LAPTOP, upperMonitor))).isEqualTo(straddling);
    }

    @Test
    void aWindowLargerThanTheScreenIsShrunkAndCentered() {
        WindowState huge = new WindowState(-5000, -5000, 4000, 3000, false, VIEW);
        assertThat(huge.clampedTo(List.of(LAPTOP))).isEqualTo(new WindowState(0, 0, 1920, 1080, false, VIEW));
    }

    @Test
    void withoutScreensNothingChanges() {
        WindowState state = new WindowState(-5000, 0, 1280, 820, false, VIEW);
        assertThat(state.clampedTo(List.of())).isEqualTo(state);
    }

    @Test
    void boundsRoundTripAndAnEmptySizeIsRefused() {
        WindowState state = WindowState.of(new Rectangle(10, 20, 300, 200), true, VIEW);
        assertThat(state).isEqualTo(new WindowState(10, 20, 300, 200, true, VIEW));
        assertThat(state.bounds()).isEqualTo(new Rectangle(10, 20, 300, 200));
        assertThatThrownBy(() -> new WindowState(0, 0, 0, 200, false, VIEW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WindowState(0, 0, 300, -1, false, VIEW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WindowState(0, 0, 300, 200, false, null)).isInstanceOf(NullPointerException.class);
    }
}
