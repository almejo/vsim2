package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.ai.AiExtension;
import cl.almejo.vsim.ui.ai.AiPlaceMarks;
import cl.almejo.vsim.ui.ai.AiServerService;
import cl.almejo.vsim.ui.ai.SessionPort;
import cl.almejo.vsim.ui.ai.EditorAutomationHost;
import cl.almejo.vsim.ui.ai.ShownWindow;
import cl.almejo.vsim.ui.ai.EditorWindows;
import cl.almejo.vsim.ui.ai.PreferenceProposals;
import cl.almejo.vsim.ui.ai.SettingsOverride;
import cl.almejo.vsim.ui.ai.PresentationModeWatcher;
import cl.almejo.vsim.ui.ai.PresentationPreferences;
import cl.almejo.vsim.ui.ai.present.AutomationPresentationControls;
import cl.almejo.vsim.ui.ai.present.PresentationControls;
import cl.almejo.vsim.ui.ai.present.PresentationEvents;
import cl.almejo.vsim.ui.ai.present.PresentationExtension;
import cl.almejo.vsim.ui.appearance.AppearanceExtension;
import cl.almejo.vsim.ui.examples.ExamplesFeature;
import cl.almejo.vsim.ui.logisim.LogisimExportExtension;
import cl.almejo.vsim.ui.logisim.LogisimImportExtension;
import cl.almejo.vsim.ui.capture.CaptureButtonExtension;
import cl.almejo.vsim.ui.capture.WindowExportExtension;
import cl.almejo.vsim.ui.export.ExportImageExtension;
import cl.almejo.vsim.ui.memory.MemoryMapExtension;
import cl.almejo.vsim.ui.print.ExportPdfExtension;
import cl.almejo.vsim.ui.print.PrintExtension;
import cl.almejo.vsim.ui.print.PrinterAccess;
import cl.almejo.vsim.ui.sheets.SheetAnchors;
import cl.almejo.vsim.ui.sheets.SheetsExtension;
import cl.almejo.vsim.ui.favorites.FavoritesExtension;
import cl.almejo.vsim.ui.frames.FramesExtension;
import cl.almejo.vsim.ui.sticky.StickyNotesTabExtension;
import cl.almejo.vsim.ui.labels.TextToolExtension;
import cl.almejo.vsim.ui.sticky.StickyNotesExtension;
import cl.almejo.vsim.ui.resize.PartResizeExtension;
import cl.almejo.vsim.ui.grid.GridExtension;
import cl.almejo.vsim.ui.busvalues.BusValuesExtension;
import cl.almejo.vsim.ui.magnifier.MagnifierExtension;
import cl.almejo.vsim.ui.guides.GuidesExtension;
import cl.almejo.vsim.ui.snap.SnapExtension;
import cl.almejo.vsim.ui.karnaugh.KarnaughExtension;
import cl.almejo.vsim.ui.terminal.TerminalExtension;
import cl.almejo.vsim.ui.wiring.BusPinMenuExtension;
import cl.almejo.vsim.ui.wiring.TidyWiresExtension;
import cl.almejo.vsim.ui.wiring.WiresFollowExtension;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.ActionIcons;
import cl.almejo.vsim.ui.library.LibraryDialogs;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.library.TemplatesExtension;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.groups.GroupsExtension;
import cl.almejo.vsim.ui.memory.MemoryEditorExtension;
import cl.almejo.vsim.ui.memory.MemoryEditorLauncher;
import cl.almejo.vsim.ui.memory.MemoryLinks;
import cl.almejo.vsim.ui.memory.MemoryMapActions;
import cl.almejo.vsim.ui.preferences.PreferencesFeature;
import cl.almejo.vsim.ui.quickinsert.QuickInsertExtension;
import cl.almejo.vsim.ui.recents.RecentComponentsExtension;
import cl.almejo.vsim.ui.search.FindExtension;
import cl.almejo.vsim.ui.session.AutosaveService;
import cl.almejo.vsim.ui.session.RecentFiles;
import cl.almejo.vsim.ui.session.RecentFilesFeature;
import cl.almejo.vsim.ui.session.RecoveryChoices;
import cl.almejo.vsim.ui.session.RecoveryCopy;
import cl.almejo.vsim.ui.session.RecoveryPrompt;
import cl.almejo.vsim.ui.session.RecoveryStore;
import cl.almejo.vsim.ui.session.SessionStore;
import cl.almejo.vsim.ui.session.StartupSequence;
import cl.almejo.vsim.ui.session.TabState;
import cl.almejo.vsim.ui.session.WindowSession;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.stepping.StepModeExtension;
import cl.almejo.vsim.ui.theme.TextFontManager;
import cl.almejo.vsim.ui.theme.ThemeManager;
import cl.almejo.vsim.ui.tree.CircuitTreeExtension;
import cl.almejo.vsim.ui.truthtable.TruthTableExtension;
import cl.almejo.vsim.ui.tunnel.TunnelsExtension;
import cl.almejo.vsim.ui.waveform.WaveformsExtension;
import cl.almejo.vsim.ui.window.CircuitWindow;
import cl.almejo.vsim.ui.window.CloseSequence;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.NewViewPlacement;
import cl.almejo.vsim.ui.window.PlaceMarks;
import cl.almejo.vsim.ui.window.WindowMenu;
import cl.almejo.vsim.ui.window.panes.SplitViewsExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.Executor;

/**
 * Open windows and documents of the running application (EDT only). A window holds groups of tabs, one per document
 * (spec §4); a document closes when no window holds a tab of it any more.
 */
