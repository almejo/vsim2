package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Where the parts of a wire tunnel lie (spec §3, sketch v5), in the component's local pixels before its rotation
 * (8 px per grid unit, the pin at the origin's left edge): the pin one grid unit outside the tag, the tag from there
 * as long as {@link Tunnels#lengthInGridUnits} says, the hole inside the tag on the wire's side, the stub from the pin
 * to the hole's centre, the arrow right next to the hole (pointing at it for an entrada, away from it for a salida) and
 * the box the name fills. The painter draws these, and the inline name editor sits on {@link #documentTextBox}.
 */
public final class TunnelShape {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    /** The radius of the hole, in local pixels. */
    public static final double HOLE_RADIUS = 4;
    /** The pin sits on the middle line of the tag, one grid unit down. */
    private static final double MIDDLE = UNIT;
    /** The tag leaves a pixel above and below, so its border stays inside the component's two grid units. */
    private static final double TAG_INSET = 1;
    private static final double TAG_CORNER = 8;
    /** The hole's centre: one grid unit into the tag. */
    private static final double HOLE_CENTRE_X = 2 * UNIT;
    /** The gap between the hole's edge and the arrow. */
    private static final double ARROW_GAP = 1.5;
    private static final double ARROW_LENGTH = 6;
    private static final double ARROW_HALF_HEIGHT = 3.5;
    /** The name's box starts after the arrow and stops this far before the end of the tag. */
    private static final double TEXT_GAP = 2;
    private static final double TEXT_END_MARGIN = UNIT / 2;

    private final TunnelKind kind;
    private final double tagEnd;

    private TunnelShape(TunnelKind kind, String name) {
        this.kind = kind;
        this.tagEnd = (1 + Tunnels.lengthInGridUnits(name)) * UNIT;
    }

    /** The shape of a tunnel of {@code kind} named {@code name}. */
    public static TunnelShape of(TunnelKind kind, String name) {
        return new TunnelShape(kind, name);
    }

    /** The shape of tunnel {@code component}; throws IllegalArgumentException for any other component. */
    public static TunnelShape of(Component component) {
        TunnelKind kind = Tunnels.kindOf(component)
                .orElseThrow(() -> new IllegalArgumentException(component.id() + " is not a tunnel"));
        return new TunnelShape(kind, Tunnels.nameOf(component));
    }

    /** From local pixels to document pixels for {@code component}: its position, then its rotation. */
    public static AffineTransform placement(Component component) {
        AffineTransform placement = AffineTransform.getTranslateInstance(component.position().x() * UNIT,
                component.position().y() * UNIT);
        // Rotation.apply matches Java2D's rotate(+90°) on a y-down screen, as ComponentDrawing does
        placement.rotate(component.rotation().quarterTurns() * Math.PI / 2);
        return placement;
    }

    /** True when the tag stands vertical (90°, 270°): the name is then drawn sideways, reading bottom to top. */
    public static boolean textSideways(Rotation rotation) {
        return rotation.quarterTurns() % 2 == 1;
    }

    public TunnelKind kind() {
        return kind;
    }

    public Point2D pin() {
        return new Point2D.Double(0, MIDDLE);
    }

    public RoundRectangle2D tag() {
        return new RoundRectangle2D.Double(UNIT, TAG_INSET, tagEnd - UNIT, 2 * MIDDLE - 2 * TAG_INSET, TAG_CORNER,
                TAG_CORNER);
    }

    public Point2D holeCentre() {
        return new Point2D.Double(HOLE_CENTRE_X, MIDDLE);
    }

    /** The wire from the pin into the centre of the hole. */
    public Line2D stub() {
        return new Line2D.Double(pin(), holeCentre());
    }

    /** The end of the arrow nearer the hole. */
    private double arrowNear() {
        return HOLE_CENTRE_X + HOLE_RADIUS + ARROW_GAP;
    }

    /** The point of the arrow: at the hole for an entrada, away from it for a salida. */
    public Point2D arrowTip() {
        double x = kind == TunnelKind.ENTRANCE ? arrowNear() : arrowNear() + ARROW_LENGTH;
        return new Point2D.Double(x, MIDDLE);
    }

    /** The middle of the arrow's flat back. */
    public Point2D arrowBack() {
        double x = kind == TunnelKind.ENTRANCE ? arrowNear() + ARROW_LENGTH : arrowNear();
        return new Point2D.Double(x, MIDDLE);
    }

    /** The arrow, a filled triangle from its back to its tip. */
    public Path2D arrow() {
        Point2D tip = arrowTip();
        Point2D back = arrowBack();
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(back.getX(), back.getY() - ARROW_HALF_HEIGHT);
        arrow.lineTo(tip.getX(), tip.getY());
        arrow.lineTo(back.getX(), back.getY() + ARROW_HALF_HEIGHT);
        arrow.closePath();
        return arrow;
    }

    /** Where the name goes: the rest of the tag after the arrow. */
    public Rectangle2D textBox() {
        double left = arrowNear() + ARROW_LENGTH + TEXT_GAP;
        return new Rectangle2D.Double(left, TAG_INSET, tagEnd - TEXT_END_MARGIN - left, 2 * MIDDLE - 2 * TAG_INSET);
    }

    /** {@link #textBox()} of {@code component} in document pixels. */
    public Rectangle2D documentTextBox(Component component) {
        return placement(component).createTransformedShape(textBox()).getBounds2D();
    }
}
