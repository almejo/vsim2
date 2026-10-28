package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.Map;

public final class LedPainter implements ComponentPainter {

    /** How much of its color an unlit LED keeps; the rest is {@link #UNLIT_LENS}, like a dark lens. */
    private static final double UNLIT_SHARE = 0.35;
    private static final Color UNLIT_LENS = new Color(0x202020);

    private static final Color RED = new Color(0xE53935);
    /** The lit color of each colour name; also the colour vocabulary of every COLOR parameter's swatches. */
    private static final Map<String, Color> LIT_COLORS = Map.of("red", RED, "green", new Color(0x43A047),
            "yellow", new Color(0xFDD835), "blue", new Color(0x1E88E5), "orange", new Color(0xFB8C00),
            "white", new Color(0xFFFFFF), "purple", new Color(0x8E24AA), "cyan", new Color(0x00E5FF));

    /** The lit color of each {@code color} choice; an unknown name is red, the default. */
    public static Color colorFor(String colorName) {
        return LIT_COLORS.getOrDefault(String.valueOf(colorName), RED);
    }

    /** True when {@link #colorFor} has a colour of its own for {@code colorName} (it is not the red fallback). */
    public static boolean knowsColor(String colorName) {
        return colorName != null && LIT_COLORS.containsKey(colorName);
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

    /** The radius of the light of one bit of a multi-bit LED, and how far its glow reaches past it. */
    private static final double BIT_LIGHT_RADIUS = 3.5;
    private static final double BIT_GLOW_REACH = 2;
    private static final double BIT_GLINT_SHIFT_X = 0.3;
    private static final double BIT_GLINT_SHIFT_Y = 0.35;
    private static final double BIT_GLINT_SHARE = 0.32;
    /** Thin, so eight lights in a row do not turn into outlines. */
    private static final BasicStroke BIT_LIGHT_STROKE = new BasicStroke(0.6f);

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        if (MultiBitLayout.isMultiBit(type)) {
            paintLights(graphics, type, parameters, state, scheme);
            return;
        }
        String colorName = String.valueOf(parameters.get("color"));
        Color color = colorFor(colorName);
        boolean lit = PainterSupport.valueOf(state).equals(Value.ONE);
        PainterSupport.lead(graphics, 8, 16, 8, 24, scheme);
        if (lit) {
            graphics.setColor(PainterSupport.withAlpha(color, 70));
            graphics.fill(new Ellipse2D.Double(-3, -3, 22, 22));
        }
        Color lens;
        if (MonochromeScheme.isMonochrome(scheme)) {
            // grey alone barely tells a lit LED from an unlit one on paper: filled black against an empty outline
            lens = lit ? MonochromeScheme.BLACK : Color.WHITE;
        } else {
            lens = lit ? color : unlitColorFor(colorName);
        }
        PainterSupport.body(graphics, new Ellipse2D.Double(0.5, 0.5, 15, 15), scheme, lens);
        graphics.setColor(new Color(255, 255, 255, lit ? 170 : 110));
        graphics.fill(new Ellipse2D.Double(4, 3, 5, 4));
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** The row of lights of a multi-bit LED: one per bit, lit only where the bit is 1 (Z and a conflict stay unlit). */
    private static void paintLights(Graphics2D graphics, ComponentType type, Map<String, Object> parameters,
                                    VisibleState state, ColorScheme scheme) {
        String colorName = String.valueOf(parameters.get("color"));
        Value value = state instanceof VisibleState.LedState ledState ? ledState.value() : Value.highImpedance(1);
        MultiBitBody.paint(graphics, type, parameters, scheme, (cellGraphics, cell) -> {
            boolean lit = isLit(value, cell.bit());
            paintLight(cellGraphics, colorName, lit, scheme, cell.x() + cell.width() / 2,
                    cell.y() + cell.height() / 2);
        });
    }

    private static boolean isLit(Value value, int bit) {
        return !value.conflict() && bit < value.width() && value.bit(bit) == BitState.ONE;
    }

    private static void paintLight(Graphics2D graphics, String colorName, boolean lit, ColorScheme scheme,
                                   double centerX, double centerY) {
        Color color = colorFor(colorName);
        if (lit) {
            graphics.setColor(PainterSupport.withAlpha(color, 70));
            double reach = BIT_LIGHT_RADIUS + BIT_GLOW_REACH;
            graphics.fill(new Ellipse2D.Double(centerX - reach, centerY - reach, 2 * reach, 2 * reach));
        }
        Color lens;
        if (MonochromeScheme.isMonochrome(scheme)) {
            lens = lit ? MonochromeScheme.BLACK : Color.WHITE;
        } else {
            lens = lit ? color : unlitColorFor(colorName);
        }
        Ellipse2D lensShape = new Ellipse2D.Double(centerX - BIT_LIGHT_RADIUS, centerY - BIT_LIGHT_RADIUS,
                2 * BIT_LIGHT_RADIUS, 2 * BIT_LIGHT_RADIUS);
        graphics.setColor(lens);
        graphics.fill(lensShape);
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(BIT_LIGHT_STROKE);
        graphics.draw(lensShape);
        graphics.setColor(new Color(255, 255, 255, lit ? 170 : 110));
        double glint = BIT_LIGHT_RADIUS * BIT_GLINT_SHARE;
        graphics.fill(new Ellipse2D.Double(centerX - BIT_LIGHT_RADIUS * BIT_GLINT_SHIFT_X - glint,
                centerY - BIT_LIGHT_RADIUS * BIT_GLINT_SHIFT_Y - glint, 2 * glint, 2 * glint));
    }
}
