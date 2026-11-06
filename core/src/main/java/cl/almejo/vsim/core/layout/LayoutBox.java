package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;

/**
 * One rectangle the overlap separation works with, in grid units. A fixed box never moves; {@code gap} is the space
 * this box wants around itself (a pair keeps the larger of its two gaps); {@code weight} (at least 1) is how much it
 * resists moving: of two overlapping boxes the heavier moves less, the much heavier not at all.
 */
public record LayoutBox(ElementId id, Bounds bounds, boolean movable, int gap, long weight) {

    public LayoutBox {
        if (weight < 1) {
            throw new IllegalArgumentException("the weight of " + id + " must be at least 1: " + weight);
        }
    }

    /** A box that weighs 1. */
    public LayoutBox(ElementId id, Bounds bounds, boolean movable, int gap) {
        this(id, bounds, movable, gap, 1);
    }
}
