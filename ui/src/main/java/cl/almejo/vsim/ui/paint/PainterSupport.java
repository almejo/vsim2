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
import java.awt.geom.RoundRectangle2D;

/** Shared drawing style: shadowed bodies with screen-aligned light, round strokes, pin dots. */
public final class PainterSupport {

    static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    static final BasicStroke STROKE = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final double PIN_DOT_RADIUS = 1.6;
    /** Half the side of the square that marks a pin wider than one bit. */
    public static final double BUS_PIN_HALF_SIDE = 2.2;
    private static final double SHADOW_DISTANCE = 2.0;
    /** The font of every text drawn by painters (sized per use); also for measuring a text before drawing it. */
    static final Font BASE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    static final double ALIGN_LEFT = 0;
    static final double ALIGN_CENTER = 0.5;
    static final double ALIGN_RIGHT = 1;

    /** Opacity of the board-colored backdrop behind a tag. */
    public static final int TAG_BACKDROP_ALPHA = 200;

    private PainterSupport() {
    }

    /** Text on a translucent backdrop of {@code backgroundColor}, so it stays legible over wires. */
    public static void paintTag(Graphics2D graphics, FontMetrics metrics, String text, double left, double baseline,
                                Color textColor, Color backgroundColor) {
        double textWidth = metrics.stringWidth(text);
        graphics.setColor(new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(),
                TAG_BACKDROP_ALPHA));
        graphics.fill(new RoundRectangle2D.Double(left - 2, baseline - metrics.getAscent(), textWidth + 4,
                metrics.getAscent() + metrics.getDescent(), 4, 4));
        graphics.setColor(textColor);
        graphics.drawString(text, (float) left, (float) baseline);
    }

    /** Fills {@code shape} with a shadow and a top-lit gradient, then outlines it. */
    static void body(Graphics2D graphics, Shape shape, ColorScheme scheme) {
        body(graphics, shape, scheme, STROKE);
    }

    /** Like {@link #body(Graphics2D, Shape, ColorScheme)} but with a flat fill when {@code flatFill} is not null. */
    static void body(Graphics2D graphics, Shape shape, ColorScheme scheme, Color flatFill) {
        body(graphics, shape, scheme, flatFill, STROKE);
    }

    /** Like {@link #body(Graphics2D, Shape, ColorScheme)} with an explicit outline stroke and the gradient fill. */
    static void body(Graphics2D graphics, Shape shape, ColorScheme scheme, BasicStroke stroke) {
        body(graphics, shape, scheme, null, stroke);
    }

    /** Like the flat-fill overload with an explicit outline stroke (a zoomed view keeps its width on screen). */
    static void body(Graphics2D graphics, Shape shape, ColorScheme scheme, Color flatFill, BasicStroke stroke) {
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
        outline(graphics, shape, scheme, stroke);
    }

    static void outline(Graphics2D graphics, Shape shape, ColorScheme scheme) {
        outline(graphics, shape, scheme, STROKE);
    }

    private static void outline(Graphics2D graphics, Shape shape, ColorScheme scheme, BasicStroke stroke) {
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(stroke);
        graphics.draw(shape);
    }

    /** A pin wire from the pin tip to the body. */
    static void lead(Graphics2D graphics, double startX, double startY, double endX, double endY, ColorScheme scheme) {
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(STROKE);
        graphics.draw(new Line2D.Double(startX, startY, endX, endY));
    }

    /** A round dot on each 1-bit pin and a square on each wider (bus) pin, in the scheme's pin colour. */
    static void pinDots(Graphics2D graphics, ComponentType type, ColorScheme scheme) {
        pinDots(graphics, type, scheme, 1);
    }

    /** Pin dots at {@code scale} times their real size (a zoomed view keeps them a constant size on screen). */
    static void pinDots(Graphics2D graphics, ComponentType type, ColorScheme scheme, double scale) {
        double busHalfSide = BUS_PIN_HALF_SIDE * scale;
        double dotRadius = PIN_DOT_RADIUS * scale;
        graphics.setColor(scheme.pad());
        for (PinSpec pin : type.pins()) {
            double x = pin.position().x() * UNIT;
            double y = pin.position().y() * UNIT;
            if (pin.width() > 1) {
                graphics.fill(new Rectangle2D.Double(x - busHalfSide, y - busHalfSide, 2 * busHalfSide,
                        2 * busHalfSide));
            } else {
                graphics.fill(new Ellipse2D.Double(x - dotRadius, y - dotRadius, 2 * dotRadius, 2 * dotRadius));
            }
        }
    }

    /** Draws {@code text} centered on the local point, but always upright on screen whatever the rotation. */
    static void centeredText(Graphics2D graphics, String text, double centerX, double centerY, float size, Color color) {
        uprightText(graphics, text, centerX, centerY, size, color, ALIGN_CENTER);
    }

    /**
     * Draws {@code text} upright on screen whatever the rotation, vertically centered on the local anchor point.
     * {@code alignment} is the part of the text, measured along the screen's horizontal, that sits on the anchor:
     * {@link #ALIGN_LEFT} its left end, {@link #ALIGN_CENTER} its middle, {@link #ALIGN_RIGHT} its right end. Painters
     * must draw every text through this method so no text is ever upside down.
     */
    static void uprightText(Graphics2D graphics, String text, double anchorX, double anchorY, float size, Color color,
                            double alignment) {
        uprightText(graphics, text, anchorX, anchorY, size, color, alignment, BASE_FONT);
    }

    /** Upright text using an explicitly supplied current or export-frozen font. */
    static void uprightText(Graphics2D graphics, String text, double anchorX, double anchorY, float size, Color color,
                            double alignment, Font font) {
        int quarterTurns = quarterTurns(graphics);
        Graphics2D upright = (Graphics2D) graphics.create();
        try {
            upright.rotate(-quarterTurns * Math.PI / 2, anchorX, anchorY);
            upright.setFont(font.deriveFont(size));
            upright.setColor(color);
            FontMetrics metrics = upright.getFontMetrics();
            Rectangle2D textBounds = metrics.getStringBounds(text, upright);
            upright.drawString(text, (float) (anchorX - textBounds.getWidth() * alignment),
                    (float) (anchorY + (metrics.getAscent() - metrics.getDescent()) / 2.0));
        } finally {
            upright.dispose();
        }
    }

    /**
     * Draws {@code text} centred in the local {@code box}, along the box's local x axis and readable on screen: left to
     * right when that axis is horizontal on screen (never upside down), bottom to top when it is vertical (a tunnel
     * turned 90° or 270°). The font shrinks below {@code size} when the text would not fit the box's length.
     */
    static void textAlong(Graphics2D graphics, String text, Rectangle2D box, float size, Color color) {
        double centerX = box.getCenterX();
        double centerY = box.getCenterY();
        Graphics2D along = (Graphics2D) graphics.create();
        try {
            along.rotate(-quarterTurns(graphics) * Math.PI / 2, centerX, centerY);
            if (turnedSideways(graphics)) {
                along.rotate(-Math.PI / 2, centerX, centerY);
            }
            Font font = BASE_FONT.deriveFont(size);
            double width = along.getFontMetrics(font).getStringBounds(text, along).getWidth();
            if (width > box.getWidth()) {
                font = BASE_FONT.deriveFont((float) (size * box.getWidth() / width));
            }
            along.setFont(font);
            along.setColor(color);
            FontMetrics metrics = along.getFontMetrics();
            along.drawString(text, (float) (centerX - metrics.getStringBounds(text, along).getWidth() / 2),
                    (float) (centerY + (metrics.getAscent() - metrics.getDescent()) / 2.0));
        } finally {
            along.dispose();
        }
    }

    /** The rotation of {@code graphics}' transform in quarter turns, from -2 to 2 (0 and ±2: texts run along local x). */
    static int quarterTurns(Graphics2D graphics) {
        AffineTransform transform = graphics.getTransform();
        return (int) Math.round(Math.atan2(transform.getShearY(), transform.getScaleX()) / (Math.PI / 2));
    }

    /** True when the local x axis is vertical on screen (R90, R270), so an upright text runs along local y. */
    static boolean turnedSideways(Graphics2D graphics) {
        return Math.abs(quarterTurns(graphics)) % 2 == 1;
    }

    /** Like {@link #centeredText} but nothing is drawn outside {@code area} (local coordinates). */
    static void centeredText(Graphics2D graphics, Shape area, String text, double centerX, double centerY, float size,
                             Color color) {
        Graphics2D clipped = (Graphics2D) graphics.create();
        try {
            clipped.clip(area);
            centeredText(clipped, text, centerX, centerY, size, color);
        } finally {
            clipped.dispose();
        }
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

    public static Color withAlpha(Color color, int alpha) {
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
