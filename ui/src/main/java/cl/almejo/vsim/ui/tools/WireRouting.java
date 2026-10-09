package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.List;

/** Orthogonal route between two points: an "L" made of at most two wire segments, like VSim. */
public final class WireRouting {

    public record Segment(GridPoint start, GridPoint end) {
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
