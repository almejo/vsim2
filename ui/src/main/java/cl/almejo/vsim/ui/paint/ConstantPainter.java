package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/** A plain painting for the constant: a box with its value in the chosen format and a lead to the pin. */
public final class ConstantPainter implements ComponentPainter {

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        int width = (Integer) resolved.get("width");
        long value = ConstantValue.parse((String) resolved.get("value"));
        double pixelWidth = type.size().width() * 8.0;
        PainterSupport.lead(graphics, pixelWidth - 5, 8, pixelWidth, 8, scheme);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(0, 1, pixelWidth - 5, 14, 7, 7), scheme,
                SignalColors.valueColor(Value.of(width, value), scheme));
        PainterSupport.centeredText(graphics, ConstantValue.display(value, width, (String) resolved.get("format")),
                (pixelWidth - 5) / 2, 8, 10f, Color.WHITE);
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
