package cl.almejo.vsim.core.geometry;

/** Pure snapping geometry (alignment-guides spec §3.1–§3.5, §4.4). */
public final class Snapping {

    private Snapping() {
    }

    /** The snapped delta for {@code input}. */
    public static SnapResult solve(SnapInput input) {
        return SnapResult.unsnapped(input.rawDeltaX(), input.rawDeltaY());
    }

    /** The magnet's reach in grid units at a zoom of {@code pixelsPerUnit} screen pixels per grid unit. */
    public static int thresholdUnits(double pixelsPerUnit) {
        return 1;
    }
}
