package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;

/**
 * Where the view goes while the AI presents (spec §5, §6): to a stage's origin when the stage starts and to each
 * step's change, only when no pane of the window shows it already; then the active pane travels there
 * ({@link CircuitCanvas#travelTo}: smooth, or a jump when the user asked for reduced motion). EDT only.
 */
final class PresentationTravel {

    /** The room a stage starts with to the right of and below its origin, in grid units. */
    static final int STAGE_WIDTH = 30;
    static final int STAGE_HEIGHT = 20;

    private final EditorView view;

    PresentationTravel(EditorView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    /** The area a stage starting at {@code origin} first builds in: the origin at its top left. */
    static Bounds stageArea(GridPoint origin) {
        return new Bounds(origin.x(), origin.y(), STAGE_WIDTH, STAGE_HEIGHT);
    }

    void toStageOrigin(GridPoint origin) {
        to(stageArea(Objects.requireNonNull(origin, "origin")));
    }

    void toStep(Bounds changed) {
        to(Objects.requireNonNull(changed, "changed"));
    }

    /** Travels the active pane to {@code bounds} unless some pane of the window shows all of it. */
    private void to(Bounds bounds) {
        if (view.panes().stream().anyMatch(pane -> pane.shows(bounds))) {
            return;
        }
        view.canvas().travelTo(bounds);
    }
}
