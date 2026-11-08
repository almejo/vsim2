package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.FrameTicker;

import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Gives a frame its bounds again once the window manager has finished taking its decorations away (entering full
 * screen) or putting them back (leaving it). Bounds set while the title bar is still changing leave AWT's idea of
 * where the window is off by the title bar's height on GNOME/XWayland (the user's grab log, 2026-11-08: popups opened
 * 37 px below the pointer, a click on a menu counted as outside the window and the menu closed at once, and the window
 * grew by the title bar on each round trip). Set again once the insets changed, the bounds are computed with the
 * right decorations and the window manager places the window where AWT believes it is. When the insets never change
 * (no decorations, or the change came before the start), the bounds are given again after {@link
 * #TIMEOUT_MILLISECONDS}. Bounds AWT already believes are first nudged by a pixel, or the request would not reach the
 * window manager. EDT only.
 */
final class FrameResync {

    /** The longest wait for the window manager's answer before the bounds are given again anyway. */
    static final long TIMEOUT_MILLISECONDS = 1_500;

    private final FrameTicker ticker;
    private final LongSupplier clock;
    /** The check of the resync still pending; nothing once it ran or was cancelled. */
    private Runnable pending = () -> {
    };

    FrameResync(FrameTicker ticker, LongSupplier clock) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Watches {@code insets} from now on; when they change, or after the timeout, gives {@code wanted} to
     * {@code apply}, nudged first when {@code bounds} already says so. Replaces a resync still pending.
     */
    void start(Supplier<Insets> insets, Supplier<Rectangle> bounds, Rectangle wanted, Consumer<Rectangle> apply) {
        Objects.requireNonNull(insets, "insets");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(apply, "apply");
        Rectangle target = new Rectangle(Objects.requireNonNull(wanted, "wanted"));
        Insets atStart = insets.get();
        long start = clock.getAsLong();
        pending = () -> {
            boolean decorationsChanged = !insets.get().equals(atStart);
            if (!decorationsChanged && clock.getAsLong() - start < TIMEOUT_MILLISECONDS) {
                return;
            }
            cancel();
            if (bounds.get().equals(target)) {
                apply.accept(new Rectangle(target.x, target.y, target.width, target.height - 1));
            }
            apply.accept(target);
        };
        ticker.start(() -> pending.run());
    }

    /**
     * Looks at the insets now: the frame was moved, resized or changed state (the window manager answered), so the
     * resync need not wait for its next tick. Nothing happens when none is pending.
     */
    void check() {
        pending.run();
    }

    /** Forgets a resync still pending (another transition starts). */
    void cancel() {
        ticker.stop();
        pending = () -> {
        };
    }
}
