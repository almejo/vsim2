package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.Test;

import java.awt.Frame;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A window that was maximized before full screen is maximized again after leaving it, also when the window manager
 * ignores the first request while it is still putting the title bar back (the user, 2026-11-08: under mutter/XWayland
 * the window came back in its small normal bounds).
 */
class RemaximizeTest {

    private static final Insets FULL_SCREEN = new Insets(0, 0, 0, 0);
    private static final Insets DECORATED = new Insets(37, 0, 0, 0);

    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final AtomicReference<Insets> insets = new AtomicReference<>(FULL_SCREEN);
    private final AtomicInteger state = new AtomicInteger(Frame.NORMAL);
    private final AtomicInteger ignoredRequests = new AtomicInteger();
    private final List<Integer> requests = new ArrayList<>();
    private final Remaximize remaximize = new Remaximize(ticker, ticker::now);

    private void start() {
        remaximize.start(state::get, requested -> {
            requests.add(requested);
            if (ignoredRequests.getAndDecrement() <= 0) {
                state.set(requested);
            }
        }, insets::get, Frame.MAXIMIZED_BOTH);
    }

    @Test
    void theWindowIsMaximizedAgainOnceTheTitleBarIsBack() {
        start();
        ticker.advance(200);
        assertThat(requests).as("not while the title bar is coming back").isEmpty();
        insets.set(DECORATED);
        remaximize.check(); // the window manager's configure arrived
        assertThat(state.get()).isEqualTo(Frame.MAXIMIZED_BOTH);
        ticker.advance(2_000);
        assertThat(requests).containsExactly(Frame.MAXIMIZED_BOTH);
        assertThat(remaximize.isPending()).isFalse();
    }

    @Test
    void aMaximizeTheWindowManagerIgnoredIsAskedAgain() {
        ignoredRequests.set(1);
        start();
        insets.set(DECORATED);
        ticker.advance(Remaximize.TIMEOUT_MILLISECONDS);
        assertThat(state.get()).isEqualTo(Frame.MAXIMIZED_BOTH);
        assertThat(requests).hasSize(2);
        assertThat(remaximize.isPending()).isFalse();
    }

    @Test
    void withoutAnyAnswerItStillAsksAndGivesUpAfterAFewTries() {
        ignoredRequests.set(100);
        start();
        ticker.advance(10_000);
        assertThat(requests).hasSize(Remaximize.MAXIMUM_REQUESTS);
        assertThat(remaximize.isPending()).isFalse();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void cancellingStopsIt() {
        start();
        remaximize.cancel();
        insets.set(DECORATED);
        ticker.advance(5_000);
        assertThat(requests).isEmpty();
        assertThat(remaximize.isPending()).isFalse();
    }
}
