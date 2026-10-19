package cl.almejo.vsim.ui.canvas;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Plays {@link ViewTravel}s on a canvas, one frame per tick of a {@link FrameTicker}; each frame shows the travel at
 * the clock's current time. A new travel replaces the one in progress. When the animations are turned off mid-flight
 * the next frame jumps to the target.
 */
final class ViewAnimator {

    private final Consumer<ViewState> show;
    private final BooleanSupplier enabled;
    private FrameTicker ticker;
    private LongSupplier clock;
    private ViewTravel travel;
    private long startedAt;

    /**
     * @param show    shows one frame on the canvas
     * @param enabled whether view moves are animated now (read every frame)
     */
    ViewAnimator(FrameTicker ticker, LongSupplier clock, Consumer<ViewState> show, BooleanSupplier enabled) {
        this.ticker = ticker;
        this.clock = clock;
        this.show = show;
        this.enabled = enabled;
    }

    /** Replaces the ticker and the clock (milliseconds), stopping any travel. Tests drive the frames by hand. */
    void use(FrameTicker newTicker, LongSupplier newClock) {
        stop();
        ticker = newTicker;
        clock = newClock;
    }

    /** Starts travelling from {@code from} to {@code to} on a canvas of the given size, from the current moment. */
    void travel(ViewState from, ViewState to, double screenWidth, double screenHeight) {
        travel = ViewTravel.between(from, to, screenWidth, screenHeight, travel != null);
        startedAt = clock.getAsLong();
        if (travel.durationMilliseconds() == 0) {
            finish();
            return;
        }
        ticker.start(this::frame);
    }

    /** One frame: the view at this moment of the travel; the last one is exactly the target. */
    void frame() {
        if (travel == null) {
            ticker.stop();
            return;
        }
        if (!enabled.getAsBoolean()) {
            finish();
            return;
        }
        ViewTravel current = travel;
        long elapsed = clock.getAsLong() - startedAt;
        if (elapsed >= current.durationMilliseconds()) {
            travel = null;
            ticker.stop();
        }
        show.accept(current.at(elapsed));
    }

    /** Jumps to the target of the travel in progress, if any. */
    void finish() {
        if (travel == null) {
            return;
        }
        ViewState target = travel.target();
        travel = null;
        ticker.stop();
        show.accept(target);
    }

    /** Stops where the view is now. */
    void stop() {
        travel = null;
        ticker.stop();
    }

    boolean isTravelling() {
        return travel != null;
    }

    /** Where the travel in progress ends. */
    Optional<ViewState> target() {
        return Optional.ofNullable(travel).map(ViewTravel::target);
    }
}
