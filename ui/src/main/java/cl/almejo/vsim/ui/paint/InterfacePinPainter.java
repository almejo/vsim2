package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * {@code input-pin} and {@code output-pin}: a tag pointing right, tinted with the value it carries. A pin of more than
 * one bit also writes its value, in its «Mostrar como» (M7), in a value box on the free side of the tag (left of an
 * input pin, whose wire leaves on the right; right of an output pin). The box stays upright on screen whatever the
 * rotation, and lies outside the component's bounds: the pin's shape cannot grow without moving the pins of files
 * written before.
 */
public final class InterfacePinPainter implements ComponentPainter {

    private static final double WIDTH_TEXT_X = 7;
    private static final double WIDTH_TEXT_Y = 8;
    private static final float WIDTH_TEXT_SIZE = 6f;
    /** The middle of the tag, in local pixels. */
    private static final double TAG_MIDDLE_Y = 8;
    private static final double TAG_LEFT = 1;
    private static final double TAG_RIGHT = 15;
    /** Space between the tag and the value box. */
    private static final double BOX_GAP = 3;
    private static final double BOX_HEIGHT = 13;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Path2D tag = new Path2D.Double();
        tag.moveTo(TAG_LEFT, 2);
        tag.lineTo(10, 2);
        tag.lineTo(TAG_RIGHT, TAG_MIDDLE_Y);
        tag.lineTo(10, 14);
        tag.lineTo(TAG_LEFT, 14);
        tag.closePath();
        Value value = switch (state) {
            case VisibleState.SwitchState switchState -> switchState.value();
            case VisibleState.LedState led -> led.value();
            default -> null;
        };
        Color fill = value == null ? scheme.componentFill()
                : PainterSupport.withAlpha(SignalColors.valueColor(value, scheme), 110);
        PainterSupport.body(graphics, tag, scheme, fill);
        int width = type.pin(0).width();
        if (width > 1) {
            PainterSupport.centeredText(graphics, String.valueOf(width), WIDTH_TEXT_X, WIDTH_TEXT_Y, WIDTH_TEXT_SIZE,
                    scheme.text());
            paintValueBox(graphics, type, parameters, value, scheme);
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    private static void paintValueBox(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                                      Value value, ColorScheme scheme) {
        String format = formatOf(type, parameters);
        Point2D anchor = anchor(type);
        Point2D screenDirection = graphics.getTransform().deltaTransform(localDirection(type), null);
        Graphics2D upright = (Graphics2D) graphics.create();
        try {
            // turn back around the anchor: from here on local x and y run like the screen's
            upright.rotate(-PainterSupport.quarterTurns(graphics) * Math.PI / 2, anchor.getX(), anchor.getY());
            Rectangle2D box = boxBeside(anchor.getX(), anchor.getY(), screenDirection,
                    boxWidth(type.pin(0).width(), format));
            PainterSupport.body(upright, new RoundRectangle2D.Double(box.getX(), box.getY(), box.getWidth(),
                    box.getHeight(), 5, 5), scheme, scheme.componentFill());
            String text = shownText(value, format);
            if (!text.isEmpty()) {
                PainterSupport.centeredText(upright, box, text, box.getCenterX(), box.getCenterY(),
                        ConstantPainter.TEXT_SIZE, scheme.text());
            }
        } finally {
            upright.dispose();
        }
    }

    /**
     * The value box of a pin of more than one bit, in document pixels: where to place a value editor. Empty for a
     * 1-bit pin, which draws no box.
     */
    public static Optional<Rectangle2D> valueBox(ComponentType type, Component component) {
        ComponentType shaped = type.specializedFor(component.parameters());
        if (shaped.pin(0).width() <= 1) {
            return Optional.empty();
        }
        AffineTransform placement = new AffineTransform();
        placement.translate(component.position().x() * UNIT, component.position().y() * UNIT);
        placement.rotate(component.rotation().quarterTurns() * Math.PI / 2);
        Point2D anchor = placement.transform(anchor(shaped), null);
        Point2D direction = placement.deltaTransform(localDirection(shaped), null);
        return Optional.of(boxBeside(anchor.getX(), anchor.getY(), direction,
                boxWidth(shaped.pin(0).width(), formatOf(shaped, component.parameters()))));
    }

    /** The font the value is written with at 100 %. */
    public static Font valueFont() {
        return ConstantPainter.valueFont();
    }

    /**
     * The value as the box writes it: in {@code format} when every bit is 0 or 1; otherwise, in binary each bit (Z for
     * a bit in high impedance), in hex each digit as {@link Value#hex()} writes it, in decimal {@code Z} when all the
     * bits are in high impedance and {@code ?} otherwise. A conflict is {@code ?}; no value is an empty text.
     */
    static String shownText(Value value, String format) {
        if (value == null) {
            return "";
        }
        if (value.conflict()) {
            return "?";
        }
        int width = value.width();
        if (value.isFullyDefined()) {
            return ConstantValue.display(value.bits(), width, format);
        }
        return switch (ConstantValue.resolveFormat(format, width)) {
            case ConstantValue.BINARY -> {
                StringBuilder bits = new StringBuilder(width);
                for (int index = width - 1; index >= 0; index--) {
                    bits.append((value.highImpedanceMask() >>> index & 1) == 1 ? 'Z'
                            : (char) ('0' + (value.bits() >>> index & 1)));
                }
                yield bits.toString();
            }
            case ConstantValue.HEX -> "0x" + value.hex();
            default -> value.isFullyHighImpedance() ? "Z" : "?";
        };
    }

    private static String formatOf(ComponentType type, Map<String, Object> parameters) {
        Object format = type.resolveParameters(parameters).get(ConstantValue.FORMAT);
        return format == null ? ConstantValue.AUTO : (String) format;
    }

    private static boolean isInput(ComponentType type) {
        return type.id().equals(InterfacePins.INPUT_PIN);
    }

    /** The middle of the tag's side that faces the box, moved out by the gap (local pixels). */
    private static Point2D anchor(ComponentType type) {
        return isInput(type) ? new Point2D.Double(TAG_LEFT - BOX_GAP, TAG_MIDDLE_Y)
                : new Point2D.Double(TAG_RIGHT + BOX_GAP + 1, TAG_MIDDLE_Y);
    }

    /** Where the box lies from the anchor, in local coordinates: away from the pin. */
    private static Point2D localDirection(ComponentType type) {
        return new Point2D.Double(isInput(type) ? -1 : 1, 0);
    }

    private static double boxWidth(int width, String format) {
        return ConstantPainter.widestTextWidth(width, format) + 2 * ConstantPainter.TEXT_PADDING;
    }

    /**
     * An upright box next to {@code (anchorX, anchorY)} on the side {@code direction} points to (in the same upright
     * frame): beside the anchor and centred on it vertically when the direction is horizontal, above or below and
     * centred horizontally when it is vertical.
     */
    private static Rectangle2D boxBeside(double anchorX, double anchorY, Point2D direction, double boxWidth) {
        if (Math.abs(direction.getX()) >= Math.abs(direction.getY())) {
            double left = direction.getX() < 0 ? anchorX - boxWidth : anchorX;
            return new Rectangle2D.Double(left, anchorY - BOX_HEIGHT / 2, boxWidth, BOX_HEIGHT);
        }
        double top = direction.getY() < 0 ? anchorY - BOX_HEIGHT : anchorY;
        return new Rectangle2D.Double(anchorX - boxWidth / 2, top, boxWidth, BOX_HEIGHT);
    }
}
