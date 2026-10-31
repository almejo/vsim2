package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * The soft welcome text of a canvas with nothing in it. It is screen chrome, not part of the document: the canvas
 * paints it in screen pixels after everything else, so it never reaches an export or a printout, cannot be selected
 * and takes no clicks or drops. Its colour is the theme's disabled text, read each time it is painted.
 */
final class EmptyCanvasHint {

    private static final float FONT_PIXELS = 15f;
    private static final double LINE_SPACING = 1.5;

    private EmptyCanvasHint() {
    }

    static boolean isShownFor(CircuitDocument document) {
        return document.isEmpty();
    }

    /** The two lines of the hint, in the current language. */
    static List<String> lines() {
        return List.of(Messages.text("canvas.emptyHint.drag"),
                Messages.format("canvas.emptyHint.examples", Messages.text("menu.file"),
                        Messages.text("menu.file.examples")));
    }

    /** Paints the hint centred in a viewport of {@code width} x {@code height} screen pixels, if it fits whole. */
    static void paint(Graphics2D graphics, int width, int height) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(graphics.getFont().deriveFont(Font.PLAIN, FONT_PIXELS));
        graphics.setColor(color());
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> lines = lines();
        int lineHeight = (int) Math.round(metrics.getHeight() * LINE_SPACING);
        if (!fits(metrics, lines, width, lineHeight * lines.size(), height)) {
            return; // a pane too small for the whole text shows none rather than a clipped one
        }
        int top = (height - lineHeight * lines.size()) / 2 + metrics.getAscent();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            graphics.drawString(line, (width - metrics.stringWidth(line)) / 2, top + index * lineHeight);
        }
    }

    private static boolean fits(FontMetrics metrics, List<String> lines, int width, int textHeight, int height) {
        int widest = lines.stream().mapToInt(metrics::stringWidth).max().orElse(0);
        return widest <= width && textHeight <= height;
    }

    private static Color color() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }
}
