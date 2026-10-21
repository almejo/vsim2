package cl.almejo.vsim.automation;

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

    private final AutomationHost host;
    private final Duration editorTimeout;
    private final List<AutomationListener> listeners = new CopyOnWriteArrayList<>();
    private final Reads reads;
    private final SimulationOperations simulation;

    public AutomationApi(AutomationHost host) {
        this(host, EDITOR_TIMEOUT);
    }

    AutomationApi(AutomationHost host, Duration editorTimeout) {
        this.host = Objects.requireNonNull(host, "host");
        this.editorTimeout = Objects.requireNonNull(editorTimeout, "editorTimeout");
        this.reads = new Reads(host, editorTimeout);
        this.simulation = new SimulationOperations(host);
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

    int listenerCount() {
        return listeners.size();
    }

    /** «Detener»: stops the task; its applied edits stay. Until the tasks exist (IA.1 Task 3) there is none to stop. */
    public void stopTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
    }

    /** «Deshacer lo que hizo la IA»: undoes the task's steps from the top. Until Task 3 there is nothing to undo. */
    public UndoOutcome undoTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return new UndoOutcome(0, false);
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
