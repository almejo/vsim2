package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.snap.PointResolution;
import cl.almejo.vsim.ui.tools.WireRouting.Segment;

import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A wire drawn with the Wire tool in its continuous mode: a start, the corners fixed by clicks (each step from one to
 * the next is an «L») and the segment being drawn from the last corner to the pointer. The whole path is added as one
 * undo step, with consecutive collinear segments merged.
 *
 * <p>Ajustar (alignment-guides spec §4.4), as {@link WireDrawing}: the start is resolved once, each corner when it is
 * fixed and the pointer's end while it moves; {@link #refreshSnap} redoes the pointer's end from the same raw point.
 *
 * <p>The elbow of the segment being drawn goes horizontal first when the pointer is at least as far sideways as up or
 * down from the last corner; {@link #flipElbow} puts it on the other side until the next corner is fixed.
 */
final class ContinuousWirePath {

    /**
     * The radius, in document pixels, of the dots the preview shows where the path will join the circuit: a little
     * larger than a drawn junction (3.5) so it stands out over the wire it lands on.
     */
    private static final double JUNCTION_PREVIEW_RADIUS = 4.5;

    /** One fixed step of the path: the «L» to {@code end}, its elbow on the side {@code horizontalFirst} says. */
    private record Step(GridPoint end, boolean horizontalFirst) {
    }

    private final GridPoint start;
    private final List<Guide> startGuides;
    private final int startWidth;
    private final List<Step> steps = new ArrayList<>();
    private GridPoint current;
    private GridPoint rawCurrent;
    private List<Guide> currentGuides = List.of();
    /** The side of the elbow Espacio chose for the segment being drawn; null while it follows the pointer. */
    private Boolean elbowChoice;

    /** {@code startWidth} is the width of the pin the path starts from (1 from anything else): above 1 it previews as a bus. */
    ContinuousWirePath(PointResolution start, int startWidth) {
        this.start = start.point();
        this.startGuides = start.guides();
        this.startWidth = startWidth;
        this.current = this.start;
        this.rawCurrent = this.start;
    }

    GridPoint start() {
        return start;
    }

    /** The start and every corner fixed since, in order. */
    List<GridPoint> corners() {
        List<GridPoint> corners = new ArrayList<>();
        corners.add(start);
        steps.forEach(step -> corners.add(step.end()));
        return List.copyOf(corners);
    }

    private GridPoint lastCorner() {
        return steps.isEmpty() ? start : steps.get(steps.size() - 1).end();
    }

    /** The segment being drawn now ends where the raw grid point {@code raw} resolves. */
    void previewTo(ToolContext context, GridPoint raw) {
        resolveCurrent(context, raw);
    }

    private void resolveCurrent(ToolContext context, GridPoint raw) {
        PointResolution resolution = context.snapper().snapPoint(context, raw);
        rawCurrent = raw;
        current = resolution.point();
        currentGuides = resolution.guides();
    }

    /** Puts the elbow of the segment being drawn on the other side (a tap of Espacio). */
    void flipElbow() {
        elbowChoice = !horizontalFirstTo(current);
    }

    private boolean horizontalFirstTo(GridPoint end) {
        if (elbowChoice != null) {
            return elbowChoice;
        }
        GridPoint from = lastCorner();
        return Math.abs(end.x() - from.x()) >= Math.abs(end.y() - from.y());
    }

    /**
     * Fixes a corner where the raw grid point {@code raw} resolves (nothing when it is the last corner); true when the
     * path ends there because the point already is a connection of the circuit (a pin, a wire end or a wire's
     * interior). The path's own corners and segments are not in the document yet, so a click on them goes on.
     */
    boolean addCorner(ToolContext context, GridPoint raw) {
        resolveCurrent(context, raw);
        if (current.equals(lastCorner())) {
            return false;
        }
        steps.add(new Step(current, horizontalFirstTo(current)));
        elbowChoice = null;
        return connections(context).isConnection(context.openDocument().document(), current);
    }

    /** Drops the last fixed corner; false (nothing changed) when only the start is left. */
    boolean dropLastCorner() {
        if (steps.isEmpty()) {
            return false;
        }
        steps.remove(steps.size() - 1);
        elbowChoice = null;
        return true;
    }

    /** The wires the fixed corners make, as they would be added: collinear consecutive segments merged. */
    List<Segment> segments() {
        return merged(routes(false));
    }

    /** {@link #segments()} and the segment being drawn to the pointer: what the preview shows. */
    private List<Segment> previewSegments() {
        return merged(routes(true));
    }

    private List<Segment> routes(boolean withTheSegmentBeingDrawn) {
        List<Segment> routes = new ArrayList<>();
        GridPoint from = start;
        for (Step step : steps) {
            routes.addAll(WireRouting.route(from, step.end(), step.horizontalFirst()));
            from = step.end();
        }
        if (withTheSegmentBeingDrawn) {
            routes.addAll(WireRouting.route(from, current, horizontalFirstTo(current)));
        }
        return routes;
    }

    /**
     * {@code routes} with each run of consecutive segments on one line made one: going on in the same direction
     * lengthens the wire; coming back over it shortens it to where the path turned back (a run that comes back to
     * where it started leaves nothing), so the path never adds two wires over each other.
     */
    private static List<Segment> merged(List<Segment> routes) {
        List<Segment> merged = new ArrayList<>();
        for (Segment segment : routes) {
            Segment next = segment;
            while (next != null && !merged.isEmpty() && collinear(merged.get(merged.size() - 1), next)) {
                Segment previous = merged.remove(merged.size() - 1);
                next = previous.start().equals(next.end()) ? null : new Segment(previous.start(), next.end());
            }
            if (next != null) {
                merged.add(next);
            }
        }
        return List.copyOf(merged);
    }

    private static boolean collinear(Segment first, Segment second) {
        if (first.isHorizontal() != second.isHorizontal()) {
            return false;
        }
        return first.isHorizontal() ? first.start().y() == second.start().y()
                : first.start().x() == second.start().x();
    }

    /**
     * Where the path, as the preview shows it, will join the circuit (decided point 1 of 2026-11-08): the ends of its
     * wires that land on a pin or on a wire (end or interior), and every pin and wire end lying on its way, which
     * connect too. Crossing a wire's interior is no junction.
     */
    List<GridPoint> junctionPoints(ToolContext context) {
        List<Segment> segments = previewSegments();
        if (segments.isEmpty()) {
            return List.of();
        }
        CircuitDocument document = context.openDocument().document();
        ConnectionPoints connections = connections(context);
        Set<GridPoint> junctions = new LinkedHashSet<>();
        wireEndsOf(segments).stream()
                .filter(point -> connections.isConnection(document, point))
                .forEach(junctions::add);
        List<GridPoint> pointsOnTheWay = new ArrayList<>(connections.pinLocations(document));
        for (Wire wire : document.wires()) {
            pointsOnTheWay.add(wire.start());
            pointsOnTheWay.add(wire.end());
        }
        pointsOnTheWay.stream()
                .filter(point -> segments.stream().anyMatch(segment -> segment.contains(point)))
                .forEach(junctions::add);
        return List.copyOf(junctions);
    }

    private static List<GridPoint> wireEndsOf(List<Segment> segments) {
        List<GridPoint> ends = new ArrayList<>();
        ends.add(segments.get(0).start());
        segments.forEach(segment -> ends.add(segment.end()));
        return ends;
    }

    private static ConnectionPoints connections(ToolContext context) {
        return new ConnectionPoints(context.openDocument().registry());
    }

    /** Adds the fixed path as one undo step; false (nothing added) when it has no length. */
    boolean commit(ToolContext context) {
        return WireDrawing.addSegments(context, segments());
    }

    /** Resolves the pointer's end again from the last raw point; true when it or its guides changed. */
    boolean refreshSnap(ToolContext context) {
        GridPoint before = current;
        List<Guide> guidesBefore = currentGuides;
        resolveCurrent(context, rawCurrent);
        return !current.equals(before) || !currentGuides.equals(guidesBefore);
    }

    /** The guides the start and the pointer's end lie on because they snapped to them. */
    List<Guide> snappedGuides() {
        if (currentGuides.isEmpty()) {
            return startGuides;
        }
        List<Guide> guides = new ArrayList<>(startGuides);
        currentGuides.stream().filter(guide -> !guides.contains(guide)).forEach(guides::add);
        return List.copyOf(guides);
    }

    /** The path and the segment being drawn as ghosts, and a dot at every point where it will join the circuit. */
    void paintPreview(Graphics2D graphics, ToolContext context) {
        WireDrawing.paintSegments(graphics, context, previewSegments(), startWidth);
        for (GridPoint junction : junctionPoints(context)) {
            paintJunction(graphics, context, junction);
        }
    }

    /** One dot of the preview where a wire will join the circuit at {@code junction} (the scheme's preview colour). */
    static void paintJunction(Graphics2D graphics, ToolContext context, GridPoint junction) {
        graphics.setColor(context.scheme().preview());
        double unit = GridPoint.PIXELS_PER_UNIT;
        graphics.fill(new Ellipse2D.Double(junction.x() * unit - JUNCTION_PREVIEW_RADIUS,
                junction.y() * unit - JUNCTION_PREVIEW_RADIUS, 2 * JUNCTION_PREVIEW_RADIUS,
                2 * JUNCTION_PREVIEW_RADIUS));
    }
}
