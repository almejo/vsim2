package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.print.SheetGrid;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;

/**
 * The print sheets of one view as its panes draw them, laid out once per change ({@link SheetsFeatures#picture()}):
 * the grid of sheets (none when the paper leaves no room), the «Todo» area it was laid out over, the drawn bounds of
 * the parts and frames a border cuts, and the summary label's text. Grid units throughout.
 */
record SheetPicture(SheetGrid grid, Rectangle2D area, List<Rectangle2D> cuts, String summary) {

    SheetPicture {
        Objects.requireNonNull(grid, "grid");
        area = (Rectangle2D) Objects.requireNonNull(area, "area").clone();
        cuts = List.copyOf(cuts);
        Objects.requireNonNull(summary, "summary");
    }

    @Override
    public Rectangle2D area() {
        return (Rectangle2D) area.clone();
    }

    /** Where the summary label stands on: the first sheet's top-left corner, else the area's. */
    Point2D summaryCorner() {
        if (grid.columns() > 0 && grid.rows() > 0) {
            Rectangle2D first = grid.sheetRectangle(0, 0);
            return new Point2D.Double(first.getX(), first.getY());
        }
        return new Point2D.Double(area.getX(), area.getY());
    }
}
