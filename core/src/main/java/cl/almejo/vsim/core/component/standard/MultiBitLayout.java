package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The compact rows of the multi-bit Switch and LED: one cell per bit, most significant bit on the left, a small gap
 * every four bits and rows of eight from nine bits on (the first row on top). The Switch and the LED use the same
 * function, so the same width gives the same box and their bits line up when one is placed under the other.
 *
 * <p>The <em>body</em> is the box of the rows; the component also has a short lead from the middle of one side to its
 * one bus pin ({@code side}: {@code right}, {@code bottom}, {@code left} or {@code top}), so the shape of the
 * component is the body plus that lead. Pixels are relative to the body's top-left corner, at {@link #UNIT} pixels per
 * grid unit; the body is always upright whatever the side.
 */
public final class MultiBitLayout {

    public static final String RIGHT = "right";
    public static final String BOTTOM = "bottom";
    public static final String LEFT = "left";
    public static final String TOP = "top";
    /** The sides in clockwise order: a quarter turn moves the pin to the next one. */
    public static final List<String> SIDES = List.of(RIGHT, BOTTOM, LEFT, TOP);

    /** The name of the hidden parameter that holds the side of the pin. */
    public static final String SIDE = "side";

    public static final int MINIMUM_WIDTH = 1;
    public static final int MAXIMUM_WIDTH = ConstantValue.MAXIMUM_WIDTH;

    /** Pixels per grid unit ({@link GridPoint#PIXELS_PER_UNIT}). */
    public static final int UNIT = GridPoint.PIXELS_PER_UNIT;
    /** The gap, in pixels, between groups of four bits. */
    public static final int GAP = 4;
    /** The length of the lead from the body to the pin, in grid units. */
    public static final int LEAD_UNITS = 2;

    private static final int BITS_PER_ROW = 8;
    private static final int BITS_PER_GROUP = 4;
    private static final int ROW_HEIGHT = 14;
    private static final int ROW_GAP = 4;
    private static final int BODY_PADDING = 6;
    private static final int VERTICAL_PADDING = 4;

    private MultiBitLayout() {
    }

    /** One bit's cell, in pixels from the body's corner: {@code bit} is the bit's index (0 = least significant). */
    public record BitCell(int bit, double x, double y, double width, double height) {

        public boolean contains(double pixelX, double pixelY) {
            return pixelX >= x && pixelX < x + width && pixelY >= y && pixelY < y + height;
        }
    }

    /** The hidden «side» parameter every multi-bit part carries; {@code defaultSide} is where its pin starts. */
    public static ParameterSpec sideParameter(String defaultSide) {
        return ParameterSpec.choice(SIDE, defaultSide, SIDES).asHidden();
    }

    /** Whether {@code type} is a Switch or LED wider than one bit: the ones drawn as rows of bits. */
    public static boolean isMultiBit(ComponentType type) {
        return defaultSideOf(type.id()).isPresent() && type.pinCount() == 1 && type.pin(0).width() > 1;
    }

    /** The width in bits of a multi-bit part (see {@link #isMultiBit}). */
    public static int widthOf(ComponentType type) {
        return type.pin(0).width();
    }

    /** The side of the pin of a part of {@code type} with {@code parameters}: the «side» one or the type's default. */
    public static String sideOf(ComponentType type, Map<String, Object> parameters) {
        return String.valueOf(type.resolveParameters(parameters).get(SIDE));
    }

    /** Where the pin of a part of {@code typeId} starts: right for the Switch, left for the LED; empty otherwise. */
    public static Optional<String> defaultSideOf(String typeId) {
        return switch (typeId) {
            case StandardComponentTypes.SWITCH -> Optional.of(RIGHT);
            case StandardComponentTypes.LED -> Optional.of(LEFT);
            default -> Optional.empty();
        };
    }

    /**
     * The parameters of a Switch or LED as a file keeps them: «width» when it is 1 and «side» when it is the
     * default are left out, so the one-bit parts written before the multi-bit ones existed keep their bytes (reading
     * fills the defaults back in). Any other type's parameters come back unchanged.
     */
    public static Map<String, Object> withoutDefaults(String typeId, Map<String, Object> parameters) {
        Optional<String> defaultSide = defaultSideOf(typeId);
        if (defaultSide.isEmpty()) {
            return parameters;
        }
        Map<String, Object> written = new TreeMap<>(parameters);
        if (Integer.valueOf(1).equals(written.get(StandardComponentTypes.WIDTH))) {
            written.remove(StandardComponentTypes.WIDTH);
        }
        if (defaultSide.get().equals(written.get(SIDE))) {
            written.remove(SIDE);
        }
        return written;
    }

