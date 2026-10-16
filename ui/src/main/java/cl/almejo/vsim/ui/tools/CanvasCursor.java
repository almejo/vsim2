package cl.almejo.vsim.ui.tools;

/**
 * What a press on the canvas would do here, as a cursor (spec §5.3): DEFAULT select or rubber band, MOVE move the
 * selection or pan, CROSSHAIR start a wire or place a component, HAND toggle an interactive component or press a
 * control. The RESIZE_* cursors grab an edge or a corner of an unlocked frame (M6.3), named by the side that moves.
 * SLIDE_VERTICAL and SLIDE_HORIZONTAL drag a selected wire sideways with the segment key (M6.7 §8): a horizontal wire
 * slides vertically, a vertical one horizontally.
 */
public enum CanvasCursor {
    DEFAULT, MOVE, CROSSHAIR, HAND,
    RESIZE_NORTH, RESIZE_SOUTH, RESIZE_EAST, RESIZE_WEST,
    RESIZE_NORTH_EAST, RESIZE_NORTH_WEST, RESIZE_SOUTH_EAST, RESIZE_SOUTH_WEST,
    SLIDE_VERTICAL, SLIDE_HORIZONTAL
}
