package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.FrameTicker;

import java.awt.Frame;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Maximizes a frame again after it leaves full screen, when it was maximized before entering. Under mutter/XWayland
 * a maximize asked while the window manager is still putting the title bar back is lost, and the window comes back in
 * its small normal bounds (the user, 2026-11-08). So the request waits until the insets change (the decorations are
 * back) or {@link #TIMEOUT_MILLISECONDS} pass, and is asked again every {@link #RETRY_MILLISECONDS} while the frame
 * is not maximized, at most {@link #MAXIMUM_REQUESTS} times. The frame's move, resize and state events call
 * {@link #check()} so it answers at once; a ticker is the fallback. EDT only.
 */
final class Remaximize {

    /** The longest wait for the decorations to come back before maximizing anyway. */
    static final long TIMEOUT_MILLISECONDS = 1_500;
    /** The wait between a request and the next one while the frame is still not maximized. */
    static final long RETRY_MILLISECONDS = 300;
    /** How many times it asks before giving up (the window manager may refuse for good). */
    static final int MAXIMUM_REQUESTS = 4;

    private final FrameTicker ticker;
    private final LongSupplier clock;
    private Runnable pending;

    Remaximize(FrameTicker ticker, LongSupplier clock) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * From now on, maximizes the frame whose state {@code state} reads and {@code setState} asks for, with the
     * extended state {@code wanted}; {@code insets} tells when the decorations are back. Replaces one still pending.
     */
    void start(IntSupplier state, IntConsumer setState, Supplier<Insets> insets, int wanted) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(setState, "setState");
        Objects.requireNonNull(insets, "insets");
        Insets atStart = insets.get();
        long start = clock.getAsLong();
        long[] lastRequest = {-1};
        int[] requests = {0};
        pending = () -> {
            long now = clock.getAsLong();
            if (requests[0] == 0) {
                if (insets.get().equals(atStart) && now - start < TIMEOUT_MILLISECONDS) {
                    return; // the title bar is not back yet
                }
                ask(setState, wanted, lastRequest, requests, now);
                return;
            }
            if (isMaximized(state.getAsInt())) {
                cancel();
            } else if (now - lastRequest[0] >= RETRY_MILLISECONDS) {
                if (requests[0] >= MAXIMUM_REQUESTS) {
                    cancel();
                } else {
                    ask(setState, wanted, lastRequest, requests, now);
                }
            }
        };
        ticker.start(this::check);
    }

    private static void ask(IntConsumer setState, int wanted, long[] lastRequest, int[] requests, long now) {
        requests[0]++;
        lastRequest[0] = now;
        setState.accept(wanted);
    }

    private static boolean isMaximized(int state) {
        return (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    /** The frame moved, was resized or changed state: looks now instead of at the next tick. */
    void check() {
        if (pending != null) {
            pending.run();
        }
    }

    /** Whether it is still waiting to maximize the frame or to see it maximized. */
    boolean isPending() {
        return pending != null;
    }

    /** Stops (another transition starts, or the frame is maximized). */
    void cancel() {
        ticker.stop();
        pending = null;
    }
}
