package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Anchor;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Curve;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Paint;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Port;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Rectangle;
import cl.almejo.vsim.core.io.logisim.LogisimAppearance.Shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The box Logisim 2.7 draws for a circuit without {@code <appear>}, as the shapes it would save for it: a notch at the
 * middle of the top edge, the box, one port per pin and the anchor. It is the one source of that box for both the
 * symbol the importer draws ({@link LogisimAppearanceConverter}) and where an instance's ends are ({@link LogisimEnds}).
 *
 * <p>What it reproduces, as {@code ends-fixture.ends} (the «plain» circuit) and the files Logisim saves show it:
 * <ul>
 *   <li>a pin sits on the side opposite its facing (an input facing east, the default, on the west side), 10 px from
 *       the next, ordered along the side by its place in the circuit;</li>
 *   <li>the box starts at (50, 50); a side measures 10 px per pin of the fuller of the two sides along it, plus 10 px
 *       unless the box has no pins across, and never less than 30 px;</li>
 *   <li>the pins of each side are centred on it and then moved back onto the 10 px grid; when the box has no pins
 *       across, it is the box that moves half a step so that the side with the most pins is exactly centred;</li>
 *   <li>the anchor, facing east, is on the first pin of the first side that has pins, taking the east, north, west
 *       and south sides in that order; without pins it is the box's top left corner.</li>
 * </ul>
 */
final class LogisimDefaultAppearance {

    /** Where Logisim starts the box, in the appearance's own pixels. */
    private static final int START = 50;
    private static final int STEP = 10;
    private static final int SHORTEST_SIDE = 30;
    private static final int INPUT_PORT_SIZE = 8;
    private static final int OUTPUT_PORT_SIZE = 10;
    private static final int ANCHOR_SIZE = 6;
    private static final int NOTCH_HALF_WIDTH = 4;
    /** The notch is a curve from 1 px under the top edge, pulled 10 px further down by its control point. */
    private static final int NOTCH_TOP = 1;
    private static final int NOTCH_PULL = 10;
    private static final Paint BOX = new Paint("#000000", "none", 2);
    private static final Paint NOTCH = new Paint("#808080", "none", 2);
    /** The sides in the order their ports are written. */
    private static final List<LogisimFacing> PORT_ORDER = List.of(LogisimFacing.WEST, LogisimFacing.EAST,
            LogisimFacing.NORTH, LogisimFacing.SOUTH);
    /** The sides in the order the anchor looks for a pin. */
    private static final List<LogisimFacing> ANCHOR_ORDER = List.of(LogisimFacing.EAST, LogisimFacing.NORTH,
            LogisimFacing.WEST, LogisimFacing.SOUTH);

    private LogisimDefaultAppearance() {
    }

    /** The default box for a circuit whose {@code Pin}s are {@code pins}. */
    static LogisimAppearance build(List<LogisimInterfacePin> pins) {
        Map<LogisimFacing, List<LogisimInterfacePin>> sides = bySide(pins);
        int westAndEast = Math.max(count(sides, LogisimFacing.WEST), count(sides, LogisimFacing.EAST));
        int northAndSouth = Math.max(count(sides, LogisimFacing.NORTH), count(sides, LogisimFacing.SOUTH));
        Axis vertical = Axis.of(count(sides, LogisimFacing.WEST), count(sides, LogisimFacing.EAST), northAndSouth);
        Axis horizontal = Axis.of(count(sides, LogisimFacing.NORTH), count(sides, LogisimFacing.SOUTH), westAndEast);

        List<Shape> shapes = new ArrayList<>();
        int middle = horizontal.start() + horizontal.length() / 2;
        int notchTop = vertical.start() + NOTCH_TOP;
        shapes.add(new Curve(new LogisimPoint(middle - NOTCH_HALF_WIDTH, notchTop),
                new LogisimPoint(middle + NOTCH_HALF_WIDTH, notchTop), new LogisimPoint(middle, notchTop + NOTCH_PULL),
                NOTCH));
        shapes.add(new Rectangle(horizontal.start(), vertical.start(), horizontal.length(), vertical.length(), 0, BOX));
        Map<LogisimFacing, List<LogisimPoint>> places = new EnumMap<>(LogisimFacing.class);
        for (LogisimFacing side : PORT_ORDER) {
            List<LogisimPoint> sidePlaces = places(side, sides.get(side).size(), vertical, horizontal);
            places.put(side, sidePlaces);
            for (int index = 0; index < sidePlaces.size(); index++) {
                shapes.add(port(sides.get(side).get(index), sidePlaces.get(index)));
            }
        }
        LogisimPoint anchor = anchor(places, new LogisimPoint(horizontal.start(), vertical.start()));
        shapes.add(new Anchor(anchor.x() - ANCHOR_SIZE / 2, anchor.y() - ANCHOR_SIZE / 2, ANCHOR_SIZE, ANCHOR_SIZE,
                "east"));
        return new LogisimAppearance(shapes);
    }

