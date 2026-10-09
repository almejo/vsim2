package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.Map;

public final class LedPainter implements ComponentPainter {

    /** How much of its color an unlit LED keeps; the rest is {@link #UNLIT_LENS}, like a dark lens. */
    private static final double UNLIT_SHARE = 0.35;
    private static final Color UNLIT_LENS = new Color(0x202020);

    /** The lit color of each {@code color} choice; an unknown name is red, the default. */
    public static Color colorFor(String colorName) {
        return switch (String.valueOf(colorName)) {
            case "green" -> new Color(0x43A047);
            case "yellow" -> new Color(0xFDD835);
            case "blue" -> new Color(0x1E88E5);
            case "orange" -> new Color(0xFB8C00);
            case "white" -> new Color(0xFFFFFF);
            case "purple" -> new Color(0x8E24AA);
            case "cyan" -> new Color(0x00E5FF);
            default -> new Color(0xE53935);
        };
    }

    /**
     * An unlit LED: its color mixed into a dark lens, opaque, so "off" reads the same on light and dark boards (a
     * translucent tint made a white LED vanish on the light board).
     */
    public static Color unlitColorFor(String colorName) {
        Color lit = colorFor(colorName);
        return new Color(mix(lit.getRed(), UNLIT_LENS.getRed()), mix(lit.getGreen(), UNLIT_LENS.getGreen()),
                mix(lit.getBlue(), UNLIT_LENS.getBlue()));
    }

    private static int mix(int litChannel, int lensChannel) {
        return (int) Math.round(litChannel * UNLIT_SHARE + lensChannel * (1 - UNLIT_SHARE));
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        String colorName = String.valueOf(parameters.get("color"));
        Color color = colorFor(colorName);
        boolean lit = PainterSupport.valueOf(state).equals(Value.ONE);
        PainterSupport.lead(graphics, 8, 16, 8, 24, scheme);
        if (lit) {
            graphics.setColor(PainterSupport.withAlpha(color, 70));
            graphics.fill(new Ellipse2D.Double(-3, -3, 22, 22));
        }
        PainterSupport.body(graphics, new Ellipse2D.Double(0.5, 0.5, 15, 15), scheme,
                lit ? color : unlitColorFor(colorName));
        graphics.setColor(new Color(255, 255, 255, lit ? 170 : 110));
        graphics.fill(new Ellipse2D.Double(4, 3, 5, 4));
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
