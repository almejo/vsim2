package cl.almejo.vsim.ui.paint;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.image.ImageObserver;

/**
 * A {@link Graphics2D} that paints into another one with every colour turned grey ({@link MonochromeScheme#grey}):
 * the safety net of «Blanco y negro» for the colours painters pick by themselves (an LED's hue, a highlight mixed from
 * a scheme colour), so no colour reaches the paper even from a painter that does not know about black and white.
 * Colours and gradients are greyed; other paints and images pass unchanged (nothing VSim paints on a page uses them).
 * Every other call goes straight to the wrapped graphics ({@link ForwardingGraphics}), so a vector destination (PDF,
 * SVG) stays vector.
 */
public final class MonochromeGraphics extends ForwardingGraphics {

    public MonochromeGraphics(Graphics2D delegate) {
        super(delegate);
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
    protected Graphics2D wrap(Graphics2D created) {
        return new MonochromeGraphics(created);
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
                             int sourceX1, int sourceY1, int sourceX2, int sourceY2, Color background,
                             ImageObserver observer) {
        return delegate.drawImage(image, destinationX1, destinationY1, destinationX2, destinationY2, sourceX1,
                sourceY1, sourceX2, sourceY2, greyOrNull(background), observer);
    }
}
