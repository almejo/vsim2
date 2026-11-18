package cl.almejo.vsim.ui.tools;

import java.awt.geom.Rectangle2D;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * How Shift and Ctrl change a selection: the one rule both the circuit's {@link SelectTool} and a machine's diagram
 * follow, so they cannot drift apart (hand test of the machines, 2026-11-17). Ctrl is the Control key on every
 * platform, macOS included, as the circuit has always read it ({@code isControlDown}).
 * <ul>
 * <li>Shift+click on an element toggles it; Shift+drag from it moves the selection with it (adding it first);</li>
 * <li>Ctrl+click (with or without Shift) toggles it and nothing else;</li>
 * <li>a drag on empty space is a band: plain it replaces the selection, with Shift or Ctrl it adds to it, with both it
 * takes out of it;</li>
 * <li>with a key held, the cursor is the arrow with a «+» where a press adds, with a «−» where it takes away.</li>
 * </ul>
 */
public final class SelectionModifiers {

    /** What a primary press on an element does to the selection. */
    public enum ElementPress {
        /** No selection key: the tool's own press (select, move, draw…). */
        PLAIN,
        /** Shift: the click toggles the element; a drag moves the selection with it. */
        TOGGLE_OR_DRAG,
        /** Ctrl (with or without Shift): the click toggles the element. */
        TOGGLE
    }

    /** What a band on empty space does with what it encloses. */
    public enum BandMode { REPLACE, ADD, REMOVE }

    private SelectionModifiers() {
    }

    /** Whether a press with these keys changes the selection instead of doing the tool's plain press. */
    public static boolean changesSelection(boolean shiftDown, boolean controlDown) {
        return shiftDown || controlDown;
    }

    /** What a press on an element does with these keys. */
    public static ElementPress elementPress(boolean shiftDown, boolean controlDown) {
        if (controlDown) {
            return ElementPress.TOGGLE;
        }
        return shiftDown ? ElementPress.TOGGLE_OR_DRAG : ElementPress.PLAIN;
    }

    /** What a band started with these keys does. */
    public static BandMode bandMode(boolean shiftDown, boolean controlDown) {
        if (shiftDown && controlDown) {
            return BandMode.REMOVE;
        }
        return changesSelection(shiftDown, controlDown) ? BandMode.ADD : BandMode.REPLACE;
    }

    /** The selection a band in {@code mode} leaves: {@code inside} replaces, joins or leaves {@code current}. */
    public static <T> Set<T> combine(BandMode mode, Set<T> current, Set<T> inside) {
        Set<T> result = new LinkedHashSet<>(mode == BandMode.REPLACE ? Set.of() : current);
        if (mode == BandMode.REMOVE) {
            result.removeAll(inside);
        } else {
            result.addAll(inside);
        }
        return result;
    }

    /**
     * Whether a band takes what lies in {@code bounds}: it encloses them, edges included. Not
     * {@link Rectangle2D#contains(Rectangle2D)}, which never contains a rectangle with no width or height, as the bounds
     * of a straight horizontal arrow are (hand test of the machines, 2026-11-17: such an arrow was never taken).
     */
    public static boolean encloses(Rectangle2D band, Rectangle2D bounds) {
        return bounds.getMinX() >= band.getMinX() && bounds.getMaxX() <= band.getMaxX()
                && bounds.getMinY() >= band.getMinY() && bounds.getMaxY() <= band.getMaxY();
    }

    /**
     * The cursor with a selection key held: over an element ({@code targetSelected} says whether it is selected) a
     * press toggles it, so «−» when it is selected and «+» when not; over empty space the band's cursor.
     */
    public static CanvasCursor hoverCursor(boolean shiftDown, boolean controlDown, Optional<Boolean> targetSelected) {
        if (targetSelected.isEmpty()) {
            return bandCursor(bandMode(shiftDown, controlDown));
        }
        return targetSelected.get() ? CanvasCursor.REMOVE_FROM_SELECTION : CanvasCursor.ADD_TO_SELECTION;
    }

    /** The cursor while a band in {@code mode} is dragged: the plain arrow, or the arrow with a «+» or a «−». */
    public static CanvasCursor bandCursor(BandMode mode) {
        return switch (mode) {
            case REPLACE -> CanvasCursor.DEFAULT;
            case ADD -> CanvasCursor.ADD_TO_SELECTION;
            case REMOVE -> CanvasCursor.REMOVE_FROM_SELECTION;
        };
    }
}
