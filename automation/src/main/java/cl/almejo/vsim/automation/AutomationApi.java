package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.check.StageCheck;
import cl.almejo.vsim.automation.check.StageChecks;
import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.automation.read.CatalogView;
import cl.almejo.vsim.automation.read.CircuitView;
import cl.almejo.vsim.automation.read.ComponentTypeView;
import cl.almejo.vsim.automation.read.ProblemsView;
import cl.almejo.vsim.automation.read.ReadFilter;
import cl.almejo.vsim.automation.read.Reads;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.automation.simulate.InputValue;
import cl.almejo.vsim.automation.simulate.SignalsView;
import cl.almejo.vsim.automation.simulate.SimulationOperations;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.StepKind;
import cl.almejo.vsim.automation.simulate.TruthTableView;
import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.EditService;
import cl.almejo.vsim.automation.edit.OrderedExecutor;
import cl.almejo.vsim.automation.edit.TaskEnding;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.PresentationService;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.PresentedEdit;
import cl.almejo.vsim.automation.present.StepOutcome;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * The domain API the AI works with, independent of MCP: the MCP layer calls it from its own threads and it reaches
 * the editor only through the {@link AutomationHost}. Task 1 lists the open circuits; later tasks add reading,
 * editing, simulating and the AI tasks.
 */
public final class AutomationApi {

    /** How long a request waits for the editor thread before telling the AI the outcome is unknown. */
    private static final Duration EDITOR_TIMEOUT = Duration.ofSeconds(10);
    /** How long an export may take (a PDF of many sheets) before the AI is told the outcome is unknown. */
    private static final Duration EXPORT_TIMEOUT = Duration.ofSeconds(60);
    /** The longest wait for the user's answer to a proposal in one call: well under the client's tool timeout. */
    public static final Duration MAXIMUM_PROPOSAL_WAIT = Duration.ofSeconds(30);

    private final AutomationHost host;
    private final Duration editorTimeout;
    private final List<AutomationListener> listeners = new CopyOnWriteArrayList<>();
    private final List<PresentationListener> presentationListeners = new CopyOnWriteArrayList<>();
    private final Reads reads;
    private final SimulationOperations simulation;
    private final EditService edits;
    private final PresentationService presentation;
    private final StageChecks checks;

    public AutomationApi(AutomationHost host) {
        this(host, EDITOR_TIMEOUT);
    }

    AutomationApi(AutomationHost host, Duration editorTimeout) {
        this.host = Objects.requireNonNull(host, "host");
        this.editorTimeout = Objects.requireNonNull(editorTimeout, "editorTimeout");
        // One ordered stream of events: a task's start reaches the listeners before its plan.
        OrderedExecutor events = new OrderedExecutor();
        this.edits = new EditService(host, listeners, editorTimeout, events);
        this.presentation = new PresentationService(host, edits, presentationListeners, events,
                PresentationService.MAXIMUM_STEP_WAIT);
        this.reads = new Reads(host, editorTimeout);
        this.simulation = new SimulationOperations(host);
        this.checks = new StageChecks(new StageChecks.Measurements() {
            @Override
            public ProblemsView problems(DocumentId document) {
                return reads.readProblems(document);
            }

            @Override
            public StageCheck.LayoutView layout(DocumentId document, Set<String> elementIds) {
                return reads.layout(document, elementIds);
            }

            @Override
            public TruthTableView truthTable(DocumentId document, Set<String> elementIds, int maximumRows,
                                             int eventBudget) {
                return simulation.truthTable(document, elementIds, maximumRows, eventBudget);
            }

            @Override
            public SignalsView signals(DocumentId document, List<PinReference> pins) {
                return simulation.readSignals(document, pins);
            }
        });
    }

    // --- New circuits, editor settings and preference proposals (spec §7): the host does them on the editor thread ---

    /** Opens a new, unsaved circuit window named {@code name}; the AI never saves it. */
    public DocumentId newCircuit(String name) {
        Objects.requireNonNull(name, "name");
        PlainText.require(name, "circuit name");
        return onEditorThread(access -> host.newCircuit(name));
    }

