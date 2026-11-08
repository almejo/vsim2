package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.Test;

import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * After the window enters or leaves full screen, its bounds are given again once the window manager has put its
 * decorations back (or taken them away): set while the title bar was still changing, Java's idea of where the window
 * is stays 37 px off (the user's grab log, 2026-11-08: a click on a menu counted as outside the window, so the menu
 * closed at once), and the window grows by the title bar's height on each round trip.
 */
class FrameResyncTest {

    private static final Insets FULL_SCREEN = new Insets(0, 0, 0, 0);
    private static final Insets DECORATED = new Insets(37, 0, 0, 0);
    private static final Rectangle WANTED = new Rectangle(200, 150, 500, 300);

    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private final AtomicReference<Insets> insets = new AtomicReference<>(FULL_SCREEN);
    private final AtomicReference<Rectangle> bounds = new AtomicReference<>(new Rectangle(200, 150, 500, 337));
    private final List<Rectangle> applied = new ArrayList<>();
    private final FrameResync resync = new FrameResync(ticker, ticker::now);

    private void start() {
        resync.start(insets::get, bounds::get, WANTED, rectangle -> {
            applied.add(new Rectangle(rectangle));
            bounds.set(new Rectangle(rectangle));
        });
    }

    @Test
    void theBoundsAreGivenAgainOnceTheDecorationsComeBack() {
        start();
        ticker.advance(300);
        assertThat(applied).as("the window manager has not answered yet").isEmpty();
        insets.set(DECORATED);
        ticker.advance(32);
        assertThat(applied).containsExactly(WANTED);
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void withoutAChangeOfDecorationsTheBoundsAreGivenAgainAfterAWhile() {
        start();
        ticker.advance(FrameResync.TIMEOUT_MILLISECONDS + 32);
        assertThat(applied).containsExactly(WANTED);
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void boundsJavaAlreadyBelievesAreNudgedSoTheyReachTheWindowManager() {
        bounds.set(new Rectangle(WANTED)); // Java's bounds are right, its idea of the screen place is not
        start();
        insets.set(DECORATED);
        ticker.advance(32);
        assertThat(applied).hasSize(2);
        assertThat(applied.get(0)).isNotEqualTo(WANTED);
        assertThat(applied.get(1)).isEqualTo(WANTED);
    }

    @Test
    void aWindowEventLooksAtTheDecorationsWithoutWaitingForTheNextTick() {
        start();
        insets.set(DECORATED);
        resync.check(); // the frame was resized: the window manager answered
        assertThat(applied).containsExactly(WANTED);
        resync.check();
        ticker.advance(FrameResync.TIMEOUT_MILLISECONDS + 32);
        assertThat(applied).as("once").containsExactly(WANTED);
    }

    @Test
    void aNewTransitionCancelsThePendingOne() {
        start();
        resync.cancel();
        insets.set(DECORATED);
        ticker.advance(FrameResync.TIMEOUT_MILLISECONDS + 32);
        assertThat(applied).isEmpty();
    }
}
