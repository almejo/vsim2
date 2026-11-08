package cl.almejo.vsim.ui;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Clicks and pointer moves delivered straight to a canvas's listeners (headless), at points of its own: what Swing
 * sends for a click of the primary button or a move with no button held.
 */
public final class CanvasClicks {

    private CanvasClicks() {
    }

    /** A click (press and release) of the primary button at ({@code x}, {@code y}). */
    public static void click(CircuitCanvas canvas, int x, int y) {
        click(canvas, x, y, 1);
    }

    /** The two clicks of a double click at ({@code x}, {@code y}). */
    public static void doubleClick(CircuitCanvas canvas, int x, int y) {
        click(canvas, x, y, 1);
        click(canvas, x, y, 2);
    }

    private static void click(CircuitCanvas canvas, int x, int y, int clickCount) {
        MouseEvent pressed = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, x, y,
                clickCount, false, MouseEvent.BUTTON1);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mousePressed(pressed);
        }
        MouseEvent released = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, clickCount, false,
                MouseEvent.BUTTON1);
        for (MouseListener listener : canvas.getMouseListeners()) {
            listener.mouseReleased(released);
        }
    }

    /** The pointer moves to ({@code x}, {@code y}) with no button held. */
    public static void move(CircuitCanvas canvas, int x, int y) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false, MouseEvent.NOBUTTON);
        for (MouseMotionListener listener : canvas.getMouseMotionListeners()) {
            listener.mouseMoved(event);
        }
    }
}
