package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.ui.tools.CanvasCursor;

/**
 * The part of a frame under the pointer. The edges and corners exist only on unlocked frames; LOCK and MENU are the
 * controls at the right end of the title bar.
 */
public enum FramePart {
    TITLE_BAR(CanvasCursor.MOVE, false, false, false, false),
    LOCK(CanvasCursor.HAND, false, false, false, false),
    MENU(CanvasCursor.HAND, false, false, false, false),
    EDGE_NORTH(CanvasCursor.RESIZE_NORTH, true, false, false, false),
    EDGE_SOUTH(CanvasCursor.RESIZE_SOUTH, false, true, false, false),
    EDGE_EAST(CanvasCursor.RESIZE_EAST, false, false, true, false),
    EDGE_WEST(CanvasCursor.RESIZE_WEST, false, false, false, true),
    CORNER_NORTH_EAST(CanvasCursor.RESIZE_NORTH_EAST, true, false, true, false),
    CORNER_NORTH_WEST(CanvasCursor.RESIZE_NORTH_WEST, true, false, false, true),
    CORNER_SOUTH_EAST(CanvasCursor.RESIZE_SOUTH_EAST, false, true, true, false),
    CORNER_SOUTH_WEST(CanvasCursor.RESIZE_SOUTH_WEST, false, true, false, true);

    private final CanvasCursor cursor;
    private final boolean movesNorth;
    private final boolean movesSouth;
    private final boolean movesEast;
    private final boolean movesWest;

    FramePart(CanvasCursor cursor, boolean movesNorth, boolean movesSouth, boolean movesEast, boolean movesWest) {
        this.cursor = cursor;
        this.movesNorth = movesNorth;
        this.movesSouth = movesSouth;
        this.movesEast = movesEast;
        this.movesWest = movesWest;
    }

    /** The cursor over this part (spec §7). */
    public CanvasCursor cursor() {
        return cursor;
    }

    /** True for the edges and corners: dragging them resizes the frame. */
    public boolean resizes() {
        return movesNorth || movesSouth || movesEast || movesWest;
    }

    boolean movesNorth() {
        return movesNorth;
    }

    boolean movesSouth() {
        return movesSouth;
    }

    boolean movesEast() {
        return movesEast;
    }

    boolean movesWest() {
        return movesWest;
    }
}
