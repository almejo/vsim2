package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.FrameTicker;

import java.awt.Frame;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Asks to maximize a frame again after it leaves full screen, when it was maximized before entering. A maximize asked
 * while the window manager is still putting the title bar back is lost, so it waits until the insets change (the title
 * bar is back) or {@link #TIMEOUT_MILLISECONDS} pass; and AWT may still believe the frame maximized, which would make
 * the request a no-op, so it asks for the normal state first in that case. One attempt: under mutter/XWayland the
 * window may still come back in its normal bounds (known, 2026-11-08; the user accepted it as technical debt). The
 * frame's events call {@link #check()}; a ticker is the fallback. EDT only.
 */
final class Remaximize {

    /** The longest wait for the title bar to come back before asking anyway. */
    static final long TIMEOUT_MILLISECONDS = 1_500;

    /** What it needs of the frame: AWT's extended state and the insets. */
    interface Window {

        int state();

        void setState(int state);

        Insets insets();
    }

    private final FrameTicker ticker;
    private final LongSupplier clock;
    private Runnable pending;

    Remaximize(FrameTicker ticker, LongSupplier clock) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Maximizes {@code target} with the extended state {@code wanted} once its title bar is back. */
    void start(Window target, int wanted) {
        Objects.requireNonNull(target, "target");
        Insets atStart = target.insets();
        long start = clock.getAsLong();
        pending = () -> {
            if (target.insets().equals(atStart) && clock.getAsLong() - start < TIMEOUT_MILLISECONDS) {
                return; // the title bar is not back yet
            }
            cancel();
            if ((target.state() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                target.setState(wanted & ~Frame.MAXIMIZED_BOTH); // a real change, so the next one reaches the system
            }
            target.setState(wanted);
        };
        ticker.start(this::check);
    }

    /** The frame moved, was resized or changed state: looks now instead of at the next tick. */
    void check() {
        if (pending != null) {
            pending.run();
        }
    }

    /** Whether it still waits for the title bar. */
    boolean isPending() {
        return pending != null;
    }

    /** Stops waiting (another transition starts, or it asked). */
    void cancel() {
        ticker.stop();
        pending = null;
    }
}
