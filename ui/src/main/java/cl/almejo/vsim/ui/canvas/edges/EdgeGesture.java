package cl.almejo.vsim.ui.canvas.edges;

import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Graphics2D;

/** A gesture started on an {@link EdgeHandle}: it sees the drag and the release, or is cancelled. */
public interface EdgeGesture {

    void drag(CanvasMouseEvent event);

    void release(CanvasMouseEvent event);

    void cancel();

    /** Paints the gesture's preview in screen coordinates. */
    default void paint(Graphics2D screenGraphics) {
    }
}
