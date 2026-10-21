package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.edit.EditableCircuit;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** The editor-side services for applying the AI's edits as commands (IA.1 Task 3). */
public interface EditHost {

    /**
     * Runs {@code work} on the editor thread with the open circuit {@code document} and completes the future with what
     * it returned, or exceptionally with what it threw ({@link AutomationError#DOCUMENT_CLOSED} when no open circuit
     * has that id). Called on the editor thread itself it runs at once, so «Deshacer lo que hizo la IA» can use it
     * from a button. By default no circuit can be edited.
     */
    default <T> CompletableFuture<T> onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        return CompletableFuture.failedFuture(new AutomationException(AutomationError.DOCUMENT_CLOSED,
                "No open circuit has the id " + document.value() + "; list the documents again",
                Map.of("document", document.value())));
    }
}
