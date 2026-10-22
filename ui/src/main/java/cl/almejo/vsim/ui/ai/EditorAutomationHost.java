package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.automation.read.ReadSource;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.automation.simulate.PreparedTruthTable;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.SimulationRequest;
import cl.almejo.vsim.automation.simulate.TruthTableRequest;
import cl.almejo.vsim.automation.edit.EditableCircuit;
import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.ProposedPreference;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.StepPacer;
import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
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
    private final ReadHostSupport reads;
    private final SimulationHostSupport simulation;
    private final EditHostSupport edits;
    private final ExportHostSupport exports;
    private final EditorQueue queue = new EditorQueue();
    /** «De corrido»'s clock and timer: the JDK's delayed executor, never the EDT. */
    private final StepPacer stepPacer;

    /** @param openDocuments the open circuits, one per circuit however many views it has; called on the EDT */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments) {
        this(openDocuments, document -> List.of());
    }

    /** @param windowsOf the windows showing a circuit, the active one first (for the pictures); called on the EDT */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments,
                                Function<OpenDocument, List<ShownWindow>> windowsOf) {
        this(openDocuments, windowsOf, EditorWindows.NONE, Optional.empty());
    }

    /**
     * @param windows   opens new circuits and gives each circuit's settings override (spec §7)
     * @param proposals the application's preference proposals
     */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments, EditorWindows windows,
                                PreferenceProposals proposals) {
        this(openDocuments, document -> List.of(), windows, Optional.of(proposals));
    }

    /** Everything: the windows of each circuit (pictures), new circuits and settings, and the preference proposals. */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments,
                                Function<OpenDocument, List<ShownWindow>> windowsOf, EditorWindows windows,
                                PreferenceProposals proposals) {
        this(openDocuments, windowsOf, windows, Optional.of(proposals), StepPacer.system());
    }

    /** Everything, with «De corrido»'s clock and timer given (tests move them by hand). */
    public EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments,
                                Function<OpenDocument, List<ShownWindow>> windowsOf, EditorWindows windows,
                                PreferenceProposals proposals, StepPacer stepPacer) {
        this(openDocuments, windowsOf, windows, Optional.of(proposals), stepPacer);
    }

    private EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments,
                                 Function<OpenDocument, List<ShownWindow>> windowsOf, EditorWindows windows,
                                 Optional<PreferenceProposals> proposals) {
        this(openDocuments, windowsOf, windows, proposals, StepPacer.system());
    }

    private EditorAutomationHost(Supplier<List<OpenDocument>> openDocuments,
                                 Function<OpenDocument, List<ShownWindow>> windowsOf, EditorWindows windows,
                                 Optional<PreferenceProposals> proposals, StepPacer stepPacer) {
        this.stepPacer = Objects.requireNonNull(stepPacer, "stepPacer");
        this.openDocuments = Objects.requireNonNull(openDocuments, "openDocuments");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.proposals = proposals;
        this.reads = new ReadHostSupport(windowsOf);
        // An id VSim never gave is an INVALID_REFERENCE, a closed circuit's DOCUMENT_CLOSED (documentOf).
        this.simulation = new SimulationHostSupport(id -> Optional.of(documentOf(id)), SwingUtilities::invokeLater,
                queue);
        this.edits = new EditHostSupport(id -> Optional.of(documentOf(id)), this.windows::libraryComponents, queue);
        this.exports = new ExportHostSupport(windowsOf, () -> Path.of(System.getProperty("user.home")),
                ForkJoinPool.commonPool(), SwingUtilities::invokeLater, Clock.systemDefaultZone(),
                FolderOpener.desktop());
    }

    @Override
    public CompletableFuture<SimulationReceipt> simulate(SimulationRequest request) {
        return simulation.simulate(request);
    }

    @Override
    public CompletableFuture<PreparedTruthTable> prepareTruthTable(TruthTableRequest request) {
        return simulation.prepareTruthTable(request);
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

    // --- «Presentar» (IA.2): the mode of the circuit's first window, from its preferences ---

    @Override
    public PresentationSettings presentationSettings(DocumentId document) {
        return windows.presentationSettings(documentOf(document));
    }

    @Override
    public void setPresenting(DocumentId document, boolean presenting) {
        windows.setPresenting(documentOf(document), presenting);
    }

    /**
     * The AI's pace and pause for its task, in the temporary layer of every window of the circuit (spec §7): the
     * first one is read, the others show it; the user's pick in any of them replaces it in all.
     */
    @Override
    public void layerPresentation(DocumentId document, Optional<PresentationPace> pace, Optional<Duration> pause) {
        OpenDocument open = documentOf(document);
        List<SettingsOverride> overrides = windows.settingsOverridesOfEveryWindow(open);
        if (overrides.isEmpty()) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "This circuit has no window settings");
        }
        overrides.forEach(override -> override.layerPresentation(pace, pause));
    }

    @Override
    public void travelToStageOrigin(DocumentId document, GridPoint origin) {
        windows.travelToStageOrigin(documentOf(document), origin);
    }

    /** Texts as the canvas draws them, so a stage's frame encloses its notes (IA.2 storyboard). EDT only. */
    @Override
    public TextMeasure textMeasure() {
        return LabelTextMeasure.INSTANCE;
    }

    @Override
    public StepPacer stepPacer() {
        return stepPacer;
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

    /** «Desactivar» or quitting: the AI's jobs still waiting for the EDT are refused at once and never run. */
    public void refuseQueuedWork() {
        queue.refuse();
    }

    /** «Activar»: the AI's jobs run again. */
    public void acceptQueuedWork() {
        queue.accept();
    }

    @Override
    public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
        requestListeners.forEach(Runnable::run);
        CompletableFuture<T> result = queue.track(new CompletableFuture<>());
        SwingUtilities.invokeLater(() -> {
            if (EditorQueue.skips(result)) {
                return;
            }
            try {
                result.complete(work.apply(new Access()));
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    @Override
    public <T> CompletableFuture<T> onCircuit(DocumentId document, Function<EditableCircuit, T> work) {
        return edits.onCircuit(document, work);
    }

    // --- Reading (IA.1 Task 2): ReadHostSupport does the work; pictures are encoded off the EDT ---

    @Override
    public <T> CompletableFuture<T> reading(DocumentId id, Function<ReadSource, T> work) {
        return onEditorThread(access -> work.apply(reads.source(id, documentOf(id), windows.libraryComponents())));
    }

    // --- Exports (spec §7): ExportHostSupport takes the picture on the EDT and writes the file off it ---

    @Override
    public CompletableFuture<ExportReceipt> export(ExportRequest request) {
        return onEditorThread(access -> {
            OpenDocument document = documentOf(request.document());
            return exports.prepare(request.document(), ReadHostSupport.version(document), document, request);
        }).thenApplyAsync(exports::write, exports.writer());
    }

    @Override
    public CompletableFuture<RegionPicture> pictureOf(DocumentId id, Optional<Bounds> region, boolean asOnScreen) {
        return onEditorThread(access -> {
            OpenDocument document = documentOf(id);
            return Map.entry(ReadHostSupport.version(document), reads.paintRegion(document, region, asOnScreen));
        }).thenApplyAsync(painted -> ReadHostSupport.regionPicture(id, painted.getKey(), asOnScreen,
                painted.getValue()));
    }

    @Override
    public CompletableFuture<WindowPicture> pictureOfWindow(DocumentId id) {
        return onEditorThread(access -> {
            OpenDocument document = documentOf(id);
            return Map.entry(ReadHostSupport.version(document), reads.paintWindow(document));
        }).thenApplyAsync(painted -> ReadHostSupport.windowPicture(id, painted.getKey(), painted.getValue()));
    }

    /** EDT only: the open circuit {@code id} names, or the refusal that says whether it was closed or never given. */
    private OpenDocument documentOf(DocumentId id) {
        for (OpenDocument document : openDocuments.get()) {
            if (idOf(document).equals(id)) {
                return document;
            }
        }
        if (wasGiven(id)) {
            throw new AutomationException(AutomationError.DOCUMENT_CLOSED,
                    "Circuit " + id.value() + " was closed; list the documents again");
        }
        throw new AutomationException(AutomationError.INVALID_REFERENCE,
                "No circuit " + id.value() + "; list the documents to get their ids");
    }

    private boolean wasGiven(DocumentId id) {
        if (!id.value().startsWith(ID_PREFIX)) {
            return false;
        }
        try {
            long number = Long.parseLong(id.value().substring(ID_PREFIX.length()));
            return number >= 1 && number <= lastId;
        } catch (NumberFormatException exception) {
            return false;
        }
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
