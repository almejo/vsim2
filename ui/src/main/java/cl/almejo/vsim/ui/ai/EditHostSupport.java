package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.automation.edit.EditableCircuit;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The editor's side of the AI's edits (IA.1 Task 3): finds the open circuit by its id and gives the automation layer
 * its document, registry, history and version on the EDT. The edits themselves are planned and written by
 * {@code automation.edit}; what they execute goes through the circuit's own {@link History}, like the user's
 * commands, so Ctrl+Z, the modified flag, autosave and the recompile all follow.
 */
final class EditHostSupport {

    private final Function<DocumentId, Optional<OpenDocument>> circuits;
    private final Supplier<List<DefinitionBundle>> library;
    private final EditorQueue queue;

    /** @param circuits the open circuit with an id, or empty when none has it; called on the EDT */
    EditHostSupport(Function<DocumentId, Optional<OpenDocument>> circuits) {
        this(circuits, List::of);
    }

    /** @param library the user's component library, which the AI may place from; called on the EDT */
    EditHostSupport(Function<DocumentId, Optional<OpenDocument>> circuits,
                    Supplier<List<DefinitionBundle>> library) {
        this(circuits, library, new EditorQueue());
    }

    /** @param queue refuses the jobs still waiting for the EDT once the AI is turned off */
    EditHostSupport(Function<DocumentId, Optional<OpenDocument>> circuits,
                    Supplier<List<DefinitionBundle>> library, EditorQueue queue) {
        this.circuits = Objects.requireNonNull(circuits, "circuits");
        this.library = Objects.requireNonNull(library, "library");
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /**
     * Runs {@code work} on the EDT with the circuit {@code document}: at once when called on the EDT («Deshacer lo que
     * hizo la IA» from a button must not wait for a turn of its own thread), later otherwise.
     */
    <T> CompletableFuture<T> onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        CompletableFuture<T> result = SwingUtilities.isEventDispatchThread() ? new CompletableFuture<>()
                : queue.track(new CompletableFuture<>());
        Runnable job = EditorQueue.job(result, future -> {
            OpenDocument circuit = circuits.apply(document).orElseThrow(() -> closed(document));
            future.complete(work.apply(new Circuit(circuit, library.get())));
        });
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
    private record Circuit(OpenDocument openDocument, List<DefinitionBundle> library) implements EditableCircuit {

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
            return new DocumentVersion(openDocument.contentVersion());
        }

        /** Texts as the canvas draws them, so a {@code MakeFrame} wraps its notes whole. */
        @Override
        public TextMeasure textMeasure() {
            return LabelTextMeasure.INSTANCE;
        }
    }
}
