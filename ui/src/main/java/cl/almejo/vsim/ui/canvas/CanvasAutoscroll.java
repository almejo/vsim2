package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.tools.SelectTool;

import javax.swing.Timer;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * Scrolls a canvas's view while a move or a rubber band is held at its edge, replaying the last drag at each step; and
 * while the pointer only hovers near the edge with a tool drawing between clicks (a continuous wire's path), whose
 * preview the canvas redoes after each step as for any view move under the pointer.
 */
final class CanvasAutoscroll {

    private static final int AUTOSCROLL_MILLISECONDS = 30;

    private final CircuitCanvas canvas;
    private final Timer autoscrollTimer;
    private MouseEvent autoscrollEvent;
    /** The scroll follows a hover (no button down) rather than a drag. */
    private boolean hovering;
    private boolean automaticAutoscrollClock = true;

    CanvasAutoscroll(CircuitCanvas canvas) {
        this.canvas = canvas;
        autoscrollTimer = new Timer(AUTOSCROLL_MILLISECONDS, event -> autoscrollTick());
    }

    /** True while a move, a rubber band or a path being drawn is held at the edge and the view scrolls under it. */
    boolean isAutoscrolling() {
        return autoscrollEvent != null;
    }

    /** Tests call {@link #autoscrollTick()} themselves instead of the Swing timer, so positions are exact. */
    void useManualAutoscrollClock() {
        automaticAutoscrollClock = false;
        autoscrollTimer.stop();
    }

    /**
     * One autoscroll step: scrolls the view and, for a drag, replays it, so the move or the band follows the pointer
     * (a hover's preview follows through the canvas's view listener).
     */
    void autoscrollTick() {
        if (autoscrollEvent == null || !stillWanted()) {
            stopAutoscroll();
            return;
        }
        Point step = EdgeAutoscroll.step(canvas.getWidth(), canvas.getHeight(), autoscrollEvent.getPoint());
        if (step.x == 0 && step.y == 0) {
            stopAutoscroll();
            return;
        }
        canvas.viewport().panBy(step.x, step.y);
        if (!hovering) {
            canvas.tool().mouseDragged(canvas, canvas.convert(autoscrollEvent));
        }
        canvas.repaint();
    }

    private boolean stillWanted() {
        return hovering ? canvas.pointerHoversWhileDrawing() : selectToolAutoscrolls();
    }

    private boolean selectToolAutoscrolls() {
        return canvas.tool() instanceof SelectTool select && select.autoscrolls();
    }

    /** Starts or stops the autoscroll after a drag to {@code event}. */
    void followEdge(MouseEvent event) {
        follow(event, selectToolAutoscrolls(), false);
    }

    /** Starts or stops the autoscroll after the pointer hovered to {@code event} (no button down). */
    void followHover(MouseEvent event) {
        follow(event, canvas.pointerHoversWhileDrawing(), true);
    }

    private void follow(MouseEvent event, boolean toolWantsIt, boolean byHover) {
        Point step = EdgeAutoscroll.step(canvas.getWidth(), canvas.getHeight(), event.getPoint());
        if (!toolWantsIt || (step.x == 0 && step.y == 0)) {
            stopAutoscroll();
            return;
        }
        autoscrollEvent = event;
        hovering = byHover;
        if (automaticAutoscrollClock && !autoscrollTimer.isRunning()) {
            autoscrollTimer.start();
        }
    }

    /** Stops a scroll that followed a hover (the pointer left, a button went down); a drag's goes on. */
    void stopHoverAutoscroll() {
        if (hovering) {
            stopAutoscroll();
        }
    }

    void stopAutoscroll() {
        autoscrollEvent = null;
        hovering = false;
        autoscrollTimer.stop();
    }
}
