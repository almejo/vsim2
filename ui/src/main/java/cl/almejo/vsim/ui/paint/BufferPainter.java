package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.Graphics2D;
import java.util.Map;

/** The Buffer: a triangle; with «Negar salida» the bubble before the output (the NOT). */
public final class BufferPainter implements ComponentPainter {

    private static final double OUTPUT_LEAD_START = 30;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        boolean negated = BufferTriangle.negated(parameters);
        double tip = BufferTriangle.tip(negated, OUTPUT_LEAD_START);
        ComponentType shaped = type.specializedFor(parameters);
        BufferTriangle.paintLeads(graphics, shaped, OUTPUT_LEAD_START, scheme);
        BufferTriangle.paintBody(graphics, tip, negated, scheme);
        PainterSupport.pinDots(graphics, shaped, scheme);
    }
}
