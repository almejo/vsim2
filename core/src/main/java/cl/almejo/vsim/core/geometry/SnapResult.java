package cl.almejo.vsim.core.geometry;

import java.util.List;
import java.util.Optional;

/** The delta to apply after snapping, the target won on each axis (if any) and the alignment lines to draw. */
public record SnapResult(int deltaX, int deltaY, Optional<SnapTarget> winnerX, Optional<SnapTarget> winnerY,
                         List<SnapMatch> matches) {

    public SnapResult {
        matches = List.copyOf(matches);
    }

    /** The raw delta, nothing won, nothing to draw. */
    public static SnapResult unsnapped(int deltaX, int deltaY) {
        return new SnapResult(deltaX, deltaY, Optional.empty(), Optional.empty(), List.of());
    }
}
