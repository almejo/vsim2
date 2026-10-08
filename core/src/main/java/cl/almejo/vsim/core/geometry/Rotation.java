package cl.almejo.vsim.core.geometry;

/** Clockwise quarter turns on screen (y grows downwards), like Java2D rotate(+90°). */
public enum Rotation {
    R0, R90, R180, R270;

    public GridPoint apply(GridPoint local) {
        return switch (this) {
            case R0 -> local;
            case R90 -> new GridPoint(-local.y(), local.x());
            case R180 -> new GridPoint(-local.x(), -local.y());
            case R270 -> new GridPoint(local.y(), -local.x());
        };
    }

    public Rotation clockwise() {
        return fromQuarterTurns(ordinal() + 1);
    }

    public Rotation counterClockwise() {
        return fromQuarterTurns(ordinal() - 1);
    }

    public int quarterTurns() {
        return ordinal();
    }

    public static Rotation fromQuarterTurns(int turns) {
        return values()[Math.floorMod(turns, 4)];
    }
}
