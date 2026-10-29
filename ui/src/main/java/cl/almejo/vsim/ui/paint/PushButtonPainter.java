package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/**
 * «Pulsador»: a grip one grid unit wide on the left (two columns of dots: the part is moved from there), then a
 * square frame with a round cap, and the output lead on the right, in the one-bit Switch's box. Released the cap
 * stands out with a shadow and reads «0»; pressed it sinks one pixel, fills with the colour of 1 and reads «1».
 */
public final class PushButtonPainter implements ComponentPainter {

    /** The grip's width and the cap area's side, in grid units: only the cap area presses the button. */
    public static final int GRIP_UNITS = 1;
    public static final int CAP_AREA_UNITS = 2;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    /** The body (grip and frame) fills the part's height (16 pixels); the lead runs from its right side to the pin. */
    private static final double FRAME_SIZE = 15;
    private static final double FRAME_INSET = 0.5;
    private static final double FRAME_CORNER = 5;
    private static final double BODY_WIDTH = (GRIP_UNITS + CAP_AREA_UNITS) * UNIT - 2 * FRAME_INSET;
    private static final double CENTRE = 8;
    private static final double CAP_CENTRE_X = (GRIP_UNITS + CAP_AREA_UNITS / 2.0) * UNIT;
    private static final double GRIP_DOT_RADIUS = 0.8;
    private static final double[] GRIP_DOT_COLUMNS = {2.75, 5.25};
    private static final double[] GRIP_DOT_ROWS = {3.5, 6.5, 9.5, 12.5};
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
        PainterSupport.lead(graphics, FRAME_INSET + BODY_WIDTH, CENTRE, PIN_X, CENTRE, scheme);
        PainterSupport.body(graphics, new RoundRectangle2D.Double(FRAME_INSET, FRAME_INSET, BODY_WIDTH, FRAME_SIZE,
                FRAME_CORNER, FRAME_CORNER), scheme);
        paintGrip(graphics, scheme);
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
        PainterSupport.centeredText(graphics, pressed ? "1" : "0", CAP_CENTRE_X, capCentreY, TEXT_SIZE, textColor);
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** The handle: a divider and two columns of small dots, in the scheme's stroke colour (read at paint time). */
    private static void paintGrip(Graphics2D graphics, ColorScheme scheme) {
        graphics.setColor(scheme.componentStroke());
        graphics.setStroke(new BasicStroke(1f));
        graphics.draw(new Line2D.Double(GRIP_UNITS * UNIT, FRAME_INSET, GRIP_UNITS * UNIT, FRAME_INSET + FRAME_SIZE));
        for (double columnX : GRIP_DOT_COLUMNS) {
            for (double rowY : GRIP_DOT_ROWS) {
                graphics.fill(new Ellipse2D.Double(columnX - GRIP_DOT_RADIUS, rowY - GRIP_DOT_RADIUS,
                        2 * GRIP_DOT_RADIUS, 2 * GRIP_DOT_RADIUS));
            }
        }
    }

    private static Ellipse2D cap(double centreY) {
        return new Ellipse2D.Double(CAP_CENTRE_X - CAP_RADIUS, centreY - CAP_RADIUS, 2 * CAP_RADIUS, 2 * CAP_RADIUS);
    }
}
