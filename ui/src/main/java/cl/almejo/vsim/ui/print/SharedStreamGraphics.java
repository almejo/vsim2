package cl.almejo.vsim.ui.print;

import java.awt.AlphaComposite;
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
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * The {@link Graphics2D} the painters get for one PDF page: what {@link PdfPages} hands out. Each one (the page's and
 * every {@link #create()}d child) keeps its own state — transform, clip, paint, stroke, transparency, font — and
 * writes it into the page's one JFreePDF graphics right before it paints, so a child never leaves its state behind.
 *
 * <p>Why not JFreePDF's own children: in JFreePDF 2.0.1 a child writes into the same content stream as its parent,
 * {@code setColor} and {@code setStroke} skip a value equal to the one that graphics believes is current, and {@code
 * dispose()} only puts the transform back; so a child's colour, stroke and transparency stayed in the stream and the
 * parent painted with them (component labels came out white or vanished). Worse, {@code java.awt.Graphics} disposes
 * a graphics again when the garbage collector finalises it, and JFreePDF's {@code dispose()} then writes a stale
 * transform into whatever page is being painted at that moment, from the finaliser thread. Here no JFreePDF child is
 * ever made, and only the page's own graphics, whose dispose writes nothing, touches the stream.
 *
 * <p>Two more JFreePDF gaps are covered on the way: text is filled as the outline of its {@link TextLayout} (JFreePDF
 * would draw that layout into itself, moving the transform inside a saved state for text that needs a fallback
 * font), and a linear gradient is mapped through the transform (JFreePDF writes its points in page coordinates; VSim
 * only rotates and scales uniformly, under which a mapped linear gradient is the same gradient).
 */
final class SharedStreamGraphics extends Graphics2D {

    private final Graphics2D page;
    private AffineTransform transform;
    /** In page (device) coordinates; null when nothing is cut. */
    private Shape clip;
    private Paint paint;
    private Color color;
    private Stroke stroke;
    private Composite composite;
    private Font font;
    private Color background;
    private RenderingHints hints;

    /** The graphics the painters get for {@code page}, the page's own JFreePDF graphics, with its current state. */
    static SharedStreamGraphics forPage(Graphics2D page) {
        return new SharedStreamGraphics(page);
    }

    private SharedStreamGraphics(Graphics2D page) {
        this.page = page;
        this.transform = page.getTransform();
        this.clip = null;
        this.paint = page.getPaint();
        this.color = page.getColor();
        this.stroke = page.getStroke();
        this.composite = page.getComposite();
        this.font = page.getFont();
        this.background = page.getBackground();
        this.hints = page.getRenderingHints();
    }

    private SharedStreamGraphics(SharedStreamGraphics parent) {
        this.page = parent.page;
        this.transform = new AffineTransform(parent.transform);
        this.clip = parent.clip;
        this.paint = parent.paint;
        this.color = parent.color;
        this.stroke = parent.stroke;
        this.composite = parent.composite;
        this.font = parent.font;
        this.background = parent.background;
        this.hints = (RenderingHints) parent.hints.clone();
    }

    /**
     * Writes this graphics' state into the page's graphics. JFreePDF skips the values it already has, and since every
     * graphics of the page goes through the one JFreePDF graphics, what it has is what the stream has.
     */
    private void beforePainting() {
        if (!page.getTransform().equals(transform)) {
            page.setTransform(transform);
        }
        page.setClip(userClip());
        page.setComposite(composite);
        if (paint instanceof GradientPaint gradient) {
            page.setPaint(new GradientPaint(transform.transform(gradient.getPoint1(), null), gradient.getColor1(),
                    transform.transform(gradient.getPoint2(), null), gradient.getColor2(), gradient.isCyclic()));
        } else {
            page.setPaint(paint);
        }
        page.setStroke(stroke);
        page.setFont(font);
    }

    /** The clip in this graphics' user space, or null. */
    private Shape userClip() {
        if (clip == null) {
            return null;
        }
        try {
            return transform.createInverse().createTransformedShape(clip);
        } catch (NoninvertibleTransformException exception) {
            return new Rectangle2D.Double();
        }
    }

    // --- state ------------------------------------------------------------------------------------------------

    @Override
    public Graphics create() {
        return new SharedStreamGraphics(this);
    }

    @Override
    public void dispose() {
        // nothing to give back: the page's graphics is disposed by PdfPages, and a finaliser calling this is harmless
    }

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public void setColor(Color color) {
        if (color != null) {
            this.color = color;
            this.paint = color;
        }
    }

    @Override
    public Paint getPaint() {
        return paint;
    }

    @Override
    public void setPaint(Paint paint) {
        if (paint instanceof Color asColor) {
            setColor(asColor);
        } else if (paint != null) {
            this.paint = paint;
        }
    }

    @Override
    public Color getBackground() {
        return background;
    }

    @Override
    public void setBackground(Color color) {
        this.background = color;
    }

    @Override
    public void setPaintMode() {
        // XOR mode is not supported by the PDF; painting mode is the only one
    }

    @Override
    public void setXORMode(Color color) {
        // ignored, as JFreePDF does
    }

    @Override
    public Composite getComposite() {
        return composite;
    }

    @Override
    public void setComposite(Composite composite) {
        if (composite == null) {
            throw new IllegalArgumentException("composite is null");
        }
        this.composite = composite;
    }

    @Override
    public Stroke getStroke() {
        return stroke;
    }

    @Override
    public void setStroke(Stroke stroke) {
        if (stroke == null) {
            throw new IllegalArgumentException("stroke is null");
        }
        this.stroke = stroke;
    }

    @Override
    public Font getFont() {
        return font;
    }

    @Override
    public void setFont(Font font) {
        if (font != null) {
            this.font = font;
        }
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
        return page.getFontMetrics(font);
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return page.getFontRenderContext();
    }

    @Override
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        hints.put(key, value);
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key key) {
        return hints.get(key);
    }

    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        this.hints.clear();
        this.hints.putAll(hints);
    }

    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        this.hints.putAll(hints);
    }

    @Override
    public RenderingHints getRenderingHints() {
        return (RenderingHints) hints.clone();
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return page.getDeviceConfiguration();
    }

    // --- transform --------------------------------------------------------------------------------------------

    @Override
    public void translate(int x, int y) {
        transform.translate(x, y);
    }

    @Override
    public void translate(double x, double y) {
        transform.translate(x, y);
    }

    @Override
    public void rotate(double theta) {
        transform.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        transform.rotate(theta, x, y);
    }

    @Override
    public void scale(double scaleX, double scaleY) {
        transform.scale(scaleX, scaleY);
    }

    @Override
    public void shear(double shearX, double shearY) {
        transform.shear(shearX, shearY);
    }

    @Override
    public void transform(AffineTransform transform) {
        this.transform.concatenate(transform);
    }

    @Override
    public void setTransform(AffineTransform transform) {
        this.transform = transform == null ? new AffineTransform() : new AffineTransform(transform);
    }

    @Override
    public AffineTransform getTransform() {
        return new AffineTransform(transform);
    }

    // --- clip -------------------------------------------------------------------------------------------------

    @Override
    public void clip(Shape shape) {
        if (shape == null) {
            clip = null;
            return;
        }
        Shape transformed = transform.createTransformedShape(shape);
        if (clip == null) {
            clip = transformed;
            return;
        }
        Area area = new Area(transformed);
        area.intersect(new Area(clip));
        clip = new Path2D.Double(area);
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        clip(new Rectangle(x, y, width, height));
    }

    @Override
    public void setClip(Shape shape) {
        clip = shape == null ? null : transform.createTransformedShape(shape);
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        setClip(new Rectangle(x, y, width, height));
    }

    @Override
    public Shape getClip() {
        return userClip();
    }

    @Override
    public Rectangle getClipBounds() {
        Shape userClip = userClip();
        return userClip == null ? null : userClip.getBounds();
    }

    @Override
    public boolean hit(Rectangle rectangle, Shape shape, boolean onStroke) {
        Shape tested = onStroke ? stroke.createStrokedShape(shape) : shape;
        return transform.createTransformedShape(tested).intersects(rectangle);
    }

    // --- painting ---------------------------------------------------------------------------------------------

    @Override
    public void draw(Shape shape) {
        beforePainting();
        page.draw(shape);
    }

    @Override
    public void fill(Shape shape) {
        beforePainting();
        page.fill(shape);
    }

    @Override
    public void drawString(String text, int x, int y) {
        drawString(text, (float) x, (float) y);
    }

    @Override
    public void drawString(String text, float x, float y) {
        if (text.isEmpty()) {
            return;
        }
        fill(new TextLayout(text, font, getFontRenderContext()).getOutline(AffineTransform.getTranslateInstance(x,
                y)));
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
        fill(new TextLayout(iterator, getFontRenderContext()).getOutline(AffineTransform.getTranslateInstance(x, y)));
    }

    @Override
    public void drawGlyphVector(GlyphVector glyphs, float x, float y) {
        fill(glyphs.getOutline(x, y));
    }

    @Override
    public void drawLine(int fromX, int fromY, int toX, int toY) {
        draw(new Line2D.Double(fromX, fromY, toX, toY));
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        fill(new Rectangle(x, y, width, height));
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        if (background == null) {
            return;
        }
        Paint savedPaint = paint;
        Color savedColor = color;
        Composite savedComposite = composite;
        paint = background;
        composite = AlphaComposite.Src;
        fill(new Rectangle(x, y, width, height));
        paint = savedPaint;
        color = savedColor;
        composite = savedComposite;
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        draw(new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight));
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        fill(new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight));
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        draw(new Ellipse2D.Double(x, y, width, height));
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        fill(new Ellipse2D.Double(x, y, width, height));
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        draw(new Arc2D.Double(x, y, width, height, startAngle, arcAngle, Arc2D.OPEN));
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        fill(new Arc2D.Double(x, y, width, height, startAngle, arcAngle, Arc2D.PIE));
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int pointCount) {
        if (pointCount < 2) {
            return;
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xPoints[0], yPoints[0]);
        for (int index = 1; index < pointCount; index++) {
            path.lineTo(xPoints[index], yPoints[index]);
        }
        draw(path);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        draw(new Polygon(xPoints, yPoints, pointCount));
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int pointCount) {
        fill(new Polygon(xPoints, yPoints, pointCount));
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int deltaX, int deltaY) {
        // a PDF cannot copy what it already drew, as in JFreePDF
    }

    @Override
    public boolean drawImage(Image image, AffineTransform imageTransform, ImageObserver observer) {
        beforePainting();
        return page.drawImage(image, imageTransform, observer);
    }

    @Override
    public void drawImage(BufferedImage image, BufferedImageOp operation, int x, int y) {
        beforePainting();
        page.drawImage(image, operation, x, y);
    }

    @Override
    public void drawRenderedImage(RenderedImage image, AffineTransform imageTransform) {
        beforePainting();
        page.drawRenderedImage(image, imageTransform);
    }

    @Override
    public void drawRenderableImage(RenderableImage image, AffineTransform imageTransform) {
        beforePainting();
        page.drawRenderableImage(image, imageTransform);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, ImageObserver observer) {
        beforePainting();
        return page.drawImage(image, x, y, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height, ImageObserver observer) {
        beforePainting();
        return page.drawImage(image, x, y, width, height, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, Color imageBackground, ImageObserver observer) {
        beforePainting();
        return page.drawImage(image, x, y, imageBackground, observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height, Color imageBackground,
                             ImageObserver observer) {
        beforePainting();
        return page.drawImage(image, x, y, width, height, imageBackground, observer);
    }

    @Override
    public boolean drawImage(Image image, int destinationX1, int destinationY1, int destinationX2, int destinationY2,
                             int sourceX1, int sourceY1, int sourceX2, int sourceY2, ImageObserver observer) {
        beforePainting();
        return page.drawImage(image, destinationX1, destinationY1, destinationX2, destinationY2, sourceX1, sourceY1,
                sourceX2, sourceY2, observer);
    }

    @Override
    public boolean drawImage(Image image, int destinationX1, int destinationY1, int destinationX2, int destinationY2,
                             int sourceX1, int sourceY1, int sourceX2, int sourceY2, Color imageBackground,
                             ImageObserver observer) {
        beforePainting();
        return page.drawImage(image, destinationX1, destinationY1, destinationX2, destinationY2, sourceX1, sourceY1,
                sourceX2, sourceY2, imageBackground, observer);
    }
}
