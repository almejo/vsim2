package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Map;

/** The Buffer: a triangle; with «Negar salida» the bubble before the output (the NOT). */
public final class BufferPainter implements ComponentPainter {

    private static final double BUBBLE_DIAMETER = 6;
    private static final double TRIANGLE_BACK = 6;
    private static final double OUTPUT_LEAD_END = 32;
    private static final double OUTPUT_LEAD_START = 30;
    private static final double MIDDLE = 16;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        boolean negated = Boolean.TRUE.equals(parameters.get(StandardComponentTypes.NEGATE));
        double tip = negated ? OUTPUT_LEAD_START - BUBBLE_DIAMETER : OUTPUT_LEAD_START;
        PainterSupport.lead(graphics, 0, MIDDLE, TRIANGLE_BACK, MIDDLE, scheme);
        PainterSupport.lead(graphics, OUTPUT_LEAD_START, MIDDLE, OUTPUT_LEAD_END, MIDDLE, scheme);
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(TRIANGLE_BACK, 4);
        triangle.lineTo(tip, MIDDLE);
        triangle.lineTo(TRIANGLE_BACK, 28);
        triangle.closePath();
        PainterSupport.body(graphics, triangle, scheme);
        if (negated) {
            PainterSupport.body(graphics, new Ellipse2D.Double(tip, MIDDLE - BUBBLE_DIAMETER / 2, BUBBLE_DIAMETER,
                    BUBBLE_DIAMETER), scheme);
        }
        PainterSupport.pinDots(graphics, type.specializedFor(parameters), scheme);
    }
}
