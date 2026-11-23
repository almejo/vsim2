package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.awt.Graphics2D;
import java.util.Map;

/** The Buffer: a triangle; with «Negar salida» the bubble before the output (the NOT). */
public final class BufferPainter implements ComponentPainter {

    private static final double OUTPUT_LEAD_START = 30;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        boolean negated = Boolean.TRUE.equals(parameters.get(StandardComponentTypes.NEGATE));
        double tip = BufferTriangle.tip(negated, OUTPUT_LEAD_START);
        ComponentType shaped = type.specializedFor(parameters);
        boolean bus = shaped.pins().stream().anyMatch(pin -> pin.width() > 1);
        BufferTriangle.paintLeads(graphics, bus, OUTPUT_LEAD_START, scheme);
        BufferTriangle.paintBody(graphics, tip, negated, scheme);
        PainterSupport.pinDots(graphics, shaped, scheme);
    }
}