public final class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    /** Every way of opening a circuit file goes through it, so a file that cannot be read is explained to the user. */
    private final OpeningFiles openingFiles = new OpeningFiles(registry, new SwingFileProblemDialog());
    private final AppPreferences preferences;
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private final ComponentLibrary library = new ComponentLibrary();
    private final LibraryService libraryService;
    private final EditorServices services;
    private final ThemeManager themeManager;
    private final TextFontManager textFontManager;
    private final DocumentWindows<CircuitWindow> windows = new DocumentWindows<>();
    private SystemOpenFiles systemOpenFiles = new SystemOpenFiles();
    private final RecentFiles recentFiles;
    private final SessionStore sessionStore;
    private final RecoveryStore recoveryStore;
    private final AutosaveService autosave;
    // Autosave shutdown stops the timer, drains the pending writes, then deletes this run's recovery folder.
    private final SessionEnding sessionEnding = new SessionEnding(this::saveSession, this::shutdownAutosave);
    private Path lastDirectory;
    // IA.1: one AI server per process, an application service (A§4.2); off until started.
    private final AiServerService aiServer;
    private final EditorAutomationHost automationHost;
    private final PreferenceProposals proposals;
    // IA.2: the API the server and «Presentar» share, the panel's buttons over it, and the watcher that tells the
    // API when a window's mode changes or a circuit closes.
    private final AutomationApi automationApi;
    private final PresentationControls presentationControls;
    private final PresentationModeWatcher presentationWatcher;
    /** IA.2: «Presentar» in every window; the AI asks for it through {@link PresentationExtension#aiAskedForPresenting}. */
    private final PresentationExtension presentation;
    /** Each window reads the preferences through its own layer: the AI's temporary settings (spec §7). */
    private final Map<CircuitWindow, AppPreferences> windowPreferences = new WeakHashMap<>();
    /** What the AI's host may do with the windows; stateless, one for the whole application. */
    private final ApplicationWindows applicationWindows = new ApplicationWindows();
    /** Where each document's print sheets are anchored while some view shows them; one for the whole application. */
    private final SheetAnchors sheetAnchors = new SheetAnchors();
    /** Where the AI works now: the robot on the tabs of every window (fix round D, item 5n). */
    private final AiPlaceMarks placeMarks = new AiPlaceMarks();

    public Application(AppPreferences preferences) {
        this.preferences = preferences;
        this.libraryService = new LibraryService(preferences, library, registry);
        this.proposals = new PreferenceProposals(preferences);
        this.automationHost = new EditorAutomationHost(windows::documents,
                document -> ShownWindow.activeFirst(windows.windowsOf(document)), applicationWindows, proposals,
                sheetAnchors);
        this.automationApi = new AutomationApi(automationHost);
        this.presentationControls = new AutomationPresentationControls(automationApi);
        this.presentationWatcher = new PresentationModeWatcher(windows::documents,
                applicationWindows::presentationSettings, automationHost::documentId,
                PresentationModeWatcher.Target.of(automationApi), presentationModeThread());
        preferences.addListener(presentationWatcher::refresh); // lives as long as the application
        this.aiServer = new AiServerService(preferences, automationApi,
                new AiServerService.QueuedWork() {
                    @Override
                    public void refuse() {
                        automationHost.refuseQueuedWork();
                    }

                    @Override
                    public void accept() {
                        automationHost.acceptQueuedWork();
                    }
                }, SessionPort.fromLaunch(System::getProperty, System::getenv));
        automationHost.addRequestListener(aiServer::requestServed);
        aiServer.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                if (!aiServer.isRunning() && !aiServer.isStarting()) {
                    proposals.withdrawAll(); // turning it off revokes everything (spec §9.1)
                    automationApi.stopPresenting(); // and ends every wait for «Siguiente» (IA.2)
                }
            }

            @Override
            public void taskStarted(TaskInfo task) {
                placeMarks.taskStarted(task);
            }

            @Override
            public void taskEnded(TaskInfo task, Set<String> changedElementIds) {
                placeMarks.taskEnded(task);
            }

            @Override
            public void taskStopped(TaskInfo task) {
                placeMarks.taskEnded(task);
            }
        });
        automationHost.addPlaceListener(placeMarks::touched); // lives as long as the application
        this.presentation = new PresentationExtension(
                PresentationEvents.of(automationApi, automationHost::addRequestListener),
                presentationControls, automationHost::documentId, placeMarks);
        this.services = new EditorServices(PainterRegistry.standard(), appearance, preferences, library,
                editorExtensions(libraryService, SwingLibraryDialogs.INSTANCE, this::initialDirectory,
                        this::rememberDirectory,
                        new AiExtension(aiServer, automationHost::documentId, proposals,
                                applicationWindows::settingsOverride), presentation, new PrintExtension(sheetAnchors),
                        sheetAnchors));
        // Lives as long as the application: it follows the preferences and is never disposed.
        this.themeManager = ThemeManager.forCurrentPlatform(preferences, appearance);
        this.textFontManager = new TextFontManager(preferences);
        this.recentFiles = new RecentFiles(preferences);
        this.sessionStore = new SessionStore(preferences);
        this.recoveryStore = new RecoveryStore(preferences.dataDirectory().resolve("recovery"));
        this.autosave = AutosaveService.create(recoveryStore);
    }

    /**
     * The extensions with the import's file chooser starting in the user's home and remembering nothing, and no
     * printers (tests, also outside this package: every extension in every pane; none may reach a real printer).
     */
    public static List<EditorExtension> editorExtensions(LibraryService libraryService, LibraryDialogs libraryDialogs) {
        SheetAnchors sheetAnchors = new SheetAnchors();
        return editorExtensions(libraryService, libraryDialogs, () -> Path.of(System.getProperty("user.home")),
                path -> { }, AiExtension.inactive(), PresentationExtension.inactive(),
                new PrintExtension(PrinterAccess.none(), sheetAnchors), sheetAnchors);
    }

    /**
     * The extensions of every editor view, in install order. Package-private so a test can install them all.
     * {@code startDirectory} and {@code chosen} are the folder «Abrir» starts in and remembers, shared with the
     * table import's file chooser. {@code ai} is the AI's panel and status line over the application's server,
     * {@code presentation} its «Presentar» toggle and floating panel, {@code print} «Imprimir…» over the system's
     * printers or none, {@code sheetAnchors} where the shown print sheets of each document are anchored.
     */
    static List<EditorExtension> editorExtensions(LibraryService libraryService, LibraryDialogs libraryDialogs,
                                                  Supplier<Path> startDirectory, Consumer<Path> chosen,
                                                  AiExtension ai, PresentationExtension presentation,
                                                  PrintExtension print, SheetAnchors sheetAnchors) {
        List<EditorExtension> editorExtensions = new ArrayList<>();
        // M6: waveforms before ActionIcons, recents after PreferencesFeature, shortcuts last. M6.1: the step mode and
        // the truth table before ActionIcons, the examples after RecentFilesFeature (its submenu goes after «Recientes»).
        editorExtensions.add(new CircuitTreeExtension()); // the circuit tree above the palette
        editorExtensions.add(new TemplatesExtension(libraryService, libraryDialogs));
        editorExtensions.add(new AppearanceExtension(libraryService, libraryDialogs));
        editorExtensions.add(new WaveformsExtension());
        editorExtensions.add(new StepModeExtension());
        editorExtensions.add(new TruthTableExtension());
        editorExtensions.add(new ExportImageExtension());
        editorExtensions.add(new ExportPdfExtension(sheetAnchors));
        editorExtensions.add(new LogisimExportExtension()); // after «PDF…» in Archivo ▸ Exportar
        editorExtensions.add(new WindowExportExtension()); // after the other exports: lands right after «Imagen…»
        editorExtensions.add(new CaptureButtonExtension()); // the screenshot button at the end of the toolbar
        editorExtensions.add(print); // its item goes right after «Guardar como…»
        editorExtensions.add(new TidyWiresExtension());
        // the «Texto» tool before sticky notes: its button lands after «Nota adhesiva»
        editorExtensions.add(new TextToolExtension());
        // sticky notes before frames: their tool lands after «Marco» and their gestures are asked first (on top)
        editorExtensions.add(new StickyNotesExtension());
        editorExtensions.add(new PartResizeExtension()); // the tab of a resizable part: after the notes, before frames
        editorExtensions.add(new FramesExtension());
        editorExtensions.add(new StickyNotesTabExtension());
        MemoryLinks memoryLinks = MemoryLinks.standard(); // one for every window (and one watcher per document)
        MemoryEditorLauncher memoryLauncher = new MemoryEditorLauncher(memoryLinks);
        // the «Mapa de memoria» tab after «Notas»: a click on a cell opens the content editor there
        editorExtensions.add(new MemoryMapExtension(view -> MemoryMapActions.of(view, memoryLauncher)));
        editorExtensions.add(new WiresFollowExtension());
        editorExtensions.add(new BusPinMenuExtension());
        editorExtensions.add(new GroupsExtension()); // «Bits por pin» and the «Grupos» window of bus parts
        editorExtensions.add(new GridExtension());
        editorExtensions.add(new SnapExtension());
        editorExtensions.add(new GuidesExtension());
        editorExtensions.add(new SheetsExtension(sheetAnchors)); // its Ver item right after «Borrar guías»
        editorExtensions.add(new BusValuesExtension());
        editorExtensions.add(new MagnifierExtension()); // its Ver item right after «Valores de los buses»
        editorExtensions.add(new SplitViewsExtension());
        editorExtensions.add(new KarnaughExtension(libraryService, startDirectory, chosen));
        editorExtensions.add(new FindExtension());
        editorExtensions.add(new TunnelsExtension());
        editorExtensions.add(new FavoritesExtension());
        editorExtensions.add(new RecentComponentsExtension()); // one list for every window: created once here
        editorExtensions.add(new QuickInsertExtension()); // its Insert key before the shortcuts extension
        editorExtensions.add(ai); // IA.1: last in Herramientas, before the shortcuts see its item
        editorExtensions.add(presentation); // IA.2: last in Ver and the toolbar, before the icons and shortcuts
        editorExtensions.add(new MemoryEditorExtension(memoryLauncher, memoryLinks));
        editorExtensions.add(new TerminalExtension()); // Terminal windows, menus and sounds, after the memories
        editorExtensions.add(new ActionIcons());
        editorExtensions.add(new PreferencesFeature(ai.server()));
        editorExtensions.add(new RecentFilesFeature());
        editorExtensions.add(new ExamplesFeature());
        editorExtensions.add(new LogisimImportExtension(startDirectory, chosen)); // the last item of Importar
        editorExtensions.add(new ShortcutsExtension());
        return List.copyOf(editorExtensions);
    }

    public EditorServices services() {
        return services;
    }

    public AppPreferences preferences() {
        return preferences;
    }

    public Appearance appearance() {
        return appearance;
    }

    public ComponentLibrary library() {
        return library;
    }

    public LibraryService libraryService() {
        return libraryService;
    }

    public AiServerService aiServer() {
        return aiServer;
    }

    public PresentationExtension presentation() {
        return presentation;
    }

    public ComponentTypeRegistry registry() {
        return registry;
    }

    /**
     * M6-B order at start: recovery dialog → last session (a recovered copy replaces its file) → command line. The
     * recovery folder is scanned off the EDT (it reads every copy); the dialog and the windows come on the EDT.
     */
    public void start(List<Path> files) {
        start(files, () -> { });
    }

    /**
     * Starts and runs {@code beforeWindows} on the recovery-scan thread, off the EDT, before the first window opens
     * (the splash's minimum time, {@link StartupSplash}).
     */
    public void start(List<Path> files, Runnable beforeWindows) {
        themeManager.start();
        textFontManager.start();
        libraryService.start(); // spec §5.6: read the library before the first palette is shown
        aiServer.startAtLaunch(System.err);
        // Not a daemon: it keeps the JVM alive until the EDT has the rest of the start queued.
        Thread scan = new Thread(() -> {
            Optional<RecoveryChoices<RecoveryCopy>> choices = Optional.empty();
            try {
                choices = scanRecoveryCopies();
                beforeWindows.run();
            } finally {
                // Even after an Error on this thread the application starts, then without a recovery offer.
                Optional<RecoveryChoices<RecoveryCopy>> offered = choices;
                SwingUtilities.invokeLater(() -> finishStart(files, offered));
            }
        }, "vsim-recovery-scan");
        scan.start();
    }

    /**
     * Where the «Presentar» mode watcher tells the API of a mode change: one daemon thread of its own, so the changes
     * arrive in order and never hold a shared pool while they wait for the editor (review M6).
     */
    private static Executor presentationModeThread() {
        return DaemonThreads.single("vsim-ai-presentation-mode");
    }

    /**
     * The EDT half of the start. Each opening is guarded, and whatever fails the start ends with at least one window
     * and autosave running ({@link StartupSteps}).
     */
    private void finishStart(List<Path> files, Optional<RecoveryChoices<RecoveryCopy>> choices) {
        List<Path> components = files.stream().filter(LibraryDirectory::isComponentFile).toList();
        List<Path> circuits = files.stream().filter(file -> !components.contains(file)).toList();
        List<RecoveryCopy> reopened = new ArrayList<>();
        // The files that do not open at the start (session, recovered copies, command line) get one message together.
        StartupSteps.run(() -> openingFiles.collecting(() -> {
            List<RecoveryCopy> recovered = choices.map(this::askAboutRecoveryCopies).orElse(List.of());
            StartupSequence.Plan plan = StartupSequence.plan(recovered, preferences.restoreSession(), sessionStore.load(),
                    circuits, Files::isRegularFile);
            // Every file opens once; then the session's windows come back with their tabs (spec §4.7).
            Map<Path, OpenDocument> opened = new LinkedHashMap<>();
            List<OpenDocument> untitled = new ArrayList<>();
            StartupSteps.openEach(plan.openings(), opening -> {
                switch (opening) {
                    case StartupSequence.Recovered copy -> openRecovered(copy, reopened).ifPresent(document ->
                            copy.copy().originalPath().map(FileNames::normalized).ifPresentOrElse(
                                    original -> opened.put(original, document), () -> untitled.add(document)));
                    case StartupSequence.FromSession session -> read(session.path(), null).ifPresent(document -> {
                        rememberDirectory(session.path());
                        opened.put(FileNames.normalized(session.path()), document);
                    });
                }
            });
            List<String> notOpened = new ArrayList<>(plan.missing().stream().map(Path::toString).toList());
            StartupSteps.openEach(plan.windows(), window -> restoreWindow(window, opened).forEach(tab ->
                    notOpened.add(Messages.format("session.missingComponent", tab.definitionName().orElseThrow(),
                            tab.path().toString()))));
            // A recovered copy not in the session, or a file whose window could not come back: a window of its own.
            List<OpenDocument> unplaced = new ArrayList<>(untitled);
            opened.values().stream().filter(document -> windows.windowsOf(document).isEmpty())
                    .forEach(unplaced::add);
            StartupSteps.openEach(unplaced, document -> showViews(document,
                    document.path().flatMap(recentFiles::stateFor).stream().toList()));
            if (!notOpened.isEmpty()) {
                JOptionPane.showMessageDialog(null, Messages.format("session.missing", String.join(", ", notOpened)),
                        Messages.text("session.missing.title"), JOptionPane.WARNING_MESSAGE);
            }
            // Through the already-open check (a session file is only focused, never opened twice), as new groups of
            // the active window (the last restored one); one file each so a failure skips only that one.
            StartupSteps.openEach(plan.commandLineFiles(), file -> openPaths(List.of(file), null));
        }), () -> !windows.isEmpty(), this::newDocument, () -> {
            if (!components.isEmpty()) {
                // A component file on the command line is imported into the library, as a drop on the window would.
                windows.all().get(0).openOrImport(components);
            }
        }, () -> startAutosave(reopened));
        // Files the system sent while the start was running (macOS open-file events) open now, after the start's own.
        systemOpenFiles.attach(this::openFromSystem);
    }

    /**
     * The window system asks to open these files (macOS Finder double click, drop on the Dock icon): the same path as
     * the command line. Component files are imported into the library; with no window left one is made first.
     */
    void openFromSystem(List<Path> files) {
        List<Path> components = files.stream().filter(LibraryDirectory::isComponentFile).toList();
        List<Path> circuits = files.stream().filter(file -> !components.contains(file)).toList();
        StartupSteps.openEach(circuits, file -> openPaths(List.of(file), null));
        if (!components.isEmpty()) {
            if (windows.isEmpty()) {
                newDocument();
            }
            windows.all().get(0).openOrImport(components);
        }
    }

    /** Where the system's open-file events wait until the start is over; set before {@link #start}. */
    public void setSystemOpenFiles(SystemOpenFiles incoming) {
        systemOpenFiles = Objects.requireNonNull(incoming);
    }

    private void startAutosave(List<RecoveryCopy> reopened) {
        sessionEnding.started();
        autosave.start();
        if (!reopened.isEmpty()) {
            // The recovered documents get a copy of this run at once; each old copy goes only once the new copy of
            // its document is on disk (openRecovered asked for that), so a failing write keeps the old one.
            autosave.tick();
        }
    }

    /** A new window with a new empty document (the start, when nothing else opened). */
    public CircuitWindow newDocument() {
        return showInNewWindow(OpenDocument.createEmpty(registry), window -> {
        });
    }

    /** Archivo ▸ Nuevo from {@code requester}: a new empty document as a new group of tabs there. */
    public void newDocument(CircuitWindow requester) {
        showIn(Optional.of(requester), OpenDocument.createEmpty(registry), Optional.empty());
    }

    /**
     * Ventana ▸ «Nueva ventana con este circuito»: a new window with a new main tab of the active tab's document, 30 px
     * down-right of its source, same zoom and center.
     */
    public CircuitWindow newWindow(CircuitWindow source) {
        NewViewPlacement placement = NewViewPlacement.after(source.getLocation(), source.viewState());
        // setLocation also cancels the platform placement of the constructor
        return showInNewWindow(source.activeDocument(), window -> placement.applyTo(
                state -> window.tabs().setInitialViewState(window.activeView(), state), window::setLocation));
    }

    /**
     * Ventana ▸ «Mover el grupo a una ventana nueva», or a group dropped outside every strip (spec §4.3, §4.6): a new
     * window at {@code location}, the size of {@code source}, holding the group.
     */
    public void moveGroupToNewWindow(CircuitWindow source, OpenDocument document, Point location) {
        CircuitWindow created = createWindow();
        source.tabs().moveGroupTo(document, created.tabs(), 0);
        created.setBounds(location.x, location.y, source.getWidth(), source.getHeight());
        created.setVisible(true);
    }

    public void open(CircuitWindow requester) {
        // A chosen component file is imported into the library instead of being read as a circuit (as a drop does).
        FileDialogs.chooseOpen(requester, initialDirectory()).ifPresent(path -> requester.openOrImport(List.of(path)));
    }

    /**
     * Opens each file as a new group in {@code requester}, or, when nothing asked (start-up, command line, the
     * system's open-file events), in the active window (a new window only when there is none), or focuses the main
     * tab of the file when it is open already (spec §10.2). The pristine placeholder documents of a window that got
     * something new go away.
     */
    public void openPaths(List<Path> paths, CircuitWindow requester) {
        Set<CircuitWindow> received = new LinkedHashSet<>();
        for (Path path : paths) {
            switch (windows.targetFor(path, Optional.ofNullable(requester))) {
                case OpeningTarget.FocusTab<CircuitWindow> focusTab -> {
                    focus(focusTab.window());
                    focusTab.window().activateMainTab(focusTab.document());
                }
                case OpeningTarget.NewGroup<CircuitWindow> newGroup -> {
                    if (openFile(path, newGroup.window())) {
                        received.add(newGroup.window());
                    }
                }
                case OpeningTarget.NewWindow<CircuitWindow> newWindow -> openFile(path, null);
            }
        }
        received.forEach(window -> {
            closeReplaced(window);
            if (window != requester) {
                focus(window); // a file sent from outside brings its window to the front
            }
        });
    }

    /** Reads {@code path} and shows it in {@code requester}, or in a new window when it is null; false if unread. */
    private boolean openFile(Path path, CircuitWindow requester) {
        Optional<OpenDocument> document = read(path, requester);
        if (document.isEmpty()) {
            return false;
        }
        rememberDirectory(path);
        recentFiles.touch(path); // never throws: a Recientes failure never blocks opening (P3)
        // Assumption 19: whenever the file is opened
        showIn(Optional.ofNullable(requester), document.get(), recentFiles.stateFor(path));
        return true;
    }

    /** The pristine documents of {@code requester} go once something was opened from it (Abrir, Ejemplos). */
    private void closeReplaced(CircuitWindow requester) {
        for (OpenDocument replaced : windows.replacedBy(requester)) {
            windows.windowsOf(replaced).forEach(window -> window.closeGroup(replaced));
        }
    }

    /**
     * «Circuito desde tabla de verdad» ▸ Crear: an untitled document named {@code name} whose first undo step is
     * {@code creation}, as a new group of {@code requester}. The wizard that asked is a dialog over the window, so the
     * window is brought to the front once the dialog's event is over.
     *
     * @return the editor of the new tab
     */
    public EditorView openNewCircuit(String name, Command creation, CircuitWindow requester) {
        OpenDocument created = OpenDocument.createEmpty(registry);
        // Not a user edit: the name is what the window shows until the document is saved.
        created.document().setName(name);
        created.execute(creation);
        EditorView view = showIn(Optional.of(requester), created, Optional.empty());
        SwingUtilities.invokeLater(() -> focus(requester));
        return view;
    }

    /**
     * Archivo ▸ Ejemplos (M6.1-A): {@code document} as a new group of {@code requester}, an untitled, unmodified
     * document, so Guardar asks for a file name and nothing is added to Recientes. Like Abrir, the pristine documents
     * of the requester go away.
     *
     * @return the editor of the new tab
     */
    public EditorView openUntitled(CircuitDocument document, CircuitWindow requester) {
        return openUntitled(document, "", requester);
    }

    /**
     * Like {@link #openUntitled(CircuitDocument, CircuitWindow)}, the tab showing {@code windowName} until the
     * document is saved instead of the circuit's own name (an import shows its file's name); blank shows the circuit's.
     */
    public EditorView openUntitled(CircuitDocument document, String windowName, CircuitWindow requester) {
        OpenDocument opened = new OpenDocument(document, registry, null, false);
        opened.showUntitledAs(windowName);
        EditorView view = showIn(Optional.ofNullable(requester), opened, Optional.empty());
        closeReplaced(requester);
        return view;
    }

    /** Asks once per modified document; true when all windows were closed. The session is remembered first. */
    public boolean exit() {
        // a half-typed inline edit counts as a change, in whichever tab of the document it is open
        boolean confirmed = CloseSequence.confirm(windows.all().stream()
                        .flatMap(window -> window.inlineEditors().stream()).toList(),
                () -> windows.documents().stream()
                        .allMatch(document -> windows.windowsOf(document).get(0).confirmSave(document)));
        if (!confirmed) {
            return false;
        }
        sessionEnding.exiting();
        windows.all().forEach(CircuitWindow::closeQuietly);
        return true;
    }

    /** Whether no window but {@code window} holds a tab of {@code document} (closing it asks to save). */
    public boolean isLastHolder(OpenDocument document, CircuitWindow window) {
        return windows.isLastHolder(document, window);
    }

    /** The Ventana list and «▾»: every tab of every window, grouped by window; {@code current}'s active tab checked. */
    public List<WindowMenu.Entry> windowEntries(CircuitWindow current) {
        return windows.menuEntries(window -> window.tabEntries(window == current, () -> focus(window)));
    }

    /** Where the AI works now, for the tabs of every window. */
    public PlaceMarks placeMarks() {
        return placeMarks;
    }

    /** {@code window} was activated: files sent from outside and the AI's new circuits go there now. */
    public void windowActivated(CircuitWindow window) {
        windows.activated(window);
    }

    /** {@code window} added or removed a tab of {@code document}: it holds the document, and titles renumber. */
    public void tabsChanged(OpenDocument document, CircuitWindow window) {
        windows.add(document, window);
    }

    /** {@code document}'s group in {@code window} is about to close: Recientes keeps where it was. */
    public void groupClosing(OpenDocument document, CircuitWindow window) {
        rememberWindowState(window, document);
    }

    /**
     * {@code document}'s group in {@code window} closed or moved away. The document stops (simulation thread included)
     * and loses its recovery copy when no window holds it any more.
     */
    public void groupClosed(OpenDocument document, CircuitWindow window) {
        if (windows.remove(document, window)) {
            closeDocument(document);
        }
        presentationWatcher.refresh(); // a closed circuit's step waiting for «Siguiente» is dropped
    }

    private void closeDocument(OpenDocument document) {
        autosave.untrack(document);
        document.close();
    }

    /** Runs {@code listener} after every window opens or closes (view numbers may have changed). */
    public void addWindowsListener(Runnable listener) {
        windows.addChangeListener(listener);
    }

    public void removeWindowsListener(Runnable listener) {
        windows.removeChangeListener(listener);
    }

    /**
     * The number of main tab {@code mainTabIndex} of {@code document} in {@code window} among all its main tabs, across
     * windows in opening order; 0 when it is the only one.
     */
    public int viewNumber(OpenDocument document, CircuitWindow window, int mainTabIndex) {
        return windows.viewNumber(document, window, mainTabIndex, CircuitWindow::mainTabCount);
    }

    public Path initialDirectory() {
        if (lastDirectory != null) {
            return lastDirectory;
        }
        for (String candidate : List.of("examples", "../examples")) {
            Path examples = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(examples)) {
                return examples;
            }
        }
        return Path.of(System.getProperty("user.home"));
    }

    public void rememberDirectory(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            lastDirectory = parent;
        }
    }

    /**
     * Called by a window after disposing itself (its bounds are still readable). The documents it was the last holder
     * of stop (simulation thread included) and lose their recovery copy; the last window of the application also ends
     * the session (Assumption 22) and, on a clean exit, this run's recovery folder goes.
     */
    public void windowClosed(CircuitWindow window) {
        sessionEnding.windowClosing(windows.all().equals(List.of(window)));
        windows.documentsOf(window).forEach(document -> rememberWindowState(window, document));
        windowPreferences.remove(window);
        windows.removeWindow(window).forEach(this::closeDocument);
        presentationWatcher.refresh(); // a closed circuit's step waiting for «Siguiente» is dropped
        sessionEnding.windowClosed(windows.isEmpty());
        if (windows.isEmpty()) {
            libraryService.close();
            aiServer.quit(() -> System.exit(0)); // the server stops off the EDT, then VSim ends (review M1)
        }
    }

    private Optional<OpenDocument> read(Path path, CircuitWindow requester) {
        return openingFiles.read(path, requester);
    }

    /**
     * Off the EDT: deletes the copies older than 30 days, then lists and reads the copies of runs that did not close
     * properly. Empty when there is nothing to offer; a failure is logged and offers nothing.
     */
    private Optional<RecoveryChoices<RecoveryCopy>> scanRecoveryCopies() {
        try {
            recoveryStore.cleanUp(Instant.now());
            List<RecoveryCopy> orphans = recoveryStore.orphans();
            if (orphans.isEmpty()) {
                return Optional.empty();
            }
            CircuitReader reader = new CircuitReader(registry);
            List<RecoveryChoices.Candidate<RecoveryCopy>> candidates = orphans.stream()
                    .map(copy -> new RecoveryChoices.Candidate<>(copy, copy.displayName(), copy.originalPath(),
                            copy.savedAt(), isReadable(reader, copy)))
                    .toList();
            return Optional.of(new RecoveryChoices<>(candidates, Application::lastModified,
                    Clock.systemDefaultZone()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not look for recovery copies", exception);
            return Optional.empty();
        }
    }

    /** On the EDT: asks about the copies and returns those to recover; the discarded ones are deleted off the EDT. */
    private List<RecoveryCopy> askAboutRecoveryCopies(RecoveryChoices<RecoveryCopy> choices) {
        RecoveryChoices.Outcome<RecoveryCopy> outcome = choices.outcome(RecoveryPrompt.ask(null, choices));
        List<RecoveryCopy> discarded = outcome.discard();
        if (!discarded.isEmpty()) {
            // Queued on the writer before autosave.start(), which is fine: the writer runs from creation, and these
            // deletes touch only old runs' folders, never this run's, so their order against its copies is irrelevant.
            autosave.afterPendingWrites(() -> discarded.forEach(this::discardQuietly));
        }
        return outcome.recover();
    }

    private static boolean isReadable(CircuitReader reader, RecoveryCopy copy) {
        try {
            reader.read(copy.content());
            return true;
        } catch (CircuitFormatException | RuntimeException exception) {
            // A malformed copy is shown as unreadable; it must not hide the other copies.
            LOGGER.warn("The recovery copy {} cannot be read", copy.content(), exception);
            return false;
        }
    }

    private static Optional<Instant> lastModified(Path file) {
        try {
            return Optional.of(Files.getLastModifiedTime(file).toInstant());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    /** Runs on the autosave writer, never on the EDT. */
    private void discardQuietly(RecoveryCopy copy) {
        try {
            recoveryStore.discard(copy);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not delete the recovery copy {}", copy.content(), exception);
        }
    }

    /**
     * Opens a recovered copy as its modified original (not shown yet: it takes its file's tabs in the session, else a
     * window of its own) and adds the copy to {@code reopened}.
     */
    private Optional<OpenDocument> openRecovered(StartupSequence.Recovered recovered, List<RecoveryCopy> reopened) {
        RecoveryCopy copy = recovered.copy();
        try {
            OpenDocument document = OpenDocument.openRecovered(copy.content(), copy.originalPath().orElse(null),
                    copy.displayName(), registry);
            autosave.afterCopied(document, () -> discardQuietly(copy));
            reopened.add(copy);
            return Optional.of(document);
        } catch (CircuitFormatException exception) {
            openingFiles.report(copy.originalPath().orElse(copy.content()), exception, null);
            return Optional.empty(); // the copy stays for the next start
        }
    }

    /**
     * A window of the last session, with its tabs on the documents {@code opened} holds (by normalized file), placed
     * as it was (clamped to the screens). Nothing when none of its files opened.
     *
     * @return its component tabs left out because their definition is gone, to be told once
     */
    private List<TabState> restoreWindow(WindowSession session, Map<Path, OpenDocument> opened) {
        Function<Path, Optional<OpenDocument>> documentOf =
                path -> Optional.ofNullable(opened.get(FileNames.normalized(path)));
        List<OpenDocument> documents = session.documents().stream().flatMap(path -> documentOf.apply(path).stream())
                .toList();
        if (documents.isEmpty()) {
            return List.of();
        }
        CircuitWindow window = createWindow();
        try {
            return window.tabs().restore(session, documentOf);
        } finally {
            if (window.tabs().isEmpty()) {
                windowPreferences.remove(window);
                window.discardUnshown(); // its documents get a window of their own
            } else {
                documents.forEach(autosave::track); // M6-B: every open document is copied while modified
                window.restoreWindowState(session.bounds().clampedTo(screens()));
                window.setVisible(true);
            }
        }
    }

    /** One window per state, each placed and zoomed as it was; one plain window when there is no state. */
    private void showViews(OpenDocument document, List<WindowState> states) {
        if (states.isEmpty()) {
            showIn(Optional.empty(), document, Optional.empty());
            return;
        }
        states.forEach(state -> showIn(Optional.empty(), document, Optional.of(state)));
    }

    private void shutdownAutosave() {
        autosave.shutdown();
    }

    /** Capturing a window's state can fail (a window with no size); that must not stop the exit. */
    private void saveSession() {
        try {
            sessionStore.save(windows.all().stream().flatMap(window -> window.windowState().stream()).toList());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not remember the session", exception);
        }
    }

    /**
     * Recientes keeps where a file's window was when its group or window closed, without moving the file in the list.
     * Guarded for the window's state, which can fail (a window with no size), so the window still closes.
     */
    private void rememberWindowState(CircuitWindow window, OpenDocument document) {
        try {
            document.path().ifPresent(path -> recentFiles.updateState(path, window.windowState(document)));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not remember where {} was", document.displayName(), exception);
        }
    }

    /**
     * Shows {@code document} as a new group of tabs in {@code requester}, its main tab active; with no requester
     * (recovery, a file opened when no window exists) in a new window placed, sized and zoomed as {@code state} says,
     * clamped to the screens. A file being opened comes back split as it was (Dividir la vista).
     *
     * @return the editor of the document's new main tab
     */
    private EditorView showIn(Optional<CircuitWindow> requester, OpenDocument document,
                              Optional<WindowState> state) {
        if (requester.isPresent()) {
            EditorView view = requester.get().addTab(document, state.map(WindowState::view));
            requester.get().restoreRememberedSplit(view);
            autosave.track(document); // M6-B: every open document is copied while modified
            return view;
        }
        CircuitWindow window = showInNewWindow(document, created -> {
            state.ifPresent(saved -> created.restoreWindowState(saved.clampedTo(screens())));
            created.restoreRememberedSplit(created.activeView());
        });
        return window.activeView();
    }

    /**
     * A new window with a main tab of {@code document}; {@code beforeShowing} places it (location, size, the view
     * state it opens with) and only then it is shown: a window is never shown empty.
     */
    private CircuitWindow showInNewWindow(OpenDocument document, Consumer<CircuitWindow> beforeShowing) {
        CircuitWindow window = createWindow();
        window.addTab(document, Optional.empty());
        autosave.track(document); // M6-B: every open document is copied while modified
        beforeShowing.accept(window);
        window.setVisible(true);
        return window;
    }

    /** A window with no tab yet, reading the preferences through its own layer (the AI's temporary settings). */
    private CircuitWindow createWindow() {
        AppPreferences layered = preferences.layered();
        CircuitWindow window = new CircuitWindow(this, services.withPreferences(layered));
        windowPreferences.put(window, layered);
        return window;
    }

    /** The bounds of every screen, the primary first (clampedTo falls back to the first); none when headless. */
    private static List<Rectangle> screens() {
        if (GraphicsEnvironment.isHeadless()) {
            return List.of();
        }
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice primary = environment.getDefaultScreenDevice();
        List<Rectangle> screens = new ArrayList<>();
        screens.add(primary.getDefaultConfiguration().getBounds());
        for (GraphicsDevice device : environment.getScreenDevices()) {
            if (device != primary) {
                screens.add(device.getDefaultConfiguration().getBounds());
            }
        }
        return screens;
    }

    /** What the AI's host may do with the windows (spec §7): open a new unsaved circuit, reach an override. */
    private final class ApplicationWindows implements EditorWindows {

        @Override
        public OpenDocument openNewCircuit(String name) {
            OpenDocument created = OpenDocument.createEmpty(registry);
            // Not a user edit: the name is what the tab shows until the user saves it (the AI never does).
            created.document().setName(name);
            // A new group in the active window, as Archivo ▸ Nuevo; a window of its own only when there is none.
            showIn(windows.active(), created, Optional.empty());
            return created;
        }

        @Override
        public List<DefinitionBundle> libraryComponents() {
            return library.entries();
        }

        /** What the Problemas list shows of the library: the service's load problems (TemplateFeatures lists them). */
        @Override
        public List<LibraryProblem> libraryProblems() {
            return libraryService.problems();
        }

        /** «Presentar» as the circuit's first window's preferences hold it (IA.2). */
        @Override
        public PresentationSettings presentationSettings(OpenDocument document) {
            return PresentationPreferences.read(preferencesOf(document));
        }

        @Override
        public void setPresenting(OpenDocument document, boolean presenting) {
            if (presenting) {
                // The toggle turns on and the panel says «La IA pasó a Presentar» (vsim_set_presentation).
                presentation.aiAskedForPresenting(automationHost.documentId(document));
            }
            // Also when no window shows the circuit, which the panel's hook leaves alone.
            preferencesOf(document).setAiPresenting(presenting);
        }

        /** Every tab of the circuit goes to where the AI's next stage is built (IA.2 storyboard). */
        @Override
        public void travelToStageOrigin(OpenDocument document, GridPoint origin) {
            windows.windowsOf(document).forEach(window -> window.viewsOf(document).forEach(view ->
                    presentation.overlaysOf(view).ifPresent(overlays -> overlays.travelToStageOrigin(origin))));
        }

        /**
         * {@code vsim_open_place} (spec §12): the place's tab comes to the front in the window the AI works in, which is
         * given the circuit's group first when no window holds it (as Abrir would).
         */
        @Override
        public void openPlace(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath) {
            CircuitWindow window = aiWindowOf(document).orElseGet(() -> {
                showIn(Optional.empty(), document, Optional.empty());
                return windows.windowsOf(document).getFirst();
            });
            window.showPlace(document, definitionId, contextPath);
        }

        private AppPreferences preferencesOf(OpenDocument document) {
            return aiWindowOf(document).map(windowPreferences::get).orElse(preferences);
        }

        /** The window the AI works in for the circuit (spec §4.6): the one whose active tab shows it, else the first. */
        private Optional<CircuitWindow> aiWindowOf(OpenDocument document) {
            List<CircuitWindow> holders = windows.windowsOf(document);
            return holders.stream().filter(window -> window.activeDocument() == document).findFirst()
                    .or(() -> holders.stream().findFirst());
        }

        @Override
        public Optional<SettingsOverride> settingsOverride(OpenDocument document) {
            return aiWindowOf(document).map(windowPreferences::get).flatMap(SettingsOverride::of);
        }

        /** Every window of the circuit shows the AI's «Presentar» pace (review M4). */
        @Override
        public List<SettingsOverride> settingsOverridesOfEveryWindow(OpenDocument document) {
            return windows.windowsOf(document).stream()
                    .map(windowPreferences::get)
                    .filter(Objects::nonNull)
                    .flatMap(found -> SettingsOverride.of(found).stream())
                    .toList();
        }
    }

    private void focus(CircuitWindow window) {
        if ((window.getExtendedState() & Frame.ICONIFIED) != 0) {
            window.setExtendedState(window.getExtendedState() & ~Frame.ICONIFIED);
        }
        window.toFront();
        window.requestFocus();
    }
}
