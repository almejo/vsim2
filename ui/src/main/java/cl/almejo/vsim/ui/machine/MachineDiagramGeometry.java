package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a machine's diagram draws each thing, in diagram units (a pixel at 100 %): a circle per state, an arrow per
 * transition from border to border (straight; curved when two or more join the same two states, bending to each
 * side of the line, or when a straight one would pass behind a third state, bending away from it; a loop over the
 * state for a self loop) with the place of its label, and the initial mark. A state is {@link #STATE_RADIUS} wide
 * unless its name needs more room (it grows up to {@link #MAXIMUM_RADIUS}, then the name shrinks), and its Moore
 * outputs go under its name inside the circle, or fully below it when they do not fit (hand test of hito 2:
 * «AMARILLO» left its circle). The painter draws it and the gestures hit-test it, so both always agree. Pure: built
 * from the model each time (texts are measured with a fixed font context).
 */
final class MachineDiagramGeometry {

    /** A state's radius (the mockup's circles): the smallest a state is drawn. */
    static final double STATE_RADIUS = 40;
    /** The largest a state grows for its name; a longer name is drawn smaller instead. */
    static final double MAXIMUM_RADIUS = 2.25 * STATE_RADIUS;
    /** Up to this radius a state also grows to keep its outputs inside; beyond it they go below the circle. */
    private static final double OUTPUTS_MAXIMUM_RADIUS = 1.5 * STATE_RADIUS;
    /** The air between a state's name and its border, on each side. */
    private static final double NAME_PADDING = 10;
    /** The air between the outputs line's corners and the border. */
    private static final double OUTPUTS_PADDING = 3;
    /** How far below the centre the middle of a state's outputs line goes, inside the circle. */
    private static final double OUTPUTS_BELOW = 20;
    /** The gap between the circle and an outputs line drawn below it. */
    private static final double OUTPUTS_GAP = 4;
    /** The smallest a long name is drawn. */
    private static final float MINIMUM_NAME_SIZE = 9f;
    /** Texts are measured as the canvas draws them: antialiased, without fractional metrics. */
    private static final FontRenderContext MEASURE = new FontRenderContext(null, true, false);
    /** How far the first curved arrow between two states bends from the straight line; the next ones bend more. */
    private static final double BEND = 22;
    /** How much room a straight arrow keeps from a third state's circle before it is bent around it. */
    private static final double CLEARANCE = 8;
    /** How closely a curve is followed when measuring a distance to it. */
    private static final double FLATNESS = 0.5;
    /** How far a label sits from its arrow. */
    private static final double LABEL_GAP = 14;
    /** How high a self loop rises over its state's border, and how wide it opens (radians from its axis). */
    private static final double LOOP_HEIGHT = 2.3 * STATE_RADIUS;
    private static final double LOOP_HALF_ANGLE = Math.toRadians(28);
    private static final double LOOP_CONTROL_SPREAD = 0.9 * STATE_RADIUS;
    /** Successive self loops of one state turn this much around it (the first one is on top). */
    private static final double LOOP_TURN = Math.PI / 2;
    /** The initial mark: an arrow this long coming from the left into the initial state. */
    private static final double INITIAL_MARK_LENGTH = 52;

    /** How an arrow is drawn. */
    enum ArrowKind { STRAIGHT, CURVED, SELF_LOOP }

    /**
     * One arrow: its {@code path} from {@code start} (on the source's border) to {@code end} (on the target's border),
     * the point its head points from ({@code headFrom}, the curve's last control point), and where its label goes.
     */
    record Arrow(String transitionId, ArrowKind kind, Shape path, Point2D start, Point2D end, Point2D headFrom,
                 Point2D labelAnchor) {
    }

    /** How a state is drawn: its radius, the size of its name and where its outputs line goes (if it has one). */
    private record StateLook(double radius, float nameSize, Optional<Rectangle2D> outputs) {
    }

    private final Map<String, Point2D> centres = new LinkedHashMap<>();
    private final Map<String, StateLook> looks = new HashMap<>();
    private final Map<String, Arrow> arrows = new LinkedHashMap<>();
    private final Optional<Line2D> initialMark;

    private MachineDiagramGeometry(StateMachine machine) {
        for (MachineState state : machine.states()) {
            Point2D centre = new Point2D.Double(state.position().x(), state.position().y());
            centres.put(state.id(), centre);
            looks.put(state.id(), lookOf(state, centre));
        }
        placeArrows(machine);
        initialMark = machine.initialStateId().map(initialId -> {
            Point2D centre = centres.get(initialId);
            double radius = radius(initialId);
            return new Line2D.Double(centre.getX() - radius - INITIAL_MARK_LENGTH, centre.getY(),
                    centre.getX() - radius, centre.getY());
        });
    }

    /** How {@code state} at {@code centre} is drawn: the radius its name and its outputs need. */
    private static StateLook lookOf(MachineState state, Point2D centre) {
        Font nameFont = MachineDiagramPainter.nameFont();
        double nameWidth = width(state.name(), nameFont);
        double nameRadius = nameWidth / 2 + NAME_PADDING;
        double radius = Math.min(MAXIMUM_RADIUS, Math.max(STATE_RADIUS, nameRadius));
        float nameSize = nameRadius <= MAXIMUM_RADIUS ? nameFont.getSize2D()
                : (float) Math.max(MINIMUM_NAME_SIZE,
                nameFont.getSize2D() * 2 * (MAXIMUM_RADIUS - NAME_PADDING) / nameWidth);
        if (state.outputs().isEmpty()) {
            return new StateLook(radius, nameSize, Optional.empty());
        }
        String text = MachineDiagramPainter.mooreLabelOf(state.outputs());
        Font outputsFont = MachineDiagramPainter.outputsFont();
        double outputsWidth = width(text, outputsFont);
        LineMetrics line = outputsFont.getLineMetrics(text, MEASURE);
        double outputsHeight = line.getAscent() + line.getDescent();
        double insideRadius = Math.hypot(outputsWidth / 2, OUTPUTS_BELOW + outputsHeight / 2) + OUTPUTS_PADDING;
        if (insideRadius <= Math.max(radius, OUTPUTS_MAXIMUM_RADIUS)) {
            return new StateLook(Math.max(radius, insideRadius), nameSize, Optional.of(new Rectangle2D.Double(
                    centre.getX() - outputsWidth / 2, centre.getY() + OUTPUTS_BELOW - outputsHeight / 2,
                    outputsWidth, outputsHeight)));
        }
        return new StateLook(radius, nameSize, Optional.of(new Rectangle2D.Double(
                centre.getX() - outputsWidth / 2, centre.getY() + radius + OUTPUTS_GAP, outputsWidth,
                outputsHeight)));
    }

    /** The radius {@code state} is drawn with, wherever it is: its name and its outputs may make it bigger. */
    static double radiusOf(MachineState state) {
        return lookOf(state, new Point2D.Double()).radius();
    }

    private static double width(String text, Font font) {
        return font.getStringBounds(text, MEASURE).getWidth();
    }

    static MachineDiagramGeometry of(StateMachine machine) {
        return new MachineDiagramGeometry(machine);
    }

    /** The centre of the state {@code stateId}. */
    Point2D centre(String stateId) {
        Point2D centre = centres.get(stateId);
        if (centre == null) {
            throw new IllegalArgumentException("no state " + stateId);
        }
        return centre;
    }

    /** The radius of the state {@code stateId}: {@link #STATE_RADIUS}, or more for a long name or outputs. */
    double radius(String stateId) {
        return look(stateId).radius();
    }

    /** The font of the state {@code stateId}'s name: smaller than usual only for a very long name. */
    Font nameFont(String stateId) {
        return MachineDiagramPainter.nameFont().deriveFont(look(stateId).nameSize());
    }

    /** Where the state {@code stateId}'s Moore outputs line goes; empty when it has none. */
    Optional<Rectangle2D> outputsBounds(String stateId) {
        return look(stateId).outputs().map(box -> (Rectangle2D) box.clone());
    }

    private StateLook look(String stateId) {
        StateLook look = looks.get(stateId);
        if (look == null) {
            throw new IllegalArgumentException("no state " + stateId);
        }
        return look;
    }

    /** The circle of the state {@code stateId}. */
    Shape circle(String stateId) {
        Point2D centre = centre(stateId);
        double radius = radius(stateId);
        return new Ellipse2D.Double(centre.getX() - radius, centre.getY() - radius, 2 * radius, 2 * radius);
    }

    /** The arrow of the transition {@code transitionId}. */
    Arrow arrow(String transitionId) {
        Arrow arrow = arrows.get(transitionId);
        if (arrow == null) {
            throw new IllegalArgumentException("no transition " + transitionId);
        }
        return arrow;
    }

    /** Every arrow, in model order. */
    List<Arrow> arrows() {
        return List.copyOf(arrows.values());
    }

    /** The arrow coming into the initial state from the left; empty when the machine has no initial state. */
    Optional<Line2D> initialMark() {
        return initialMark;
    }

    /** The state under {@code point}: the one drawn last (on top) when circles overlap. */
    Optional<String> stateAt(Point2D point) {
        String found = null;
        for (Map.Entry<String, Point2D> entry : centres.entrySet()) {
            if (entry.getValue().distance(point) <= radius(entry.getKey())) {
                found = entry.getKey();
            }
        }
        return Optional.ofNullable(found);
    }

    /** The part of a state a point is on: its middle (a drag moves it) or its border (a drag starts an arrow). */
    enum StatePart { CENTRE, BORDER }

    /** A point on the state {@code stateId}, on its {@code part}. */
    record StateHit(String stateId, StatePart part) {
    }

    /**
     * The state under {@code point} and which part of it: within {@code band} of its circle is its border (inside or
     * just outside it), nearer the middle its centre. The state drawn last wins when they overlap.
     */
    Optional<StateHit> stateHitAt(Point2D point, double band) {
        StateHit found = null;
        for (Map.Entry<String, Point2D> entry : centres.entrySet()) {
            double distance = entry.getValue().distance(point);
            double radius = radius(entry.getKey());
            if (Math.abs(distance - radius) <= band) {
                found = new StateHit(entry.getKey(), StatePart.BORDER);
            } else if (distance < radius) {
                found = new StateHit(entry.getKey(), StatePart.CENTRE);
            }
        }
        return Optional.ofNullable(found);
    }

    /** The arrow passing within {@code tolerance} of {@code point}; the nearest one when several do. */
    Optional<String> transitionAt(Point2D point, double tolerance) {
        String found = null;
        double nearest = Double.MAX_VALUE;
        for (Arrow arrow : arrows.values()) {
            double distance = distanceTo(arrow.path(), point);
            if (distance <= tolerance && distance <= nearest) {
                nearest = distance;
                found = arrow.transitionId();
            }
        }
        return Optional.ofNullable(found);
    }

    /** How far {@code point} is from the line of {@code path}. */
    private static double distanceTo(Shape path, Point2D point) {
        double nearest = Double.MAX_VALUE;
        double[] coordinates = new double[6];
        double lastX = 0;
        double lastY = 0;
        for (PathIterator segments = path.getPathIterator(null, FLATNESS); !segments.isDone(); segments.next()) {
            int type = segments.currentSegment(coordinates);
            if (type == PathIterator.SEG_LINETO) {
                nearest = Math.min(nearest, Line2D.ptSegDist(lastX, lastY, coordinates[0], coordinates[1],
                        point.getX(), point.getY()));
            }
            lastX = coordinates[0];
            lastY = coordinates[1];
        }
        return nearest;
    }

    /** What the diagram covers (states, arrows, the initial mark); empty for a machine without states. */
    Optional<Rectangle2D> bounds() {
        Rectangle2D union = null;
        List<Shape> shapes = new ArrayList<>();
        centres.keySet().forEach(stateId -> {
            shapes.add(circle(stateId));
            outputsBounds(stateId).ifPresent(shapes::add);
        });
        arrows.values().forEach(arrow -> shapes.add(arrow.path()));
        initialMark.ifPresent(shapes::add);
        for (Shape shape : shapes) {
            Rectangle2D shapeBounds = shape.getBounds2D();
            union = union == null ? shapeBounds : union.createUnion(shapeBounds);
        }
        return Optional.ofNullable(union);
    }

    private void placeArrows(StateMachine machine) {
        Map<String, Integer> joiningPair = new HashMap<>();
        for (MachineTransition transition : machine.transitions()) {
            if (!transition.isSelfLoop()) {
                joiningPair.merge(pairKey(transition), 1, Integer::sum);
            }
        }
        Map<String, Integer> placedInDirection = new HashMap<>();
        Map<String, Integer> loopsOfState = new HashMap<>();
        for (MachineTransition transition : machine.transitions()) {
            if (transition.isSelfLoop()) {
                int turn = loopsOfState.merge(transition.fromId(), 1, Integer::sum) - 1;
                arrows.put(transition.id(), selfLoop(transition, turn));
            } else if (joiningPair.get(pairKey(transition)) == 1) {
                arrows.put(transition.id(), aroundThirdState(transition)
                        .map(bend -> curved(transition, bend)).orElseGet(() -> straight(transition)));
            } else {
                int order = placedInDirection.merge(transition.fromId() + ">" + transition.toId(), 1, Integer::sum);
                arrows.put(transition.id(), curved(transition, BEND * order));
            }
        }
    }

    /**
     * How far a lone arrow must bend (positive: to the left of its direction) to clear a third state its straight
     * line would pass within its radius and {@code CLEARANCE} of, away from that state; empty when nothing is in its
     * way. With several in the way, the nearest to the line decides.
     */
    private Optional<Double> aroundThirdState(MachineTransition transition) {
        Point2D from = centre(transition.fromId());
        Point2D to = centre(transition.toId());
        Point2D normal = leftNormal(from, to);
        double clearing = 0;
        double nearest = Double.MAX_VALUE;
        double side = 0;
        for (Map.Entry<String, Point2D> entry : centres.entrySet()) {
            if (entry.getKey().equals(transition.fromId()) || entry.getKey().equals(transition.toId())) {
                continue;
            }
            Point2D third = entry.getValue();
            double reach = radius(entry.getKey()) + CLEARANCE;
            double distance = Line2D.ptSegDist(from.getX(), from.getY(), to.getX(), to.getY(), third.getX(),
                    third.getY());
            if (distance < reach && distance < nearest) {
                nearest = distance;
                clearing = reach - distance;
                double leftOfLine = (third.getX() - from.getX()) * normal.getX()
                        + (third.getY() - from.getY()) * normal.getY();
                side = leftOfLine > 0 ? -1 : 1; // bend to the other side than the state
            }
        }
        if (side == 0) {
            return Optional.empty();
        }
        return Optional.of(side * Math.max(BEND, clearing + BEND));
    }

    /** The two states an arrow joins, in either direction. */
    private static String pairKey(MachineTransition transition) {
        String from = transition.fromId();
        String to = transition.toId();
        return from.compareTo(to) < 0 ? from + "|" + to : to + "|" + from;
    }

    private Arrow straight(MachineTransition transition) {
        Point2D from = centre(transition.fromId());
        Point2D to = centre(transition.toId());
        Point2D start = towards(from, to, radius(transition.fromId()));
        Point2D end = towards(to, from, radius(transition.toId()));
        Point2D middle = middle(start, end);
        Point2D normal = leftNormal(from, to);
        Point2D label = offset(middle, normal, LABEL_GAP);
        return new Arrow(transition.id(), ArrowKind.STRAIGHT, new Line2D.Double(start, end), start, end, start, label);
    }

    /** An arrow bending {@code bend} to the left of its direction: the opposite arrow bends to the other side. */
    private Arrow curved(MachineTransition transition, double bend) {
        Point2D from = centre(transition.fromId());
        Point2D to = centre(transition.toId());
        Point2D normal = leftNormal(from, to);
        // a quadratic curve peaks halfway to its control point
        Point2D control = offset(middle(from, to), normal, 2 * bend);
        Point2D start = towards(from, control, radius(transition.fromId()));
        Point2D end = towards(to, control, radius(transition.toId()));
        Point2D peak = offset(middle(from, to), normal, bend);
        Point2D label = offset(peak, normal, Math.copySign(LABEL_GAP, bend));
        return new Arrow(transition.id(), ArrowKind.CURVED, new QuadCurve2D.Double(start.getX(), start.getY(),
                control.getX(), control.getY(), end.getX(), end.getY()), start, end, control, label);
    }

    /** A loop leaving and entering the state's border around its {@code turn}-th axis (the first one straight up). */
    private Arrow selfLoop(MachineTransition transition, int turn) {
        Point2D centre = centre(transition.fromId());
        double axis = -Math.PI / 2 + turn * LOOP_TURN;
        double radius = radius(transition.fromId());
        Point2D start = onCircle(centre, axis - LOOP_HALF_ANGLE, radius);
        Point2D end = onCircle(centre, axis + LOOP_HALF_ANGLE, radius);
        Point2D top = onCircle(centre, axis, radius + LOOP_HEIGHT);
        Point2D across = new Point2D.Double(-Math.sin(axis), Math.cos(axis));
        Point2D firstControl = offset(top, across, -LOOP_CONTROL_SPREAD);
        Point2D secondControl = offset(top, across, LOOP_CONTROL_SPREAD);
        Point2D label = onCircle(centre, axis, radius + 0.75 * LOOP_HEIGHT + LABEL_GAP);
        return new Arrow(transition.id(), ArrowKind.SELF_LOOP, new CubicCurve2D.Double(start.getX(), start.getY(),
                firstControl.getX(), firstControl.getY(), secondControl.getX(), secondControl.getY(), end.getX(),
                end.getY()), start, end, secondControl, label);
    }

    /** The point {@code distance} from {@code from} on the way to {@code to}. */
    private static Point2D towards(Point2D from, Point2D to, double distance) {
        double length = from.distance(to);
        if (length == 0) {
            return new Point2D.Double(from.getX() + distance, from.getY());
        }
        return new Point2D.Double(from.getX() + (to.getX() - from.getX()) * distance / length,
                from.getY() + (to.getY() - from.getY()) * distance / length);
    }

    /** The unit vector to the left of the way from {@code from} to {@code to} (on screen, y grows downwards). */
    private static Point2D leftNormal(Point2D from, Point2D to) {
        double length = from.distance(to);
        if (length == 0) {
            return new Point2D.Double(0, -1);
        }
        return new Point2D.Double((to.getY() - from.getY()) / length, -(to.getX() - from.getX()) / length);
    }

    private static Point2D middle(Point2D first, Point2D second) {
        return new Point2D.Double((first.getX() + second.getX()) / 2, (first.getY() + second.getY()) / 2);
    }

    private static Point2D offset(Point2D point, Point2D direction, double distance) {
        return new Point2D.Double(point.getX() + direction.getX() * distance,
                point.getY() + direction.getY() * distance);
    }

    private static Point2D onCircle(Point2D centre, double angle, double radius) {
        return new Point2D.Double(centre.getX() + Math.cos(angle) * radius, centre.getY() + Math.sin(angle) * radius);
    }
}