    /**
     * One axis of the box: where it starts, how long it is and where the first pin of each of its two sides is (the
     * west and east sides for the vertical axis, the north and south ones for the horizontal axis).
     */
    private record Axis(int start, int length, int firstOfOneSide, int firstOfOtherSide) {

        static Axis of(int oneSide, int otherSide, int mostAcross) {
            int most = Math.max(oneSide, otherSide);
            int length = Math.max(SHORTEST_SIDE, (mostAcross == 0 ? most : most + 1) * STEP);
            int shift = mostAcross == 0 && most > 0 ? Math.floorMod(centred(length, most), STEP) : 0;
            return new Axis(START + shift, length, START + onTheGrid(shift + centred(length, oneSide)),
                    START + onTheGrid(shift + centred(length, otherSide)));
        }

        /** How far from the start of a side {@code length} long its first of {@code pins} pins is when centred. */
        private static int centred(int length, int pins) {
            return (length - Math.max(0, pins - 1) * STEP) / 2;
        }

        private static int onTheGrid(int offset) {
            return Math.floorDiv(offset, STEP) * STEP;
        }
    }

    /** The circuit's pins by the side of the box they sit on, each side ordered along itself. */
    private static Map<LogisimFacing, List<LogisimInterfacePin>> bySide(List<LogisimInterfacePin> pins) {
        Map<LogisimFacing, List<LogisimInterfacePin>> sides = new EnumMap<>(LogisimFacing.class);
        for (LogisimFacing side : LogisimFacing.values()) {
            sides.put(side, new ArrayList<>());
        }
        for (LogisimInterfacePin pin : pins) {
            sides.get(facing(pin).opposite()).add(pin);
        }
        Comparator<LogisimInterfacePin> downwards = Comparator.comparingInt((LogisimInterfacePin pin) ->
                pin.location().y()).thenComparingInt(pin -> pin.location().x());
        Comparator<LogisimInterfacePin> rightwards = Comparator.comparingInt((LogisimInterfacePin pin) ->
                pin.location().x()).thenComparingInt(pin -> pin.location().y());
        sides.forEach((side, sidePins) -> sidePins.sort(side == LogisimFacing.WEST || side == LogisimFacing.EAST
                ? downwards : rightwards));
        return sides;
    }

    /** A pin's facing; one Logisim would not write is taken as east, a pin's default. */
    private static LogisimFacing facing(LogisimInterfacePin pin) {
        try {
            return LogisimFacing.parse(pin.facing());
        } catch (IllegalArgumentException exception) {
            return LogisimFacing.EAST;
        }
    }

    private static int count(Map<LogisimFacing, List<LogisimInterfacePin>> sides, LogisimFacing side) {
        return sides.get(side).size();
    }

    /** Where the {@code pins} pins of {@code side} connect, in order along it. */
    private static List<LogisimPoint> places(LogisimFacing side, int pins, Axis vertical, Axis horizontal) {
        List<LogisimPoint> places = new ArrayList<>();
        for (int index = 0; index < pins; index++) {
            int along = index * STEP;
            places.add(switch (side) {
                case WEST -> new LogisimPoint(horizontal.start(), vertical.firstOfOneSide() + along);
                case EAST -> new LogisimPoint(horizontal.start() + horizontal.length(),
                        vertical.firstOfOtherSide() + along);
                case NORTH -> new LogisimPoint(horizontal.firstOfOneSide() + along, vertical.start());
                case SOUTH -> new LogisimPoint(horizontal.firstOfOtherSide() + along,
                        vertical.start() + vertical.length());
            });
        }
        return places;
    }

    /** A port box centred on {@code place}: 8 px for an input, 10 px for an output. */
    private static Port port(LogisimInterfacePin pin, LogisimPoint place) {
        int size = pin.output() ? OUTPUT_PORT_SIZE : INPUT_PORT_SIZE;
        return new Port(pin.location(), place.x() - size / 2, place.y() - size / 2, size, size);
    }

    private static LogisimPoint anchor(Map<LogisimFacing, List<LogisimPoint>> places, LogisimPoint corner) {
        for (LogisimFacing side : ANCHOR_ORDER) {
            if (!places.get(side).isEmpty()) {
                return places.get(side).getFirst();
            }
        }
        return corner;
    }
}
