package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.awt.Graphics2D;
import java.awt.FontMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * An instance of a user definition (spec §5.6): a box with the definition name along the top and each pin's name
 * inside the box next to the pin; texts shrink to fit, stay upright and never leave the box (or, for a pin
 * name, its pin's slot) in any rotation. The library-version badge is an overlay
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
        if (SubcircuitTypes.appearanceOf(type).isPresent()) {
            SymbolPainter.paint(graphics, type, SubcircuitTypes.appearanceOf(type).orElseThrow(), scheme,
                    SubcircuitTypes.definitionOf(type).map(ComponentDefinition::name).orElse(type.id()));
            return;
        }
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        Rectangle2D box = new Rectangle2D.Double(1, 1, width - 2, height - 2);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(1, 1, width - 2, height - 2, 4, 4), scheme);
        // Texts stay upright: turned a quarter, they run across the box (local y) instead of along it (local x).
        boolean sideways = PainterSupport.turnedSideways(graphics);
        String name = SubcircuitTypes.definitionOf(type).map(ComponentDefinition::name).orElse(type.id());
        // Sideways the name runs across the box, so it goes in the middle, between the two columns of pin names.
        double nameY = height >= 4 * UNIT && !sideways ? UNIT : height / 2;
        PainterSupport.centeredText(graphics, box, name, width / 2, nameY,
                fitted(graphics, name, NAME_SIZE, (sideways ? height : width) - 2 * INSET), scheme.text());
        for (PinSpec pin : type.pins()) {
            if (pin.name().isBlank()) {
                continue;
            }
            String text = pinText(pin);
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            boolean left = pinX <= 0;
            if (!left && pinX < width) {
                double centerY = pinY <= height / 2 ? pinY + INSET + 2 : pinY - INSET - 2;
                PainterSupport.centeredText(graphics, box, text, pinX, centerY,
                        fitted(graphics, text, PIN_NAME_SIZE, Math.max(UNIT, width - 2 * INSET)), scheme.text());
                continue;
            }
            // The pin's slot: its half of the box, between the midpoints to the neighbouring pins on its side.
            double gap = gapToNeighbours(type, pin, height);
            Rectangle2D slot = new Rectangle2D.Double(left ? 1 : width / 2, pinY - gap / 2, width / 2 - 1, gap);
            double across = sideways ? gap - 2 : width / 2 - INSET;
            float size = fitted(graphics, text, PIN_NAME_SIZE, across);
            double extent = sideways ? textHeight(graphics, size) : textWidth(graphics, text, size);
            double centerX = left ? INSET + extent / 2 : width - INSET - extent / 2;
            PainterSupport.centeredText(graphics, box.createIntersection(slot), text, centerX, pinY, size,
                    scheme.text());
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** The distance to the nearest other pin on the same side (the box height when there is none). */
    private static double gapToNeighbours(ComponentType type, PinSpec pin, double height) {
        boolean left = pin.position().x() <= 0;
        double gap = height;
        for (PinSpec other : type.pins()) {
            boolean otherLeft = other.position().x() <= 0;
            boolean otherRight = other.position().x() >= type.size().width();
            if (other == pin || (left ? !otherLeft : !otherRight)) {
                continue;
            }
            double distance = Math.abs(other.position().y() - pin.position().y()) * UNIT;
            if (distance > 0) {
                gap = Math.min(gap, distance);
            }
        }
        return gap;
    }

    private static float fitted(Graphics2D graphics, String text, float preferred, double available) {
        double textWidth = textWidth(graphics, text, preferred);
        return textWidth <= available ? preferred : (float) Math.max(MINIMUM_SIZE, preferred * available / textWidth);
    }

    private static double textHeight(Graphics2D graphics, float size) {
        FontMetrics metrics = graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size));
        return metrics.getAscent() - metrics.getDescent();
    }

    private static double textWidth(Graphics2D graphics, String text, float size) {
        return graphics.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(size)).stringWidth(text);
    }

    /** A pin's name as drawn on the box: wider pins carry their width, as in {@code dato[8]}. */
    static String pinText(PinSpec pin) {
        return pin.width() > 1 ? pin.name() + "[" + pin.width() + "]" : pin.name();
    }
}
