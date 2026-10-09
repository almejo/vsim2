package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

public final class SwitchPainter implements ComponentPainter {

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Value value = state instanceof VisibleState.SwitchState switchState
                ? switchState.value()
                : fromParameter(parameters.get("value"));
        PainterSupport.lead(graphics, 27, 8, 32, 8, scheme);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(0, 1, 27, 14, 7, 7), scheme,
                SignalColors.valueColor(value, scheme));
        String text = value.equals(Value.ONE) ? "1" : value.equals(Value.ZERO) ? "0" : "Z";
        PainterSupport.centeredText(graphics, text, 13.5, 8, 10f, Color.WHITE);
        PainterSupport.pinDots(graphics, type, scheme);
    }

    private static Value fromParameter(Object parameter) {
        return switch (String.valueOf(parameter)) {
            case "1" -> Value.ONE;
            case "0" -> Value.ZERO;
            default -> Value.HIGH_IMPEDANCE;
        };
    }
}
