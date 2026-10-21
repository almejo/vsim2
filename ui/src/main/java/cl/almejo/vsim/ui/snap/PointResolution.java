package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.geometry.GridPoint;

/** Where a wire or frame point lands, and why: the plain grid point, a connection kept, a guide or a grid line. */
public record PointResolution(GridPoint point, Reason reason) {

    public enum Reason { GRID, CONNECTION, GUIDE, GRID_LINE }
}
