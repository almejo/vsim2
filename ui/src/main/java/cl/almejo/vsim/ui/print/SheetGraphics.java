package cl.almejo.vsim.ui.print;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

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
final class SheetGraphics extends Graphics2D {

    /** JFreePDF's text measuring context ({@code PDFGraphics2D}): no transform, no anti-aliasing, fractional metrics. */
    private static final FontRenderContext PDF_FONT_RENDER_CONTEXT = new FontRenderContext(null, false, true);
    /** Where fonts are measured, as JFreePDF does: the graphics of a small image with no hints. */
    private static final BufferedImage MEASURING_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private final Graphics2D delegate;
    /** What Java handed in: from the sheet's points to the device. */
    private final AffineTransform base;
    private final AffineTransform baseInverse;

    private SheetGraphics(Graphics2D delegate, AffineTransform base, AffineTransform baseInverse) {
        this.delegate = delegate;
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
    public void setColor(Color color) {
        delegate.setColor(color);
    }

    @Override
    public void setPaint(Paint paint) {
        delegate.setPaint(paint);
    }

    @Override
    public void setBackground(Color color) {
        delegate.setBackground(color);
    }

    @Override
    public void setXORMode(Color color) {
        delegate.setXORMode(color);
    }

    @Override
    public Graphics create() {
        return new SheetGraphics((Graphics2D) delegate.create(), base, baseInverse);
    }

    @Override
    public void draw(Shape shape) {
        delegate.draw(shape);
    }

    @Override
    public boolean drawImage(Image image, AffineTransform transform, ImageObserver observer) {
        return delegate.drawImage(image, transform, observer);
    }

    @Override
    public void drawImage(BufferedImage image, BufferedImageOp operation, int x, int y) {
        delegate.drawImage(image, operation, x, y);
    }

    @Override
    public void drawRenderedImage(RenderedImage image, AffineTransform transform) {
        delegate.drawRenderedImage(image, transform);
    }

    @Override
    public void drawRenderableImage(RenderableImage image, AffineTransform transform) {
        delegate.drawRenderableImage(image, transform);
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

    @Override
    public void fill(Shape shape) {
        delegate.fill(shape);
    }

    @Override
    public boolean hit(Rectangle rectangle, Shape shape, boolean onStroke) {
        return delegate.hit(rectangle, shape, onStroke);
    }

    /** None, like the PDF's: the printer's {@code scale(dpi/72)} is not a screen's HiDPI scale. */
    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return null;
    }

    @Override
    public void setComposite(Composite composite) {
        delegate.setComposite(composite);
    }

    @Override
    public void setStroke(Stroke stroke) {
        delegate.setStroke(stroke);
    }

    @Override
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        delegate.setRenderingHint(key, value);
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key key) {
        return delegate.getRenderingHint(key);
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        delegate.setRenderingHints(hints);
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        delegate.addRenderingHints(hints);
    }

    @Override
    public RenderingHints getRenderingHints() {
        return delegate.getRenderingHints();
    }

    @Override
    public void translate(int x, int y) {
        delegate.translate(x, y);
    }

    @Override
    public void translate(double x, double y) {
        delegate.translate(x, y);
    }

    @Override
    public void rotate(double theta) {
        delegate.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        delegate.rotate(theta, x, y);
    }

    @Override
    public void scale(double scaleX, double scaleY) {
        delegate.scale(scaleX, scaleY);
    }

    @Override
    public void shear(double shearX, double shearY) {
        delegate.shear(shearX, shearY);
    }

    @Override
    public void transform(AffineTransform transform) {
        delegate.transform(transform);
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
    public Paint getPaint() {
        return delegate.getPaint();
    }

    @Override
    public Composite getComposite() {
        return delegate.getComposite();
    }

    @Override
    public Color getBackground() {
        return delegate.getBackground();
    }

    @Override
    public Stroke getStroke() {
        return delegate.getStroke();
    }

    @Override
    public void clip(Shape shape) {
        delegate.clip(shape);
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return PDF_FONT_RENDER_CONTEXT;
    }

    @Override
    public Color getColor() {
        return delegate.getColor();
    }

    @Override
    public void setPaintMode() {
        delegate.setPaintMode();
    }

    @Override
    public Font getFont() {
        return delegate.getFont();
    }

    @Override
    public void setFont(Font font) {
        delegate.setFont(font);
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

    @Override
    public Rectangle getClipBounds() {
        return delegate.getClipBounds();
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        delegate.clipRect(x, y, width, height);
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        delegate.setClip(x, y, width, height);
    }

    @Override
    public Shape getClip() {
        return delegate.getClip();
    }

    @Override
    public void setClip(Shape clip) {
        delegate.setClip(clip);
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int deltaX, int deltaY) {
        delegate.copyArea(x, y, width, height, deltaX, deltaY);
    }

    @Override
    public void drawLine(int fromX, int fromY, int toX, int toY) {
        delegate.drawLine(fromX, fromY, toX, toY);
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        delegate.fillRect(x, y, width, height);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        delegate.clearRect(x, y, width, height);
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        delegate.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        delegate.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        delegate.drawOval(x, y, width, height);
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        delegate.fillOval(x, y, width, height);
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        delegate.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        delegate.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int pointCount) {
        delegate.drawPolyline(xPoints, yPoints, pointCount);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        delegate.drawPolygon(xPoints, yPoints, pointCount);
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        delegate.fillPolygon(xPoints, yPoints, pointCount);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, ImageObserver observer) {
        return delegate.drawImage(image, x, y, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height, ImageObserver observer) {
        return delegate.drawImage(image, x, y, width, height, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, Color background, ImageObserver observer) {
        return delegate.drawImage(image, x, y, background, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height, Color background,
                             ImageObserver observer) {
        return delegate.drawImage(image, x, y, width, height, background, observer);
    }

    @Override
    public boolean drawImage(Image image, int destinationX1, int destinationY1, int destinationX2, int destinationY2,
                             int sourceX1, int sourceY1, int sourceX2, int sourceY2, ImageObserver observer) {
        return delegate.drawImage(image, destinationX1, destinationY1, destinationX2, destinationY2, sourceX1,
                sourceY1, sourceX2, sourceY2, observer);
    }

    @Override
    public boolean drawImage(Image image, int destinationX1, int destinationY1, int destinationX2, int destinationY2,
                             int sourceX1, int sourceY1, int sourceX2, int sourceY2, Color background,
                             ImageObserver observer) {
        return delegate.drawImage(image, destinationX1, destinationY1, destinationX2, destinationY2, sourceX1,
                sourceY1, sourceX2, sourceY2, background, observer);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
