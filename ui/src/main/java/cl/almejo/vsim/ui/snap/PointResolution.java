package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;

/**
 * Where a wire or frame point lands, and why: the plain grid point, a connection kept, a guide or a grid line
 * (GUIDE when a guide won on at least one axis). {@code guides} are the guides the point lies on because it snapped
 * to them (at most one per axis), for the guide overlay's highlight.
 */
public record PointResolution(GridPoint point, Reason reason, List<Guide> guides) {

    public enum Reason { GRID, CONNECTION, GUIDE, GRID_LINE }

    public PointResolution {
        guides = List.copyOf(guides);
    }

    /** A resolution that lies on no guide. */
    public PointResolution(GridPoint point, Reason reason) {
        this(point, reason, List.of());
    }
}
