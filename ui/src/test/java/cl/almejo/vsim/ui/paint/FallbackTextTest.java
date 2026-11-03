package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A text font without a glyph (Fira Code has no «‑», U+2011) borrows it from SansSerif instead of drawing the
 * missing-glyph box, and the text is measured as it is drawn (final review of «Imprimir», I2). VT323, bundled for the
 * Terminal, stands in for the user's font: it lacks U+2011 too.
 */
class FallbackTextTest {

    private static final float SIZE = 40;
    private static final String NON_BREAKING_HYPHEN = "‑";
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);

    private static Font vt323() throws IOException, FontFormatException {
        try (InputStream stream = FallbackTextTest.class.getResourceAsStream("/fonts/VT323-Regular.ttf")) {
            assertThat(stream).isNotNull();
            return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(SIZE);
        }
    }

    /** The ink {@code text} leaves when drawn at (20, 80) on a white image, with {@link FallbackText#draw}. */
    private static Rectangle ink(Font font, String text) {
        BufferedImage image = new BufferedImage(400, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(font);
            FallbackText.draw(graphics, text, 20, 80);
        } finally {
            graphics.dispose();
        }
        Rectangle bounds = null;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) & 0xFF) < 128) {
                    Rectangle pixel = new Rectangle(x, y, 1, 1);
                    bounds = bounds == null ? pixel : bounds.union(pixel);
                }
            }
        }
        assertThat(bounds).as("something was drawn").isNotNull();
        return bounds;
    }

    @Test
    void theStandInFontReallyLacksTheGlyph() throws Exception {
        assertThat(vt323().canDisplay('‑')).isFalse();
        assertThat(FallbackText.needed(vt323(), "A" + NON_BREAKING_HYPHEN + "B")).isTrue();
        assertThat(FallbackText.needed(vt323(), "A-B")).isFalse();
    }

    @Test
    void aMissingHyphenIsDrawnAsAHyphenNotAsABox() throws Exception {
        Rectangle hyphen = ink(vt323(), NON_BREAKING_HYPHEN);

        assertThat(hyphen.height).as("a hyphen is a short dash; the missing-glyph box is as tall as a letter")
                .isLessThan((int) (SIZE / 4));
        assertThat(hyphen.width).isGreaterThan(hyphen.height);
    }

    @Test
    void aTextTheFontCanShowIsMeasuredExactlyAsBefore() throws Exception {
        Font font = vt323();
        String text = "Bus A-B";
        FontMetrics metrics = metrics(font);

        assertThat(FallbackText.width(font, text, RENDER_CONTEXT))
                .isEqualTo(font.getStringBounds(text, RENDER_CONTEXT).getWidth());
        assertThat(FallbackText.stringWidth(metrics, text)).isEqualTo(metrics.stringWidth(text));
    }

    @Test
    void aTextWithABorrowedGlyphIsMeasuredAsItIsDrawn() throws Exception {
        Font font = vt323();
        String text = "VT100" + NON_BREAKING_HYPHEN + "xterm";
        double expected = new TextLayout(FallbackText.attributed(font, text).getIterator(), RENDER_CONTEXT)
                .getAdvance();

        assertThat(FallbackText.width(font, text, RENDER_CONTEXT)).isCloseTo(expected, within(1e-9));
        assertThat(FallbackText.stringWidth(metrics(font), text)).isEqualTo(Math.round(
                new TextLayout(FallbackText.attributed(font, text).getIterator(),
                        metrics(font).getFontRenderContext()).getAdvance()));
        Rectangle drawn = ink(font, text);
        assertThat(drawn.getMaxX()).as("the ink ends within the measured width")
                .isLessThanOrEqualTo(20 + FallbackText.width(font, text, RENDER_CONTEXT) + 1);
    }

    @Test
    void aBorrowedGlyphTakesTheBorrowedFontsRoom() throws Exception {
        Font font = vt323();
        double borrowedHyphen = FallbackText.fallbackFor(font).getStringBounds(NON_BREAKING_HYPHEN, RENDER_CONTEXT)
                .getWidth();

        assertThat(FallbackText.width(font, NON_BREAKING_HYPHEN, RENDER_CONTEXT))
                .isCloseTo(borrowedHyphen, within(0.01));
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            assertThat(FallbackText.width(graphics.getFontMetrics(font), "A" + NON_BREAKING_HYPHEN, graphics))
                    .isCloseTo(font.getStringBounds("A", graphics.getFontRenderContext()).getWidth()
                            + FallbackText.fallbackFor(font).getStringBounds(NON_BREAKING_HYPHEN,
                            graphics.getFontRenderContext()).getWidth(), within(0.01));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void theBorrowedFontKeepsTheSizeAndStyle() throws Exception {
        Font bold = vt323().deriveFont(Font.BOLD, 13f);
        Font borrowed = FallbackText.fallbackFor(bold);

        assertThat(borrowed.getSize2D()).isEqualTo(13f);
        assertThat(borrowed.isBold()).isTrue();
        assertThat(borrowed.canDisplay('‑')).isTrue();
    }

    private static FontMetrics metrics(Font font) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return graphics.getFontMetrics(font);
        } finally {
            graphics.dispose();
        }
    }
}
