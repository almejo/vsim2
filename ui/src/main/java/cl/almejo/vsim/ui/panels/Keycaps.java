package cl.almejo.vsim.ui.panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;

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
        int width = 0;
        for (int index = 0; index < keys.size(); index++) {
            width += keyWidth(metricsSource, font, scale, keys.get(index))
                    + (index > 0 ? separatorWidth(metricsSource, font, scale, plusBetween) : 0);
        }
        return width;
    }

    /** How tall a keycap is: the text's line and its padding. */
    public static int height(Component metricsSource, Font font, float scale) {
        return metricsSource.getFontMetrics(font).getHeight() + 2 * scaled(KEY_PADDING_Y, scale);
    }

    private static int keyWidth(Component metricsSource, Font font, float scale, String key) {
        return metricsSource.getFontMetrics(keyFont(font)).stringWidth(key) + 2 * scaled(KEY_PADDING_X, scale);
    }

    private static int separatorWidth(Component metricsSource, Font font, float scale, boolean plusBetween) {
        return plusBetween ? metricsSource.getFontMetrics(font).stringWidth(PLUS) + 2 * scaled(PLUS_GAP, scale)
                : scaled(PLUS_GAP, scale);
    }

    /**
     * Draws {@code keys} from {@code x}, the caps' top at {@code top}, in {@code foreground}. Returns the x just past
     * the last cap.
     */
    public static int paint(Graphics2D painter, Font font, float scale, List<String> keys, boolean plusBetween, int x,
                            int top, Color foreground) {
        FontMetrics regular = painter.getFontMetrics(font);
        int capHeight = regular.getHeight() + 2 * scaled(KEY_PADDING_Y, scale);
        int baseline = top + scaled(KEY_PADDING_Y, scale) + regular.getAscent();
        int corner = scaled(CORNER, scale);
        int position = x;
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                painter.setFont(font);
                painter.setColor(foreground);
                if (plusBetween) {
                    painter.drawString(PLUS, position + scaled(PLUS_GAP, scale), baseline);
                }
                position += plusBetween ? regular.stringWidth(PLUS) + 2 * scaled(PLUS_GAP, scale)
                        : scaled(PLUS_GAP, scale);
            }
            String key = keys.get(index);
            int width = painter.getFontMetrics(keyFont(font)).stringWidth(key) + 2 * scaled(KEY_PADDING_X, scale);
            painter.setColor(withAlpha(foreground, FILL_ALPHA));
            painter.fillRoundRect(position, top, width, capHeight, corner, corner);
            painter.setColor(withAlpha(foreground, BORDER_ALPHA));
            painter.setStroke(new BasicStroke(Math.max(1f, scale)));
            painter.drawRoundRect(position, top, width - 1, capHeight - 1, corner, corner);
            painter.setColor(foreground);
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
