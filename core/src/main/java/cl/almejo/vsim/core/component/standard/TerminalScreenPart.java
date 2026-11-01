package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalScreen;

/**
 * A running part that shows a Terminal screen: the Terminal itself, or a UART 16550 with its integrated screen. The
 * Terminal window, «Limpiar» and the AI's {@code vsim_read_terminal} reach the screen through it, on the simulation
 * thread only.
 */
public interface TerminalScreenPart {

    /** A number unique to this behaviour, so a screen started over never looks like an older one with its version. */
    long owner();

    /** The screen, read on the simulation thread only, and only while {@link #showsScreen()}. */
    TerminalScreen screen();

    /** «Limpiar» from the window or the context menu: the screen and its history empty, not an edit. */
    void clearScreen();

    /** The glyphs read from a character ROM so far; empty with the internal generator (every part but a Terminal). */
    default GlyphCache glyphs() {
        return GlyphCache.EMPTY;
    }

    /** Whether the part shows its screen now (a UART with TX and RX pins has none); true for a Terminal. */
    default boolean showsScreen() {
        return true;
    }
}
