package cl.almejo.vsim.ui.print;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * A {@link Graphics2D} for one PDF page that keeps each graphics' state its own, as Java2D promises: what {@link
 * PdfPages} hands to the painters.
 *
 * <p>JFreePDF 2.0.1's {@code create()} returns a graphics that writes into the same content stream as its parent,
 * its {@code setColor} and {@code setStroke} skip a value equal to the one that graphics believes is current, and its
 * {@code dispose()} only puts the transform back. So a colour, stroke or transparency that a child sets stays in the
 * stream, and the parent then paints with it (component labels came out white or vanished). This wrapper remembers
 * which graphics of the page wrote its state last; before any other graphics paints, that one writes its whole state
 * again (transform, transparency, paint, stroke), defeating the equality shortcut with a different value first. It
 * also places linear gradients where Java2D would ({@link #beforePainting()}).
 * Every call goes straight to the wrapped graphics, so the page stays vector.
 */
final class SharedStreamGraphics extends Graphics2D {

    /** Which graphics of one page the content stream currently has the state of. */
    private static final class Stream {
        private SharedStreamGraphics owner;
    }

    private final Graphics2D delegate;
    private final Stream stream;
    /** The linear gradient being painted with, in user space; null when painting with anything else. */
    private GradientPaint gradient;

    /** The page's own graphics, whose state the stream has. */
    static SharedStreamGraphics forPage(Graphics2D page) {
        Stream stream = new Stream();
        SharedStreamGraphics graphics = new SharedStreamGraphics(page, stream);
        stream.owner = graphics;
        return graphics;
    }

    private SharedStreamGraphics(Graphics2D delegate, Stream stream) {
        this.delegate = delegate;
        this.stream = stream;
    }

    /** Writes this graphics' state into the stream when another graphics wrote its own since. */
    private void takeTheStream() {
        if (stream.owner == this) {
            return;
        }
        delegate.setTransform(delegate.getTransform());
        delegate.setComposite(delegate.getComposite());
        Paint paint = delegate.getPaint();
        if (paint instanceof Color color) {
            delegate.setColor(new Color(color.getRed() ^ 1, color.getGreen(), color.getBlue(), color.getAlpha()));
            delegate.setColor(color);
        } else {
            delegate.setPaint(paint);
        }
        Stroke stroke = delegate.getStroke();
        float otherWidth = stroke instanceof BasicStroke basic ? basic.getLineWidth() + 1f : 1f;
        delegate.setStroke(new BasicStroke(otherWidth));
        delegate.setStroke(stroke);
        stream.owner = this;
    }

    /**
     * Gets the stream ready for painting: this graphics' state, and its gradient placed where Java2D puts it. JFreePDF
     * writes a gradient's points in the page's coordinates, ignoring the current transform (a block's shading came
     * out flat and dark), so the points are mapped through the transform first; VSim only rotates and scales
     * uniformly, under which a mapped linear gradient is the same gradient.
     */
    private void beforePainting() {
        takeTheStream();
        if (gradient != null) {
            AffineTransform transform = delegate.getTransform();
            delegate.setPaint(new GradientPaint(transform.transform(gradient.getPoint1(), null), gradient.getColor1(),
                    transform.transform(gradient.getPoint2(), null), gradient.getColor2(), gradient.isCyclic()));
        }
    }

    @Override
    public void setColor(Color color) {
        takeTheStream();
        if (color != null) {
            gradient = null;
        }
        delegate.setColor(color);
    }

    @Override
    public void setPaint(Paint paint) {
        takeTheStream();
        if (paint != null) {
            gradient = paint instanceof GradientPaint linear ? linear : null;
        }
        delegate.setPaint(paint);
    }

    @Override
    public void setBackground(Color color) {
        delegate.setBackground(color);
    }

    @Override
    public void setXORMode(Color color) {
        takeTheStream();
        delegate.setXORMode(color);
    }

    @Override
    public Graphics create() {
        takeTheStream();
        SharedStreamGraphics child = new SharedStreamGraphics((Graphics2D) delegate.create(), stream);
        child.gradient = gradient;
        return child;
    }

    @Override
    public void draw(Shape shape) {
        beforePainting();
        delegate.draw(shape);
    }

    @Override
    public boolean drawImage(Image image, AffineTransform transform, ImageObserver observer) {
        beforePainting();
        return delegate.drawImage(image, transform, observer);
    }

    @Override
    public void drawImage(BufferedImage image, BufferedImageOp operation, int x, int y) {
        beforePainting();
        delegate.drawImage(image, operation, x, y);
    }

    @Override
    public void drawRenderedImage(RenderedImage image, AffineTransform transform) {
        beforePainting();
        delegate.drawRenderedImage(image, transform);
    }

    @Override
    public void drawRenderableImage(RenderableImage image, AffineTransform transform) {
        beforePainting();
        delegate.drawRenderableImage(image, transform);
    }

    @Override
    public void drawString(String text, int x, int y) {
        beforePainting();
        delegate.drawString(text, x, y);
    }

    @Override
    public void drawString(String text, float x, float y) {
        beforePainting();
        delegate.drawString(text, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        beforePainting();
        delegate.drawString(iterator, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        beforePainting();
        delegate.drawString(iterator, x, y);
    }

    @Override
    public void drawGlyphVector(GlyphVector glyphs, float x, float y) {
        beforePainting();
        delegate.drawGlyphVector(glyphs, x, y);
    }

    @Override
    public void fill(Shape shape) {
        beforePainting();
        delegate.fill(shape);
    }

    @Override
    public boolean hit(Rectangle rectangle, Shape shape, boolean onStroke) {
        return delegate.hit(rectangle, shape, onStroke);
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return delegate.getDeviceConfiguration();
    }

    @Override
    public void setComposite(Composite composite) {
        takeTheStream();
        delegate.setComposite(composite);
    }

    @Override
    public void setStroke(Stroke stroke) {
        takeTheStream();
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
        takeTheStream();
        delegate.translate(x, y);
    }

    @Override
    public void translate(double x, double y) {
        takeTheStream();
        delegate.translate(x, y);
    }

    @Override
    public void rotate(double theta) {
        takeTheStream();
        delegate.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        takeTheStream();
        delegate.rotate(theta, x, y);
    }

    @Override
    public void scale(double scaleX, double scaleY) {
        takeTheStream();
        delegate.scale(scaleX, scaleY);
    }

    @Override
    public void shear(double shearX, double shearY) {
        takeTheStream();
        delegate.shear(shearX, shearY);
    }

    @Override
    public void transform(AffineTransform transform) {
        takeTheStream();
        delegate.transform(transform);
    }

    @Override
    public void setTransform(AffineTransform transform) {
        takeTheStream();
        delegate.setTransform(transform);
    }

    @Override
    public AffineTransform getTransform() {
        return delegate.getTransform();
    }

    @Override
    public Paint getPaint() {
        return gradient != null ? gradient : delegate.getPaint();
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
        return delegate.getFontRenderContext();
    }

    @Override
    public Color getColor() {
        return delegate.getColor();
    }

    @Override
    public void setPaintMode() {
        takeTheStream();
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
        return delegate.getFontMetrics(font);
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
        beforePainting();
        delegate.copyArea(x, y, width, height, deltaX, deltaY);
    }

    @Override
    public void drawLine(int fromX, int fromY, int toX, int toY) {
        beforePainting();
        delegate.drawLine(fromX, fromY, toX, toY);
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        beforePainting();
        delegate.fillRect(x, y, width, height);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        beforePainting();
        delegate.clearRect(x, y, width, height);
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        beforePainting();
        delegate.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        beforePainting();
        delegate.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        beforePainting();
        delegate.drawOval(x, y, width, height);
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        beforePainting();
        delegate.fillOval(x, y, width, height);
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        beforePainting();
        delegate.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        beforePainting();
        delegate.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int pointCount) {
        beforePainting();
        delegate.drawPolyline(xPoints, yPoints, pointCount);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        beforePainting();
        delegate.drawPolygon(xPoints, yPoints, pointCount);
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        beforePainting();
        delegate.fillPolygon(xPoints, yPoints, pointCount);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, ImageObserver observer) {
        beforePainting();
        return delegate.drawImage(image, x, y, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height, ImageObserver observer) {
        beforePainting();
        return delegate.drawImage(image, x, y, width, height, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, Color background, ImageObserver observer) {
        beforePainting();
        return delegate.drawImage(image, x, y, background, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height, Color background,
                             ImageObserver observer) {
        beforePainting();
        return delegate.drawImage(image, x, y, width, height, background, observer);
    }

    @Override
    public boolean drawImage(Image image, int destinationX1, int destinationY1, int destinationX2, int destinationY2,
                             int sourceX1, int sourceY1, int sourceX2, int sourceY2, ImageObserver observer) {
        beforePainting();
        return delegate.drawImage(image, destinationX1, destinationY1, destinationX2, destinationY2, sourceX1,
                sourceY1, sourceX2, sourceY2, observer);
    }

    @Override
    public boolean drawImage(Image image, int destinationX1, int destinationY1, int destinationX2, int destinationY2,
                             int sourceX1, int sourceY1, int sourceX2, int sourceY2, Color background,
                             ImageObserver observer) {
        beforePainting();
        return delegate.drawImage(image, destinationX1, destinationY1, destinationX2, destinationY2, sourceX1,
                sourceY1, sourceX2, sourceY2, background, observer);
    }

    @Override
    public void dispose() {
        // JFreePDF puts back the transform this child started with, which may not be the current owner's
        delegate.dispose();
        stream.owner = null;
    }
}
