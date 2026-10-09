package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * One display module with a seven-segment digit per 4 input pins (pin 0 is the least significant bit). Segments are
 * beveled bars lit with {@code segmentOn} or left in {@code segmentOff}; a digit with a Z or conflicting bit shows a
 * centered dash.
 */
public final class SevenSegmentsPainter implements ComponentPainter {

    /** Segment g alone: the dash shown when a digit cannot be read. No hexadecimal digit lights only g. */
    static final int DASH = 1 << 6;
    static final int SEGMENT_COUNT = 7;
    static final double DIGIT_WIDTH = 12;
    static final double DIGIT_HEIGHT = 22;
    static final double SEGMENT_THICKNESS = 3;
    static final double SEGMENT_GAP = 0.6;
    private static final double MODULE_TOP = 6;
    private static final double MODULE_LEFT = 1;
    /** Start and end of segments a..g in a unit digit box: {x1, y1, x2, y2}. */
    private static final double[][] SEGMENT_LINES = {
            {0, 0, 1, 0}, {1, 0, 1, 0.5}, {1, 0.5, 1, 1}, {0, 1, 1, 1}, {0, 0.5, 0, 1}, {0, 0, 0, 0.5}, {0, 0.5, 1, 0.5}};

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        for (PinSpec pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            PainterSupport.lead(graphics, pinX, pin.position().y() * UNIT, pinX, MODULE_TOP, scheme);
        }
        PainterSupport.body(graphics, new RoundRectangle2D.Double(MODULE_LEFT, MODULE_TOP, width - MODULE_LEFT,
                height - MODULE_TOP - 1, 6, 6), scheme, scheme.displayBackground());
        Value value = state instanceof VisibleState.SevenSegmentsState display ? display.value() : null;
        for (int digit = 0; digit < digitCount(type); digit++) {
            int lit = litSegments(value, digit);
            for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
                graphics.setColor((lit >>> segment & 1) == 1 ? scheme.segmentOn() : scheme.segmentOff());
                graphics.fill(segmentShape(type, digit, segment));
            }
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    static int digitCount(ComponentType type) {
        return type.pinCount() / 4;
    }

    /**
     * Bit mask of the segments (a..g = bits 0..6) lit for {@code digit}: nothing without a value, the hexadecimal glyph
     * of its nibble, or {@link #DASH} when the value is too narrow, in conflict or has a Z bit in that nibble.
     */
    static int litSegments(Value value, int digit) {
        if (value == null) {
            return 0;
        }
        if (value.conflict() || value.width() < (digit + 1) * 4) {
            return DASH;
        }
        int number = 0;
        for (int bit = 0; bit < 4; bit++) {
            BitState state = value.bit(digit * 4 + bit);
            if (state == BitState.HIGH_IMPEDANCE) {
                return DASH;
            }
            if (state == BitState.ONE) {
                number |= 1 << bit;
            }
        }
        return SevenSegmentGlyph.segments(number);
    }

    /** Segment {@code segment} (0 = a … 6 = g) of {@code digit} (0 = rightmost), in local coordinates. */
    static Shape segmentShape(ComponentType type, int digit, int segment) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        int digitCount = digitCount(type);
        double slot = (width - MODULE_LEFT) / digitCount;
        double left = MODULE_LEFT + (digitCount - 1 - digit) * slot + (slot - DIGIT_WIDTH) / 2;
        double top = MODULE_TOP + (height - MODULE_TOP - 1 - DIGIT_HEIGHT) / 2;
        double[] line = SEGMENT_LINES[segment];
        return beveledBar(left + line[0] * DIGIT_WIDTH, top + line[1] * DIGIT_HEIGHT,
                left + line[2] * DIGIT_WIDTH, top + line[3] * DIGIT_HEIGHT);
    }

    /** A bar of {@link #SEGMENT_THICKNESS} with pointed ends, {@link #SEGMENT_GAP} short of each end point. */
    static Shape beveledBar(double startX, double startY, double endX, double endY) {
        double length = Math.hypot(endX - startX, endY - startY);
        double alongX = (endX - startX) / length;
        double alongY = (endY - startY) / length;
        double half = SEGMENT_THICKNESS / 2;
        double acrossX = -alongY * half;
        double acrossY = alongX * half;
        double fromX = startX + alongX * SEGMENT_GAP;
        double fromY = startY + alongY * SEGMENT_GAP;
        double toX = endX - alongX * SEGMENT_GAP;
        double toY = endY - alongY * SEGMENT_GAP;
        Path2D bar = new Path2D.Double();
        bar.moveTo(fromX, fromY);
        bar.lineTo(fromX + alongX * half + acrossX, fromY + alongY * half + acrossY);
        bar.lineTo(toX - alongX * half + acrossX, toY - alongY * half + acrossY);
        bar.lineTo(toX, toY);
        bar.lineTo(toX - alongX * half - acrossX, toY - alongY * half - acrossY);
        bar.lineTo(fromX + alongX * half - acrossX, fromY + alongY * half - acrossY);
        bar.closePath();
        return bar;
    }
}
