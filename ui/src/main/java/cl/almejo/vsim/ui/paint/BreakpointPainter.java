package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.BreakpointTypes;
import cl.almejo.vsim.core.component.standard.ConstantValue;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * The «Punto de parada»: a stop-sign octagon with «=» or «≠» in it, a lead from the input pin and the value to the
 * right. Active it is filled with the scheme's conflict colour (the red of «something is wrong here»); inactive it is
 * the plain body colour. Colours are read at paint time.
 */
public final class BreakpointPainter implements ComponentPainter {

    private static final double LEAD_END = 12;
    private static final double OCTAGON_SIDE = 15;
    private static final double CORNER = 4.5;
    private static final float SYMBOL_SIZE = 11f;
    private static final float VALUE_SIZE = 9f;
    private static final double VALUE_LEFT = LEAD_END + OCTAGON_SIDE + 3;
    private static final double RIGHT_MARGIN = 2;
    private static final int ACTIVE_FILL_ALPHA = 170;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        ComponentType shaped = type.specializedFor(parameters);
        boolean active = (Boolean) resolved.get(BreakpointTypes.ACTIVE);
        boolean equalsCondition = BreakpointTypes.EQUALS.equals(resolved.get(BreakpointTypes.CONDITION));
        double middle = shaped.pin(0).position().y() * UNIT;
        PainterSupport.pinLead(graphics, shaped.pin(0), 0, middle, LEAD_END, middle, scheme);
        Shape octagon = octagon(middle);
        Color fill = active ? PainterSupport.withAlpha(scheme.wireConflict(), ACTIVE_FILL_ALPHA)
                : scheme.componentFill();
        PainterSupport.body(graphics, octagon, scheme, fill);
        PainterSupport.centeredText(graphics, equalsCondition ? "=" : "≠", LEAD_END + OCTAGON_SIDE / 2, middle,
                SYMBOL_SIZE, scheme.text());
        double available = shaped.size().width() * UNIT - VALUE_LEFT - RIGHT_MARGIN;
        PainterSupport.uprightText(graphics, ellipsized(shownValue(resolved), available), VALUE_LEFT, middle,
                VALUE_SIZE, active ? scheme.text() : scheme.wireHighImpedance(), PainterSupport.ALIGN_LEFT);
        PainterSupport.pinDots(graphics, shaped, scheme);
    }

    /** The value in the part's text, shown like a Constante shows it (binary up to 4 bits, hex above). */
    static String shownValue(Map<String, Object> resolved) {
        int width = (Integer) resolved.get("width");
        return ConstantValue.display(ConstantValue.bitsOf((String) resolved.get("value"), width).orElseThrow(),
                width, ConstantValue.AUTO);
    }

    private static Shape octagon(double middle) {
        double left = LEAD_END;
        double top = middle - OCTAGON_SIDE / 2;
        double right = left + OCTAGON_SIDE;
        double bottom = top + OCTAGON_SIDE;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(left + CORNER, top);
        path.lineTo(right - CORNER, top);
        path.lineTo(right, top + CORNER);
        path.lineTo(right, bottom - CORNER);
        path.lineTo(right - CORNER, bottom);
        path.lineTo(left + CORNER, bottom);
        path.lineTo(left, bottom - CORNER);
        path.lineTo(left, top + CORNER);
        path.closePath();
        return path;
    }

    private static String ellipsized(String text, double available) {
        Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            FontMetrics metrics = scratch.getFontMetrics(PainterSupport.BASE_FONT.deriveFont(VALUE_SIZE));
            return PainterSupport.ellipsized(text, metrics, available);
        } finally {
            scratch.dispose();
        }
    }
}
