package cl.almejo.vsim.ui.ai.present;

import javax.swing.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The fade in and out of what «Presentar» shows over the panes (the subtitle, the step callout), shared by both: a new
 * one fades in, a replacement takes the place of a visible one at once, a cleared one fades out and is then gone.
 * With animations off (read at each change and each fade step) every change is immediate. A timer repaints while
 * something fades. The owner keeps what it shows and forgets it when told it is gone. EDT only.
 */
final class PresentationFade {

    static final int FADE_MILLISECONDS = 250;
    static final int TIMER_MILLISECONDS = 33;
    private static final long FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(FADE_MILLISECONDS);

    private final BooleanSupplier animate;
    private final Runnable repaint;
    private final LongSupplier nanoClock;
    private final Runnable gone;
    private final Timer timer;
    /** Whether something shows (also while it fades out). */
    private boolean present;
    private long shownAt;
    /** When it started fading out; {@code -1} while it is not leaving. */
    private long leavingSince = -1;
    private boolean disposed;

    /**
     * @param animate false with «Animaciones: No»
     * @param repaint repaints every pane
     * @param gone    forgets what the owner shows: it faded out, was cleared at once, or the owner was disposed
     */
    PresentationFade(BooleanSupplier animate, Runnable repaint, LongSupplier nanoClock, Runnable gone) {
        this.animate = Objects.requireNonNull(animate, "animate");
        this.repaint = Objects.requireNonNull(repaint, "repaint");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.gone = Objects.requireNonNull(gone, "gone");
        this.timer = new Timer(TIMER_MILLISECONDS, event -> tick());
    }

    boolean disposed() {
        return disposed;
    }

    /**
     * The owner shows something new: it replaces in place one still visible, else it fades in; then repaints. The
     * owner stores what it shows first.
     */
    void show() {
        boolean replacing = present && leavingSince < 0;
        present = true;
        leavingSince = -1;
        if (!replacing || !animate.getAsBoolean()) {
            shownAt = animate.getAsBoolean() ? nanoClock.getAsLong() : Long.MIN_VALUE;
        }
        startFadingIfNeeded();
        repaint.run();
    }

    /** Takes it away (fading out unless animations are off); nothing when nothing shows or it is already leaving. */
    void clear() {
        if (!present || leavingSince >= 0) {
            return;
        }
        if (animate.getAsBoolean() && !disposed) {
            leavingSince = nanoClock.getAsLong();
            startFadingIfNeeded();
        } else {
            drop();
        }
        repaint.run();
    }

    private void drop() {
        present = false;
        leavingSince = -1;
        timer.stop();
        gone.run();
    }

    private void startFadingIfNeeded() {
        if ((alpha() < 1.0 || leavingSince >= 0) && !timer.isRunning()) {
            timer.start();
        }
    }

    /**
     * One fade step: repaints, drops what faded out, stops the timer when nothing fades. With animations turned off
     * meanwhile, the fade ends at once in its final state (shown whole, or gone).
     */
    void tick() {
        long now = nanoClock.getAsLong();
        if (!animate.getAsBoolean()) {
            endFade();
        } else if (leavingSince >= 0 && now - leavingSince >= FADE_NANOS) {
            drop();
        } else if (leavingSince < 0 && alpha() >= 1.0) {
            timer.stop();
        }
        repaint.run();
    }

    private void endFade() {
        if (leavingSince >= 0) {
            drop();
        } else {
            shownAt = Long.MIN_VALUE;
            timer.stop();
        }
    }

    /** The opacity now, 0 to 1. */
    double alpha() {
        if (!present) {
            return 0;
        }
        long now = nanoClock.getAsLong();
        if (leavingSince >= 0) {
            return Math.max(0, 1 - (now - leavingSince) / (double) FADE_NANOS);
        }
        if (shownAt == Long.MIN_VALUE) {
            return 1;
        }
        return Math.min(1, Math.max(0, (now - shownAt) / (double) FADE_NANOS));
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    /** Forgets what shows at once and refuses what comes later. */
    void dispose() {
        disposed = true;
        drop();
    }
}
