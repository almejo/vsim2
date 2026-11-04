package cl.almejo.vsim.ui.paint;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Map;

/**
 * Text in the user's text font with the glyphs that font lacks borrowed from SansSerif, drawn and measured the same
 * way (final review of «Imprimir», I2). A physical font has no fallback in Java: Fira Code has no «‑» (U+2011), so a
 * label «VT100‑xterm» showed the missing-glyph box on the canvas, in the PDF and on paper. SansSerif is a logical
 * font, which reaches the system's fonts for any character.
 *
 * <p>A text the font can show is drawn and measured exactly as before ({@code drawString}, {@code stringWidth},
 * {@code getStringBounds}), so nothing moves for it; only a text with a missing glyph goes through a
 * {@link TextLayout} with a font per run, both to draw it and to measure it. Draw and measure a text with the same
 * pair of methods, or its box and its ink part ways.
 */
public final class FallbackText {

    private FallbackText() {
    }

    /** True when {@code font} lacks a glyph of {@code text}. */
    public static boolean needed(Font font, String text) {
        return font.canDisplayUpTo(text) != -1;
    }

    /** SansSerif with {@code font}'s size, style and transform. */
    public static Font fallbackFor(Font font) {
        Map<TextAttribute, Object> attributes = new HashMap<>(font.getAttributes());
        attributes.put(TextAttribute.FAMILY, Font.SANS_SERIF);
        return new Font(attributes);
    }

    /** {@code text} in {@code font}, each run of characters it lacks in {@link #fallbackFor} it. */
    public static AttributedString attributed(Font font, String text) {
        AttributedString attributed = new AttributedString(text);
        if (text.isEmpty()) {
            return attributed;
        }
        attributed.addAttribute(TextAttribute.FONT, font);
        Font fallback = fallbackFor(font);
        int index = 0;
        while (index < text.length()) {
            int missing = font.canDisplayUpTo(text.toCharArray(), index, text.length());
            if (missing == -1) {
                break;
            }
            int end = missing;
            while (end < text.length() && !font.canDisplay(text.codePointAt(end))) {
                end += Character.charCount(text.codePointAt(end));
            }
            attributed.addAttribute(TextAttribute.FONT, fallback, missing, end);
            index = end;
        }
        return attributed;
    }

    /** Draws {@code text} in {@code graphics}' font at the baseline ({@code x}, {@code y}), borrowing missing glyphs. */
    public static void draw(Graphics2D graphics, String text, float x, float y) {
        Font font = graphics.getFont();
        if (!needed(font, text)) {
            graphics.drawString(text, x, y);
            return;
        }
        graphics.drawString(attributed(font, text).getIterator(), x, y);
    }

    /** {@code font.getStringBounds(text, renderContext).getWidth()}, or the advance of the text as drawn. */
    public static double width(Font font, String text, FontRenderContext renderContext) {
        if (!needed(font, text)) {
            return font.getStringBounds(text, renderContext).getWidth();
        }
        return layout(font, text, renderContext).getAdvance();
    }

    /** {@code metrics.stringWidth(text)}, or the rounded advance of the text as drawn. */
    public static int stringWidth(FontMetrics metrics, String text) {
        if (!needed(metrics.getFont(), text)) {
            return metrics.stringWidth(text);
        }
        return Math.round(layout(metrics.getFont(), text, metrics.getFontRenderContext()).getAdvance());
    }

    /** {@code metrics.getStringBounds(text, graphics).getWidth()}, or the advance of the text as drawn there. */
    public static double width(FontMetrics metrics, String text, Graphics2D graphics) {
        if (!needed(metrics.getFont(), text)) {
            return metrics.getStringBounds(text, graphics).getWidth();
        }
        return layout(metrics.getFont(), text, graphics.getFontRenderContext()).getAdvance();
    }

    private static TextLayout layout(Font font, String text, FontRenderContext renderContext) {
        AttributedCharacterIterator iterator = attributed(font, text).getIterator();
        return new TextLayout(iterator, renderContext);
    }
}
