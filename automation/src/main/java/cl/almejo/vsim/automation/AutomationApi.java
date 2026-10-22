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
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationService;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.PresentedEdit;
import cl.almejo.vsim.automation.present.StageOrigin;
import cl.almejo.vsim.automation.present.Storyboard;
import cl.almejo.vsim.automation.present.StepOutcome;
import cl.almejo.vsim.automation.present.StoryboardState;

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
    private final Storyboard storyboard;
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
        this.storyboard = new Storyboard(host, edits, this::hasPendingStep);
        this.presentation = new PresentationService(host, edits, presentationListeners, events,
                PresentationService.MAXIMUM_STEP_WAIT, storyboard);
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
     * names a task, «Presentar» shows the result in the stage's card, and «De corrido» waits at the next stage after
     * a functional failure ({@link StageCheck.Result#functionalFailure}); a layout-only failure lets it go on.
     */
    public StageCheck.Result checkStage(StageCheck.Request request) {
        request.taskId().ifPresent(presentation::heardFrom);
        StageCheck.Result result = checks.run(request);
        result.taskId().ifPresent(taskId -> presentation.stageChecked(taskId, result.stage(), result.passed(),
                result.functionalFailure(), summaryOf(result)));
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
     * As {@link #editStep(EditRequest)}, with a line the AI says first about what comes and why ({@link #say}):
     * shown before the step, paced like a short step in «De corrido». A call that only validates says nothing.
     */
    public PresentedEdit editStep(EditRequest request, Optional<String> narration) {
        Objects.requireNonNull(narration, "narration");
        if (narration.isPresent() && !request.validateOnly() && request.step().isPresent()) {
            presentation.say(request.step().get().taskId(), narration.get());
        }
        return presentation.edit(request);
    }

    /**
     * {@code vsim_say} (IA.3): the AI says in one short line what comes and why. «Presentar» shows it as a subtitle
     * and in the stage's card, paced like a short step in «De corrido»; in working mode only the status line keeps
     * it. See {@link PresentationService#say}.
     */
    public Narration say(String taskId, String text) {
        return presentation.say(taskId, text);
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
     * {@code begin_stage} (spec §6): the AI begins stage {@code stageIndex} (from 0, as the plan lists it) of the
     * task. Returns the free origin, in grid units, where it builds the stage: to the right of the previous stage's
     * frame, {@link Storyboard#GAP} units away and aligned on its top. In «Presentar» the stages before it get their
     * frames first (VSim's own undo steps, which never make the AI's version stale) and the view travels to the
     * origin. Refused while a step waits for «Siguiente» ({@code NOT_ALLOWED}) or for a stage the plan does not have
     * ({@code INVALID_PARAMETER}).
     */
    public StageOrigin beginStage(String taskId, int stageIndex) {
        return beginStage(taskId, stageIndex, Optional.empty());
    }

    /** As {@link #beginStage(String, int)}, then the AI says {@code narration} about the stage ({@link #say}). */
    public StageOrigin beginStage(String taskId, int stageIndex, Optional<String> narration) {
        Objects.requireNonNull(narration, "narration");
        presentation.heardFrom(taskId);
        StageOrigin origin = storyboard.beginStage(taskId, stageIndex);
        presentation.stageBegun(taskId, stageIndex);
        narration.ifPresent(line -> presentation.say(taskId, line));
        return origin;
    }

    /**
     * «Deshacer etapa» (spec §4.1): undoes the steps of stage {@code stageIndex} (from 0) of the task, its frame
     * included, while they are on top of the history; stops at the user's own edit (or another stage's step) and says
     * so. Off the editor thread.
     */
    public StageUndoOutcome undoStage(String taskId, int stageIndex) {
        return edits.undoStage(taskId, stageIndex);
    }

    /**
     * The panel's «Deshacer etapa»: undoes the latest stage that still has steps in the circuit (a stage that only
     * checked has none), so pressing it again goes back one more stage; says what it stopped at. Off the editor
     * thread.
     */
    public StageUndoOutcome undoLatestStage(String taskId) {
        return edits.undoLatestStage(taskId);
    }

    /** What the «Presentar» panel may offer for the task's storyboard now. Off the editor thread. */
    public StoryboardState storyboardState(String taskId) {
        return storyboard.state(taskId);
    }

    /**
     * «Dejar solo el resultado» (spec §6): removes the frames of the task's stages but the last one and what those
     * stages added, as one undo step; the last stage and every component definition stay. Refused while a step waits
     * for «Siguiente» or the task still runs ({@code NOT_ALLOWED}). Off the editor thread.
     */
    public void keepOnlyResult(String taskId) {
        storyboard.keepOnlyResult(taskId);
    }

    /** The presentation mode of the window the AI works in for {@code document} (spec §4). */
    public PresentationSettings presentationSettings(DocumentId document) {
        Objects.requireNonNull(document, "document");
        return onEditorThread(access -> host.presentationSettings(document));
    }

    /**
     * Switches «Presentar» on or off for {@code document}'s window, as the AI asks when the user wants to see how it
     * builds (spec §4), and returns the window's settings then. A step waiting for «Siguiente» is written when it is
     * switched off. Switching it off while a task runs on {@code document} is refused ({@code NOT_ALLOWED}): the AI
     * never leaves «Presentar» by itself during a task.
     */
    public PresentationSettings setPresenting(DocumentId document, boolean presenting) {
        Objects.requireNonNull(document, "document");
        if (!presenting && edits.hasRunningTask(document)) {
            // Spec §4: the AI never goes back to working mode by itself during a task; the user's toggle can.
            throw new AutomationException(AutomationError.NOT_ALLOWED, "A task runs on " + document.value()
                    + ": «Presentar» stays on until it ends (end the task first; the user can switch it off in VSim)",
                    Map.of("document", document.value()));
        }
        PresentationSettings settings = onEditorThread(access -> {
            host.setPresenting(document, presenting);
            return host.presentationSettings(document);
        });
        presentation.settingsChanged(document, settings);
        return settings;
    }

    /**
     * As {@link #setPresenting}, and while a task runs on {@code document}, the pace and the pause between steps
     * for that task (spec §7, agreed with the user: «De corrido» for a recording without the user's clicks): layered
     * in that window only and given back when the task ends. NOT_ALLOWED with a pace or pause outside a task,
     * INVALID_PARAMETER for a pause outside {@link PresentationSettings#MINIMUM_PAUSE} … {@code MAXIMUM_PAUSE}. A
     * step waiting for «Siguiente» is written when the pace becomes «De corrido».
     */
    public PresentationSettings setPresentation(DocumentId document, boolean presenting,
                                                Optional<PresentationPace> pace, Optional<Duration> pause) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(pace, "pace");
        Objects.requireNonNull(pause, "pause");
        if (pace.isEmpty() && pause.isEmpty()) {
            return setPresenting(document, presenting);
        }
        if (!edits.hasRunningTask(document)) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "The pace and the pause change only for a "
                    + "task on that circuit (call vsim_set_presentation after vsim_begin_task, before the first "
                    + "step): they go back to the user's when it ends", Map.of("document", document.value()));
        }
        pause.ifPresent(wanted -> {
            if (wanted.compareTo(PresentationSettings.MINIMUM_PAUSE) < 0
                    || wanted.compareTo(PresentationSettings.MAXIMUM_PAUSE) > 0) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, "The pause is "
                        + PresentationSettings.MINIMUM_PAUSE.toMillis() + " to "
                        + PresentationSettings.MAXIMUM_PAUSE.toMillis() + " ms",
                        Map.of("pauseMillis", wanted.toMillis()));
            }
        });
        if (!presenting) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "A task runs on " + document.value()
                    + ": «Presentar» stays on until it ends; a pace or pause goes with presenting true",
                    Map.of("document", document.value()));
        }
        PresentationSettings settings = onEditorThread(access -> {
            host.setPresenting(document, true);
            host.layerPresentation(document, pace, pause);
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
        presentation.heardFrom(taskId);
        edits.updatePlan(taskId, stages);
        presentation.planUpdated(taskId);
    }

    /** Ends a task; listeners hear it end with the elements it changed. Its layout-only guides go away. */
    public TaskInfo endTask(String taskId) {
        presentation.taskEnding(taskId);
        storyboard.taskEnding(taskId);
        return edits.endTask(taskId);
    }

    /**
     * Ends a task; its guides added only to align go away first, as one more step of it, unless
     * {@code keepLayoutGuides} (spec §7). Listeners hear it end with the elements it changed.
     */
    public TaskEnding endTask(String taskId, boolean keepLayoutGuides) {
        presentation.taskEnding(taskId);
        storyboard.taskEnding(taskId);
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

    /** Whether a step of the task waits for «Siguiente» (the storyboard asks it on the editor thread). */
    private boolean hasPendingStep(String taskId) {
        return presentation.hasPendingStep(taskId);
    }

    /** Runs {@code work} on the editor thread and waits for it here, off that thread, at most the editor timeout. */
    private <T> T onEditorThread(Function<EditorAccess, T> work) {
        return awaited(host.onEditorThread(work));
    }

    /** Waits here, off the editor thread, at most the editor timeout, for work queued on the editor thread. */
    private <T> T awaited(CompletableFuture<T> result) {
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
