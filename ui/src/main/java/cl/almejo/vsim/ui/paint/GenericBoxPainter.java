package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/** Fallback for types without a dedicated painter: a labelled box. */
public final class GenericBoxPainter implements ComponentPainter {

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        PainterSupport.body(graphics, new Rectangle2D.Double(0, 0, width, height), scheme);
        PainterSupport.centeredText(graphics, type.id(), width / 2, height / 2, 7f, scheme.text());
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
