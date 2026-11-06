package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.tools.EnterableInstances;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * When the magnifier shows (spec §8.2): while the pointer is over a user-component instance and the toggle is on, Alt
 * is held, or the instance menu asked for it. It goes when the pointer leaves the instance or the canvas, on a press
 * or a drag, on Escape (and stays away until the pointer has left that instance), and when none of the three reasons
 * holds any more. Pure state over a {@link MagnifierPresenter}: EDT only.
 */
final class MagnifierHover {

    private final BooleanSupplier toggledOn;
    private final MagnifierPresenter presenter;
    private boolean altHeld;
    private Optional<ElementId> shown = Optional.empty();
    /** The instance the user dismissed (Escape, a press): not shown again until the pointer has left it. */
    private Optional<ElementId> dismissed = Optional.empty();
    /** The instance the menu item asked for: shown even with the toggle off, until the pointer leaves it. */
    private Optional<ElementId> requested = Optional.empty();
    private CircuitCanvas lastPane;
    private Point2D lastPoint;

    MagnifierHover(BooleanSupplier toggledOn, MagnifierPresenter presenter) {
        this.toggledOn = toggledOn;
        this.presenter = presenter;
    }

    /** The user-component instance under {@code screenPoint} (pixels of {@code pane}), if any. */
    static Optional<Component> instanceAt(CircuitCanvas pane, Point2D screenPoint) {
        Point2D gridPoint = pane.viewport().screenToGrid(screenPoint.getX(), screenPoint.getY());
        Optional<DocumentElement> hit = pane.hitTester().elementAt(pane.openDocument().document(), gridPoint);
        return hit.filter(Component.class::isInstance).map(Component.class::cast)
                .filter(component -> EnterableInstances.definitionOf(pane.openDocument(), component).isPresent());
    }

    void pointerMoved(CircuitCanvas pane, Point2D screenPoint) {
        lastPane = pane;
        lastPoint = screenPoint;
        Optional<Component> hovered = instanceAt(pane, screenPoint);
        if (hovered.isEmpty()) {
            dismissed = Optional.empty();
            requested = Optional.empty();
            hideIfShown();
            return;
        }
        ElementId id = hovered.get().id();
        if (dismissed.isPresent() && !dismissed.get().equals(id)) {
            dismissed = Optional.empty();
        }
        if (requested.isPresent() && !requested.get().equals(id)) {
            requested = Optional.empty();
        }
        if (dismissed.isPresent() || !isActiveFor(id)) {
            hideIfShown();
            return;
        }
        if (!shown.equals(Optional.of(id))) {
            hideIfShown();
            shown = Optional.of(id);
            presenter.show(pane, hovered.get(), screenPoint);
        }
    }

    /** The instance whose panel shows now, if any (the canvas outlines it). */
    Optional<ElementId> shownInstance() {
        return shown;
    }

    /** The pointer left the canvas. */
    void pointerExited() {
        lastPane = null;
        lastPoint = null;
        dismissed = Optional.empty();
        requested = Optional.empty();
        hideIfShown();
    }

    /** A press or a drag: the user is working, not looking. */
    void pointerPressed() {
        dismissed = shown.or(() -> dismissed);
        hideIfShown();
    }

    void escapePressed() {
        pointerPressed();
    }

    /** Alt went down or up (the key itself, while the pointer rests); the picture follows at once. */
    void altChanged(boolean held) {
        altHeld = held;
        refresh();
    }

    /** Alt as the last mouse event had it; no re-evaluation (the move that carries it follows). */
    void rememberAlt(boolean held) {
        altHeld = held;
    }

    /** The «Mirar adentro» item: shows {@code instance} though the toggle is off. */
    void requestedFromMenu(CircuitCanvas pane, Component instance, Point2D screenPoint) {
        requested = Optional.of(instance.id());
        dismissed = Optional.empty();
        pointerMoved(pane, screenPoint);
    }

    /** Re-evaluates what shows at the last pointer position (the toggle changed). */
    void refresh() {
        if (lastPane != null) {
            pointerMoved(lastPane, lastPoint);
        } else if (!altHeld && !toggledOn.getAsBoolean()) {
            hideIfShown();
        }
    }

    private boolean isActiveFor(ElementId id) {
        return toggledOn.getAsBoolean() || altHeld || requested.equals(Optional.of(id));
    }

    private void hideIfShown() {
        if (shown.isPresent()) {
            shown = Optional.empty();
            presenter.hide();
        }
    }
}
