package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/** Dark display module, one hexadecimal digit per 4 input pins (pin 0 is the least significant bit). */
public final class SevenSegmentsPainter implements ComponentPainter {

    private static final Color MODULE = new Color(0x22272E);
    private static final Color LIT = new Color(0xFF3B30);
    private static final Color UNLIT = new Color(255, 255, 255, 28);
    private static final BasicStroke SEGMENT = new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final double DIGIT_WIDTH = 10;
    private static final double DIGIT_HEIGHT = 18;
    private static final double DIGIT_TOP = 17;
    /** Start and end of segments a..g in a DIGIT_WIDTH x DIGIT_HEIGHT box: {x1, y1, x2, y2}. */
    private static final double[][] SEGMENT_LINES = {
            {0, 0, 1, 0}, {1, 0, 1, 0.5}, {1, 0.5, 1, 1}, {0, 1, 1, 1}, {0, 0.5, 0, 1}, {0, 0, 0, 0.5}, {0, 0.5, 1, 0.5}};

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        for (PinSpec pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            PainterSupport.lead(graphics, pinX, pin.position().y() * UNIT, pinX, 6, scheme);
        }
        PainterSupport.body(graphics, new RoundRectangle2D.Double(1, 6, width - 1, 41, 6, 6), scheme, MODULE);
        Value value = state instanceof VisibleState.SevenSegmentsState display ? display.value() : null;
        int digitCount = type.pinCount() / 4;
        double slot = (width - 1) / digitCount;
        graphics.setStroke(SEGMENT);
        for (int digit = 0; digit < digitCount; digit++) {
            double left = 1 + (digitCount - 1 - digit) * slot + (slot - DIGIT_WIDTH) / 2;
            int number = digitValue(value, digit);
            for (int segment = 0; segment < SEGMENT_LINES.length; segment++) {
                double[] line = SEGMENT_LINES[segment];
                graphics.setColor(number >= 0 && SevenSegmentGlyph.isLit(number, segment) ? LIT : UNLIT);
                graphics.draw(new Line2D.Double(left + line[0] * DIGIT_WIDTH, DIGIT_TOP + line[1] * DIGIT_HEIGHT,
                        left + line[2] * DIGIT_WIDTH, DIGIT_TOP + line[3] * DIGIT_HEIGHT));
            }
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** The nibble of {@code digit}, or -1 when the value is unknown or a bit is not 0/1. */
    private static int digitValue(Value value, int digit) {
        if (value == null || value.conflict() || value.width() < (digit + 1) * 4) {
            return -1;
        }
        int number = 0;
        for (int bit = 0; bit < 4; bit++) {
            BitState state = value.bit(digit * 4 + bit);
            if (state == BitState.HIGH_IMPEDANCE) {
                return -1;
            }
            if (state == BitState.ONE) {
                number |= 1 << bit;
            }
        }
        return number;
    }
}
