package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.terminal.TerminalFrame;

import java.util.Objects;
import java.util.Optional;

/**
 * What one paint of a Terminal shows besides its cells, worked out by the view's animation on its clock: whether the
 * screen effect may show, the moment of Automan's star, whether a blinking cursor is in its visible half, the last
 * character's glow, and the old picture fading after a scroll or a clear.
 *
 * @param animation       how much moves (an export: nothing, not even the screen effect)
 * @param automanSeconds  the time of Automan's star, in seconds; NaN for the still star
 * @param cursorOn        false in the hidden half of a blink
 * @param lastGlowAlpha   the alpha of the pale yellow under the last written cell, 0 for none
 * @param ghost           the picture before the last scroll or clear while it fades
 * @param ghostOpacity    how much of that picture still shows, 0 to {@link #GHOST_OPACITY}
 * @param nextChangeNanos the clock reading when this moment stops being right (the next blink, or now while something
 *                        moves continuously); {@link #NEVER} when nothing moves
 */
public record TerminalMoment(TerminalAnimation animation, double automanSeconds, boolean cursorOn,
                             double lastGlowAlpha, Optional<TerminalFrame> ghost, double ghostOpacity,
                             long nextChangeNanos) {

    /** The {@code nextChangeNanos} of a moment where nothing moves. */
    public static final long NEVER = Long.MAX_VALUE;
    /** The glow of a character just written: the mockup's {@code rgba(255, 255, 160, .55)}. */
    public static final double LAST_GLOW_ALPHA = 0.55;
    /** How much of the old picture shows when it starts fading: the mockup's {@code ghost-fade} from .55. */
    public static final double GHOST_OPACITY = 0.55;
    /** An export or a printed page, and the palette: a still screen without effects. */
    public static final TerminalMoment EXPORTED = still(TerminalAnimation.EXPORT);

    public TerminalMoment {
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(ghost, "ghost");
    }

    /** A moment where nothing moves: the cursor on, the still star, no glow, no fading picture. */
    public static TerminalMoment still(TerminalAnimation animation) {
        return new TerminalMoment(animation, Double.NaN, true, 0, Optional.empty(), 0, NEVER);
    }

    /** Whether the screen effect (scanlines, glow, vignette) shows: everywhere but in exports. */
    public boolean effects() {
        return animation != TerminalAnimation.EXPORT;
    }

    /** Whether something on the screen will look different later without a new frame. */
    public boolean moving() {
        return nextChangeNanos != NEVER;
    }
}
