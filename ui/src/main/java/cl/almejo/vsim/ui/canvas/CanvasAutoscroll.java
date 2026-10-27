package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.tools.SelectTool;

import javax.swing.Timer;
import java.awt.Point;
import java.awt.event.MouseEvent;

/** Scrolls a canvas's view while a move or a rubber band is held at its edge, replaying the last drag at each step. */
final class CanvasAutoscroll {

    private static final int AUTOSCROLL_MILLISECONDS = 30;

    private final CircuitCanvas canvas;
    private final Timer autoscrollTimer;
    private MouseEvent autoscrollEvent;
    private boolean automaticAutoscrollClock = true;

    CanvasAutoscroll(CircuitCanvas canvas) {
        this.canvas = canvas;
        autoscrollTimer = new Timer(AUTOSCROLL_MILLISECONDS, event -> autoscrollTick());
    }

    /** True while a move or a rubber band is held at the edge and the view scrolls under it. */
    boolean isAutoscrolling() {
        return autoscrollEvent != null;
    }

    /** Tests call {@link #autoscrollTick()} themselves instead of the Swing timer, so positions are exact. */
    void useManualAutoscrollClock() {
        automaticAutoscrollClock = false;
        autoscrollTimer.stop();
    }

    /** One autoscroll step: scrolls the view and replays the last drag, so the move or the band follows the pointer. */
    void autoscrollTick() {
        if (autoscrollEvent == null || !(canvas.tool() instanceof SelectTool select) || !select.autoscrolls()) {
            stopAutoscroll();
            return;
        }
        Point step = EdgeAutoscroll.step(canvas.getWidth(), canvas.getHeight(), autoscrollEvent.getPoint());
        if (step.x == 0 && step.y == 0) {
            stopAutoscroll();
            return;
        }
        canvas.viewport().panBy(step.x, step.y);
        canvas.tool().mouseDragged(canvas, canvas.convert(autoscrollEvent));
        canvas.repaint();
    }

    /** Starts or stops the autoscroll after a drag to {@code event}. */
    void followEdge(MouseEvent event) {
        Point step = EdgeAutoscroll.step(canvas.getWidth(), canvas.getHeight(), event.getPoint());
        boolean wanted = canvas.tool() instanceof SelectTool select && select.autoscrolls()
                && (step.x != 0 || step.y != 0);
        if (!wanted) {
            stopAutoscroll();
            return;
        }
        autoscrollEvent = event;
        if (automaticAutoscrollClock && !autoscrollTimer.isRunning()) {
            autoscrollTimer.start();
        }
    }

    void stopAutoscroll() {
        autoscrollEvent = null;
        autoscrollTimer.stop();
    }
}
