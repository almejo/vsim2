package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TerminalPainter;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;

/**
 * Draws the cells of a Terminal's screen in the window (spec §9). {@code visible} holds the rows the window shows now
 * (history rows and screen rows, top first) and {@code screen} is where they go; {@code glyphs} are the ones its
 * character ROM gave (drawn only when the part takes its glyphs from one). {@link #standard()} is the canvas
 * painter's own look ({@link TerminalPainter#paintScreen}), still; tests may draw nothing.
 */
@FunctionalInterface
public interface TerminalScreenRenderer {

    void paint(Graphics2D graphics, Rectangle2D screen, TerminalFrame visible, GlyphCache glyphs,
               Map<String, Object> resolved, ColorScheme scheme);

    /** The part's look, as the canvas paints it. */
    static TerminalScreenRenderer standard() {
        return TerminalPainter::paintScreen;
    }
}
