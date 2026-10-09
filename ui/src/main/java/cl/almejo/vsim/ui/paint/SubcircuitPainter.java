package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/** An instance of a user definition: a box with the definition name, shrunk to fit. M4-A refines it. */
public final class SubcircuitPainter implements ComponentPainter {

    private static final Font MEASURE = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    private static final float NAME_SIZE = 9f;
    private static final float MINIMUM_NAME_SIZE = 4f;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        PainterSupport.body(graphics, new RoundRectangle2D.Double(1, 1, width - 2, height - 2, 4, 4), scheme);
        String name = SubcircuitTypes.definitionOf(type).map(ComponentDefinition::name).orElse(type.id());
        float size = NAME_SIZE;
        double textWidth = graphics.getFontMetrics(MEASURE.deriveFont(size)).stringWidth(name);
        double available = width - 6;
        if (textWidth > available) {
            size = (float) Math.max(MINIMUM_NAME_SIZE, size * available / textWidth);
        }
        PainterSupport.centeredText(graphics, name, width / 2, height / 2, size, scheme.text());
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
