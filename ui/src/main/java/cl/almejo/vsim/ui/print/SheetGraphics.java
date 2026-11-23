package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.paint.ForwardingGraphics;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;

/**
 * The graphics {@link PrintPageable} hands the painters: Java's printer graphics seen from the sheet, as the PDF's is
 * (final review of «Imprimir», C1). Java's graphics carries {@code [band flip] · scale(dpi/72) ·
 * PageFormat.getMatrix()}: a quarter turn on a landscape sheet and a vertical mirror on a page painted in raster bands.
 * The painters decide content from the transform they read: which way a Terminal's glyphs and a part's texts stand
 * ({@code PainterSupport.quarterTurns}), whether a sticky note shows its text (pixels per grid), whether a Terminal's
 * title bar fits. Here {@link #getTransform()} answers in points from the sheet's corner, {@link #setTransform} is
 * read the same way, there is no device configuration (scale 1, like the PDF's) and fonts are measured as JFreePDF
 * measures them, so every decision comes out as on the PDF. The drawing itself still goes through Java's graphics,
 * turned and scaled, so the sheet lands on the paper as Java laid it out. Text goes as the shapes of its glyphs, as on
 * the PDF, so it prints in VSim's font and not in the printer's (final review, I3).
 *
 * <p>Built once per {@link PrintPageable#print} call: every pass and every band has its own base.
 */
final class SheetGraphics extends ForwardingGraphics {

    /** JFreePDF's text measuring context ({@code PDFGraphics2D}): no transform, no anti-aliasing, fractional metrics. */
    private static final FontRenderContext PDF_FONT_RENDER_CONTEXT = new FontRenderContext(null, false, true);
    /** Where fonts are measured, as JFreePDF does: the graphics of a small image with no hints. */
    private static final BufferedImage MEASURING_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /** What Java handed in: from the sheet's points to the device. */
    private final AffineTransform base;
    private final AffineTransform baseInverse;

    private SheetGraphics(Graphics2D delegate, AffineTransform base, AffineTransform baseInverse) {
        super(delegate);
        this.base = base;
        this.baseInverse = baseInverse;
    }

    /** {@code printerGraphics} seen from the sheet: its current transform becomes the sheet's identity. */
    static SheetGraphics over(Graphics2D printerGraphics) {
        AffineTransform base = printerGraphics.getTransform();
        try {
            return new SheetGraphics(printerGraphics, base, base.createInverse());
        } catch (NoninvertibleTransformException exception) {
            throw new IllegalArgumentException("a printer graphics with a flat transform: " + base, exception);
        }
    }

    @Override
    protected Graphics2D wrap(Graphics2D created) {
        return new SheetGraphics(created, base, baseInverse);
    }

    @Override
    public void drawString(String text, int x, int y) {
        drawString(text, (float) x, (float) y);
    }

    /**
     * As the shapes of its glyphs, like the PDF ({@code SharedStreamGraphics}): a printer would set plain text in its
     * own Helvetica or Courier.
     */
    @Override
    public void drawString(String text, float x, float y) {
        if (text.isEmpty()) {
            return;
        }
        fillOutline(new TextLayout(text, getFont(), getFontRenderContext()), x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        drawString(iterator, (float) x, (float) y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        if (iterator.getBeginIndex() == iterator.getEndIndex()) {
            return;
        }
        fillOutline(new TextLayout(iterator, getFontRenderContext()), x, y);
    }

    private void fillOutline(TextLayout layout, float x, float y) {
        fill(layout.getOutline(AffineTransform.getTranslateInstance(x, y)));
    }

    @Override
    public void drawGlyphVector(GlyphVector glyphs, float x, float y) {
        fill(glyphs.getOutline(x, y));
    }

    /** None, like the PDF's: the printer's {@code scale(dpi/72)} is not a screen's HiDPI scale. */
    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return null;
    }

    /** {@code transform} goes from user space to the sheet's points, as {@link #getTransform()} answers. */
    @Override
    public void setTransform(AffineTransform transform) {
        AffineTransform absolute = new AffineTransform(base);
        absolute.concatenate(transform);
        delegate.setTransform(absolute);
    }

    /** From the current user space to the sheet's points: what the PDF's graphics would answer. */
    @Override
    public AffineTransform getTransform() {
        AffineTransform relative = new AffineTransform(baseInverse);
        relative.concatenate(delegate.getTransform());
        return relative;
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return PDF_FONT_RENDER_CONTEXT;
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
        Graphics2D measuring = MEASURING_IMAGE.createGraphics();
        try {
            return measuring.getFontMetrics(font);
        } finally {
            measuring.dispose();
        }
    }
}
