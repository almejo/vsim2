package cl.almejo.vsim.ui.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Function;

/**
 * The keys of a shortcut drawn as keycaps: a translucent rounded box with a border and the key's name in bold, one per
 * key, with a «+» between them (or just a small gap in the macOS notation). Shared by the big chips of «Mostrar los
 * atajos al usarlos» ({@link CanvasNotice}, scaled up for projectors) and the sheet of shortcuts (at the UI's size).
 * The colour is the caller's, read at paint time.
 */
public final class Keycaps {

    private static final int KEY_PADDING_X = 8;
    private static final int KEY_PADDING_Y = 3;
    private static final int PLUS_GAP = 6;
    private static final int CORNER = 8;
    private static final int FILL_ALPHA = 48;
    private static final int BORDER_ALPHA = 190;
    private static final String PLUS = "+";

    private Keycaps() {
    }

    private static int scaled(int value, float scale) {
        return Math.round(value * scale);
    }

    private static Font keyFont(Font font) {
        return font.deriveFont(Font.BOLD);
    }

    /** How wide {@code keys} are drawn with {@code font} (the text font; the caps use its bold). */
    public static int width(Component metricsSource, Font font, float scale, List<String> keys, boolean plusBetween) {
        return width(metricsSource::getFontMetrics, font, scale, keys, plusBetween);
    }

    /**
     * Like {@link #width(Component, Font, float, List, boolean)} with the metrics {@code metrics} gives (a painter's
     * own, so the caps take the room they are drawn in on a scaled screen).
     */
    public static int width(Function<Font, FontMetrics> metrics, Font font, float scale, List<String> keys,
                            boolean plusBetween) {
        int width = 0;
        for (int index = 0; index < keys.size(); index++) {
            width += keyWidth(metrics, font, scale, keys.get(index))
                    + (index > 0 ? separatorWidth(metrics, font, scale, plusBetween) : 0);
        }
        return width;
    }

    /** How tall a keycap is: the text's line and its padding. */
    public static int height(Component metricsSource, Font font, float scale) {
        return height(metricsSource::getFontMetrics, font, scale);
    }

    /** Like {@link #height(Component, Font, float)} with the metrics {@code metrics} gives. */
    public static int height(Function<Font, FontMetrics> metrics, Font font, float scale) {
        return metrics.apply(font).getHeight() + 2 * scaled(KEY_PADDING_Y, scale);
    }

    private static int keyWidth(Function<Font, FontMetrics> metrics, Font font, float scale, String key) {
        return metrics.apply(keyFont(font)).stringWidth(key) + 2 * scaled(KEY_PADDING_X, scale);
    }

    private static int separatorWidth(Function<Font, FontMetrics> metrics, Font font, float scale,
                                      boolean plusBetween) {
        return plusBetween ? metrics.apply(font).stringWidth(PLUS) + 2 * scaled(PLUS_GAP, scale)
                : scaled(PLUS_GAP, scale);
    }

    /**
     * Draws {@code keys} from {@code x}, the caps' top at {@code top}, in {@code foreground}. Returns the x just past
     * the last cap.
     */
    public static int paint(Graphics2D painter, Font font, float scale, List<String> keys, boolean plusBetween, int x,
                            int top, Color foreground) {
        return paint(painter, font, scale, keys, plusBetween, x, top,
                new Colours(withAlpha(foreground, FILL_ALPHA), withAlpha(foreground, BORDER_ALPHA), foreground,
                        foreground));
    }

    /**
     * Draws {@code keys} as solid caps, in inverted colours: filled with {@code capColour}, the names in
     * {@code textColour} and the «+» between them in {@code capColour} (the keys inside a «Qué hay de nuevo» bubble,
     * whose ink is the cap and whose colour the name). Returns the x just past the last cap.
     */
    public static int paintSolid(Graphics2D painter, Font font, float scale, List<String> keys, boolean plusBetween,
                                 int x, int top, Color capColour, Color textColour) {
        return paint(painter, font, scale, keys, plusBetween, x, top,
                new Colours(capColour, capColour, textColour, capColour));
    }

    /**
     * Draws {@code keys} as light caps of {@code keyColour} (a translucent fill and a border) with their names and the
     * «+» between them in {@code textColour}, so the names read in both themes (a tip of the day: the accent's caps
     * with the text's ink). Returns the x just past the last cap.
     */
    public static int paintOutlined(Graphics2D painter, Font font, float scale, List<String> keys,
                                    boolean plusBetween, int x, int top, Color keyColour, Color textColour) {
        return paint(painter, font, scale, keys, plusBetween, x, top,
                new Colours(withAlpha(keyColour, FILL_ALPHA), withAlpha(keyColour, BORDER_ALPHA), textColour,
                        textColour));
    }

    /** The colours of a row of caps: their fill, border and names, and the «+» between them. */
    private record Colours(Color fill, Color border, Color text, Color plus) {
    }

    private static int paint(Graphics2D painter, Font font, float scale, List<String> keys, boolean plusBetween, int x,
                             int top, Colours colours) {
        FontMetrics regular = painter.getFontMetrics(font);
        int capHeight = regular.getHeight() + 2 * scaled(KEY_PADDING_Y, scale);
        int baseline = top + scaled(KEY_PADDING_Y, scale) + regular.getAscent();
        int corner = scaled(CORNER, scale);
        int position = x;
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                painter.setFont(font);
                painter.setColor(colours.plus());
                if (plusBetween) {
                    painter.drawString(PLUS, position + scaled(PLUS_GAP, scale), baseline);
                }
                position += plusBetween ? regular.stringWidth(PLUS) + 2 * scaled(PLUS_GAP, scale)
                        : scaled(PLUS_GAP, scale);
            }
            String key = keys.get(index);
            int width = painter.getFontMetrics(keyFont(font)).stringWidth(key) + 2 * scaled(KEY_PADDING_X, scale);
            painter.setColor(colours.fill());
            painter.fillRoundRect(position, top, width, capHeight, corner, corner);
            painter.setColor(colours.border());
            painter.setStroke(new BasicStroke(Math.max(1f, scale)));
            painter.drawRoundRect(position, top, width - 1, capHeight - 1, corner, corner);
            painter.setColor(colours.text());
            painter.setFont(keyFont(font));
            painter.drawString(key, position + scaled(KEY_PADDING_X, scale), baseline);
            position += width;
        }
        return position;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
