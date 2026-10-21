package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The editor's side of the AI (spec §11): runs the automation layer's work on the EDT over the open circuits. One per
 * application, like the server. Later tasks add the read, edit, simulation and settings services.
 */
public final class EditorAutomationHost implements AutomationHost {

    private static final String ID_PREFIX = "circuit-";

    private final Supplier<List<OpenDocument>> openDocuments;
    private final EditorWindows windows;
    private final Optional<PreferenceProposals> proposals;
    /** Told of every request (any thread): the panel's «Claude Code conectado». */
    private final List<Runnable> requestListeners = new CopyOnWriteArrayList<>();
    /** EDT only. Weak, so a closed circuit's entry goes with it; the counter never gives an id twice. */
    private final Map<OpenDocument, DocumentId> ids = new WeakHashMap<>();
    private long lastId;

    /** @param openDocuments the open circuits, one per circuit however many views it has; called on the EDT */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments) {
        this.openDocuments = Objects.requireNonNull(openDocuments, "openDocuments");
        this.windows = EditorWindows.NONE;
        this.proposals = Optional.empty();
    }

    /**
     * @param windows   opens new circuits and gives each circuit's settings override (spec §7)
     * @param proposals the application's preference proposals
     */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments, EditorWindows windows,
                                PreferenceProposals proposals) {
        this.openDocuments = Objects.requireNonNull(openDocuments, "openDocuments");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.proposals = Optional.of(proposals);
    }

    /** {@code listener} runs (on the caller's thread) at every request until the returned runnable takes it back. */
    public Runnable addRequestListener(Runnable listener) {
        requestListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> requestListeners.remove(listener);
    }

    /** EDT only: the id the AI knows {@code document} by, given now if it has none yet. */
    public DocumentId documentId(OpenDocument document) {
        return idOf(document);
    }

    @Override
    public DocumentId newCircuit(String name) {
        Objects.requireNonNull(name, "name");
        if (windows == EditorWindows.NONE) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor cannot open new circuits");
        }
        return idOf(windows.openNewCircuit(name));
    }

    @Override
    public void setEditorSetting(DocumentId document, EditorSetting setting, String value) {
        OpenDocument open = documentOf(document);
        SettingsOverride override = windows.settingsOverride(open).orElseThrow(() ->
                new AutomationException(AutomationError.NOT_ALLOWED, "This circuit has no window settings"));
        override.set(setting, value);
    }

    @Override
    public String proposePreference(ProposedPreference preference, String value, String reason) {
        return proposalsOrRefuse().propose(preference, value, reason).id();
    }

    @Override
    public CompletableFuture<ProposalAnswer> proposalAnswer(String proposalId) {
        return proposalsOrRefuse().answerOf(proposalId);
    }

    private PreferenceProposals proposalsOrRefuse() {
        return proposals.orElseThrow(() ->
                new AutomationException(AutomationError.NOT_ALLOWED, "This editor takes no preference proposals"));
    }

    /** The open circuit the AI knows as {@code id}; {@link AutomationError#DOCUMENT_CLOSED} when it is not open. */
    private OpenDocument documentOf(DocumentId id) {
        for (OpenDocument document : openDocuments.get()) {
            if (id.equals(ids.get(document))) {
                return document;
            }
        }
        throw new AutomationException(AutomationError.DOCUMENT_CLOSED,
                "The circuit " + id.value() + " is not open; list the documents again");
    }

    @Override
    public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
        requestListeners.forEach(Runnable::run);
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
