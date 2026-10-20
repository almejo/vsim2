package cl.almejo.vsim.ui.print;

import java.awt.geom.Rectangle2D;

/**
 * One sheet of a {@link PageLayout}.
 *
 * @param index          position in reading order, from 0 («Hoja index + 1 de N»)
 * @param row            from 0, top to bottom
 * @param column         from 0, left to right
 * @param gridRectangle  the part of the area printed on this sheet, in grid units
 * @param paperRectangle where that part goes on the paper, in points from the sheet's top-left corner
 */
public record PrintPage(int index, int row, int column, Rectangle2D gridRectangle, Rectangle2D paperRectangle) {

    public PrintPage {
        gridRectangle = (Rectangle2D) gridRectangle.clone();
        paperRectangle = (Rectangle2D) paperRectangle.clone();
    }

    @Override
    public Rectangle2D gridRectangle() {
        return (Rectangle2D) gridRectangle.clone();
    }

    @Override
    public Rectangle2D paperRectangle() {
        return (Rectangle2D) paperRectangle.clone();
    }
}
