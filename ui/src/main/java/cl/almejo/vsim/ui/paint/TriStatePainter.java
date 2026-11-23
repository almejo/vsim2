package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.awt.Graphics2D;
import java.util.Map;

/**
 * The tri-state buffer: a triangle with the enable lead at the bottom; with «Negar salida» the bubble before the
 * output (the controlled inverter); the input and output leads are thick for a bus.
 */
public final class TriStatePainter implements ComponentPainter {

    private static final double MIDDLE = BufferTriangle.MIDDLE;
    private static final double TRIANGLE_BACK = BufferTriangle.TRIANGLE_BACK;
    private static final double TRIANGLE_BOTTOM = BufferTriangle.TRIANGLE_BOTTOM;
    /** The tri-state's own: its triangle ends two pixels before the Buffer's (30). */
    private static final double OUTPUT_LEAD_START = 28;
    private static final double ENABLE_LEAD_START = 32;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        boolean negated = Boolean.TRUE.equals(parameters.get(StandardComponentTypes.NEGATE));
        double tip = BufferTriangle.tip(negated, OUTPUT_LEAD_START);
        ComponentType shaped = type.specializedFor(parameters);
        boolean bus = shaped.pins().stream().anyMatch(pin -> pin.width() > 1);
        BufferTriangle.paintLeads(graphics, bus, OUTPUT_LEAD_START, scheme);
        // under the triangle, which covers its end
        PainterSupport.lead(graphics, MIDDLE, ENABLE_LEAD_START, MIDDLE, lowerEdgeAtMiddle(tip), scheme);
        BufferTriangle.paintBody(graphics, tip, negated, scheme);
        PainterSupport.pinDots(graphics, shaped, scheme);
    }

    /** Where the enable lead meets the triangle's lower edge, whatever the tip's position. */
    private static double lowerEdgeAtMiddle(double tip) {
        double fraction = (MIDDLE - TRIANGLE_BACK) / (tip - TRIANGLE_BACK);
        return TRIANGLE_BOTTOM - fraction * (TRIANGLE_BOTTOM - MIDDLE);
    }
}
