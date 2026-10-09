package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * {@code bus-splitter} and {@code bus-joiner}: a thick spine one unit in from the bus pin's side, the bus lead along
 * the top, and a thin numbered lead per bit (b0 at the top). The joiner is the same drawing with the bus on the right.
 */
public final class BusSplitterPainter implements ComponentPainter {

    private static final BasicStroke BUS_STROKE = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final float BIT_NUMBER_SIZE = 5f;
    private static final double BIT_NUMBER_RISE = 2.5;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        PinSpec bus = type.pin(0);
        double spineX = UNIT;
        double bottom = (type.pinCount() - 1) * UNIT;
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(BUS_STROKE);
        graphics.draw(new Line2D.Double(bus.position().x() * UNIT, 0, spineX, 0));
        graphics.draw(new Line2D.Double(spineX, 0, spineX, bottom));
        for (int index = 1; index < type.pinCount(); index++) {
            PinSpec bit = type.pin(index);
            double bitX = bit.position().x() * UNIT;
            double bitY = bit.position().y() * UNIT;
            PainterSupport.lead(graphics, spineX, bitY, bitX, bitY, scheme);
            PainterSupport.uprightText(graphics, String.valueOf(index - 1), (spineX + bitX) / 2, bitY - BIT_NUMBER_RISE,
                    BIT_NUMBER_SIZE, scheme.text(), PainterSupport.ALIGN_CENTER);
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
