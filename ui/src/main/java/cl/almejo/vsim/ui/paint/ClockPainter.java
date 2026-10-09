package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

public final class ClockPainter implements ComponentPainter {

    private static final BasicStroke WAVE = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        PainterSupport.lead(graphics, 13, 13, 16, 16, scheme);
        boolean high = PainterSupport.valueOf(state).equals(Value.ONE);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(0, 0, 13, 13, 4, 4), scheme,
                high ? PainterSupport.withAlpha(scheme.wireOne(), 120) : null);
        Path2D wave = new Path2D.Double();
        wave.moveTo(2.5, 9.5);
        wave.lineTo(4.5, 9.5);
        wave.lineTo(4.5, 3.5);
        wave.lineTo(8.5, 3.5);
        wave.lineTo(8.5, 9.5);
        wave.lineTo(10.5, 9.5);
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(WAVE);
        graphics.draw(wave);
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
