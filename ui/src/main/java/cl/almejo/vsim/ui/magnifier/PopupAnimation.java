package cl.almejo.vsim.ui.magnifier;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * How the «Mirar adentro» panel grows from the piece and shrinks back to it: a scale from 0 (a point at the piece) to
 * 1 (full size) in {@value #DURATION_MILLISECONDS} ms with an ease-out curve. Pure over an injectable nanosecond clock,
 * so it is tested without a window; the popup's timer only asks it for the current scale.
 */
final class PopupAnimation {

    static final long DURATION_MILLISECONDS = 200;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private final LongSupplier nanoClock;
    private final BooleanSupplier animations;
    private long startedAt;
    private double fromScale;
    private double toScale;
    private boolean started;

    PopupAnimation(LongSupplier nanoClock, BooleanSupplier animations) {
        this.nanoClock = nanoClock;
        this.animations = animations;
    }

    /** Starts growing from a point. */
    void grow() {
        begin(0.0, 1.0);
    }

    /** Starts shrinking from the current size back to the piece. */
    void shrink() {
        begin(scale(), 0.0);
    }

    private void begin(double from, double to) {
        fromScale = from;
        toScale = to;
        startedAt = nanoClock.getAsLong();
        started = true;
    }

    boolean isShrinking() {
        return started && toScale == 0.0;
    }

    /** The share of the full size now: between 0 and 1. */
    double scale() {
        if (!started) {
            return 0.0;
        }
        double eased = easeOut(progress());
        return fromScale + (toScale - fromScale) * eased;
    }

    boolean isFinished() {
        return started && progress() >= 1.0;
    }

    private double progress() {
        if (!animations.getAsBoolean()) {
            return 1.0; // «Animaciones: No»: it is at its end the moment it starts (and a running one ends at once)
        }
        long elapsed = nanoClock.getAsLong() - startedAt;
        return Math.max(0.0, Math.min(1.0, elapsed / (double) (DURATION_MILLISECONDS * NANOS_PER_MILLISECOND)));
    }

    /** Fast at the start, slow at the end (cubic). */
    static double easeOut(double progress) {
        double remaining = 1.0 - progress;
        return 1.0 - remaining * remaining * remaining;
    }

    /**
     * The window bounds at {@code scale}: its size scaled, its centre between {@code anchor} (the piece) and the
     * centre of {@code full}.
     */
    static Rectangle boundsAt(Point anchor, Rectangle full, double scale) {
        int width = (int) Math.round(full.width * scale);
        int height = (int) Math.round(full.height * scale);
        double centerX = anchor.x + (full.getCenterX() - anchor.x) * scale;
        double centerY = anchor.y + (full.getCenterY() - anchor.y) * scale;
        if (scale >= 1.0) {
            return new Rectangle(full);
        }
        return new Rectangle((int) Math.round(centerX - width / 2.0), (int) Math.round(centerY - height / 2.0), width,
                height);
    }
}
