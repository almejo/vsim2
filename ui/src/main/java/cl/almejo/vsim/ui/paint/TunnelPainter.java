package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * «Túnel (entrada)» and «Túnel (salida)», first cut: the tag with the tunnel's name and a lead to the pin, one grid
 * unit outside the tag. The full look (hole, arrow, colours, upright text) comes with the tunnel drawing work.
 */
public final class TunnelPainter implements ComponentPainter {

    private static final float TEXT_SIZE = 9f;
    private static final double CORNER = 6;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        String name = (String) type.resolveParameters(parameters).get(Tunnels.NAME);
        ComponentType shaped = type.specializedFor(parameters);
        double width = shaped.size().width() * UNIT;
        double height = shaped.size().height() * UNIT;
        double middle = shaped.pin(0).position().y() * UNIT;
        PainterSupport.lead(graphics, 0, middle, UNIT, middle, scheme);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(UNIT, 0, width - UNIT, height, CORNER, CORNER),
                scheme);
        PainterSupport.centeredText(graphics, name, (UNIT + width) / 2, middle, TEXT_SIZE, scheme.text());
        PainterSupport.pinDots(graphics, shaped, scheme);
    }
}
