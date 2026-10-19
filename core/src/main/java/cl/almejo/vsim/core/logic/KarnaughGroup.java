package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.KarnaughLayout.GridCell;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One group of a Karnaugh map: the term it stands for, its cells, and the pieces to draw. A group inside the map is
 * one rectangle; a group that wraps around an edge is two rectangles open at that edge; the four corners are four.
 *
 * @param term       the term (a group of 1s in a sum of products, of 0s in a product of sums)
 * @param cells      its cells, in truth-table row order
 * @param rectangles the pieces to draw, top to bottom then left to right
 */
public record KarnaughGroup(Term term, Set<GridCell> cells, List<CellRectangle> rectangles) {

    public KarnaughGroup {
        cells = Collections.unmodifiableSet(new LinkedHashSet<>(cells));
        rectangles = List.copyOf(rectangles);
    }

    /** A side of a rectangle; an open side continues past the edge of the map. */
    public enum Side {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    /**
     * A rectangle of cells to draw.
     *
     * @param row        top row
     * @param column     left column
     * @param rowSpan    rows covered (1..4)
     * @param columnSpan columns covered (1..4)
     * @param openSides  the sides at a map edge where the group goes on at the opposite edge (drawn without border)
     */
    public record CellRectangle(int row, int column, int rowSpan, int columnSpan, Set<Side> openSides) {

        public CellRectangle {
            openSides = Set.copyOf(openSides);
        }

        public boolean isOpen(Side side) {
            return openSides.contains(side);
        }
    }
}
