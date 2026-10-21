package cl.almejo.vsim.core.geometry;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;
import java.util.Optional;

/**
 * A line a moving set may snap to, in grid units on {@code axis} (an {@link Axis#X} target is a vertical line at
 * {@code x = position}); a part's centre may sit on a half unit. {@code ownerId} and {@code ownerBox} name the part
 * the line belongs to (empty for guides and grid lines).
 */
public record SnapTarget(SnapKind kind, Axis axis, double position, Optional<ElementId> ownerId,
                         Optional<Bounds> ownerBox) {

    public SnapTarget {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerBox, "ownerBox");
    }
}
