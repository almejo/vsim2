package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Deshacer / Rehacer of a view (round C, the user's decision over spec §10.4 «the view never jumps»): the circuit keeps
 * one history, and a step made in another place of its group — the circuit, or a component — is undone or redone
 * there. That place's tab comes to the front first ({@link WindowCommands#showPlaceOfStep}: opened again on the step's
 * «Valores de» when it was closed), the step runs, and what it touched glows as «Ver la pieza» makes a piece glow
 * (brought into view when it is outside). Never something the user is not looking at. A step made in this view's place
 * runs here as it always did; outside a real window (no tabs to show) too.
 */
final class UndoInItsPlace implements EditorActions.StepRunner {

    private final EditorView view;

    UndoInItsPlace(EditorView view) {
        this.view = view;
    }

    @Override
    public void undo() {
        run(History::undoCommand, History::undo);
    }

    @Override
    public void redo() {
        run(History::redoCommand, History::redo);
    }

    private void run(Function<History, Optional<Command>> stepOf, Consumer<History> direction) {
        OpenDocument here = view.openDocument();
        History history = here.history();
        Optional<Command> step = stepOf.apply(history);
        if (step.isEmpty()) {
            return;
        }
        Optional<EditInsideCommand> inside = OpenDocument.insideEditOf(step.get());
        Optional<String> definitionId = inside.map(EditInsideCommand::definitionId);
        if (definitionId.equals(here.definitionId())) {
            direction.accept(history);
            return;
        }
        OpenDocument circuit = here.parent().orElse(here);
        Optional<EditorView> there = view.windowCommands().showPlaceOfStep(circuit, definitionId,
                inside.flatMap(EditInsideCommand::contextPath));
        if (there.isEmpty()) {
            direction.accept(history);
            return;
        }
        Map<ElementId, DocumentElement> before = elementsOf(there.get().openDocument());
        direction.accept(history);
        glowWhatChanged(there.get(), before);
    }

    private static Map<ElementId, DocumentElement> elementsOf(OpenDocument place) {
        Map<ElementId, DocumentElement> elements = new LinkedHashMap<>();
        place.document().elements().forEach(element -> elements.put(element.id(), element));
        return elements;
    }

    /**
     * Makes glow the elements the step added or changed in {@code target}'s place, and where the ones it removed were;
     * nothing when it changed no element there (a definition's description, say).
     */
    private static void glowWhatChanged(EditorView target, Map<ElementId, DocumentElement> before) {
        OpenDocument place = target.openDocument();
        Map<ElementId, DocumentElement> removed = new LinkedHashMap<>(before);
        List<DocumentElement> touched = new ArrayList<>();
        for (DocumentElement element : place.document().elements()) {
            DocumentElement previous = removed.remove(element.id());
            if (!element.equals(previous)) {
                touched.add(element);
            }
        }
        touched.addAll(removed.values());
        DrawnBounds.of(touched, place.registry()).ifPresent(bounds -> {
            CircuitCanvas canvas = target.canvas();
            if (!isInView(canvas, bounds)) {
                canvas.travelTo(bounds);
            }
            canvas.glow(bounds);
        });
    }

    /** Whether {@code bounds} are wholly on the canvas as it is now (a canvas not laid out yet shows nothing). */
    private static boolean isInView(CircuitCanvas canvas, Bounds bounds) {
        Point2D topLeft = canvas.viewport().gridToScreen(bounds.x(), bounds.y());
        Point2D bottomRight = canvas.viewport().gridToScreen(bounds.x() + bounds.width(), bounds.y() + bounds.height());
        return topLeft.getX() >= 0 && topLeft.getY() >= 0 && bottomRight.getX() <= canvas.getWidth()
                && bottomRight.getY() <= canvas.getHeight();
    }
}
