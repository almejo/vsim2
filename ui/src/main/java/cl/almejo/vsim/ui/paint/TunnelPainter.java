package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;

/**
 * «Túnel (entrada)» and «Túnel (salida)» (spec §3, sketch v5): a rounded tag with a border in the tunnel's colour,
 * the pin outside it, a wire stub from the pin into the centre of the hole, the arrow next to the hole (pointing at it
 * for an entrada, away for a salida) and the name, upright when the tag is horizontal and sideways when vertical. The
 * geometry is {@link TunnelShape}'s. On the canvas the colour of a salida is its entrada's and the stub is drawn like
 * its wire ({@link Look}); without a circuit (palette icon) the painter uses the tunnel's own colour parameter (palette
 * colour 0 for a salida) and a plain stub. A ghost being placed or pasted gets its colour from the tool that knows the
 * target circuit ({@code CircuitRenderer.paintGhost} with the tunnel colours).
 */
public final class TunnelPainter implements ComponentPainter {

    private static final BasicStroke TAG_BORDER = new BasicStroke(2.5f);
    private static final BasicStroke HOLE_RIM = new BasicStroke(1f);
    /** The stub has the canvas' wire widths (CircuitRenderer), with flat ends so it stops at the hole's centre. */
    private static final BasicStroke STUB = new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    private static final BasicStroke BUS_STUB = new BasicStroke(4.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    private static final double STUB_END_RADIUS = 1.6;
    private static final double BUS_STUB_END_RADIUS = 2.2;
    private static final float TEXT_SIZE = 10f;

    /**
     * How one tunnel is drawn: {@code color} its tag, arrow and name ({@link ColorScheme#tunnelColor}), {@code wire} the
     * stub (its net's state colour), {@code bus} true when its net is wider than one bit.
     */
    public record Look(Color color, Color wire, boolean bus) {
    }

    /** The font of the name at 100 %, for an editor that sits over it. */
    public static Font nameFont() {
        return PainterSupport.BASE_FONT.deriveFont(TEXT_SIZE);
    }

    /** The palette colour {@code tunnel} is drawn in: an entrada's own, a salida's entrada's (from {@code colors}). */
    public static int colorIndex(Component tunnel, Map<String, Integer> colorsByName) {
        if (Tunnels.kindOf(tunnel).orElse(null) == TunnelKind.ENTRANCE) {
            return Tunnels.colorParameterOf(tunnel);
        }
        return colorsByName.getOrDefault(Tunnels.nameOf(tunnel), Tunnels.NO_COLOR);
    }

    @Override
    public void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state,
                      ColorScheme scheme) {
        Map<String, Object> resolved = type.resolveParameters(parameters);
        TunnelKind kind = TunnelKind.of(type.id()).orElseThrow();
        Object color = resolved.get(Tunnels.COLOR);
        int colorIndex = color instanceof Number number ? number.intValue() : 0;
        paint(graphics, TunnelShape.of(kind, (String) resolved.get(Tunnels.NAME)), (String) resolved.get(Tunnels.NAME),
                new Look(scheme.tunnelColor(colorIndex), scheme.componentStroke(), false), scheme);
    }

    /** Paints {@code tunnel} in its local coordinates (ComponentDrawing's placement) with {@code look}. */
    public void paint(Graphics2D graphics, Component tunnel, Look look, ColorScheme scheme) {
        paint(graphics, TunnelShape.of(tunnel), Tunnels.nameOf(tunnel), look, scheme);
    }

    private static void paint(Graphics2D graphics, TunnelShape shape, String name, Look look, ColorScheme scheme) {
        graphics.setColor(scheme.tunnelSurface());
        graphics.fill(shape.tag());
        graphics.setStroke(TAG_BORDER);
        graphics.setColor(look.color());
        graphics.draw(shape.tag());
        paintHole(graphics, shape, scheme);
        paintStub(graphics, shape, look);
        paintPin(graphics, shape, look.bus(), scheme);
        graphics.setColor(look.color());
        graphics.fill(shape.arrow());
        Rectangle2D textBox = shape.textBox();
        PainterSupport.textAlong(graphics, name, textBox, TEXT_SIZE, look.color());
    }

    private static void paintHole(Graphics2D graphics, TunnelShape shape, ColorScheme scheme) {
        Ellipse2D hole = circle(shape.holeCentre(), TunnelShape.HOLE_RADIUS);
        graphics.setColor(scheme.tunnelHole());
        graphics.fill(hole);
        graphics.setStroke(HOLE_RIM);
        graphics.setColor(scheme.tunnelHoleRim());
        graphics.draw(hole);
    }

    /** Drawn over the hole so the wire visibly reaches its centre, where a dot ends it. */
    private static void paintStub(Graphics2D graphics, TunnelShape shape, Look look) {
        graphics.setStroke(look.bus() ? BUS_STUB : STUB);
        graphics.setColor(look.wire());
        graphics.draw(shape.stub());
        graphics.fill(circle(shape.holeCentre(), look.bus() ? BUS_STUB_END_RADIUS : STUB_END_RADIUS));
    }

    /** The pin's mark, like every pin's: a round dot on a 1-bit net, a square on a bus. */
    private static void paintPin(Graphics2D graphics, TunnelShape shape, boolean bus, ColorScheme scheme) {
        Point2D pin = shape.pin();
        graphics.setColor(scheme.pad());
        if (bus) {
            double side = 2 * PainterSupport.BUS_PIN_HALF_SIDE;
            graphics.fill(new Rectangle2D.Double(pin.getX() - PainterSupport.BUS_PIN_HALF_SIDE,
                    pin.getY() - PainterSupport.BUS_PIN_HALF_SIDE, side, side));
        } else {
            graphics.fill(circle(pin, PainterSupport.PIN_DOT_RADIUS));
        }
    }

    private static Ellipse2D circle(Point2D centre, double radius) {
        return new Ellipse2D.Double(centre.getX() - radius, centre.getY() - radius, 2 * radius, 2 * radius);
    }
}