    /** The body of a part {@code width} bits wide, in grid units; the same for the Switch and the LED. */
    public static GridSize boxFor(int width) {
        checkWidth(width);
        int perRow = Math.min(width, BITS_PER_ROW);
        int rows = rowsFor(width);
        int widthPixels = perRow * UNIT + (groupsIn(perRow) - 1) * GAP + BODY_PADDING;
        int heightPixels = rows * ROW_HEIGHT + (rows - 1) * ROW_GAP + VERTICAL_PADDING;
        return new GridSize(unitsFor(widthPixels), unitsFor(heightPixels));
    }

    /** The cells of every bit, most significant first (the order they are read: left to right, top to bottom). */
    public static List<BitCell> cells(int width) {
        GridSize box = boxFor(width);
        int perRow = Math.min(width, BITS_PER_ROW);
        double inner = perRow * UNIT + (groupsIn(perRow) - 1) * GAP;
        double left = (box.width() * UNIT - inner) / 2;
        double contentHeight = rowsFor(width) * ROW_HEIGHT + (rowsFor(width) - 1) * ROW_GAP;
        double top = (box.height() * UNIT - contentHeight) / 2;
        List<BitCell> cells = new ArrayList<>();
        for (int position = 0; position < width; position++) {
            int row = position / BITS_PER_ROW;
            int column = position % BITS_PER_ROW;
            double x = left + column * UNIT + (column / BITS_PER_GROUP) * GAP;
            double y = top + row * (ROW_HEIGHT + ROW_GAP);
            cells.add(new BitCell(width - 1 - position, x, y, UNIT, ROW_HEIGHT));
        }
        return List.copyOf(cells);
    }

    /** The cell under a point given in pixels from the body's corner, if any. */
    public static Optional<BitCell> cellAt(int width, double pixelX, double pixelY) {
        return cells(width).stream().filter(cell -> cell.contains(pixelX, pixelY)).findFirst();
    }

    /** Where the body's corner is inside the component: the lead takes room on the left or on the top edge. */
    public static GridPoint bodyOrigin(int width, String side) {
        checkWidth(width);
        return switch (checkSide(side)) {
            case LEFT -> new GridPoint(LEAD_UNITS, 0);
            case TOP -> new GridPoint(0, LEAD_UNITS);
            default -> GridPoint.ORIGIN;
        };
    }

    /** The size of the component: the body plus the lead on the side of the pin. */
    public static GridSize sizeFor(int width, String side) {
        GridSize box = boxFor(width);
        return switch (checkSide(side)) {
            case LEFT, RIGHT -> new GridSize(box.width() + LEAD_UNITS, box.height());
            default -> new GridSize(box.width(), box.height() + LEAD_UNITS);
        };
    }

    /** The bus pin, at the end of the lead, in grid units from the component's corner. */
    public static GridPoint pinFor(int width, String side) {
        GridSize box = boxFor(width);
        int middleX = Math.round(box.width() / 2f);
        int middleY = Math.round(box.height() / 2f);
        return switch (checkSide(side)) {
            case RIGHT -> new GridPoint(box.width() + LEAD_UNITS, middleY);
            case LEFT -> new GridPoint(0, middleY);
            case BOTTOM -> new GridPoint(middleX, box.height() + LEAD_UNITS);
            default -> new GridPoint(middleX, 0);
        };
    }

    /** The size and the one bus pin of a part {@code width} bits wide with its pin on {@code side}. */
    public static ComponentShape shape(int width, String side, String pinName, PinDirection direction) {
        GridPoint pin = pinFor(width, side);
        return new ComponentShape(sizeFor(width, side),
                List.of(new PinSpec(pinName, pin, direction, width)));
    }

    private static int rowsFor(int width) {
        return (width + BITS_PER_ROW - 1) / BITS_PER_ROW;
    }

    private static int groupsIn(int bitsInRow) {
        return (bitsInRow + BITS_PER_GROUP - 1) / BITS_PER_GROUP;
    }

    private static int unitsFor(int pixels) {
        return (pixels + UNIT - 1) / UNIT;
    }

    private static void checkWidth(int width) {
        if (width < MINIMUM_WIDTH || width > MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("width must be between " + MINIMUM_WIDTH + " and " + MAXIMUM_WIDTH
                    + ": " + width);
        }
    }

    private static String checkSide(String side) {
        if (!SIDES.contains(side)) {
            throw unknownSide(side);
        }
        return side;
    }

    private static IllegalArgumentException unknownSide(String side) {
        return new IllegalArgumentException("side must be one of " + SIDES + ": " + side);
    }
}
