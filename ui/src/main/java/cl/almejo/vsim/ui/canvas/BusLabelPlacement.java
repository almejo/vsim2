package cl.almejo.vsim.ui.canvas;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Keeps a bus value's tag off the pieces' bodies: a tag centred on a wire shorter than itself (a bus output
 * right at a piece's edge) would be written over the pin's name, so it slides sideways until it is outside.
 */
final class BusLabelPlacement {

    private BusLabelPlacement() {
    }

    /**
     * Where to write a tag of {@code textWidth} whose left edge and baseline are {@code spot}: the same place when it
     * touches no body, otherwise moved horizontally to the side of each body it overlaps, {@code gap} clear of it.
     */
    static Point2D clearOf(Point2D spot, double textWidth, double ascent, double descent, double gap,
                           List<Rectangle2D> bodies) {
        double left = spot.getX();
        for (Rectangle2D body : bodies) {
            Rectangle2D tag = new Rectangle2D.Double(left, spot.getY() - ascent, textWidth, ascent + descent);
            if (!tag.intersects(body)) {
                continue;
            }
            left = tag.getCenterX() < body.getCenterX() ? body.getMinX() - gap - textWidth : body.getMaxX() + gap;
        }
        return new Point2D.Double(left, spot.getY());
    }
}
