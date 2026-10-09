package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Set;

/**
 * Where a drag out of a canvas started: the source canvas, the dragged element ids, the snapped grid point that was
 * pressed and that point's offset from the top-left of the dragged elements. It travels with the drag inside this JVM,
 * so the drop can tell a move (same document) from a copy.
 */
record DragOrigin(CircuitCanvas canvas, Set<ElementId> ids, GridPoint pressPoint, GridPoint grabOffset) {

    DragOrigin {
        ids = Set.copyOf(ids);
    }
}
