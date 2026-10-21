package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.edit.EditableCircuit;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.SwingUtilities;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The editor's side of the AI's edits (IA.1 Task 3): finds the open circuit by its id and gives the automation layer
 * its document, registry, history and version on the EDT. The edits themselves are planned and written by
 * {@code automation.edit}; what they execute goes through the circuit's own {@link History}, like the user's
 * commands, so Ctrl+Z, the modified flag, autosave and the recompile all follow.
 */
final class EditHostSupport {

    private final Function<DocumentId, Optional<OpenDocument>> circuits;

    /** @param circuits the open circuit with an id, or empty when none has it; called on the EDT */
    EditHostSupport(Function<DocumentId, Optional<OpenDocument>> circuits) {
        this.circuits = Objects.requireNonNull(circuits, "circuits");
    }

    /**
     * Runs {@code work} on the EDT with the circuit {@code document}: at once when called on the EDT («Deshacer lo que
     * hizo la IA» from a button must not wait for a turn of its own thread), later otherwise.
     */
    <T> CompletableFuture<T> onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable job = () -> {
            try {
                OpenDocument circuit = circuits.apply(document).orElseThrow(() -> closed(document));
                result.complete(work.apply(new Circuit(circuit)));
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            job.run();
        } else {
            SwingUtilities.invokeLater(job);
        }
        return result;
    }

    private static AutomationException closed(DocumentId document) {
        return new AutomationException(AutomationError.DOCUMENT_CLOSED, "The circuit " + document.value()
                + " is not open any more; list the documents again", Map.of("document", document.value()));
    }

    /** One open circuit during one {@link #onCircuit} call. */
    private record Circuit(OpenDocument openDocument) implements EditableCircuit {

        @Override
        public CircuitDocument document() {
            return openDocument.document();
        }

        @Override
        public ComponentTypeRegistry registry() {
            return openDocument.registry();
        }

        @Override
        public History history() {
            return openDocument.history();
        }

        @Override
        public DocumentVersion version() {
            return new DocumentVersion(openDocument.changeCount());
        }
    }
}
