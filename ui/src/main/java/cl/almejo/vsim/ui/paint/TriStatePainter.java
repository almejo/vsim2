package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Map;

/**
 * The tri-state buffer: a triangle with the enable lead at the bottom; with «Negar salida» the bubble before the
 * output (the controlled inverter); the input and output leads are thick for a bus.
 */
public final class TriStatePainter implements ComponentPainter {

    private static final double BUBBLE_DIAMETER = 6;
    private static final double TRIANGLE_BACK = 6;
    private static final double TRIANGLE_TOP = 4;
    private static final double TRIANGLE_BOTTOM = 28;
    private static final double OUTPUT_LEAD_START = 28;
    private static final double OUTPUT_LEAD_END = 32;
    private static final double MIDDLE = 16;
    private static final double ENABLE_LEAD_START = 32;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        boolean negated = Boolean.TRUE.equals(parameters.get(StandardComponentTypes.NEGATE));
        double tip = negated ? OUTPUT_LEAD_START - BUBBLE_DIAMETER : OUTPUT_LEAD_START;
        ComponentType shaped = type.specializedFor(parameters);
        boolean bus = shaped.pins().stream().anyMatch(pin -> pin.width() > 1);
        PainterSupport.pinLead(graphics, bus, 0, MIDDLE, TRIANGLE_BACK, MIDDLE, scheme);
        PainterSupport.pinLead(graphics, bus, OUTPUT_LEAD_START, MIDDLE, OUTPUT_LEAD_END, MIDDLE, scheme);
        PainterSupport.lead(graphics, MIDDLE, ENABLE_LEAD_START, MIDDLE, lowerEdgeAtMiddle(tip), scheme);
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
        PainterSupport.pinDots(graphics, shaped, scheme);
    }

    /** Where the enable lead meets the triangle's lower edge, whatever the tip's position. */
    private static double lowerEdgeAtMiddle(double tip) {
        double fraction = (MIDDLE - TRIANGLE_BACK) / (tip - TRIANGLE_BACK);
        return TRIANGLE_BOTTOM - fraction * (TRIANGLE_BOTTOM - MIDDLE);
    }
}
