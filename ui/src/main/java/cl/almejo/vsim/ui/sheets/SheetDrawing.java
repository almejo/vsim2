package cl.almejo.vsim.ui.sheets;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.List;

/**
 * What one pane draws of the sheets, in logical screen pixels ({@link SheetOverlay#drawing()}): the borders of the
 * visible sheets that go out, the borders of the visible empty sheets «Saltar hojas vacías» skips (drawn dimmer),
 * the overlap bands between sheets that both go out joined into one area, the «Hoja n» labels and the cut marks.
 */
record SheetDrawing(List<Rectangle> borders, List<Rectangle> emptyBorders, Area bands, List<SheetLabel> labels,
                    List<Rectangle> cuts) {

    static final SheetDrawing EMPTY = new SheetDrawing(List.of(), List.of(), new Area(), List.of(), List.of());

    SheetDrawing {
        borders = List.copyOf(borders);
        emptyBorders = List.copyOf(emptyBorders);
        bands = new Area(bands);
        labels = List.copyOf(labels);
        cuts = List.copyOf(cuts);
    }

    @Override
    public Area bands() {
        return new Area(bands);
    }

    boolean isEmpty() {
        return borders.isEmpty() && emptyBorders.isEmpty() && cuts.isEmpty();
    }
}
