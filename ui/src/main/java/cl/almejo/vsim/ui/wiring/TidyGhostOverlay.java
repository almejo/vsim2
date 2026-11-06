package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.GhostChangePainter;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The preview of «Ordenar circuito» while its dialog is open: what the plan changes, painted over the canvas of every
 * pane of the view — elements of the result that are new or differ from the circuit the plan was computed on are
 * ghosts (semi-transparent, at their new place), and the elements of that circuit that are gone or differ are dimmed
 * with a dashed outline ({@link GhostChangePainter}, shared with the «Presentar» step ghost). Nothing in the circuit
 * changes. Colours from each pane's scheme at paint time; editor chrome, so never on paper, print or export. EDT only.
 */
final class TidyGhostOverlay {

    private final EditorView view;
    private final List<CircuitCanvas> panes = new ArrayList<>();
    private final Runnable removeFromPanes;
    private List<DocumentElement> arriving = List.of();
    private List<DocumentElement> leaving = List.of();
    private ComponentTypeRegistry registry;

    TidyGhostOverlay(EditorView view) {
        this.view = Objects.requireNonNull(view, "view");
        this.removeFromPanes = view.forEachPane(pane -> {
            CanvasOverlay overlay = (graphics, state) -> paintIn(graphics, pane);
            panes.add(pane);
            pane.addOverlay(overlay);
            return () -> {
                panes.remove(pane);
                pane.removeOverlay(overlay);
            };
        });
    }

    /**
     * Shows what {@code plan} changes compared with {@code before}, the circuit it was computed on (a snapshot: it
     * never changes under the ghost).
     */
    void show(CircuitDocument before, CircuitTidyPlan plan) {
        CircuitDocument after = plan.after();
        arriving = after.elements().stream().filter(element -> differsFrom(before, element)).toList();
        leaving = before.elements().stream().filter(element -> differsFrom(after, element)).toList();
        registry = view.openDocument().registry().copy();
        registry.addResolver(SubcircuitTypes.resolverFor(before));
        repaintPanes();
    }

    /** True when {@code document} has no element equal to {@code element} (new, gone or changed). */
    private static boolean differsFrom(CircuitDocument document, DocumentElement element) {
        return !document.element(element.id()).equals(Optional.of(element));
    }

    void clear() {
        if (!isShowing()) {
            return;
        }
        arriving = List.of();
        leaving = List.of();
        registry = null;
        repaintPanes();
    }

    /** Takes the overlay off every pane. */
    void dispose() {
        removeFromPanes.run();
    }

    /** True while there is something to paint. */
    boolean isShowing() {
        return !arriving.isEmpty() || !leaving.isEmpty();
    }

    /** Ids of the elements painted as ghosts. */
    List<ElementId> ghostIds() {
        return arriving.stream().map(DocumentElement::id).toList();
    }

    /** Ids of the elements painted dimmed. */
    List<ElementId> dimmedIds() {
        return leaving.stream().map(DocumentElement::id).toList();
    }

    private void repaintPanes() {
        panes.forEach(CircuitCanvas::repaint);
    }

    private void paintIn(Graphics2D graphics, CircuitCanvas pane) {
        if (!isShowing()) {
            return;
        }
        new GhostChangePainter(pane::scheme, registry).paint(graphics, leaving, arriving, pane);
    }
}
