package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.KarnaughLayout.GridCell;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A group drawn by the student on a Karnaugh map: its cells and the pieces to draw, like a {@link KarnaughGroup} but
 * without a term (a drawn rectangle need not be a valid group: {@link KarnaughLayout#term} tells).
 *
 * @param cells      its cells, in truth-table row order
 * @param rectangles the pieces to draw, top to bottom then left to right; a piece crossing an edge is open there
 */
public record DrawnGroup(Set<GridCell> cells, List<KarnaughGroup.CellRectangle> rectangles) {

    public DrawnGroup {
        cells = Collections.unmodifiableSet(new LinkedHashSet<>(cells));
        rectangles = List.copyOf(rectangles);
    }
}
