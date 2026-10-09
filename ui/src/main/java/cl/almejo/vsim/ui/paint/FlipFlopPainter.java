package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

public final class FlipFlopPainter implements ComponentPainter {

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        PainterSupport.lead(graphics, 0, 16, 4, 16, scheme);
        PainterSupport.lead(graphics, 28, 16, 32, 16, scheme);
        PainterSupport.lead(graphics, 16, 32, 16, 30, scheme);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(4, 2, 24, 28, 4, 4), scheme);
        Path2D clockMark = new Path2D.Double();
        clockMark.moveTo(12, 30);
        clockMark.lineTo(16, 25);
        clockMark.lineTo(20, 30);
        PainterSupport.outline(graphics, clockMark, scheme);
        PainterSupport.centeredText(graphics, "D", 9.5, 16, 8f, scheme.text());
        PainterSupport.centeredText(graphics, "Q", 22.5, 16, 8f, scheme.text());
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
