package cl.almejo.vsim.ui.canvas;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * How the wheel and the touchpad move a view, the same on every canvas (the circuit's and a machine's diagram):
 * Ctrl+wheel zooms at the pointer, Mayús+wheel scrolls sideways, the wheel alone scrolls up and down, and a
 * touchpad's sideways swipe, which reaches Swing on Linux as presses of extra mouse buttons (X11 buttons 6 and 7) and
 * not as a wheel turn, scrolls sideways one Mayús+wheel step per press.
 */
public final class WheelNavigation {

    /** One wheel step pans this far, in screen pixels. */
    public static final double SCROLL_PIXELS = 40;
    /** The extra mouse buttons a sideways swipe presses: they scroll like Mayús+wheel up and down. */
    static final int SCROLL_LEFT_BUTTON = 4;
    static final int SCROLL_RIGHT_BUTTON = 5;

    /** What a wheel turn did to the view. */
    public enum Move { ZOOMED, PANNED }

    private WheelNavigation() {
    }

    /** Moves {@code viewport} for the wheel turn {@code event}; says whether it zoomed or panned. */
    public static Move wheel(Viewport viewport, MouseWheelEvent event) {
        double rotation = event.getPreciseWheelRotation();
        if (event.isControlDown()) {
            viewport.zoomAt(event.getX(), event.getY(), viewport.zoom() * Math.pow(Viewport.ZOOM_STEP, -rotation));
            return Move.ZOOMED;
        }
        if (event.isShiftDown()) {
            viewport.panBy(-rotation * SCROLL_PIXELS, 0);
        } else {
            viewport.panBy(0, -rotation * SCROLL_PIXELS);
        }
        return Move.PANNED;
    }

    /** Whether {@code event} is of a button past the third (sideways scrolling, back/forward): never a click. */
    public static boolean isExtraButton(MouseEvent event) {
        return event.getButton() > MouseEvent.BUTTON3;
    }

    /**
     * One sideways step of {@code viewport} for a press of extra button {@code button}, as far as one Mayús+wheel
     * step: left like wheel up, right like wheel down. Returns whether the view moved (other buttons do nothing).
     */
    public static boolean scrollSideways(Viewport viewport, int button) {
        if (button == SCROLL_LEFT_BUTTON) {
            viewport.panBy(SCROLL_PIXELS, 0);
        } else if (button == SCROLL_RIGHT_BUTTON) {
            viewport.panBy(-SCROLL_PIXELS, 0);
        } else {
            return false;
        }
        return true;
    }
}