    /**
     * Changes an editor setting in the temporary layer of {@code document}'s window only (spec §7), while a task runs
     * on that circuit, so that the task's end takes it back; NOT_ALLOWED outside a task.
     */
    public void setEditorSetting(DocumentId document, EditorSetting setting, String value) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(value, "value");
        if (!edits.hasRunningTask(document)) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "Editor settings change only inside a task "
                    + "on that circuit (vsim_begin_task): they go back to the user's when it ends",
                    Map.of("document", document.value()));
        }
        onEditorThread(access -> {
            host.setEditorSetting(document, setting, value);
            return Boolean.TRUE;
        });
    }

    /** Shows the user a proposal to change a preference; returns its id. Nothing changes until the user accepts. */
    public String proposePreference(ProposedPreference preference, String value, String reason) {
        Objects.requireNonNull(preference, "preference");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(reason, "reason");
        if (!preference.allowedValues().contains(value)) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, preference + " takes one of "
                    + preference.allowedValues(), Map.of("allowedValues", preference.allowedValues()));
        }
        return onEditorThread(access -> host.proposePreference(preference, value, reason));
    }

    /**
     * The user's answer to proposal {@code proposalId}, waiting for it at most {@code wait} (capped at
     * {@link #MAXIMUM_PROPOSAL_WAIT}); empty when the user has not answered yet (ask again).
     */
    public Optional<ProposalAnswer> awaitProposal(String proposalId, Duration wait) {
        Objects.requireNonNull(proposalId, "proposalId");
        Duration capped = wait.compareTo(MAXIMUM_PROPOSAL_WAIT) > 0 ? MAXIMUM_PROPOSAL_WAIT
                : wait.isNegative() ? Duration.ZERO : wait;
        CompletableFuture<ProposalAnswer> answer = onEditorThread(access -> host.proposalAnswer(proposalId));
        try {
            return Optional.of(answer.get(capped.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The wait was interrupted; ask again");
        } catch (ExecutionException exception) {
            throw unwrap(exception.getCause());
        }
    }

    // --- Exports (spec §7, Exports row) and stage checks (spec §3, A§9) ---

    /** Writes a PDF, PNG or SVG into the remembered export folder under a new name; never overwrites. */
    public ExportReceipt export(ExportRequest request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<ExportReceipt> result = host.export(request);
        try {
            return result.get(EXPORT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The export did not finish within "
                    + EXPORT_TIMEOUT.toSeconds() + " s; look in the export folder before trying again");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The export was interrupted");
        } catch (ExecutionException exception) {
            throw unwrap(exception.getCause());
        }
    }

    /**
     * Runs a stage's checks and reports what VSim measured; a check VSim did not finish never passes. When the request
     * names a task, «Presentar» shows the result in the stage's card (and «De corrido» waits after a failed one).
     */
    public StageCheck.Result checkStage(StageCheck.Request request) {
        StageCheck.Result result = checks.run(request);
        result.taskId().ifPresent(taskId -> presentation.stageChecked(taskId, result.stage(), result.passed(),
                summaryOf(result)));
        return result;
    }

    private static String summaryOf(StageCheck.Result result) {
        return String.join("; ", result.checks().stream().map(StageCheck.CheckResult::summary).toList());
    }

    // --- Simulation with budgets and receipts (IA.1 Task 4): see SimulationOperations ---

    public SimulationReceipt pause(DocumentId document) {
        return simulation.pause(document);
    }

    public SimulationReceipt resume(DocumentId document) {
        return simulation.resume(document);
    }

    public SimulationReceipt restart(DocumentId document) {
        return simulation.restart(document);
    }

    public SimulationReceipt setInputs(DocumentId document, DocumentVersion version, String operationId,
                                       List<InputValue> values) {
        return simulation.setInputs(document, version, operationId, values);
    }

    public SimulationReceipt step(DocumentId document, StepKind kind, long amount, int eventBudget) {
        return simulation.step(document, kind, amount, eventBudget);
    }

    public SignalsView readSignals(DocumentId document, List<PinReference> pins) {
        return simulation.readSignals(document, pins);
    }

    public TruthTableView truthTable(DocumentId document, Set<String> elementIds, int maximumRows) {
        return simulation.truthTable(document, elementIds, maximumRows, 0);
    }

    public TruthTableView truthTable(DocumentId document, Set<String> elementIds, int maximumRows, int eventBudget) {
        return simulation.truthTable(document, elementIds, maximumRows, eventBudget);
    }

    /** The open circuits, one per circuit however many windows show it. */
    public List<DocumentSummary> listDocuments() {
        return onEditorThread(EditorAccess::openDocuments);
    }

    // --- Reading (IA.1 Task 2): the logic lives in the read package ---

    /** The types the AI can place in circuit {@code id}, filtered by id, name or category. */
    public CatalogView describeComponents(DocumentId id, Optional<String> typeFilter) {
        return reads.describeComponents(id, typeFilter);
    }

    /** Type {@code typeId} with the pins a part placed with {@code parameters} would have. */
    public ComponentTypeView describeComponent(DocumentId id, String typeId, Map<String, Object> parameters) {
        return reads.describeComponent(id, typeId, parameters);
    }

    /** One page of circuit {@code id}: summary by default, pages of at most 200 elements. */
    public CircuitView readCircuit(DocumentId id, ReadFilter filter, Optional<String> cursor) {
        return reads.readCircuit(id, filter, cursor);
    }

    /** The problems of circuit {@code id}'s latest simulation. */
    public ProblemsView readProblems(DocumentId id) {
        return reads.readProblems(id);
    }

    /** A PNG of {@code region} of circuit {@code id} as the export paints it; never shown, never saved. */
    public RegionPicture pictureOf(DocumentId id, Optional<Bounds> region) {
        return reads.pictureOf(id, region, false);
    }

    /** Like {@link #pictureOf(DocumentId, Optional)}; {@code asOnScreen} paints the active pane as the user sees it. */
    public RegionPicture pictureOf(DocumentId id, Optional<Bounds> region, boolean asOnScreen) {
        return reads.pictureOf(id, region, asOnScreen);
    }

    /** A PNG of the whole window showing circuit {@code id}, painted from its components; never saved. */
    public WindowPicture pictureOfWindow(DocumentId id) {
        return reads.pictureOfWindow(id);
    }

    /** {@code listener} hears every AI task begin and end until the returned runnable takes it back. */
    public Runnable addListener(AutomationListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** {@code listener} hears every task as «Presentar» shows it (spec §5) until the returned runnable takes it back. */
    public Runnable addPresentationListener(PresentationListener listener) {
        Objects.requireNonNull(listener, "listener");
        presentationListeners.add(listener);
        return () -> presentationListeners.remove(listener);
    }

    int listenerCount() {
        return listeners.size();
    }

    /**
     * One edit call, all or nothing, as one undo step (spec §10; see {@link EditService#edit}). In «Presentar» it is
     * one step, paced or proposed ({@link #editStep}); a proposed step's receipt says {@code applied} false.
     */
    public EditReceipt edit(EditRequest request) {
        return presentation.edit(request).receipt();
    }

    /**
     * One edit call with how «Presentar» took it (spec §5.1): not paced (working mode), {@code APPLIED} («De corrido»,
     * after the pause) or {@code PROPOSED} («Paso a paso»: shown as a ghost, then {@link #awaitStep}).
     */
    public PresentedEdit editStep(EditRequest request) {
        return presentation.edit(request);
    }

    /**
     * {@code await_step}: what became of the task's proposed step, waiting at most
     * {@link PresentationService#MAXIMUM_STEP_WAIT}: {@code APPLIED} with the receipt, {@code STALE} (read again and
     * propose it again), {@code STOPPED}, or {@code STILL_WAITING} (ask again).
     */
    public StepOutcome awaitStep(String taskId) {
        return presentation.awaitStep(taskId, PresentationService.MAXIMUM_STEP_WAIT);
    }

    /** {@link #awaitStep(String)} waiting at most {@code wait} (capped at the maximum). */
    public StepOutcome awaitStep(String taskId, Duration wait) {
        return presentation.awaitStep(taskId, wait);
    }

    /** «Siguiente ▸»: writes the task's proposed step as it was shown, or discards it as stale. Off the editor thread. */
    public void nextStep(String taskId) {
        presentation.next(taskId);
    }

    /** «Hasta fin de etapa»: the rest of the current stage at the «De corrido» pace. Off the editor thread. */
    public void toStageEnd(String taskId) {
        presentation.toStageEnd(taskId);
    }

    /**
     * «Deshacer etapa» (spec §4.1): undoes the stage's steps while they are on top of the history.
     * TODO(IA.2 Task 2, Storyboard): not built yet; refused until the storyboard fills it.
     */
    public UndoOutcome undoStage(String taskId, int stageIndex) {
        edits.requireTask(taskId);
        throw new AutomationException(AutomationError.NOT_ALLOWED, "«Deshacer etapa» is not available yet");
    }

    /**
     * «Dejar solo el resultado» (spec §6): keeps the last stage's frame and the components created, as one undo step.
     * TODO(IA.2 Task 2, Storyboard): not built yet; refused until the storyboard fills it.
     */
    public void keepOnlyResult(String taskId) {
        edits.requireTask(taskId);
        throw new AutomationException(AutomationError.NOT_ALLOWED, "«Dejar solo el resultado» is not available yet");
    }

    /** The presentation mode of the window the AI works in for {@code document} (spec §4). */
    public PresentationSettings presentationSettings(DocumentId document) {
        Objects.requireNonNull(document, "document");
        return onEditorThread(access -> host.presentationSettings(document));
    }

    /**
     * Switches «Presentar» on or off for {@code document}'s window, as the AI asks when the user wants to see how it
     * builds (spec §4), and returns the window's settings then. A step waiting for «Siguiente» is written when it is
     * switched off.
     */
    public PresentationSettings setPresenting(DocumentId document, boolean presenting) {
        Objects.requireNonNull(document, "document");
        PresentationSettings settings = onEditorThread(access -> {
            host.setPresenting(document, presenting);
            return host.presentationSettings(document);
        });
        presentation.settingsChanged(document, settings);
        return settings;
    }

    /** The editor: the user changed the presentation mode of {@code document}'s window. Off the editor thread. */
    public void presentationChanged(DocumentId document, PresentationSettings settings) {
        presentation.settingsChanged(document, settings);
    }

    /** The editor: {@code document}'s last window closed; its proposed step is dropped. */
    public void documentClosed(DocumentId document) {
        presentation.documentClosed(document);
    }

    /** The editor: the AI was turned off or VSim quits; every proposed step is dropped and every wait ends. */
    public void stopPresenting() {
        presentation.stopAll();
    }

    /** Begins an AI task on an open circuit (spec §3); listeners hear it start and its plan. */
    public TaskInfo beginTask(DocumentId document, String title, List<TaskStage> stages) {
        TaskInfo info = edits.beginTask(document, title, stages);
        presentation.taskBegun(info);
        return info;
    }

    /** Replaces a task's plan of stages and steps. */
    public void updatePlan(String taskId, List<TaskStage> stages) {
        edits.updatePlan(taskId, stages);
        presentation.planUpdated(taskId);
    }

    /** Ends a task; listeners hear it end with the elements it changed. Its layout-only guides go away. */
    public TaskInfo endTask(String taskId) {
        presentation.taskEnding(taskId);
        return edits.endTask(taskId);
    }

    /**
     * Ends a task; its guides added only to align go away first, as one more step of it, unless
     * {@code keepLayoutGuides} (spec §7). Listeners hear it end with the elements it changed.
     */
    public TaskEnding endTask(String taskId, boolean keepLayoutGuides) {
        presentation.taskEnding(taskId);
        return edits.endTask(taskId, keepLayoutGuides);
    }

    /** «Detener»: stops the task; its applied edits stay, its proposed step is dropped and its next ones are refused. */
    public void stopTask(String taskId) {
        presentation.stop(taskId);
    }

    /** «Deshacer lo que hizo la IA»: undoes the task's steps from the top, never past the user's own edit. */
    public UndoOutcome undoTask(String taskId) {
        return edits.undoTask(taskId);
    }

    /** Runs {@code work} on the editor thread and waits for it here, off that thread, at most the editor timeout. */
    private <T> T onEditorThread(Function<EditorAccess, T> work) {
        CompletableFuture<T> result = host.onEditorThread(work);
        try {
            return result.get(editorTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN,
                    "The editor did not answer within " + editorTimeout.toSeconds() + " s; read again before retrying");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutomationException(AutomationError.OUTCOME_UNKNOWN, "The request was interrupted");
        } catch (ExecutionException exception) {
            throw unwrap(exception.getCause());
        }
    }

    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("The editor failed the request", cause);
    }
}
