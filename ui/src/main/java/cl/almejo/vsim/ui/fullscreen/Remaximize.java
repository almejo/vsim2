package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.FrameTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Frame;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Maximizes a frame again after it leaves full screen, when it was maximized before entering (the user, 2026-11-08:
 * under mutter/XWayland it came back in its small normal bounds, even when asked again). Three things get in the way:
 * a maximize asked while the window manager is still putting the title bar back is lost; AWT may still believe the
 * frame maximized, so asking for it is a no-op that never reaches the window manager; and the window manager may
 * ignore the request. So: it waits until the insets change (the title bar is back) or {@link #TIMEOUT_MILLISECONDS}
 * pass; asks for the normal state first when AWT believes it maximized; then for maximized, again (through the normal
 * state) every {@link #RETRY_MILLISECONDS} while the frame is not maximized over the screen, at most
 * {@link #MAXIMUM_REQUESTS} times; and as a last resort gives the frame the screen's maximum window bounds, so it looks
 * maximized ({@link #maximizedByBounds()}). Every step is logged at debug level with the states and bounds, for a hand
 * test to be diagnosed. The frame's events call {@link #check()}; a ticker is the fallback. EDT only.
 */
final class Remaximize {

    /** The longest wait for the title bar to come back before maximizing anyway. */
    static final long TIMEOUT_MILLISECONDS = 1_500;
    /** The wait for an answer before asking again. */
    static final long RETRY_MILLISECONDS = 300;
    /** How many maximize requests before the frame just takes the maximum window bounds. */
    static final int MAXIMUM_REQUESTS = 4;
    /** A maximized frame covers at least this share of the maximum window bounds each way (decorations, shadows). */
    private static final double MAXIMIZED_SHARE = 0.9;

    private static final Logger LOGGER = LoggerFactory.getLogger(Remaximize.class);

    /** What it needs of the frame: AWT's state and bounds, the insets and the screen's maximum window bounds. */
    interface Window {

        int state();

        void setState(int state);

        Insets insets();

        Rectangle bounds();

        /** The bounds of a maximized window on the frame's screen (without panels and docks). */
        Rectangle maximumBounds();

        void setBounds(Rectangle bounds);
    }

    private enum Step { WAITING_FOR_TITLE_BAR, NORMALIZING, MAXIMIZING }

    private final FrameTicker ticker;
    private final LongSupplier clock;
    private Window window;
    private int wanted;
    private Step step;
    private Insets insetsAtStart;
    private long stepStart;
    private int requests;
    private boolean maximizedByBounds;

    Remaximize(FrameTicker ticker, LongSupplier clock) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** From now on, maximizes {@code target} with the extended state {@code wantedState}. Replaces one pending. */
    void start(Window target, int wantedState) {
        this.window = Objects.requireNonNull(target, "target");
        this.wanted = wantedState;
        this.step = Step.WAITING_FOR_TITLE_BAR;
        this.insetsAtStart = target.insets();
        this.stepStart = clock.getAsLong();
        this.requests = 0;
        this.maximizedByBounds = false;
        log("start");
        ticker.start(this::check);
    }

    /** The frame moved, was resized or changed state: looks now instead of at the next tick. */
    void check() {
        if (step == null) {
            return;
        }
        long now = clock.getAsLong();
        switch (step) {
            case WAITING_FOR_TITLE_BAR -> {
                if (window.insets().equals(insetsAtStart) && now - stepStart < TIMEOUT_MILLISECONDS) {
                    return;
                }
                if (isMaximized(window.state())) {
                    moveTo(Step.NORMALIZING, now);
                    window.setState(normal()); // AWT believes it maximized: a real change first
                    log("asked for the normal state");
                } else {
                    askToMaximize(now);
                }
            }
            case NORMALIZING -> {
                if (!isMaximized(window.state()) || now - stepStart >= RETRY_MILLISECONDS) {
                    askToMaximize(now);
                }
            }
            case MAXIMIZING -> followTheMaximize(now);
        }
    }

    private void followTheMaximize(long now) {
        if (isMaximizedOverTheScreen()) {
            log("maximized");
            cancel();
            return;
        }
        if (now - stepStart < RETRY_MILLISECONDS) {
            return;
        }
        if (requests < MAXIMUM_REQUESTS) {
            window.setState(normal());
            askToMaximize(now);
            return;
        }
        window.setBounds(window.maximumBounds());
        maximizedByBounds = true;
        log("gave up asking; took the maximum window bounds");
        cancel();
    }

    private void askToMaximize(long now) {
        moveTo(Step.MAXIMIZING, now);
        requests++;
        window.setState(wanted);
        log("asked to maximize (request " + requests + ")");
    }

    private void moveTo(Step next, long now) {
        step = next;
        stepStart = now;
    }

    private boolean isMaximizedOverTheScreen() {
        Rectangle bounds = window.bounds();
        Rectangle maximum = window.maximumBounds();
        return isMaximized(window.state()) && bounds.width >= maximum.width * MAXIMIZED_SHARE
                && bounds.height >= maximum.height * MAXIMIZED_SHARE;
    }

    private int normal() {
        return wanted & ~Frame.MAXIMIZED_BOTH;
    }

    private static boolean isMaximized(int state) {
        return (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    private void log(String what) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Remaximize {}: state={}, bounds={}, insets={}, maximum={}", what, window.state(),
                    window.bounds(), window.insets(), window.maximumBounds());
        }
    }

    /** Whether it is still working on the frame. */
    boolean isPending() {
        return step != null;
    }

    /** Whether the last run ended by giving the frame the maximum window bounds instead of the maximized state. */
    boolean maximizedByBounds() {
        return maximizedByBounds;
    }

    /** Stops (another transition starts, or it is done). */
    void cancel() {
        ticker.stop();
        step = null;
    }
}
