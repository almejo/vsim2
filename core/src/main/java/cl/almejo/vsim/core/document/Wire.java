package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;

/** Horizontal or vertical wire segment; endpoints are normalized so that start &lt; end. */
public record Wire(ElementId id, GridPoint start, GridPoint end) implements DocumentElement {

    public Wire {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.equals(end)) {
            throw new IllegalArgumentException("zero-length wire at " + start);
        }
        if (start.x() != end.x() && start.y() != end.y()) {
            throw new IllegalArgumentException("wire must be horizontal or vertical: " + start + " " + end);
        }
        if (start.compareTo(end) > 0) {
            GridPoint swap = start;
            start = end;
            end = swap;
        }
    }

    public boolean isHorizontal() {
        return start.y() == end.y();
    }

    public boolean contains(GridPoint point) {
        if (isHorizontal()) {
            return point.y() == start.y() && point.x() >= start.x() && point.x() <= end.x();
        }
        return point.x() == start.x() && point.y() >= start.y() && point.y() <= end.y();
    }

    /** True when both wires lie on the same line and share a stretch of positive length. */
    public boolean overlapsCollinear(Wire other) {
        if (isHorizontal() != other.isHorizontal()) {
            return false;
        }
        if (isHorizontal()) {
            return start.y() == other.start.y()
                    && Math.min(end.x(), other.end.x()) - Math.max(start.x(), other.start.x()) > 0;
        }
        return start.x() == other.start.x()
                && Math.min(end.y(), other.end.y()) - Math.max(start.y(), other.start.y()) > 0;
    }

    public Wire withEndpoints(GridPoint newStart, GridPoint newEnd) {
        return new Wire(id, newStart, newEnd);
    }

    @Override
    public Wire translate(int deltaX, int deltaY) {
        return new Wire(id, start.translate(deltaX, deltaY), end.translate(deltaX, deltaY));
    }
}
