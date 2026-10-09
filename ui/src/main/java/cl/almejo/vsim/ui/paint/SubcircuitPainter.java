package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * An instance of a user definition (spec §5.6): a box with the definition name along the top and each pin's name
 * inside the box next to the pin; texts shrink to fit and stay upright. The library-version badge is an overlay
 * ({@code LibraryVersionBadge}), not part of the drawing.
 */
public final class SubcircuitPainter implements ComponentPainter {

    private static final float NAME_SIZE = 8f;
    private static final float PIN_NAME_SIZE = 6f;
    private static final float MINIMUM_SIZE = 4f;
    private static final double INSET = 4;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        PainterSupport.body(graphics, new RoundRectangle2D.Double(1, 1, width - 2, height - 2, 4, 4), scheme);
        String name = SubcircuitTypes.definitionOf(type).map(ComponentDefinition::name).orElse(type.id());
        double nameY = height >= 4 * UNIT ? UNIT : height / 2;
        PainterSupport.centeredText(graphics, name, width / 2, nameY,
                fitted(graphics, name, NAME_SIZE, width - 2 * INSET), scheme.text());
        for (PinSpec pin : type.pins()) {
            if (pin.name().isBlank()) {
                continue;
            }
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            float size = fitted(graphics, pin.name(), PIN_NAME_SIZE, width / 2 - INSET);
            double textWidth = textWidth(graphics, pin.name(), size);
            if (pinX <= 0) {
                PainterSupport.centeredText(graphics, pin.name(), INSET + textWidth / 2, pinY, size, scheme.text());
            } else if (pinX >= width) {
                PainterSupport.centeredText(graphics, pin.name(), width - INSET - textWidth / 2, pinY, size,
                        scheme.text());
            }
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    private static float fitted(Graphics2D graphics, String text, float preferred, double available) {
        double textWidth = textWidth(graphics, text, preferred);
        return textWidth <= available ? preferred : (float) Math.max(MINIMUM_SIZE, preferred * available / textWidth);
    }

    private static double textWidth(Graphics2D graphics, String text, float size) {
        return graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size)).stringWidth(text);
    }
}
