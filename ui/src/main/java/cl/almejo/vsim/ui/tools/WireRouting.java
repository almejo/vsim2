package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.List;

/** Orthogonal route between two points: an "L" made of at most two wire segments, like VSim. */
public final class WireRouting {

    public record Segment(GridPoint start, GridPoint end) {

        /** True when the segment (horizontal or vertical) passes through {@code point}, its ends included. */
        public boolean contains(GridPoint point) {
            return point.x() >= Math.min(start.x(), end.x()) && point.x() <= Math.max(start.x(), end.x())
                    && point.y() >= Math.min(start.y(), end.y()) && point.y() <= Math.max(start.y(), end.y());
        }

        boolean isHorizontal() {
            return start.y() == end.y();
        }
    }

    private WireRouting() {
    }

    public static List<Segment> route(GridPoint start, GridPoint end, boolean horizontalFirst) {
        GridPoint corner = horizontalFirst ? new GridPoint(end.x(), start.y()) : new GridPoint(start.x(), end.y());
        List<Segment> segments = new ArrayList<>();
        if (!start.equals(corner)) {
            segments.add(new Segment(start, corner));
        }
        if (!corner.equals(end)) {
            segments.add(new Segment(corner, end));
        }
        return List.copyOf(segments);
    }
}
