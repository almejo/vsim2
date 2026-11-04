package cl.almejo.vsim.ui.sheets;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.List;

/**
 * What one pane draws of the sheets, in logical screen pixels ({@link SheetOverlay#drawing()}): the borders of the
 * visible sheets that go out, the borders of the visible empty sheets «Saltar hojas vacías» skips (drawn dimmer),
 * the overlap bands between sheets that both go out joined into one area, the «Hoja n» labels and the cut marks.
 * While the sheets are tiled, also the lattice they continue over the whole pane, fainter: the x of its vertical lines
 * and the y of its horizontal ones, drawn outside {@code occupied} (the block the sheets cover; empty when there is no
 * lattice). And the outlines of the parts and frames a move under way drags where a border would cut them.
 */
record SheetDrawing(List<Rectangle> borders, List<Rectangle> emptyBorders, Area bands, List<SheetLabel> labels,
                    List<Rectangle> cuts, List<Integer> latticeColumns, List<Integer> latticeRows, Rectangle occupied,
                    List<Rectangle> movingCuts) {

    static final SheetDrawing EMPTY = new SheetDrawing(List.of(), List.of(), new Area(), List.of(), List.of(),
            List.of(), List.of(), new Rectangle(), List.of());

    SheetDrawing {
        borders = List.copyOf(borders);
        emptyBorders = List.copyOf(emptyBorders);
        bands = new Area(bands);
        labels = List.copyOf(labels);
        cuts = List.copyOf(cuts);
        latticeColumns = List.copyOf(latticeColumns);
        latticeRows = List.copyOf(latticeRows);
        occupied = new Rectangle(occupied);
        movingCuts = List.copyOf(movingCuts);
    }

    @Override
    public Area bands() {
        return new Area(bands);
    }

    @Override
    public Rectangle occupied() {
        return new Rectangle(occupied);
    }

    boolean isEmpty() {
        return borders.isEmpty() && emptyBorders.isEmpty() && cuts.isEmpty() && latticeColumns.isEmpty()
                && latticeRows.isEmpty() && movingCuts.isEmpty();
    }
}
