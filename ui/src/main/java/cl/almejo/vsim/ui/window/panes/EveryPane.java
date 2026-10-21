package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.function.Consumer;

/**
 * The registrations most extensions make on the panes of a window, through {@link EditorView#forEachPane}: so panes
 * created later get them too and a removed pane gives them back. Every returned {@link Runnable} undoes the
 * registration on every pane. EDT only.
 */
public final class EveryPane {

    private EveryPane() {
    }

    /** Paints {@code overlay} in every pane of {@code view} (one overlay object: it paints from the frame's state). */
    public static Runnable addOverlay(EditorView view, CanvasOverlay overlay) {
        return view.forEachPane(pane -> {
            pane.addOverlay(overlay);
            return () -> pane.removeOverlay(overlay);
        });
    }

    /**
     * Gives {@code listener} the snapshots the active pane shows (every pane shows the same simulation, so one is
     * enough), whichever pane is active at the time.
     */
    public static Runnable addActivePaneSnapshotListener(EditorView view, Consumer<SimulationSnapshot> listener) {
        return view.forEachPane(pane -> {
            Consumer<SimulationSnapshot> fromThisPane = snapshot -> {
                if (pane == view.canvas()) {
                    listener.accept(snapshot);
                }
            };
            pane.addSnapshotListener(fromThisPane);
            return () -> pane.removeSnapshotListener(fromThisPane);
        });
    }

    /** A repaint of every pane of {@code view} there is when it runs (for an overlay painted in all of them). */
    public static Runnable repaint(EditorView view) {
        return () -> view.panes().forEach(CircuitCanvas::repaint);
    }
}
