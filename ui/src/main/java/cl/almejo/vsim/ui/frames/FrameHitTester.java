package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the frame part under a point given in (fractional) grid units. Only the title bar and, on an unlocked frame,
 * the edges and corners belong to a frame: its empty interior is the canvas's (spec §2). Where frames overlap the
 * smallest one wins, as it is painted on top. Kept apart from {@code HitTester}, which never sees frames.
 */
public final class FrameHitTester {

    /** Screen pixels around an edge that still grab it, so a thin line is easy to catch. */
    public static final double GRAB_PIXELS = 4;
    /** Height of the title bar in grid units. */
    static final double TITLE_BAR_UNITS = FramePainter.TITLE_BAR_HEIGHT / GridPoint.PIXELS_PER_UNIT;
    private static final double MAXIMUM_INWARD_UNITS = TITLE_BAR_UNITS / 4;

    private FrameHitTester() {
    }

    /** {@code pixelsPerGrid} is the screen size of one grid unit at the current zoom (8 at 100 %). */
    public static Optional<FrameHit> hit(CircuitDocument document, Point2D gridPoint, double pixelsPerGrid) {
        double tolerance = GRAB_PIXELS / pixelsPerGrid;
        for (Frame frame : smallestFirst(document.frames())) {
            Optional<FramePart> part = partAt(frame, gridPoint, tolerance, pixelsPerGrid);
            if (part.isPresent()) {
                return Optional.of(new FrameHit(frame, part.get()));
            }
        }
        return Optional.empty();
    }

    /** The smallest frame whose rectangle (with the grab tolerance around it) holds the point: the one hovered. */
    public static Optional<Frame> frameUnder(CircuitDocument document, Point2D gridPoint, double pixelsPerGrid) {
        double tolerance = GRAB_PIXELS / pixelsPerGrid;
        return smallestFirst(document.frames()).stream()
                .filter(frame -> nearRectangle(frame, gridPoint, tolerance))
                .findFirst();
    }

    private static List<Frame> smallestFirst(List<Frame> frames) {
        return frames.stream()
                .sorted(Comparator.comparingLong(frame -> (long) frame.width() * frame.height()))
                .toList();
    }

    private static Optional<FramePart> partAt(Frame frame, Point2D point, double tolerance,
                                              double pixelsPerGrid) {
        if (!nearRectangle(frame, point, tolerance)) {
            return Optional.empty();
        }
        double left = frame.topLeft().x();
        double top = frame.topLeft().y();
        double right = left + frame.width();
        double bottom = top + frame.height();
        double x = point.getX();
        double y = point.getY();
        if (FramePainter.showsDetails(pixelsPerGrid)) {
            if (FramePainter.lockBounds(frame).contains(x * GridPoint.PIXELS_PER_UNIT, y * GridPoint.PIXELS_PER_UNIT)) {
                return Optional.of(FramePart.LOCK);
            }
            if (FramePainter.menuBounds(frame).contains(x * GridPoint.PIXELS_PER_UNIT, y * GridPoint.PIXELS_PER_UNIT)) {
                return Optional.of(FramePart.MENU);
            }
        }
        if (!frame.locked()) {
            Optional<FramePart> edge = edgeAt(x, y, left, top, right, bottom, tolerance);
            if (edge.isPresent()) {
                return edge;
            }
        }
        // a locked frame has no edges to grab: its top line, with the tolerance above it, belongs to the title bar
        if (x >= left && x <= right && y >= top - tolerance && y <= top + TITLE_BAR_UNITS) {
            return Optional.of(FramePart.TITLE_BAR);
        }
        return Optional.empty();
    }

    private static Optional<FramePart> edgeAt(double x, double y, double left, double top, double right,
                                              double bottom, double tolerance) {
        // inwards the band never grows past a quarter of the title bar, so the title bar and the interior stay
        // reachable when zoomed out
        double inwards = Math.min(tolerance, MAXIMUM_INWARD_UNITS);
        boolean north = y >= top - tolerance && y <= top + inwards;
        boolean south = y >= bottom - inwards && y <= bottom + tolerance;
        boolean west = x >= left - tolerance && x <= left + inwards;
        boolean east = x >= right - inwards && x <= right + tolerance;
        if (north && west) {
            return Optional.of(FramePart.CORNER_NORTH_WEST);
        }
        if (north && east) {
            return Optional.of(FramePart.CORNER_NORTH_EAST);
        }
        if (south && west) {
            return Optional.of(FramePart.CORNER_SOUTH_WEST);
        }
        if (south && east) {
            return Optional.of(FramePart.CORNER_SOUTH_EAST);
        }
        if (north) {
            return Optional.of(FramePart.EDGE_NORTH);
        }
        if (south) {
            return Optional.of(FramePart.EDGE_SOUTH);
        }
        if (west) {
            return Optional.of(FramePart.EDGE_WEST);
        }
        if (east) {
            return Optional.of(FramePart.EDGE_EAST);
        }
        return Optional.empty();
    }

    private static boolean nearRectangle(Frame frame, Point2D point, double tolerance) {
        double left = frame.topLeft().x();
        double top = frame.topLeft().y();
        return point.getX() >= left - tolerance && point.getX() <= left + frame.width() + tolerance
                && point.getY() >= top - tolerance && point.getY() <= top + frame.height() + tolerance;
    }
}
