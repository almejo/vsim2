package cl.almejo.vsim.ui.print;

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
 * A {@link Graphics2D} that paints into another one with every colour turned grey ({@link MonochromeScheme#grey}):
 * the safety net of «Blanco y negro» for the colours painters pick by themselves (an LED's hue, a highlight mixed from
 * a scheme colour), so no colour reaches the paper even from a painter that does not know about black and white.
 * Colours and gradients are greyed; other paints and images pass unchanged (nothing VSim paints on a page uses them).
 * Every call goes straight to the wrapped graphics, so a vector destination (PDF, SVG) stays vector.
 */
public final class MonochromeGraphics extends Graphics2D {

    private final Graphics2D delegate;

    public MonochromeGraphics(Graphics2D delegate) {
        this.delegate = delegate;
    }

    private static Paint grey(Paint paint) {
        if (paint instanceof Color color) {
            return MonochromeScheme.grey(color);
        }
        if (paint instanceof GradientPaint gradient) {
            return new GradientPaint(gradient.getPoint1(), MonochromeScheme.grey(gradient.getColor1()),
                    gradient.getPoint2(), MonochromeScheme.grey(gradient.getColor2()), gradient.isCyclic());
        }
        return paint;
    }

    private static Color greyOrNull(Color color) {
        return color == null ? null : MonochromeScheme.grey(color);
    }

    @Override
    public void setColor(Color color) {
        delegate.setColor(greyOrNull(color));
    }

    @Override
    public void setPaint(Paint paint) {
        delegate.setPaint(paint == null ? null : grey(paint));
    }

    @Override
    public void setBackground(Color color) {
        delegate.setBackground(greyOrNull(color));
    }

    @Override
    public void setXORMode(Color color) {
        delegate.setXORMode(MonochromeScheme.grey(color));
    }

    @Override
    public Graphics create() {
        return new MonochromeGraphics((Graphics2D) delegate.create());
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
        delegate.drawString(text, x, y);
    }

    @Override
    public void drawString(String text, float x, float y) {
        delegate.drawString(text, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        delegate.drawString(iterator, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        delegate.drawString(iterator, x, y);
    }

    @Override
    public void drawGlyphVector(GlyphVector glyphs, float x, float y) {
        delegate.drawGlyphVector(glyphs, x, y);
    }

    @Override
    public void fill(Shape shape) {
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

    @Override
    public void setTransform(AffineTransform transform) {
        delegate.setTransform(transform);
    }

    @Override
    public AffineTransform getTransform() {
        return delegate.getTransform();
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
        return delegate.getFontRenderContext();
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
        return delegate.drawImage(image, x, y, greyOrNull(background), observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height, Color background,
                             ImageObserver observer) {
        return delegate.drawImage(image, x, y, width, height, greyOrNull(background), observer);
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
                sourceY1, sourceX2, sourceY2, greyOrNull(background), observer);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
