package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.KarnaughGroup;
import cl.almejo.vsim.core.logic.KarnaughLayout;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
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

    /** The map column under x {@code x}, counted on past the edges (-1 left of the map, columns() right of it). */
    int columnAt(int x) {
        return Math.floorDiv(x - left, cellSize);
    }

    /** The map row under y {@code y}, counted on past the edges like {@link #columnAt}. */
    int rowAt(int y) {
        return Math.floorDiv(y - top, cellSize);
    }

    /**
     * The rectangles that draw {@code group}, each {@code inset} pixels inside its cells; an open side instead runs
     * one cell past the map edge.
     */
    List<Rectangle2D> pieces(KarnaughGroup group, int inset) {
        return pieces(group.rectangles(), inset);
    }

    /** The rectangles that draw a group the student drew, like {@link #pieces(KarnaughGroup, int)}. */
    List<Rectangle2D> pieces(DrawnGroup group, int inset) {
        return pieces(group.rectangles(), inset);
    }

    List<Rectangle2D> pieces(List<KarnaughGroup.CellRectangle> rectangles, int inset) {
        List<Rectangle2D> pieces = new ArrayList<>();
        for (KarnaughGroup.CellRectangle rectangle : rectangles) {
            pieces.add(piece(rectangle, inset));
        }
        return pieces;
    }

    /**
     * The closed sides of the pieces of a group, {@code inset} pixels inside its cells: the outline the marching ants
     * follow. A side open at a map edge (the group goes on at the other edge) is left out.
     */
    Path2D closedSides(List<KarnaughGroup.CellRectangle> rectangles, int inset) {
        Path2D path = new Path2D.Double();
        for (KarnaughGroup.CellRectangle rectangle : rectangles) {
            Rectangle2D piece = piece(rectangle, inset);
            double minimumX = piece.getMinX();
            double minimumY = piece.getMinY();
            double maximumX = piece.getMaxX();
            double maximumY = piece.getMaxY();
            boolean openLeft = rectangle.isOpen(KarnaughGroup.Side.LEFT);
            boolean openRight = rectangle.isOpen(KarnaughGroup.Side.RIGHT);
            boolean openTop = rectangle.isOpen(KarnaughGroup.Side.TOP);
            boolean openBottom = rectangle.isOpen(KarnaughGroup.Side.BOTTOM);
            // Along an open side the piece runs past the map: the closed sides stop at the map edge.
            double fromX = openLeft ? left : minimumX;
            double toX = openRight ? left + layout.columns() * cellSize : maximumX;
            double fromY = openTop ? top : minimumY;
            double toY = openBottom ? top + layout.rows() * cellSize : maximumY;
            if (!openTop) {
                line(path, fromX, minimumY, toX, minimumY);
            }
            if (!openBottom) {
                line(path, fromX, maximumY, toX, maximumY);
            }
            if (!openLeft) {
                line(path, minimumX, fromY, minimumX, toY);
            }
            if (!openRight) {
                line(path, maximumX, fromY, maximumX, toY);
            }
        }
        return path;
    }

    private static void line(Path2D path, double fromX, double fromY, double toX, double toY) {
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);
    }

    private Rectangle2D piece(KarnaughGroup.CellRectangle rectangle, int inset) {
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
        return new Rectangle2D.Double(minimumX, minimumY, maximumX - minimumX, maximumY - minimumY);
    }
}
