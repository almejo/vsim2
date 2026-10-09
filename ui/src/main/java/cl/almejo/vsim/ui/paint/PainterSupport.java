package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/** Shared drawing style: shadowed bodies with screen-aligned light, round strokes, pin dots. */
final class PainterSupport {

    static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    static final BasicStroke STROKE = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final double PIN_DOT_RADIUS = 1.6;
    private static final double SHADOW_DISTANCE = 2.0;
    private static final Font BASE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    private PainterSupport() {
    }

    /** Fills {@code shape} with a shadow and a top-lit gradient, then outlines it. */
    static void body(Graphics2D graphics, Shape shape, ColorScheme scheme) {
        body(graphics, shape, scheme, null);
    }

    /** Like {@link #body(Graphics2D, Shape, ColorScheme)} but with a flat fill when {@code flatFill} is not null. */
    static void body(Graphics2D graphics, Shape shape, ColorScheme scheme, Color flatFill) {
        Point2D shadowOffset = localDirection(graphics, 1, 1, SHADOW_DISTANCE);
        graphics.setColor(scheme.shadow());
        graphics.fill(AffineTransform.getTranslateInstance(shadowOffset.getX(), shadowOffset.getY())
                .createTransformedShape(shape));
        if (flatFill != null) {
            graphics.setColor(flatFill);
        } else {
            Rectangle2D box = shape.getBounds2D();
            double reach = Math.hypot(box.getWidth(), box.getHeight()) / 2;
            Point2D down = localDirection(graphics, 0, 1, reach);
            graphics.setPaint(new GradientPaint(
                    (float) (box.getCenterX() - down.getX()), (float) (box.getCenterY() - down.getY()), scheme.componentFill(),
                    (float) (box.getCenterX() + down.getX()), (float) (box.getCenterY() + down.getY()), scheme.componentShade()));
        }
        graphics.fill(shape);
        outline(graphics, shape, scheme);
    }

    static void outline(Graphics2D graphics, Shape shape, ColorScheme scheme) {
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(STROKE);
        graphics.draw(shape);
    }

    /** A pin wire from the pin tip to the body. */
    static void lead(Graphics2D graphics, double startX, double startY, double endX, double endY, ColorScheme scheme) {
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(STROKE);
        graphics.draw(new Line2D.Double(startX, startY, endX, endY));
    }

    static void pinDots(Graphics2D graphics, ComponentType type, ColorScheme scheme) {
        graphics.setColor(scheme.componentStroke());
        for (PinSpec pin : type.pins()) {
            double x = pin.position().x() * UNIT;
            double y = pin.position().y() * UNIT;
            graphics.fill(new Ellipse2D.Double(x - PIN_DOT_RADIUS, y - PIN_DOT_RADIUS, 2 * PIN_DOT_RADIUS, 2 * PIN_DOT_RADIUS));
        }
    }

    static void centeredText(Graphics2D graphics, String text, double centerX, double centerY, float size, Color color) {
        graphics.setFont(BASE_FONT.deriveFont(size));
        graphics.setColor(color);
        FontMetrics metrics = graphics.getFontMetrics();
        Rectangle2D textBounds = metrics.getStringBounds(text, graphics);
        graphics.drawString(text, (float) (centerX - textBounds.getWidth() / 2),
                (float) (centerY + (metrics.getAscent() - metrics.getDescent()) / 2.0));
    }

    static Value valueOf(VisibleState state) {
        return switch (state) {
            case VisibleState.LedState led -> led.value();
            case VisibleState.SwitchState switchState -> switchState.value();
            case VisibleState.ClockState clock -> clock.value();
            case VisibleState.SevenSegmentsState display -> display.value();
            default -> Value.HIGH_IMPEDANCE;
        };
    }

    static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** The local vector that points like the screen vector (screenX, screenY), scaled to {@code length}. */
    private static Point2D localDirection(Graphics2D graphics, double screenX, double screenY, double length) {
        try {
            Point2D local = graphics.getTransform().createInverse().deltaTransform(new Point2D.Double(screenX, screenY), null);
            double norm = Math.hypot(local.getX(), local.getY());
            return new Point2D.Double(local.getX() / norm * length, local.getY() / norm * length);
        } catch (NoninvertibleTransformException exception) {
            double norm = Math.hypot(screenX, screenY);
            return new Point2D.Double(screenX / norm * length, screenY / norm * length);
        }
    }
}
