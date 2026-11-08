package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.Test;

import java.awt.Frame;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A window that was maximized before full screen is maximized again after leaving it (the user, 2026-11-08: under
 * mutter/XWayland it came back in its small normal bounds). AWT may still believe the frame maximized, so a plain
 * maximize would never reach the window manager: it asks for the normal state first, then for maximized, asks again
 * when the window manager ignores it, and as a last resort gives the frame the screen's maximum window bounds.
 */
class RemaximizeTest {

    private static final Insets FULL_SCREEN = new Insets(0, 0, 0, 0);
    private static final Insets DECORATED = new Insets(37, 0, 0, 0);
    private static final Rectangle NORMAL_BOUNDS = new Rectangle(500, 150, 840, 890);
    private static final Rectangle MAXIMUM = new Rectangle(0, 32, 1920, 1168);

    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final FakeFrame frame = new FakeFrame();
    private final Remaximize remaximize = new Remaximize(ticker, ticker::now);

    /**
     * A frame as AWT and the window manager see it: AWT ignores a request for the state it already believes, and its
     * state follows the window manager's answer; the window manager may ignore some maximize requests.
     */
    private static final class FakeFrame implements Remaximize.Window {
        int awtState = Frame.NORMAL;
        Insets insets = FULL_SCREEN;
        Rectangle bounds = new Rectangle(NORMAL_BOUNDS);
        int ignoredMaximizes;
        final List<Integer> stateRequests = new ArrayList<>();
        final List<Rectangle> boundsRequests = new ArrayList<>();

        @Override
        public int state() {
            return awtState;
        }

        @Override
        public void setState(int state) {
            if (state == awtState) {
                return; // AWT believes it already: nothing reaches the window manager
            }
            stateRequests.add(state);
            if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                if (ignoredMaximizes-- > 0) {
                    return;
                }
                bounds = new Rectangle(MAXIMUM);
            } else {
                bounds = new Rectangle(NORMAL_BOUNDS);
            }
            awtState = state;
        }

        @Override
        public Insets insets() {
            return insets;
        }

        @Override
        public Rectangle bounds() {
            return bounds;
        }

        @Override
        public Rectangle maximumBounds() {
            return MAXIMUM;
        }

        @Override
        public void setBounds(Rectangle newBounds) {
            boundsRequests.add(new Rectangle(newBounds));
            bounds = new Rectangle(newBounds);
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
        assertThat(frame.stateRequests).as("not while the title bar is coming back").isEmpty();
        titleBarComesBack();
        ticker.advance(2_000);
        assertThat(frame.awtState).isEqualTo(Frame.MAXIMIZED_BOTH);
        assertThat(frame.bounds).isEqualTo(MAXIMUM);
        assertThat(remaximize.isPending()).isFalse();
        assertThat(remaximize.maximizedByBounds()).isFalse();
    }

    @Test
    void aFrameAwtStillBelievesMaximizedGoesThroughTheNormalStateFirst() {
        frame.awtState = Frame.MAXIMIZED_BOTH; // stale: the window manager shows it normal
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        titleBarComesBack();
        ticker.advance(2_000);
        assertThat(frame.stateRequests).containsExactly(Frame.NORMAL, Frame.MAXIMIZED_BOTH);
        assertThat(frame.bounds).isEqualTo(MAXIMUM);
        assertThat(remaximize.isPending()).isFalse();
    }

    @Test
    void aMaximizeTheWindowManagerIgnoredIsAskedAgain() {
        frame.ignoredMaximizes = 1;
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        titleBarComesBack();
        ticker.advance(Remaximize.TIMEOUT_MILLISECONDS);
        assertThat(frame.awtState).isEqualTo(Frame.MAXIMIZED_BOTH);
        assertThat(frame.bounds).isEqualTo(MAXIMUM);
        assertThat(frame.boundsRequests).isEmpty();
        assertThat(remaximize.isPending()).isFalse();
    }

    @Test
    void whenTheWindowManagerNeverMaximizesTheFrameTakesTheMaximumWindowBounds() {
        frame.ignoredMaximizes = 100;
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        ticker.advance(10_000);
        assertThat(frame.boundsRequests).containsExactly(MAXIMUM);
        assertThat(remaximize.maximizedByBounds()).isTrue();
        assertThat(remaximize.isPending()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void cancellingStopsIt() {
        remaximize.start(frame, Frame.MAXIMIZED_BOTH);
        remaximize.cancel();
        titleBarComesBack();
        ticker.advance(5_000);
        assertThat(frame.stateRequests).isEmpty();
        assertThat(remaximize.isPending()).isFalse();
    }
}
