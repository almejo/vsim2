package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.ui.paint.TerminalAnimation;
import cl.almejo.vsim.ui.paint.TerminalCursorShape;
import cl.almejo.vsim.ui.paint.TerminalLook;
import cl.almejo.vsim.ui.paint.TerminalMoment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * What one view animates on each Terminal (spec §8.2, §8.4, §8.5), like {@code MemoryActivity} for the memory lights:
 * it remembers the frame it last saw of each screen and, on its clock, works out the {@link TerminalMoment} of every
 * paint. The cursor blinks 500 ms on and 500 ms off, starting on every change of the screen so output never shows a
 * missing cursor; Automan's star moves all the time; the last written cell glows for 900 ms; after a scroll or a clear
 * the old picture fades over the effect's persistence (250 ms «Suave», 480 ms «Fuerte»).
 *
 * <p>Only a {@link TerminalAnimation#LIVE} view moves anything; a still one («Animaciones: No», an inactive window)
 * and an export show the cursor on and the still star, but the activity still follows every screen, so nothing that
 * happened meanwhile glows or fades when the view comes back to life. A screen seen for the first time (a new view,
 * a restarted simulation) neither glows nor fades. One per view, used on the EDT.
 */
public final class TerminalActivity {

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    /** «Parpadea»: 500 ms on, 500 ms off. */
    static final long BLINK_HALF_NANOS = 500 * NANOS_PER_MILLISECOND;
    /** «Marcar el último carácter»: the mockup's {@code last-glow}, 900 ms. */
    static final long LAST_GLOW_NANOS = 900 * NANOS_PER_MILLISECOND;
    private static final double NANOS_PER_SECOND = 1e9;
    /** Automan's time wraps every so many seconds, so a double keeps the precision of a long-running clock. */
    private static final long AUTOMAN_PERIOD_NANOS = 3_600L * 1_000_000_000L;
    /** Above this many screens remembered the oldest are forgotten (the ids of deleted terminals). */
    private static final int MAXIMUM_REMEMBERED = 1024;

    private final LongSupplier nanoClock;
    private final Map<String, Seen> remembered = new HashMap<>();

    /** One screen as this view last saw it, and when its changes happened on the view's clock. */
    private static final class Seen {
        private final long owner;
        private TerminalFrame frame;
        private long changedAtNanos;
        private long glowStartNanos;
        private TerminalFrame ghost;
        private long ghostStartNanos;

        Seen(long owner, TerminalFrame frame, long nowNanos) {
            this.owner = owner;
            this.frame = frame;
            this.changedAtNanos = nowNanos;
            this.glowStartNanos = nowNanos - LAST_GLOW_NANOS;
        }

        /** Takes in {@code newer}: a new character restarts the glow, a scroll or a clear starts the fading. */
        void see(TerminalFrame newer, long nowNanos) {
            if (newer.version() == frame.version()) {
                return;
            }
            if (newer.scrollOrClearCount() != frame.scrollOrClearCount()) {
                ghost = frame;
                ghostStartNanos = nowNanos;
            }
            if (newer.lastWrittenSerial() != frame.lastWrittenSerial()) {
                glowStartNanos = nowNanos;
            }
            changedAtNanos = nowNanos;
            frame = newer;
        }
    }

    /** An activity on the system's clock. */
    public TerminalActivity() {
        this(System::nanoTime);
    }

    /** An activity on {@code nanoClock} (a fake one in tests). */
    public TerminalActivity(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /** The clock this activity reads. */
    public long now() {
        return nanoClock.getAsLong();
    }

    /**
     * The moment of Terminal {@code componentId} in this paint, whose {@code state} comes from the latest snapshot
     * (a UART 16550's state for its integrated screen). Each call is one paint of this view.
     */
    public TerminalMoment moment(String componentId, VisibleState state, TerminalLook look,
                                 TerminalAnimation animation) {
        Optional<VisibleState.TerminalState> shown = VisibleState.terminalScreen(state);
        if (shown.isEmpty()) {
            remembered.remove(componentId);
            return TerminalMoment.still(animation);
        }
        VisibleState.TerminalState terminal = shown.get();
        long now = nanoClock.getAsLong();
        Seen seen = remembered.get(componentId);
        if (seen == null || seen.owner != terminal.owner()) {
            if (remembered.size() >= MAXIMUM_REMEMBERED) {
                remembered.clear();
            }
            seen = new Seen(terminal.owner(), terminal.frame(), now);
            remembered.put(componentId, seen);
        } else {
            seen.see(terminal.frame(), now);
        }
        if (animation != TerminalAnimation.LIVE) {
            return TerminalMoment.still(animation);
        }
        return liveMoment(seen, look, now);
    }

    private static TerminalMoment liveMoment(Seen seen, TerminalLook look, long now) {
        boolean cursorShown = seen.frame.cursorVisible() && look.cursor() != TerminalCursorShape.NONE;
        boolean automan = cursorShown && look.cursor() == TerminalCursorShape.AUTOMAN;
        long glowElapsed = now - seen.glowStartNanos;
        double glow = look.markLast() && seen.frame.lastWritten().isPresent() && glowElapsed < LAST_GLOW_NANOS
                ? TerminalMoment.LAST_GLOW_ALPHA * (1 - easedOut(glowElapsed, LAST_GLOW_NANOS)) : 0;
        long persistence = look.effect().persistenceNanos();
        long ghostElapsed = now - seen.ghostStartNanos;
        boolean fading = seen.ghost != null && ghostElapsed < persistence;
        if (!fading) {
            seen.ghost = null;
        }
        double ghostOpacity = fading ? TerminalMoment.GHOST_OPACITY * (1 - easedOut(ghostElapsed, persistence)) : 0;
        boolean blinking = cursorShown && look.blinking();
        long blinkHalves = (now - seen.changedAtNanos) / BLINK_HALF_NANOS;
        boolean cursorOn = !blinking || blinkHalves % 2 == 0;
        long nextChange = TerminalMoment.NEVER;
        if (automan || glow > 0 || fading) {
            nextChange = now;
        } else if (blinking) {
            nextChange = seen.changedAtNanos + (blinkHalves + 1) * BLINK_HALF_NANOS;
        }
        double automanSeconds = automan ? Math.floorMod(now, AUTOMAN_PERIOD_NANOS) / NANOS_PER_SECOND : Double.NaN;
        return new TerminalMoment(TerminalAnimation.LIVE, automanSeconds, cursorOn, glow,
                Optional.ofNullable(fading ? seen.ghost : null), ghostOpacity, nextChange);
    }

    /** CSS's ease-out, near enough: fast at first, slow at the end; 0 at the start, 1 after {@code duration}. */
    private static double easedOut(long elapsed, long duration) {
        double progress = Math.min(1, Math.max(0, elapsed / (double) duration));
        return 1 - (1 - progress) * (1 - progress);
    }
}
