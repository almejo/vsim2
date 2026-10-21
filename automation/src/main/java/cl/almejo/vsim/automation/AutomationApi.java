package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.EditService;
import cl.almejo.vsim.automation.edit.OrderedExecutor;
import cl.almejo.vsim.automation.edit.TaskStage;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
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
    private final EditService edits;

    public AutomationApi(AutomationHost host) {
        this(host, EDITOR_TIMEOUT);
    }

    AutomationApi(AutomationHost host, Duration editorTimeout) {
        this.host = Objects.requireNonNull(host, "host");
        this.editorTimeout = Objects.requireNonNull(editorTimeout, "editorTimeout");
        this.edits = new EditService(host, listeners, editorTimeout, new OrderedExecutor());
    }

    /** The open circuits, one per circuit however many windows show it. */
    public List<DocumentSummary> listDocuments() {
        return onEditorThread(EditorAccess::openDocuments);
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

    /** One edit call, all or nothing, as one undo step (spec §10; see {@link EditService#edit}). */
    public EditReceipt edit(EditRequest request) {
        return edits.edit(request);
    }

    /** Begins an AI task on an open circuit (spec §3); listeners hear it start. */
    public TaskInfo beginTask(DocumentId document, String title, List<TaskStage> stages) {
        return edits.beginTask(document, title, stages);
    }

    /** Replaces a task's plan of stages and steps. */
    public void updatePlan(String taskId, List<TaskStage> stages) {
        edits.updatePlan(taskId, stages);
    }

    /** Ends a task; listeners hear it end with the elements it changed. */
    public TaskInfo endTask(String taskId) {
        return edits.endTask(taskId);
    }

    /** «Detener»: stops the task; its applied edits stay and its next ones are refused. */
    public void stopTask(String taskId) {
        edits.stopTask(taskId);
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
