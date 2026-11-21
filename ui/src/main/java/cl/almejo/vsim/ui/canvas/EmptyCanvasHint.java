package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Optional;

/**
 * The soft welcome text of a canvas with nothing in it. It is screen chrome, not part of the document: the canvas
 * paints it in screen pixels after everything else, so it never reaches an export or a printout, cannot be selected
 * and takes no clicks or drops. Its colour is the canvas scheme's text over the scheme's background, toned down only as far as it keeps a readable
 * contrast; its size follows the label font. Both are worked out each time it is painted.
 */
final class EmptyCanvasHint {

    /** The hint's size relative to the label font (15 px beside the 13 px label of the default theme). */
    private static final float FONT_RATIO = 15f / 13f;
    /** How much of the text colour the hint keeps (the rest is the canvas background), as far as it stays readable. */
    private static final double SOFTEST_SHARE = 0.7;
    private static final double SHARE_STEP = 0.05;
    private static final double LINE_SPACING = 1.5;

    private EmptyCanvasHint() {
    }

    static boolean isShownFor(CircuitDocument document) {
        return document.isEmpty();
    }

    /**
     * The three lines of the hint, in the current language. The last one names the command palette with
     * {@code findActionKey}, the key «Buscar una acción…» has now («Ctrl+K»), or its menu when it has none.
     */
    static List<String> lines(Optional<String> findActionKey) {
        String palette = findActionKey.map(key -> Messages.format("canvas.emptyHint.palette", key))
                .orElseGet(() -> Messages.format("canvas.emptyHint.paletteFromMenu", Messages.text("menu.help"),
                        Messages.text("action.findAction")));
        return List.of(Messages.text("canvas.emptyHint.drag"),
                Messages.format("canvas.emptyHint.examples", Messages.text("menu.file"),
                        Messages.text("menu.file.examples")),
                palette);
    }

    /**
     * Paints {@code lines} centred in a viewport of {@code width} x {@code height} screen pixels, if they fit whole.
     */
    static void paint(Graphics2D graphics, ColorScheme scheme, int width, int height, List<String> lines) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font(graphics));
        graphics.setColor(color(scheme));
        FontMetrics metrics = graphics.getFontMetrics();
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

    private static Font font(Graphics2D graphics) {
        Font label = UIManager.getFont("Label.font");
        float size = (label != null ? label.getSize2D() : 13f) * FONT_RATIO;
        return graphics.getFont().deriveFont(Font.PLAIN, size);
    }

    /**
     * The scheme's text colour toned toward its background, as far as the contrast stays at least
     * {@link ReadableColors#MINIMUM_TEXT_CONTRAST}:1; black or white when the scheme's own text does not read on its
     * background (Pastel's orange on grey).
     */
    static Color color(ColorScheme scheme) {
        Color background = scheme.background();
        Color text = scheme.text();
        if (ReadableColors.contrast(text, background) < ReadableColors.MINIMUM_TEXT_CONTRAST) {
            text = ReadableColors.contrast(Color.BLACK, background) >= ReadableColors.contrast(Color.WHITE, background)
                    ? Color.BLACK : Color.WHITE;
        }
        for (double share = SOFTEST_SHARE; share < 1; share += SHARE_STEP) {
            Color toned = ReadableColors.over(new Color(text.getRed(), text.getGreen(), text.getBlue(),
                    (int) Math.round(share * 255)), background);
            if (ReadableColors.contrast(toned, background) >= ReadableColors.MINIMUM_TEXT_CONTRAST) {
                return toned;
            }
        }
        return text;
    }
}
