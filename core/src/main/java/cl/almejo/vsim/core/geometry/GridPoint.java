package cl.almejo.vsim.core.geometry;

import java.util.Comparator;

/** A point on the circuit grid. One unit equals 8 pixels of VSim at zoom 1. */
public record GridPoint(int x, int y) implements Comparable<GridPoint> {

    public static final int PIXELS_PER_UNIT = 8;
    public static final GridPoint ORIGIN = new GridPoint(0, 0);
    private static final Comparator<GridPoint> ORDER =
            Comparator.comparingInt(GridPoint::x).thenComparingInt(GridPoint::y);

    public static GridPoint fromPixels(int pixelX, int pixelY) {
        return new GridPoint(Math.floorDiv(pixelX, PIXELS_PER_UNIT), Math.floorDiv(pixelY, PIXELS_PER_UNIT));
    }

    public GridPoint translate(int deltaX, int deltaY) {
        return new GridPoint(x + deltaX, y + deltaY);
    }

    public GridPoint plus(GridPoint other) {
        return translate(other.x, other.y);
    }

    @Override
    public int compareTo(GridPoint other) {
        return ORDER.compare(this, other);
    }
}
