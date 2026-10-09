package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.Map;

public final class TriStatePainter implements ComponentPainter {

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        PainterSupport.lead(graphics, 0, 16, 6, 16, scheme);
        PainterSupport.lead(graphics, 28, 16, 32, 16, scheme);
        PainterSupport.lead(graphics, 16, 32, 16, 22, scheme);
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(6, 4);
        triangle.lineTo(28, 16);
        triangle.lineTo(6, 28);
        triangle.closePath();
        PainterSupport.body(graphics, triangle, scheme);
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
