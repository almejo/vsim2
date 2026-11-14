package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;

import java.awt.Shape;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
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
 * transition from border to border (straight; curved only when two or more join the same two states, bending to each
 * side of the line; a loop over the state for a self loop) with the place of its label, and the initial mark. The
 * painter draws it and the gestures hit-test it, so both always agree. Pure: built from the model each time.
 */
final class MachineDiagramGeometry {

    /** A state's radius (the mockup's circles). */
    static final double STATE_RADIUS = 40;
    /** How far the first curved arrow between two states bends from the straight line; the next ones bend more. */
    private static final double BEND = 22;
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

    private final Map<String, Point2D> centres = new LinkedHashMap<>();
    private final Map<String, Arrow> arrows = new LinkedHashMap<>();
    private final Optional<Line2D> initialMark;

    private MachineDiagramGeometry(StateMachine machine) {
        for (MachineState state : machine.states()) {
            centres.put(state.id(), new Point2D.Double(state.position().x(), state.position().y()));
        }
        placeArrows(machine);
        initialMark = machine.initialStateId().map(centres::get).map(centre -> new Line2D.Double(
                centre.getX() - STATE_RADIUS - INITIAL_MARK_LENGTH, centre.getY(),
                centre.getX() - STATE_RADIUS, centre.getY()));
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

    /** The circle of the state {@code stateId}. */
    Shape circle(String stateId) {
        Point2D centre = centre(stateId);
        return new Ellipse2D.Double(centre.getX() - STATE_RADIUS, centre.getY() - STATE_RADIUS, 2 * STATE_RADIUS,
                2 * STATE_RADIUS);
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
            if (entry.getValue().distance(point) <= STATE_RADIUS) {
                found = entry.getKey();
            }
        }
        return Optional.ofNullable(found);
    }

    /** What the diagram covers (states, arrows, the initial mark); empty for a machine without states. */
    Optional<Rectangle2D> bounds() {
        Rectangle2D union = null;
        List<Shape> shapes = new ArrayList<>();
        centres.keySet().forEach(stateId -> shapes.add(circle(stateId)));
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
                arrows.put(transition.id(), straight(transition));
            } else {
                int order = placedInDirection.merge(transition.fromId() + ">" + transition.toId(), 1, Integer::sum);
                arrows.put(transition.id(), curved(transition, BEND * order));
            }
        }
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
        Point2D start = towards(from, to, STATE_RADIUS);
        Point2D end = towards(to, from, STATE_RADIUS);
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
        Point2D start = towards(from, control, STATE_RADIUS);
        Point2D end = towards(to, control, STATE_RADIUS);
        Point2D peak = offset(middle(from, to), normal, bend);
        Point2D label = offset(peak, normal, LABEL_GAP);
        return new Arrow(transition.id(), ArrowKind.CURVED, new QuadCurve2D.Double(start.getX(), start.getY(),
                control.getX(), control.getY(), end.getX(), end.getY()), start, end, control, label);
    }

    /** A loop leaving and entering the state's border around its {@code turn}-th axis (the first one straight up). */
    private Arrow selfLoop(MachineTransition transition, int turn) {
        Point2D centre = centre(transition.fromId());
        double axis = -Math.PI / 2 + turn * LOOP_TURN;
        Point2D start = onCircle(centre, axis - LOOP_HALF_ANGLE, STATE_RADIUS);
        Point2D end = onCircle(centre, axis + LOOP_HALF_ANGLE, STATE_RADIUS);
        Point2D top = onCircle(centre, axis, STATE_RADIUS + LOOP_HEIGHT);
        Point2D across = new Point2D.Double(-Math.sin(axis), Math.cos(axis));
        Point2D firstControl = offset(top, across, -LOOP_CONTROL_SPREAD);
        Point2D secondControl = offset(top, across, LOOP_CONTROL_SPREAD);
        Point2D label = onCircle(centre, axis, STATE_RADIUS + 0.75 * LOOP_HEIGHT + LABEL_GAP);
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
