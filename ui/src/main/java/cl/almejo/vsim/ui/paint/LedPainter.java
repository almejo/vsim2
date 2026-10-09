package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.Map;

public final class LedPainter implements ComponentPainter {

    public static Color colorFor(String colorName) {
        return switch (String.valueOf(colorName)) {
            case "green" -> new Color(0x43A047);
            case "yellow" -> new Color(0xFDD835);
            case "blue" -> new Color(0x1E88E5);
            default -> new Color(0xE53935);
        };
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Color color = colorFor(String.valueOf(parameters.get("color")));
        boolean lit = PainterSupport.valueOf(state).equals(Value.ONE);
        PainterSupport.lead(graphics, 8, 16, 8, 24, scheme);
        if (lit) {
            graphics.setColor(PainterSupport.withAlpha(color, 70));
            graphics.fill(new Ellipse2D.Double(-3, -3, 22, 22));
        }
        PainterSupport.body(graphics, new Ellipse2D.Double(0.5, 0.5, 15, 15), scheme,
                lit ? color : PainterSupport.withAlpha(color, 55));
        graphics.setColor(new Color(255, 255, 255, lit ? 170 : 110));
        graphics.fill(new Ellipse2D.Double(4, 3, 5, 4));
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
