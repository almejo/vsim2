package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Where a group of elements starts: the top-left of their bounds, which a fragment of them puts at (0,0). */
public final class SelectionBounds {

    private SelectionBounds() {
    }

    /** Unknown ids are ignored; empty when none of the ids exists. */
    public static Optional<GridPoint> topLeft(OpenDocument openDocument, Collection<ElementId> ids) {
        List<DocumentElement> elements = ids.stream()
                .map(id -> openDocument.document().element(id))
                .flatMap(Optional::stream)
                .toList();
        return ElementBounds.union(elements, openDocument.registry())
                .map(bounds -> new GridPoint(bounds.x(), bounds.y()));
    }
}
