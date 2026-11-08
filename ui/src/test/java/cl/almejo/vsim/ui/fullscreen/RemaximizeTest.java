package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.Test;

import java.awt.Frame;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A window that was maximized before full screen is asked to maximize again once its title bar is back; when AWT
 * still believes it maximized, it goes through the normal state first, or the request would never reach the system.
 */
class RemaximizeTest {

    private static final Insets FULL_SCREEN = new Insets(0, 0, 0, 0);
    private static final Insets DECORATED = new Insets(37, 0, 0, 0);

    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final FakeFrame frame = new FakeFrame();
    private final Remaximize remaximize = new Remaximize(ticker, ticker::now);

    /** A frame whose AWT state ignores a request for the state it already believes. */
    private static final class FakeFrame implements Remaximize.Window {
        int awtState = Frame.NORMAL;
        Insets insets = FULL_SCREEN;
        final List<Integer> requests = new ArrayList<>();

        @Override
        public int state() {
            return awtState;
        }

        @Override
        public void setState(int state) {
            if (state != awtState) {
                requests.add(state);
                awtState = state;
            }
        }

        @Override
        public Insets insets() {
            return insets;
        }
    }

    private void titleBarComesBack() {
        frame.insets = DECORATED;
        remaximize.check(); // the window manager's configure arrived
    }

    @Test
    void theWindowIsMaximizedAgainOnceTheTitleBarIsBack() {
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        ticker.advance(200);
        assertThat(frame.requests).as("not while the title bar is coming back").isEmpty();
        titleBarComesBack();
        assertThat(frame.requests).containsExactly(Frame.MAXIMIZED_BOTH);
        assertThat(remaximize.isPending()).isFalse();
    }

    @Test
    void aFrameAwtStillBelievesMaximizedGoesThroughTheNormalStateFirst() {
        frame.awtState = Frame.MAXIMIZED_BOTH; // stale: the window manager shows it normal
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        titleBarComesBack();
        assertThat(frame.requests).containsExactly(Frame.NORMAL, Frame.MAXIMIZED_BOTH);
    }

    @Test
    void withoutAChangeOfTheTitleBarItAsksAfterAWhile() {
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        ticker.advance(Remaximize.TIMEOUT_MILLISECONDS + 32);
        assertThat(frame.requests).containsExactly(Frame.MAXIMIZED_BOTH);
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void cancellingStopsIt() {
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        remaximize.cancel();
        titleBarComesBack();
        ticker.advance(5_000);
        assertThat(frame.requests).isEmpty();
        assertThat(remaximize.isPending()).isFalse();
    }
}
