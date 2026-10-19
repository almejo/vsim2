package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Where the cells of a Karnaugh map and the pieces of its groups go, in pixels: square cells of {@code cellSize}
 * from {@code (left, top)}. A piece open at a map edge runs one cell past it, so once clipped to {@link #grid()} it
 * shows as an open half (no rounded corner, no border there), like on the blackboard.
 */
final class MapGeometry {

    private final KarnaughLayout layout;
    private final int left;
    private final int top;
    private final int cellSize;

    MapGeometry(KarnaughLayout layout, int left, int top, int cellSize) {
        this.layout = layout;
        this.left = left;
        this.top = top;
        this.cellSize = cellSize;
    }

    int cellSize() {
        return cellSize;
    }

    /** The area of all the cells, without the headers. */
    Rectangle grid() {
        return new Rectangle(left, top, layout.columns() * cellSize, layout.rows() * cellSize);
    }

    Rectangle cell(int row, int column) {
        return new Rectangle(left + column * cellSize, top + row * cellSize, cellSize, cellSize);
    }

    /** The table row of the cell under {@code point}, if it is on a cell. */
    OptionalInt tableRowAt(Point point) {
        if (!grid().contains(point)) {
            return OptionalInt.empty();
        }
        int column = (point.x - left) / cellSize;
        int row = (point.y - top) / cellSize;
        return OptionalInt.of(layout.tableRow(row, column));
    }

    /**
     * The rectangles that draw {@code group}, each {@code inset} pixels inside its cells; an open side instead runs
     * one cell past the map edge.
     */
    List<Rectangle2D> pieces(KarnaughGroup group, int inset) {
        List<Rectangle2D> pieces = new ArrayList<>();
        for (KarnaughGroup.CellRectangle rectangle : group.rectangles()) {
            double minimumX = left + rectangle.column() * cellSize + inset;
            double minimumY = top + rectangle.row() * cellSize + inset;
            double maximumX = left + (rectangle.column() + rectangle.columnSpan()) * cellSize - inset;
            double maximumY = top + (rectangle.row() + rectangle.rowSpan()) * cellSize - inset;
            if (rectangle.isOpen(KarnaughGroup.Side.LEFT)) {
                minimumX = left - cellSize;
            }
            if (rectangle.isOpen(KarnaughGroup.Side.RIGHT)) {
                maximumX = left + layout.columns() * cellSize + cellSize;
            }
            if (rectangle.isOpen(KarnaughGroup.Side.TOP)) {
                minimumY = top - cellSize;
            }
            if (rectangle.isOpen(KarnaughGroup.Side.BOTTOM)) {
                maximumY = top + layout.rows() * cellSize + cellSize;
            }
            pieces.add(new Rectangle2D.Double(minimumX, minimumY, maximumX - minimumX, maximumY - minimumY));
        }
        return pieces;
    }
}
