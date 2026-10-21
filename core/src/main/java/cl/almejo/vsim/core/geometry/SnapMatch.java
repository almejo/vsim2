package cl.almejo.vsim.core.geometry;

import java.util.List;

/** One alignment line to draw: every target exactly matched at {@code position} on {@code axis} after the snap. */
public record SnapMatch(Axis axis, double position, List<SnapTarget> targets) {

    public SnapMatch {
        targets = List.copyOf(targets);
    }
}
