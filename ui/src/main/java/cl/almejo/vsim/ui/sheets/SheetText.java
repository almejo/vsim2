package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * The small text the sheets put on the canvas («Hoja n» and the summary label, sheets spec §7): the theme's label font
 * at 85 % of its size, in the scheme's sheet grey, over a box of the board's colour so it reads over wires. Font and
 * colours are read on every call, never kept (theme and scheme change live).
 */
final class SheetText {

    /** The text's size against the theme's label font. */
    private static final float SIZE_FACTOR = 0.85f;
    /** Room around the text inside its box, in logical pixels. */
    private static final int HORIZONTAL_PADDING = 3;
    private static final int VERTICAL_PADDING = 1;
    /** How opaque the box behind the text is: enough to read over a wire, a part under it still shows. */
    private static final int BOX_ALPHA = 150;

    private SheetText() {
    }

    /** The theme's label font, smaller; the pane's own font when the theme has none. */
    static Font font(Component pane) {
        Font label = UIManager.getFont("Label.font");
        Font base = label != null ? label : pane.getFont();
        return base.deriveFont(base.getSize2D() * SIZE_FACTOR);
    }

    /** The box {@code text} takes on {@code pane} with its top-left corner at ({@code x}, {@code y}). */
    static Rectangle box(Component pane, String text, int x, int y) {
        FontMetrics metrics = pane.getFontMetrics(font(pane));
        return new Rectangle(x, y, metrics.stringWidth(text) + 2 * HORIZONTAL_PADDING,
                metrics.getAscent() + metrics.getDescent() + 2 * VERTICAL_PADDING);
    }

    /**
     * Paints {@code text} in {@code box} (made by {@link #box}); {@code highlighted} (the summary label under the
     * pointer) gets an opaque box with a border, so it reads as something to click.
     */
    static void paint(Graphics2D screen, Component pane, ColorScheme scheme, String text, Rectangle box,
                      boolean highlighted) {
        Graphics2D graphics = (Graphics2D) screen.create();
        try {
            Color background = scheme.background();
            graphics.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(),
                    highlighted ? 255 : BOX_ALPHA));
            graphics.fillRect(box.x, box.y, box.width, box.height);
            Color sheet = scheme.sheet();
            if (highlighted) {
                graphics.setColor(scheme.sheetBand());
                graphics.fillRect(box.x, box.y, box.width, box.height);
                graphics.setColor(sheet);
                graphics.drawRect(box.x, box.y, box.width - 1, box.height - 1);
            }
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = font(pane);
            graphics.setFont(font);
            graphics.setColor(new Color(sheet.getRed(), sheet.getGreen(), sheet.getBlue()));
            FontMetrics metrics = graphics.getFontMetrics(font);
            graphics.drawString(text, box.x + HORIZONTAL_PADDING, box.y + VERTICAL_PADDING + metrics.getAscent());
        } finally {
            graphics.dispose();
        }
    }
}
