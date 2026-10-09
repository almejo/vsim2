package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.TimeDiagramSample;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/** Functional drawing of the last samples of each channel; the final design arrives in M4. */
public final class TimeDiagramPainter implements ComponentPainter {

    private static final Color SCREEN = new Color(0x22272E);
    private static final BasicStroke TRACE = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final double LEFT = 6;
    private static final double RIGHT = 30;
    private static final double SWING = 3;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        for (PinSpec pin : type.pins()) {
            double pinY = pin.position().y() * UNIT;
            PainterSupport.lead(graphics, 0, pinY, 4, pinY, scheme);
        }
        PainterSupport.body(graphics, new RoundRectangle2D.Double(4, -4, 28, type.size().height() * UNIT + 8, 4, 4),
                scheme, SCREEN);
        if (state instanceof VisibleState.TimeDiagramState diagram && !diagram.samples().isEmpty()) {
            List<TimeDiagramSample> samples = diagram.samples();
            int maximumVisible = (int) (RIGHT - LEFT);
            List<TimeDiagramSample> visible = samples.subList(Math.max(0, samples.size() - maximumVisible), samples.size());
            double step = visible.size() > 1 ? (RIGHT - LEFT) / (visible.size() - 1) : 0;
            graphics.setStroke(TRACE);
            graphics.setColor(scheme.wireOne());
            for (int channel = 0; channel < type.pinCount(); channel++) {
                double center = type.pin(channel).position().y() * UNIT;
                Path2D trace = new Path2D.Double();
                for (int index = 0; index < visible.size(); index++) {
                    List<Value> values = visible.get(index).values();
                    double level = channel < values.size() ? levelOf(values.get(channel).bit(0), center) : center;
                    double x = LEFT + index * step;
                    if (index == 0) {
                        trace.moveTo(x, level);
                    } else {
                        trace.lineTo(x, trace.getCurrentPoint().getY());
                        trace.lineTo(x, level);
                    }
                }
                graphics.draw(trace);
            }
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    private static double levelOf(BitState bit, double center) {
        return switch (bit) {
            case ONE -> center - SWING;
            case ZERO -> center + SWING;
            case HIGH_IMPEDANCE -> center;
        };
    }
}
