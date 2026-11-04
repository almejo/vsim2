package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Locale;

/**
 * Logisim's {@code facing}, in VSim's clockwise order. For most parts it is the side of the output (gates, plexers,
 * constant) or of the one connection (pins, tunnels, probes, LEDs, buttons); for a splitter the side its ends go to.
 */
enum LogisimFacing {
    EAST, SOUTH, WEST, NORTH;

    /**
     * The facing Logisim writes ({@code east}, {@code south}, {@code west}, {@code north}).
     *
     * @throws IllegalArgumentException for any other text
     */
    static LogisimFacing parse(String text) {
        return valueOf(text.strip().toUpperCase(Locale.ROOT));
    }

    LogisimFacing opposite() {
        return values()[(ordinal() + 2) % values().length];
    }

    /**
     * How Logisim moves a point along a facing: {@code point} moved {@code distance} this way and
     * {@code right} to its right (clockwise of it).
     */
    LogisimPoint translate(LogisimPoint point, int distance, int right) {
        return switch (this) {
            case EAST -> new LogisimPoint(point.x() + distance, point.y() + right);
            case WEST -> new LogisimPoint(point.x() - distance, point.y() - right);
            case SOUTH -> new LogisimPoint(point.x() - right, point.y() + distance);
            case NORTH -> new LogisimPoint(point.x() + right, point.y() - distance);
        };
    }

    /** The VSim rotation that turns a part facing {@code natural} at rotation 0 to face this way. */
    Rotation rotationFrom(LogisimFacing natural) {
        return Rotation.fromQuarterTurns(ordinal() - natural.ordinal());
    }

    /** The way a part facing this way at rotation 0 faces once turned by {@code rotation}: rotationFrom's inverse. */
    LogisimFacing turnedBy(Rotation rotation) {
        return values()[Math.floorMod(ordinal() + rotation.quarterTurns(), values().length)];
    }

    /** As Logisim writes it: {@code east}, {@code south}, {@code west}, {@code north}. */
    String text() {
        return name().toLowerCase(Locale.ROOT);
    }
}
