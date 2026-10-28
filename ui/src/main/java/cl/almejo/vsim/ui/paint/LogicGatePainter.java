package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Map;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

/**
 * IEEE distinctive-shape AND/OR/XOR gates; with «Negar salida» the bubble before the output (NAND/NOR/XNOR). The pins
 * are the ones the gate's «Entradas» and «Ancho (bits)» give.
 */
public final class LogicGatePainter implements ComponentPainter {

    public enum GateShape { AND, OR, XOR }

    private static final double BUBBLE_DIAMETER = 6;
    private static final double OUTPUT_LEAD = 2;
    private static final double BACK_CURVE_DEPTH = 6;
    private static final double XOR_GAP = 4;

    private final GateShape shape;

    public LogicGatePainter(GateShape shape) {
        this.shape = shape;
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType baseType, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        ComponentType type = baseType.specializedFor(parameters);
        boolean negated = Boolean.TRUE.equals(parameters.get(StandardComponentTypes.NEGATE));
        double width = type.size().width() * UNIT;
        double height = type.size().height() * UNIT;
        double top = 2;
        double bottom = height - 2;
        double middle = height / 2;
        double back = shape == GateShape.AND ? 6 : 4;
        double bodyBack = shape == GateShape.XOR ? back + XOR_GAP : back;
        double bodyFront = width - OUTPUT_LEAD - (negated ? BUBBLE_DIAMETER : 0);

        double highestInput = top;
        double lowestInput = bottom;
        for (PinSpec pin : type.pins()) {
            double pinX = pin.position().x() * UNIT;
            double pinY = pin.position().y() * UNIT;
            if (pin.isInput()) {
                PainterSupport.lead(graphics, pinX, pinY, backAt(back, pinY, top, bottom), pinY, scheme);
                highestInput = Math.min(highestInput, pinY);
                lowestInput = Math.max(lowestInput, pinY);
            } else {
                PainterSupport.lead(graphics, width - OUTPUT_LEAD, pinY, pinX, pinY, scheme);
            }
        }
        // Inputs beyond the body meet an extended back line (Logisim style) instead of bent leads.
        if (highestInput < top) {
            PainterSupport.lead(graphics, back, highestInput, back, top, scheme);
        }
        if (lowestInput > bottom) {
            PainterSupport.lead(graphics, back, bottom, back, lowestInput, scheme);
        }

        PainterSupport.body(graphics, body(bodyBack, bodyFront, top, bottom, middle), scheme);
        if (shape == GateShape.XOR) {
            Path2D backCurve = new Path2D.Double();
            backCurve.moveTo(back, top);
            backCurve.quadTo(back + BACK_CURVE_DEPTH, middle, back, bottom);
            PainterSupport.outline(graphics, backCurve, scheme);
        }
        if (negated) {
            PainterSupport.body(graphics, new Ellipse2D.Double(bodyFront, middle - BUBBLE_DIAMETER / 2,
                    BUBBLE_DIAMETER, BUBBLE_DIAMETER), scheme);
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** X where an input lead at {@code y} meets the back of the gate (the concave curve for OR/XOR). */
    private double backAt(double back, double y, double top, double bottom) {
        if (shape == GateShape.AND || y <= top || y >= bottom) {
            return back;
        }
        // The back curve is a quadratic with its control point at mid-height, so y is linear in t.
        double t = (y - top) / (bottom - top);
        return back + 2 * BACK_CURVE_DEPTH * t * (1 - t);
    }

    private Shape body(double back, double front, double top, double bottom, double middle) {
        Path2D path = new Path2D.Double();
        if (shape == GateShape.AND) {
            double radius = (bottom - top) / 2;
            path.moveTo(back, top);
            path.lineTo(front - radius, top);
            path.append(new Arc2D.Double(front - 2 * radius, top, 2 * radius, 2 * radius, 90, -180, Arc2D.OPEN), true);
            path.lineTo(back, bottom);
            path.closePath();
            return path;
        }
        double shoulder = back + (front - back) * 0.5;
        path.moveTo(back, top);
        path.curveTo(shoulder, top, front - 4, middle - 6, front, middle);
        path.curveTo(front - 4, middle + 6, shoulder, bottom, back, bottom);
        path.quadTo(back + BACK_CURVE_DEPTH, middle, back, top);
        path.closePath();
        return path;
    }
}
