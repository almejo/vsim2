package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.ai.EditorAutomationHost;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.SwingUtilities;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * «Presentar» in every editor view (IA.2, spec §4, §5): Ver ▸ «Presentar lo que hace la IA» and its toolbar toggle,
 * and the floating panel that shows the AI's task while it presents ({@link PresentationFeatures}). It hears the
 * task through {@link PresentationEvents} and asks for «Siguiente», «Detener»… through {@link PresentationControls},
 * both given by the application (the automation layer) or by a test.
 */
public final class PresentationExtension implements EditorExtension {

    static final String SHORTCUT_ID = "view.aiPresent";

    private final PresentationEvents events;
    private final PresentationControls controls;
    private final Function<OpenDocument, DocumentId> ids;
    private final Executor editorThread;
    private final Map<EditorView, PresentationFeatures> installed = new IdentityHashMap<>();
    private final Map<EditorView, PresentationOverlays> overlays = new IdentityHashMap<>();

    /** @param ids the id the AI knows a circuit by ({@link EditorAutomationHost#documentId}) */
    public PresentationExtension(PresentationEvents events, PresentationControls controls,
                                 Function<OpenDocument, DocumentId> ids) {
        this(events, controls, ids, SwingUtilities::invokeLater);
    }

    /** @param editorThread moves the events, heard off the EDT, to it ({@code SwingUtilities::invokeLater}) */
    PresentationExtension(PresentationEvents events, PresentationControls controls,
                          Function<OpenDocument, DocumentId> ids, Executor editorThread) {
        this.events = Objects.requireNonNull(events, "events");
        this.controls = Objects.requireNonNull(controls, "controls");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.editorThread = Objects.requireNonNull(editorThread, "editorThread");
    }

    /** An extension whose events never come and whose buttons do nothing (tests that install every extension). */
    public static PresentationExtension inactive() {
        EditorAutomationHost host = new EditorAutomationHost(List::of);
        return new PresentationExtension(PresentationEvents.NONE, new PresentationControls() {
            @Override
            public void next(String taskId) {
            }

            @Override
            public void toStageEnd(String taskId) {
            }

            @Override
            public void stop(String taskId) {
            }

            @Override
            public void undoStage(String taskId, int stageIndex) {
            }

            @Override
            public void keepOnlyResult(String taskId) {
            }
        }, host::documentId);
    }

    @Override
    public Runnable install(EditorView view) {
        PresentationFeatures features = new PresentationFeatures(view, events, controls, ids, editorThread);
        installed.put(view, features);
        PresentationOverlays viewOverlays = PresentationOverlays.install(view, events::addPresentationListener,
                this::addTaskListener, ids, editorThread, System::nanoTime);
        overlays.put(view, viewOverlays);
        return () -> {
            installed.remove(view, features);
            overlays.remove(view, viewOverlays);
            viewOverlays.dispose();
            features.dispose();
        };
    }

    /**
     * The AI asked for «Presentar» (spec §4, {@code vsim_set_presentation}): every window of {@code document} turns
     * the toggle on (the preference, so every window) and its panel says «La IA pasó a Presentar». EDT only.
     */
    public void aiAskedForPresenting(DocumentId document) {
        Objects.requireNonNull(document, "document");
        List.copyOf(installed.values()).stream()
                .filter(features -> features.shows(document))
                .forEach(PresentationFeatures::aiSwitchedToPresenting);
    }

    /** The callouts, ghosts and travel of {@code view} (the storyboard asks them to travel to a stage's origin). */
    public Optional<PresentationOverlays> overlaysOf(EditorView view) {
        return Optional.ofNullable(overlays.get(view));
    }

    /** The overlays hear the tasks begin and end through the same events as the panel. */
    private Runnable addTaskListener(AiServerService.Listener listener) {
        return events.addTaskListener(new AutomationListener() {
            @Override
            public void taskStarted(TaskInfo task) {
                listener.taskStarted(task);
            }

            @Override
            public void taskEnded(TaskInfo task, Set<String> changedElementIds) {
                listener.taskEnded(task, changedElementIds);
            }

            @Override
            public void taskStopped(TaskInfo task) {
                listener.taskStopped(task);
            }
        });
    }

    Optional<PresentationFeatures> featuresOf(EditorView view) {
        return Optional.ofNullable(installed.get(view));
    }
}
