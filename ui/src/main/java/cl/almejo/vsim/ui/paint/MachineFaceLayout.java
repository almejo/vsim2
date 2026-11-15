package cl.almejo.vsim.ui.paint;

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
import java.util.Objects;
import java.util.Set;

/**
 * Where a state machine's face draws its miniature (mockup D, spec §1): with 1 to 4 states, its states in fixed places
 * (not the ones inside, so moving a state in the diagram never changes the piece) and its real arrows without their
 * conditions; from {@value #MINIATURE_LIMIT} + 1 states nothing, because the face shows the big name instead. Pure
 * geometry in the face's own units (pixels at 100 %), tested without painting.
 * <p>
 * The places: 1 state in the centre, 2 left and right, 3 a triangle (the first on top, then clockwise), 4 a square
 * clockwise from the top-left, in {@link StateMachine#states()} order. An arrow is straight; curved when more than one
 * arrow joins the same two states (each way bends to its own side); a small loop pointing away from the centre when it
 * leaves and enters the same state.
 */
final class MachineFaceLayout {

    /** Up to this many states the face draws the miniature; with more, the big name (decided with the user). */
    static final int MINIATURE_LIMIT = 4;

    /** The states' radius, as a share of the area's shorter side: four states and their loops fit, as in mockup D. */
    private static final double RADIUS_SHARE = 0.13;
    /** How far a state's centre sits from a side of the area, in radii: room for a self loop pointing at that side. */
    private static final double SIDE_INSET = 2.3;
    /** The same for the square's corners, whose loops point diagonally and so reach less along each side. */
    private static final double CORNER_INSET = 1.9;
    /** How far from the centre a self loop's control points go, in radii. */
    private static final double LOOP_REACH = 2.6;
    /** Half the angle between the two ends of a self loop on its state's border. */
    private static final double LOOP_SPREAD = Math.toRadians(32);
    /** How far a curved arrow's control point leaves the straight line, as a share of the distance between centres. */
    private static final double BEND_SHARE = 0.22;

    enum Mode { MINIATURE, BIG_NAME }

    enum ArrowKind { STRAIGHT, CURVED, SELF_LOOP }

    /** A state's circle on the face. */
    record FaceState(String stateId, Point2D centre, double radius) {

        FaceState {
            Objects.requireNonNull(stateId, "stateId");
            Objects.requireNonNull(centre, "centre");
        }

        Ellipse2D circle() {
            return new Ellipse2D.Double(centre.getX() - radius, centre.getY() - radius, 2 * radius, 2 * radius);
        }
    }

    /**
     * A transition's line on the face: {@code path} from its source's border to {@code tip} on its target's border;
     * its head points from {@code headFrom} to {@code tip}.
     */
    record FaceArrow(String transitionId, ArrowKind kind, Shape path, Point2D headFrom, Point2D tip) {

        FaceArrow {
            Objects.requireNonNull(transitionId, "transitionId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(headFrom, "headFrom");
            Objects.requireNonNull(tip, "tip");
        }
    }

    /** What the face draws: the miniature's states and arrows, or none with the big name. */
    record FaceLayout(List<FaceState> states, List<FaceArrow> arrows, Mode mode) {

        FaceLayout {
            states = List.copyOf(states);
            arrows = List.copyOf(arrows);
            Objects.requireNonNull(mode, "mode");
        }
    }

    private MachineFaceLayout() {
    }

    /** The face of {@code machine} with its miniature inside {@code area}. */
    static FaceLayout of(StateMachine machine, Rectangle2D area) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(area, "area");
        if (machine.states().size() > MINIATURE_LIMIT) {
            return new FaceLayout(List.of(), List.of(), Mode.BIG_NAME);
        }
        double radius = Math.min(area.getWidth(), area.getHeight()) * RADIUS_SHARE;
        List<Point2D> places = places(machine.states().size(), area, radius);
        Map<String, FaceState> states = new LinkedHashMap<>();
        for (int index = 0; index < places.size(); index++) {
            MachineState state = machine.states().get(index);
            states.put(state.id(), new FaceState(state.id(), places.get(index), radius));
        }
        Point2D centre = new Point2D.Double(area.getCenterX(), area.getCenterY());
        return new FaceLayout(List.copyOf(states.values()), arrows(machine, states, centre), Mode.MINIATURE);
    }

