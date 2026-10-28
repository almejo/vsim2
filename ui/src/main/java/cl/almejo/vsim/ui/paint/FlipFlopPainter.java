package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.SequentialTypes;

import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * Every flip-flop (D, JK, T, SR) in its 4 x 4 box: inputs on the left with their letters, Q and Q̅ on the right, the
 * clock triangle at the pin in the middle of the bottom edge. The letters come from the pin names, so one painter
 * serves every kind; the symbols (D, J, K, T, S, R, Q, Q̅) are the same in every language.
 */
public final class FlipFlopPainter implements ComponentPainter {

    private static final String CLOCK_PIN = "clock";
    private static final String NOT_OUTPUT_LABEL = "Q̅";
    private static final double BODY_LEFT = 4;
    private static final double BODY_TOP = 2;
    private static final double BODY_RIGHT = 28;
    private static final double BODY_BOTTOM = 30;
    private static final double LABEL_INSET = 5.5;
    private static final float LABEL_SIZE = 8f;
    /** Q over Q̅ one grid unit (8 px) apart: small enough that the bar of Q̅ clears the Q above it. */
    private static final float CLOSE_LABEL_SIZE = 6.5f;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        for (PinSpec pin : type.pins()) {
            paintLead(graphics, pin, scheme);
        }
        PainterSupport.body(graphics, new RoundRectangle2D.Double(BODY_LEFT, BODY_TOP, BODY_RIGHT - BODY_LEFT,
                BODY_BOTTOM - BODY_TOP, 4, 4), scheme);
        for (PinSpec pin : type.pins()) {
            if (pin.name().equals(CLOCK_PIN)) {
                paintClockMark(graphics, pin.position().x() * UNIT, scheme);
            } else {
                paintLabel(graphics, pin, labelSize(type, pin), scheme);
            }
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    private static void paintLead(Graphics2D graphics, PinSpec pin, ColorScheme scheme) {
        double pinX = pin.position().x() * UNIT;
        double pinY = pin.position().y() * UNIT;
        if (pin.position().x() == 0) {
            PainterSupport.lead(graphics, pinX, pinY, BODY_LEFT, pinY, scheme);
        } else if (pinX >= BODY_RIGHT) {
            PainterSupport.lead(graphics, BODY_RIGHT, pinY, pinX, pinY, scheme);
        } else {
            PainterSupport.lead(graphics, pinX, pinY, pinX, BODY_BOTTOM, scheme);
        }
    }

    private static void paintClockMark(Graphics2D graphics, double pinX, ColorScheme scheme) {
        Path2D clockMark = new Path2D.Double();
        clockMark.moveTo(pinX - 4, BODY_BOTTOM);
        clockMark.lineTo(pinX, BODY_BOTTOM - 5);
        clockMark.lineTo(pinX + 4, BODY_BOTTOM);
        PainterSupport.outline(graphics, clockMark, scheme);
    }

    /** An input's letter inside the body next to its pin, Q or Q̅ next to an output. */
    private static void paintLabel(Graphics2D graphics, PinSpec pin, float size, ColorScheme scheme) {
        boolean left = pin.position().x() == 0;
        String label = pin.name().equals(SequentialTypes.NOT_OUTPUT_PIN) ? NOT_OUTPUT_LABEL
                : pin.name().toUpperCase(Locale.ROOT);
        double centerX = left ? BODY_LEFT + LABEL_INSET : BODY_RIGHT - LABEL_INSET;
        PainterSupport.centeredText(graphics, label, centerX, pin.position().y() * UNIT, size, scheme.text());
    }

    /** {@link #LABEL_SIZE}, smaller when a pin on the same side is one grid unit away (D's Q and Q̅). */
    private static float labelSize(ComponentType type, PinSpec pin) {
        int nearest = type.pins().stream().filter(other -> other != pin)
                .filter(other -> other.position().x() == pin.position().x())
                .mapToInt(other -> Math.abs(other.position().y() - pin.position().y())).min()
                .orElse(Integer.MAX_VALUE);
        return nearest > 1 ? LABEL_SIZE : CLOSE_LABEL_SIZE;
    }
}
