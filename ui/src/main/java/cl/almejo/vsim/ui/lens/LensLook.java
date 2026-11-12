package cl.almejo.vsim.ui.lens;

import cl.almejo.vsim.ui.app.AppPreferences.LensShape;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

/**
 * What the lens looks like: its shape and its radius in logical pixels. Round: a circle of the radius. Rectangular:
 * a landscape rectangle with rounded corners, 1.5 x the radius to each side of the centre and 1 x up and down.
 *
 * @param shape  round or rectangular
 * @param radius logical pixels
 */
record LensLook(LensShape shape, int radius) {

    /** The rectangle's width, in radii, on each side of the centre. */
    static final double RECTANGLE_HALF_WIDTH_IN_RADII = 1.5;
    /** The rectangle's corner radius in logical pixels (the approved mockup's). */
    static final double CORNER_RADIUS = 8;

    LensLook {
        Objects.requireNonNull(shape, "shape");
    }

    /** From the centre to the left or right edge. */
    double halfWidth() {
        return shape == LensShape.RECTANGLE ? radius * RECTANGLE_HALF_WIDTH_IN_RADII : radius;
    }

    /** From the centre to the top or bottom edge. */
    double halfHeight() {
        return radius;
    }

    /** The lens's outline with {@code centre} at its middle. */
    Shape outline(Point2D centre) {
        double left = centre.getX() - halfWidth();
        double top = centre.getY() - halfHeight();
        if (shape == LensShape.RECTANGLE) {
            return new RoundRectangle2D.Double(left, top, 2 * halfWidth(), 2 * halfHeight(),
                    2 * CORNER_RADIUS, 2 * CORNER_RADIUS);
        }
        return new Ellipse2D.Double(left, top, 2 * halfWidth(), 2 * halfHeight());
    }
}
