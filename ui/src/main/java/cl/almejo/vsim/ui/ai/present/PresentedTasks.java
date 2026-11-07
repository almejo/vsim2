package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PlaceRef;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.ui.ai.TaskPlaces;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The task each circuit's «Presentar» panel shows, and on which tab (round E, 5q). One task per circuit, shared by the
 * views of all its places (its tab and its component tabs, in every window), so a tab opened during the task shows it
 * as it is and the panel can move: it shows on the tab of the place the task works in ({@link TaskPlaces}, the tab the
 * robot marks), or on the circuit's tabs when that place has no open tab. Every view of the circuit hears the task's
 * events and applies them to the same task; each change sets a state, so hearing it once per view is the same as
 * once. EDT only.
 */
final class PresentedTasks {

    private final TaskPlaces taskPlaces;
    private final Function<OpenDocument, DocumentId> ids;
    private final Supplier<Collection<EditorView>> views;
    private final Runnable refreshEveryView;
    private final Map<DocumentId, PresentedTask> byCircuit = new HashMap<>();

    /**
     * @param ids              the id the AI knows a circuit by (never asked for a component's place)
     * @param views            every view «Presentar» is installed in
     * @param refreshEveryView shows or hides the panel of every view again
     */
    PresentedTasks(TaskPlaces taskPlaces, Function<OpenDocument, DocumentId> ids,
                   Supplier<Collection<EditorView>> views, Runnable refreshEveryView) {
        this.taskPlaces = Objects.requireNonNull(taskPlaces, "taskPlaces");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.views = Objects.requireNonNull(views, "views");
        this.refreshEveryView = Objects.requireNonNull(refreshEveryView, "refreshEveryView");
    }

    /** The id of the circuit {@code view} shows a place of (the circuit itself, or one of its components). */
    DocumentId circuitOf(EditorView view) {
        OpenDocument shown = view.openDocument();
        return ids.apply(shown.parent().orElse(shown));
    }

    /** The task {@code info} of its circuit: the one shown already, or a new one that replaces the circuit's last. */
    PresentedTask taskFor(TaskInfo info) {
        DocumentId circuit = info.document().base();
        PresentedTask shown = byCircuit.get(circuit);
        if (shown == null || !shown.task().taskId().equals(info.taskId())) {
            shown = new PresentedTask(info);
            byCircuit.put(circuit, shown);
        }
        return shown;
    }

    /** The task the panels of {@code circuit} show (running, or ended and not closed yet). */
    Optional<PresentedTask> shownIn(DocumentId circuit) {
        return Optional.ofNullable(byCircuit.get(circuit));
    }

    /** «×»: the task's panel closes in every view. */
    void close(PresentedTask task) {
        byCircuit.remove(task.task().document().base(), task);
        refreshEveryView.run();
    }

    /** A task may work in another place: the running ones learn where, and every view looks again. */
    void placesChanged() {
        byCircuit.values().forEach(this::follow);
        refreshEveryView.run();
    }

    /** The views changed (a tab opened or closed): only the circuits some view shows keep their task. */
    void viewsChanged() {
        Set<DocumentId> shown = Set.copyOf(views.get().stream().map(this::circuitOf).toList());
        byCircuit.keySet().retainAll(shown);
        refreshEveryView.run();
    }

    /**
     * Whether {@code view} shows {@code task}'s panel: a view of the place the task works in, or a view of the
     * circuit itself when no view shows that place. Only where it shows, not whether its tab is in front.
     */
    boolean showsHere(EditorView view, PresentedTask task) {
        DocumentId circuit = task.task().document().base();
        if (!circuitOf(view).equals(circuit)) {
            return false;
        }
        PlaceRef place = follow(task);
        if (shows(view, place)) {
            return true;
        }
        return !view.openDocument().isBody() && List.copyOf(views.get()).stream()
                .noneMatch(other -> circuitOf(other).equals(circuit) && shows(other, place));
    }

    /** The place a running task works in now (an ended one keeps the last). */
    private PlaceRef follow(PresentedTask task) {
        if (!task.isEnded()) {
            taskPlaces.placeOf(task.task().taskId()).ifPresent(task::workingIn);
        }
        return task.place();
    }

    private static boolean shows(EditorView view, PlaceRef place) {
        OpenDocument shown = view.openDocument();
        return place.isShownBy(shown.definitionId(), shown.contextPath());
    }
}
