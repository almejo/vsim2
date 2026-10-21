package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapResult;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.util.Set;

/** Snaps the gestures of the tools to parts, guides and grid lines; one call per tool hook. */
public interface Snapper {

    /** The snapped delta for moving or placing {@code moving} by the raw delta. */
    SnapResult snapBox(ToolContext context, MovingSet moving, int rawDeltaX, int rawDeltaY);

    /** Where a wire or frame point at {@code point} lands. */
    PointResolution snapPoint(ToolContext context, GridPoint point);

    /** The snapped delta for dragging the wires {@code wireIds} (their end points are the moving lines). */
    SnapResult snapWires(ToolContext context, Set<ElementId> wireIds, int rawDeltaX, int rawDeltaY);

    /** A snapper that never snaps: raw deltas, plain grid points. */
    static Snapper none() {
        return new Snapper() {
            @Override
            public SnapResult snapBox(ToolContext context, MovingSet moving, int rawDeltaX, int rawDeltaY) {
                return SnapResult.unsnapped(rawDeltaX, rawDeltaY);
            }

            @Override
            public PointResolution snapPoint(ToolContext context, GridPoint point) {
                return new PointResolution(point, PointResolution.Reason.GRID);
            }

            @Override
            public SnapResult snapWires(ToolContext context, Set<ElementId> wireIds, int rawDeltaX, int rawDeltaY) {
                return SnapResult.unsnapped(rawDeltaX, rawDeltaY);
            }
        };
    }
}
