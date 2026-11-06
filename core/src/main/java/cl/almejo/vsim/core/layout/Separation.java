package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Map;
import java.util.Set;

/**
 * What {@link OverlapSeparation} decided: a whole-unit delta for every box that moves (never for a fixed one), and the
 * movable boxes that still overlap something after the last round.
 */
public record Separation(Map<ElementId, GridPoint> deltas, Set<ElementId> stillOverlapping) {

    public Separation {
        deltas = Map.copyOf(deltas);
        stillOverlapping = Set.copyOf(stillOverlapping);
    }
}
