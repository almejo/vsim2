package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Map;

/**
 * The drawing a Buffer and a tri-state buffer share: the triangle pointing right, the bubble of «Negar salida» before
 * the output, and the input and output leads (thick for a bus). Where the output lead starts is each painter's (the
 * tri-state's two pixels sooner than the Buffer's).
 */
final class BufferTriangle {

    static final double MIDDLE = 16;
    static final double TRIANGLE_BACK = 6;
    static final double TRIANGLE_TOP = 4;
    static final double TRIANGLE_BOTTOM = 28;
    private static final double BUBBLE_DIAMETER = 6;
    private static final double OUTPUT_LEAD_END = 32;

    private BufferTriangle() {
    }

    /** The triangle's tip for an output lead starting at {@code outputLeadStart}: the bubble, if any, comes first. */
    static double tip(boolean negated, double outputLeadStart) {
        return negated ? outputLeadStart - BUBBLE_DIAMETER : outputLeadStart;
    }

    /** Whether «Negar salida» is on: the bubble before the output. */
    static boolean negated(Map<String, Object> parameters) {
        return Boolean.TRUE.equals(parameters.get(StandardComponentTypes.NEGATE));
    }

    /**
     * The input lead up to the triangle and the output lead from {@code outputLeadStart} on, thick when a pin of
     * {@code shaped} (the type as its parameters shape it) is a bus.
     */
    static void paintLeads(Graphics2D graphics, ComponentType shaped, double outputLeadStart, ColorScheme scheme) {
        boolean bus = shaped.pins().stream().anyMatch(pin -> pin.width() > 1);
        PainterSupport.pinLead(graphics, bus, 0, MIDDLE, TRIANGLE_BACK, MIDDLE, scheme);
        PainterSupport.pinLead(graphics, bus, outputLeadStart, MIDDLE, OUTPUT_LEAD_END, MIDDLE, scheme);
    }

    /** The triangle up to {@code tip}, and the bubble after it when {@code negated}. */
    static void paintBody(Graphics2D graphics, double tip, boolean negated, ColorScheme scheme) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(TRIANGLE_BACK, TRIANGLE_TOP);
        triangle.lineTo(tip, MIDDLE);
        triangle.lineTo(TRIANGLE_BACK, TRIANGLE_BOTTOM);
        triangle.closePath();
        PainterSupport.body(graphics, triangle, scheme);
        if (negated) {
            PainterSupport.body(graphics, new Ellipse2D.Double(tip, MIDDLE - BUBBLE_DIAMETER / 2, BUBBLE_DIAMETER,
                    BUBBLE_DIAMETER), scheme);
        }
    }
}
