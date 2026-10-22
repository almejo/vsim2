package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.ai.AiChangeGlowOverlay;
import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * What «Presentar» paints over one window's panes (spec §5): the callout pointing at each step ({@link StepCallout}),
 * the ghost of the step waiting for «Siguiente» ({@link StepGhostOverlay}), the glow of each applied step
 * ({@link AiChangeGlowOverlay}), and the view travel to each step and stage ({@link PresentationTravel}). It follows
 * the presentation events of the tasks on this window's circuit, which arrive on any thread and are shown on the
 * editor thread; {@link #dispose} takes back every overlay and listener. EDT only (but the listeners).
 *
 * <p>Seams: {@code presentationListeners} registers a {@link PresentationListener} and returns its removal (in the
 * app, {@code AutomationApi::addPresentationListener}); {@code taskEvents} registers a task listener for the ends of
 * tasks ({@code AiServerService::addListener}); {@link #travelToStageOrigin} is for the storyboard's «begin stage».
 */
public final class PresentationOverlays {

    private final EditorView view;
    private final Function<OpenDocument, DocumentId> ids;
    private final Executor editorThread;
    private final StepCallout callout;
    private final StepGhostOverlay ghosts;
    private final AiChangeGlowOverlay glow;
    private final PresentationTravel travel;
    private final Runnable removeGlow;
    private final Runnable removePaneOverlays;
    private final Runnable removePresentationListener;
    private final Runnable removeTaskListener;
    /** The plan of each task on this window's circuit, by task id. */
    private final Map<String, List<TaskStage>> plans = new HashMap<>();
    /** The task whose step the callout shows. */
    private String shownTask;
    private boolean disposed;

    private PresentationOverlays(EditorView view, Function<PresentationListener, Runnable> presentationListeners,
                                 Function<AiServerService.Listener, Runnable> taskEvents,
                                 Function<OpenDocument, DocumentId> ids, Executor editorThread,
                                 LongSupplier nanoClock) {
        this.view = view;
        this.ids = ids;
        this.editorThread = editorThread;
        Runnable repaint = EveryPane.repaint(view);
        this.callout = new StepCallout(() -> view.canvas().scheme(), view::canvas,
                () -> view.services().preferences().animateViewMoves(), repaint, nanoClock);
        this.ghosts = new StepGhostOverlay(() -> view.canvas().scheme(), view.openDocument().registry(), repaint);
        this.glow = new AiChangeGlowOverlay(() -> view.canvas().scheme(), view.openDocument().registry(), repaint,
                nanoClock);
        this.travel = new PresentationTravel(view);
        this.removeGlow = EveryPane.addOverlay(view, glow);
        this.removePaneOverlays = view.forEachPane(pane -> {
            CanvasOverlay overlay = (graphics, state) -> {
                ghosts.paint(graphics, state, pane);
                callout.paint(graphics, pane);
            };
            pane.addOverlay(overlay);
            return () -> pane.removeOverlay(overlay);
        });
        this.removePresentationListener = presentationListeners.apply(new Events());
        this.removeTaskListener = taskEvents.apply(new AiServerService.Listener() {
            @Override
            public void taskEnded(TaskInfo task, Set<String> changedElementIds) {
                onEditorThread(() -> forget(task.taskId()));
            }

            @Override
            public void taskStopped(TaskInfo task) {
                onEditorThread(() -> forget(task.taskId()));
            }
        });
    }

    /**
     * Installs the presentation's overlays in every pane of {@code view}, now and later.
     *
     * @param presentationListeners registers a listener of the presentation and returns its removal
     * @param taskEvents            registers a listener of the AI's tasks (their ends) and returns its removal
     * @param ids                   the id the AI knows a circuit by
     * @param editorThread          runs jobs on the editor thread ({@code SwingUtilities::invokeLater})
     * @param nanoClock             the clock of the fades and the glow
     */
    public static PresentationOverlays install(EditorView view,
                                               Function<PresentationListener, Runnable> presentationListeners,
                                               Function<AiServerService.Listener, Runnable> taskEvents,
                                               Function<OpenDocument, DocumentId> ids, Executor editorThread,
                                               LongSupplier nanoClock) {
        return new PresentationOverlays(Objects.requireNonNull(view, "view"),
                Objects.requireNonNull(presentationListeners, "presentationListeners"),
                Objects.requireNonNull(taskEvents, "taskEvents"), Objects.requireNonNull(ids, "ids"),
                Objects.requireNonNull(editorThread, "editorThread"), Objects.requireNonNull(nanoClock, "nanoClock"));
    }

    /**
     * The view goes to where a stage starts ({@code origin}, grid units: the stage builds to its right and below),
     * unless a pane shows it already; smooth, or a jump with reduced motion. EDT only.
     */
    public void travelToStageOrigin(GridPoint origin) {
        if (!disposed) {
            travel.toStageOrigin(origin);
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        removePresentationListener.run();
        removeTaskListener.run();
        removePaneOverlays.run();
        removeGlow.run();
        callout.dispose();
        ghosts.clear();
        glow.dispose();
        plans.clear();
        shownTask = null;
    }

    private void onEditorThread(Runnable job) {
        editorThread.execute(() -> {
            if (!disposed) {
                job.run();
            }
        });
    }

    private boolean isThisCircuit(TaskInfo task) {
        return task.document().equals(ids.apply(view.openDocument()));
    }

    private void planChanged(TaskInfo task, List<TaskStage> stages) {
        if (isThisCircuit(task)) {
            plans.put(task.taskId(), List.copyOf(stages));
        } else if (plans.remove(task.taskId()) != null) {
            forget(task.taskId());
        }
    }

    private void proposed(StepPreview preview) {
        if (!plans.containsKey(preview.taskId())) {
            return;
        }
        ghosts.show(preview);
        point(preview, true);
    }

    private void applied(StepPreview preview) {
        if (!plans.containsKey(preview.taskId())) {
            return;
        }
        ghosts.clear();
        point(preview, false);
        glow.show(preview.ghostElements().stream().map(DocumentElement::id).collect(Collectors.toSet()));
    }

    private void discarded(String taskId, StepStatus why) {
        if (taskId.equals(shownTask)) {
            ghosts.clear();
            callout.clear();
            shownTask = null;
        }
    }

    /** The task ended or stopped: its callout and ghost go. */
    private void forget(String taskId) {
        plans.remove(taskId);
        discarded(taskId, StepStatus.STOPPED);
    }

    /** The callout points at {@code preview}'s change and the view goes there unless a pane shows it. */
    private void point(StepPreview preview, boolean waiting) {
        Optional<Bounds> changed = StepGhostOverlay.changedBounds(preview, view.openDocument().document(),
                view.openDocument().registry());
        shownTask = preview.taskId();
        callout.show(new StepCallout.Content(header(preview, waiting), preview.text(), preview.reason()), changed);
        changed.ifPresent(travel::toStep);
    }

    /** «Paso k de n» (n from the stage's plan when it lists the step), plus «· propuesto» while it waits. */
    private String header(StepPreview preview, boolean waiting) {
        String step = Integer.toString(preview.stepIndex() + 1);
        List<TaskStage> stages = plans.getOrDefault(preview.taskId(), List.of());
        int count = preview.stageIndex() >= 0 && preview.stageIndex() < stages.size()
                ? stages.get(preview.stageIndex()).steps().size() : 0;
        if (count > preview.stepIndex()) {
            return Messages.format(waiting ? "ai.callout.stepOfProposed" : "ai.callout.stepOf", step,
                    Integer.toString(count));
        }
        return Messages.format(waiting ? "ai.callout.stepProposed" : "ai.callout.step", step);
    }

    StepCallout callout() {
        return callout;
    }

    StepGhostOverlay ghosts() {
        return ghosts;
    }

    AiChangeGlowOverlay glow() {
        return glow;
    }

    /** The presentation events, from any thread, shown on the editor thread. */
    private final class Events implements PresentationListener {

        @Override
        public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
            List<TaskStage> copy = List.copyOf(stages);
            onEditorThread(() -> PresentationOverlays.this.planChanged(task, copy));
        }

        @Override
        public void stepProposed(StepPreview preview) {
            onEditorThread(() -> proposed(preview));
        }

        @Override
        public void stepApplied(StepPreview preview) {
            onEditorThread(() -> applied(preview));
        }

        @Override
        public void stepDiscarded(String taskId, StepStatus why) {
            onEditorThread(() -> discarded(taskId, why));
        }

        @Override
        public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
            // the panel says it (Task 3); nothing to point at
        }
    }
}
