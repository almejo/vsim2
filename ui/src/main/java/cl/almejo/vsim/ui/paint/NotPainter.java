package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Map;

public final class NotPainter implements ComponentPainter {

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        PainterSupport.lead(graphics, 0, 16, 6, 16, scheme);
        PainterSupport.lead(graphics, 30, 16, 32, 16, scheme);
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(6, 4);
        triangle.lineTo(24, 16);
        triangle.lineTo(6, 28);
        triangle.closePath();
        PainterSupport.body(graphics, triangle, scheme);
        PainterSupport.body(graphics, new Ellipse2D.Double(24, 13, 6, 6), scheme);
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
