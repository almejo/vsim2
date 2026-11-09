package cl.almejo.vsim.ui.paint;

/**
 * How much of a Terminal's look a paint shows besides its text and still cursor: an export or a printed page shows
 * nothing more (no screen effect, no animation); a still canvas (animations off, an inactive window) shows the screen
 * effect but nothing moves; a live canvas shows everything (blink, Automan's star, the last character's glow, the
 * phosphor persistence).
 */
public enum TerminalAnimation {
    EXPORT,
    STILL,
    LIVE
}
