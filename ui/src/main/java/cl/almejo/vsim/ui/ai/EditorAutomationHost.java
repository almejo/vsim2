package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The editor's side of the AI (spec §11): runs the automation layer's work on the EDT over the open circuits. One per
 * application, like the server. Later tasks add the read, edit, simulation and settings services.
 */
public final class EditorAutomationHost implements AutomationHost {

    private static final String ID_PREFIX = "circuit-";

    private final Supplier<List<OpenDocument>> openDocuments;
    /** EDT only. Weak, so a closed circuit's entry goes with it; the counter never gives an id twice. */
    private final Map<OpenDocument, DocumentId> ids = new WeakHashMap<>();
    private long lastId;

    /** @param openDocuments the open circuits, one per circuit however many views it has; called on the EDT */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments) {
        this.openDocuments = Objects.requireNonNull(openDocuments, "openDocuments");
    }

    @Override
    public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                result.complete(work.apply(new Access()));
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private DocumentId idOf(OpenDocument document) {
        return ids.computeIfAbsent(document, newDocument -> new DocumentId(ID_PREFIX + ++lastId));
    }

    private DocumentSummary summaryOf(OpenDocument document) {
        return new DocumentSummary(idOf(document), document.displayName(),
                new DocumentVersion(document.changeCount()), document.isModified());
    }

    /** Valid only during one {@link #onEditorThread} call, on the EDT. */
    private final class Access implements EditorAccess {

        @Override
        public List<DocumentSummary> openDocuments() {
            return openDocuments.get().stream().map(EditorAutomationHost.this::summaryOf).toList();
        }
    }
}
