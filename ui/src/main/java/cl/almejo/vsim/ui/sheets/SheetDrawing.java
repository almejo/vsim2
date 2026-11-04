package cl.almejo.vsim.ui.sheets;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.List;

/**
 * What one pane draws of the sheets, in logical screen pixels ({@link SheetOverlay#drawing()}): the borders of the
 * visible sheets, their overlap bands joined into one area, the «Hoja n» labels and the cut marks.
 */
record SheetDrawing(List<Rectangle> borders, Area bands, List<SheetLabel> labels, List<Rectangle> cuts) {

    static final SheetDrawing EMPTY = new SheetDrawing(List.of(), new Area(), List.of(), List.of());

    SheetDrawing {
        borders = List.copyOf(borders);
        bands = new Area(bands);
        labels = List.copyOf(labels);
        cuts = List.copyOf(cuts);
    }

    @Override
    public Area bands() {
        return new Area(bands);
    }

    boolean isEmpty() {
        return borders.isEmpty() && cuts.isEmpty();
    }
}
