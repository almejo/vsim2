package cl.almejo.vsim.core.geometry;

/** Axis-aligned rectangle in grid units; edges are inclusive for {@link #contains}. */
public record Bounds(int x, int y, int width, int height) {

    public Bounds {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("negative bounds: " + width + "x" + height);
        }
    }

    public static Bounds of(GridPoint first, GridPoint second) {
        int minimumX = Math.min(first.x(), second.x());
        int minimumY = Math.min(first.y(), second.y());
        return new Bounds(minimumX, minimumY,
                Math.abs(first.x() - second.x()), Math.abs(first.y() - second.y()));
    }

    public static Bounds rotated(GridPoint origin, GridSize size, Rotation rotation) {
        GridPoint farCorner = rotation.apply(new GridPoint(size.width(), size.height()));
        return of(origin, origin.plus(farCorner));
    }

    public int maximumX() {
        return x + width;
    }

    public int maximumY() {
        return y + height;
    }

    public boolean contains(GridPoint point) {
        return point.x() >= x && point.x() <= maximumX() && point.y() >= y && point.y() <= maximumY();
    }

    public boolean contains(Bounds other) {
        return other.x >= x && other.maximumX() <= maximumX() && other.y >= y && other.maximumY() <= maximumY();
    }

    public boolean intersects(Bounds other) {
        return other.x <= maximumX() && other.maximumX() >= x && other.y <= maximumY() && other.maximumY() >= y;
    }

    public Bounds union(Bounds other) {
        int minimumX = Math.min(x, other.x);
        int minimumY = Math.min(y, other.y);
        return new Bounds(minimumX, minimumY,
                Math.max(maximumX(), other.maximumX()) - minimumX,
                Math.max(maximumY(), other.maximumY()) - minimumY);
    }

    public Bounds translate(int deltaX, int deltaY) {
        return new Bounds(x + deltaX, y + deltaY, width, height);
    }
}
