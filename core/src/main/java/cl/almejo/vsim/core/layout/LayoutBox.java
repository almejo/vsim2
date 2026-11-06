package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;

/**
 * One rectangle the overlap separation works with, in grid units. A fixed box never moves; {@code gap} is the space
 * this box wants around itself (a pair keeps the larger of its two gaps).
 */
public record LayoutBox(ElementId id, Bounds bounds, boolean movable, int gap) {
}
