package cl.almejo.vsim.ui.window;

/**
 * When a window shows its first zoom and center (M6-B). It is applied when the window opens; a window restored
 * maximized usually gets its maximized size only afterwards, and the center was computed for the smaller canvas, so
 * the view is applied once more at the canvas's first resize after opening — unless the user changed the view first
 * or the window is no longer maximized by then. EDT only.
 */
final class InitialView {

    private final Runnable apply;
    private boolean waitingForMaximizedLayout;
    private boolean applying;

    /** @param apply shows the initial view on the canvas (the restored state, or the circuit centered) */
    InitialView(Runnable apply) {
        this.apply = apply;
    }

    /** The window opened; {@code maximized} when it is (to be) maximized. */
    void opened(boolean maximized) {
        run();
        waitingForMaximizedLayout = maximized;
    }

    /** The canvas changed size; {@code maximized} when the window is maximized now. */
    void canvasResized(boolean maximized) {
        if (waitingForMaximizedLayout) {
            waitingForMaximizedLayout = false;
            if (maximized) {
                run();
            }
        }
    }

    /** The canvas's view changed: unless this class is applying it, the user moved it, and it is kept. */
    void viewChanged() {
        if (!applying) {
            waitingForMaximizedLayout = false;
        }
    }

    private void run() {
        applying = true;
        try {
            apply.run();
        } finally {
            applying = false;
        }
    }
}
