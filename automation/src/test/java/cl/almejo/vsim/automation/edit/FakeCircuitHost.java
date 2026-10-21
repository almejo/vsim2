package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A headless editor for the edit tests: open circuits with their own history and registry, and the work of the AI run
 * at once on the calling thread (standing in for the editor thread).
 */
final class FakeCircuitHost implements AutomationHost {

    private static final ComponentTypeRegistry BASE_REGISTRY = StandardComponentTypes.registry();

    private final Map<DocumentId, Circuit> circuits = new LinkedHashMap<>();

    Circuit open(String id) {
        Circuit circuit = new Circuit(new CircuitDocument());
        circuits.put(new DocumentId(id), circuit);
        return circuit;
    }

    void close(String id) {
        circuits.remove(new DocumentId(id));
    }

    @Override
    public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
        EditorAccess access = () -> circuits.entrySet().stream()
                .map(entry -> new DocumentSummary(entry.getKey(), entry.getKey().value(), entry.getValue().version(),
                        entry.getValue().history().isModified()))
                .toList();
        return run(() -> work.apply(access));
    }

    @Override
    public <T> CompletableFuture<T> onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        Circuit circuit = circuits.get(document);
        if (circuit == null) {
            return CompletableFuture.failedFuture(new AutomationException(AutomationError.DOCUMENT_CLOSED, "closed"));
        }
        return run(() -> work.apply(circuit));
    }

    private static <T> CompletableFuture<T> run(java.util.function.Supplier<T> work) {
        try {
            return CompletableFuture.completedFuture(work.get());
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** One open circuit: its version grows with every change of its history, like {@code OpenDocument}. */
    static final class Circuit implements EditableCircuit {

        private final CircuitDocument document;
        private final ComponentTypeRegistry registry;
        private final History history;
        private long changeCount;
        private List<DefinitionBundle> library = List.of();

        Circuit(CircuitDocument document) {
            this.document = document;
            this.registry = BASE_REGISTRY.copy();
            this.registry.addResolver(SubcircuitTypes.resolverFor(document));
            this.history = new History(document);
            history.addListener(changed -> changeCount++);
        }

        @Override
        public CircuitDocument document() {
            return document;
        }

        @Override
        public ComponentTypeRegistry registry() {
            return registry;
        }

        @Override
        public History history() {
            return history;
        }

        @Override
        public DocumentVersion version() {
            return new DocumentVersion(changeCount);
        }

        @Override
        public List<DefinitionBundle> library() {
            return library;
        }

        void useLibrary(List<DefinitionBundle> newLibrary) {
            library = List.copyOf(newLibrary);
        }

        List<String> elementIds() {
            return document.elements().stream().map(element -> element.id().value()).toList();
        }
    }
}