    /** The fixed places of {@code count} (0 to 4) states, with room for their self loops inside {@code area}. */
    private static List<Point2D> places(int count, Rectangle2D area, double radius) {
        double inset = (count == MINIATURE_LIMIT ? CORNER_INSET : SIDE_INSET) * radius;
        double left = area.getMinX() + Math.max(area.getWidth() / 4, inset);
        double right = area.getMaxX() - Math.max(area.getWidth() / 4, inset);
        double top = area.getMinY() + Math.max(area.getHeight() / 4, inset);
        double bottom = area.getMaxY() - Math.max(area.getHeight() / 4, inset);
        double centreX = area.getCenterX();
        double centreY = area.getCenterY();
        return switch (count) {
            case 0 -> List.of();
            case 1 -> List.of(point(centreX, centreY));
            case 2 -> List.of(point(left, centreY), point(right, centreY));
            case 3 -> List.of(point(centreX, top), point(right, bottom), point(left, bottom));
            case 4 -> List.of(point(left, top), point(right, top), point(right, bottom), point(left, bottom));
            default -> throw new IllegalArgumentException("no fixed places for " + count + " states");
        };
    }

    private static List<FaceArrow> arrows(StateMachine machine, Map<String, FaceState> states, Point2D centre) {
        Map<Set<String>, Integer> arrowsPerPair = new HashMap<>();
        for (MachineTransition transition : machine.transitions()) {
            if (transition.isSelfLoop()) {
                continue;
            }
            arrowsPerPair.merge(Set.of(transition.fromId(), transition.toId()), 1, Integer::sum);
        }
        Map<List<String>, Integer> drawnPerWay = new HashMap<>();
        List<FaceArrow> arrows = new ArrayList<>();
        for (MachineTransition transition : machine.transitions()) {
            FaceState from = states.get(transition.fromId());
            FaceState to = states.get(transition.toId());
            if (transition.isSelfLoop()) {
                arrows.add(selfLoop(transition.id(), from, centre));
                continue;
            }
            int sameWay = drawnPerWay.merge(List.of(transition.fromId(), transition.toId()), 1, Integer::sum);
            boolean shared = arrowsPerPair.get(Set.of(transition.fromId(), transition.toId())) > 1;
            arrows.add(shared ? curved(transition.id(), from, to, sameWay) : straight(transition.id(), from, to));
        }
        return arrows;
    }

    private static FaceArrow straight(String transitionId, FaceState from, FaceState to) {
        Point2D start = towards(from, to.centre());
        Point2D tip = towards(to, from.centre());
        return new FaceArrow(transitionId, ArrowKind.STRAIGHT, new Line2D.Double(start, tip), start, tip);
    }

    /**
     * An arrow bent to the left of its way, {@code bends} times {@link #BEND_SHARE}: the two ways of a pair bend to
     * opposite sides, and a second arrow the same way bends further.
     */
    private static FaceArrow curved(String transitionId, FaceState from, FaceState to, int bends) {
        double deltaX = to.centre().getX() - from.centre().getX();
        double deltaY = to.centre().getY() - from.centre().getY();
        double distance = Math.hypot(deltaX, deltaY);
        double offset = distance * BEND_SHARE * bends;
        Point2D control = point((from.centre().getX() + to.centre().getX()) / 2 + deltaY / distance * offset,
                (from.centre().getY() + to.centre().getY()) / 2 - deltaX / distance * offset);
        Point2D start = towards(from, control);
        Point2D tip = towards(to, control);
        return new FaceArrow(transitionId, ArrowKind.CURVED, new QuadCurve2D.Double(start.getX(), start.getY(),
                control.getX(), control.getY(), tip.getX(), tip.getY()), control, tip);
    }

    /** A small loop on {@code state}'s side away from {@code centre} (above it when it is the centre). */
    private static FaceArrow selfLoop(String transitionId, FaceState state, Point2D centre) {
        double outwardX = state.centre().getX() - centre.getX();
        double outwardY = state.centre().getY() - centre.getY();
        double angle = outwardX == 0 && outwardY == 0 ? -Math.PI / 2 : Math.atan2(outwardY, outwardX);
        Point2D start = onCircle(state, angle - LOOP_SPREAD, state.radius());
        Point2D firstControl = onCircle(state, angle - LOOP_SPREAD, LOOP_REACH * state.radius());
        Point2D secondControl = onCircle(state, angle + LOOP_SPREAD, LOOP_REACH * state.radius());
        Point2D tip = onCircle(state, angle + LOOP_SPREAD, state.radius());
        return new FaceArrow(transitionId, ArrowKind.SELF_LOOP, new CubicCurve2D.Double(start.getX(), start.getY(),
                firstControl.getX(), firstControl.getY(), secondControl.getX(), secondControl.getY(), tip.getX(),
                tip.getY()), secondControl, tip);
    }

    /** The point of {@code state}'s border in the direction of {@code target}. */
    private static Point2D towards(FaceState state, Point2D target) {
        double angle = Math.atan2(target.getY() - state.centre().getY(), target.getX() - state.centre().getX());
        return onCircle(state, angle, state.radius());
    }

    private static Point2D onCircle(FaceState state, double angle, double distance) {
        return point(state.centre().getX() + Math.cos(angle) * distance,
                state.centre().getY() + Math.sin(angle) * distance);
    }

    private static Point2D point(double x, double y) {
        return new Point2D.Double(x, y);
    }
}
