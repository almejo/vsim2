package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/**
 * «Pulsador»: a round cap in a square frame with its output lead on the right, in the one-bit Switch's box. Released
 * it stands out with a shadow and reads «0»; pressed it sinks one pixel, fills with the colour of 1 and reads «1».
 */
public final class PushButtonPainter implements ComponentPainter {

    /** The frame fills the part's height (16 pixels); the lead runs from its right side to the pin. */
    private static final double FRAME_SIZE = 15;
    private static final double FRAME_INSET = 0.5;
    private static final double FRAME_CORNER = 5;
    private static final double CENTRE = 8;
    private static final double CAP_RADIUS = 5.5;
    /** How far a pressed cap sinks, and how far below a released one its shadow falls. */
    private static final double TRAVEL = 1;
    private static final double PIN_X = 32;
    private static final float TEXT_SIZE = 9f;

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        boolean pressed = state instanceof VisibleState.SwitchState switchState
                && switchState.value().equals(Value.ONE);
        PainterSupport.lead(graphics, FRAME_INSET + FRAME_SIZE, CENTRE, PIN_X, CENTRE, scheme);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(FRAME_INSET, FRAME_INSET, FRAME_SIZE, FRAME_SIZE,
                FRAME_CORNER, FRAME_CORNER), scheme);
        double capCentreY = pressed ? CENTRE + TRAVEL : CENTRE;
        if (!pressed) {
            graphics.setColor(scheme.shadow());
            graphics.fill(cap(CENTRE + TRAVEL));
        }
        Color fill = pressed ? SignalColors.valueColor(Value.ONE, scheme) : scheme.componentFill();
        graphics.setColor(fill);
        graphics.fill(cap(capCentreY));
        PainterSupport.outline(graphics, cap(capCentreY), scheme);
        Color textColor = pressed ? SwitchPainter.textColor(fill) : scheme.text();
        PainterSupport.centeredText(graphics, pressed ? "1" : "0", CENTRE, capCentreY, TEXT_SIZE, textColor);
        PainterSupport.pinDots(graphics, type, scheme);
    }

    private static Ellipse2D cap(double centreY) {
        return new Ellipse2D.Double(CENTRE - CAP_RADIUS, centreY - CAP_RADIUS, 2 * CAP_RADIUS, 2 * CAP_RADIUS);
    }
}
