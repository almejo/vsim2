package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import java.util.ArrayList;
import java.util.List;

/**
 * The sheets on one pane of a window (split views): its {@link SheetOverlay} and its {@link SheetSummaryHandle},
 * there only while the sheets are shown, so a hidden grid costs the pane nothing. The picture they draw and the
 * toggle belong to {@link SheetsFeatures}, shared by every pane.
 */
final class PaneSheets {

    private final CircuitCanvas pane;
    private final SheetOverlay overlay;
    private final SheetSummaryHandle summary;
    private final List<Runnable> removers = new ArrayList<>();

    PaneSheets(SheetsFeatures features, CircuitCanvas pane) {
        this.pane = pane;
        this.overlay = new SheetOverlay(features, pane);
        this.summary = new SheetSummaryHandle(features, pane);
    }

    /** Puts the overlay and the label on the pane ({@code shown}) or takes them away. */
    void show(boolean shown) {
        if (shown && removers.isEmpty()) {
            pane.addOverlay(overlay);
            removers.add(() -> pane.removeOverlay(overlay));
            removers.add(pane.addEdgeHandle(summary));
        } else if (!shown && !removers.isEmpty()) {
            removers.forEach(Runnable::run);
            removers.clear();
        }
    }

    /** The pane goes away, or the sheets do. */
    void dispose() {
        show(false);
    }
}
