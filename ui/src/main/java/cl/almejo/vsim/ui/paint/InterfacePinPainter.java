package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.Map;

/** {@code input-pin} and {@code output-pin}: a tag pointing right, tinted with the value it carries. */
public final class InterfacePinPainter implements ComponentPainter {

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Path2D tag = new Path2D.Double();
        tag.moveTo(1, 2);
        tag.lineTo(10, 2);
        tag.lineTo(15, 8);
        tag.lineTo(10, 14);
        tag.lineTo(1, 14);
        tag.closePath();
        Value value = switch (state) {
            case VisibleState.SwitchState switchState -> switchState.value();
            case VisibleState.LedState led -> led.value();
            default -> null;
        };
        Color fill = value == null ? scheme.componentFill()
                : PainterSupport.withAlpha(SignalColors.valueColor(value, scheme), 110);
        PainterSupport.body(graphics, tag, scheme, fill);
        PainterSupport.pinDots(graphics, type, scheme);
    }
}
