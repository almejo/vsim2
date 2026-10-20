package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.examples.ExamplesFeature;
import cl.almejo.vsim.ui.export.ExportImageExtension;
import cl.almejo.vsim.ui.frames.FramesExtension;
import cl.almejo.vsim.ui.karnaugh.KarnaughExtension;
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
import cl.almejo.vsim.ui.preferences.PreferencesFeature;
import cl.almejo.vsim.ui.search.FindExtension;
import cl.almejo.vsim.ui.session.AutosaveService;
import cl.almejo.vsim.ui.session.RecentFiles;
import cl.almejo.vsim.ui.session.RecentFilesFeature;
import cl.almejo.vsim.ui.session.RecoveryChoices;
import cl.almejo.vsim.ui.session.RecoveryCopy;
import cl.almejo.vsim.ui.session.RecoveryPrompt;
import cl.almejo.vsim.ui.session.RecoveryStore;
import cl.almejo.vsim.ui.session.SessionDocument;
import cl.almejo.vsim.ui.session.SessionStore;
import cl.almejo.vsim.ui.session.StartupSequence;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.stepping.StepModeExtension;
import cl.almejo.vsim.ui.theme.TextFontManager;
import cl.almejo.vsim.ui.theme.ThemeManager;
import cl.almejo.vsim.ui.tree.ComponentTreeExtension;
import cl.almejo.vsim.ui.truthtable.TruthTableExtension;
import cl.almejo.vsim.ui.tunnel.TunnelsExtension;
import cl.almejo.vsim.ui.waveform.WaveformsExtension;
import cl.almejo.vsim.ui.window.CircuitWindow;
import cl.almejo.vsim.ui.window.CloseSequence;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.NewViewPlacement;
import cl.almejo.vsim.ui.window.WindowMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Open windows and documents of the running application (EDT only). A document closes with its last window. */
public final class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences;
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private final ComponentLibrary library = new ComponentLibrary();
    private final LibraryService libraryService;
    private final EditorServices services;
    private final ThemeManager themeManager;
    private final TextFontManager textFontManager;
    private final DocumentWindows<CircuitWindow> windows = new DocumentWindows<>();
    private final RecentFiles recentFiles;
    private final SessionStore sessionStore;
    private final RecoveryStore recoveryStore;
    private final AutosaveService autosave;
    // Autosave shutdown stops the timer, drains the pending writes, then deletes this run's recovery folder.
    private final SessionEnding sessionEnding = new SessionEnding(this::saveSession, this::shutdownAutosave);
    private Path lastDirectory;

    public Application(AppPreferences preferences) {
        this.preferences = preferences;
        this.libraryService = new LibraryService(preferences, library, registry);
        this.services = new EditorServices(PainterRegistry.standard(), appearance, preferences, library,
                editorExtensions(libraryService, SwingLibraryDialogs.INSTANCE, this::initialDirectory,
                        this::rememberDirectory));
        // Lives as long as the application: it follows the preferences and is never disposed.
        this.themeManager = ThemeManager.forCurrentPlatform(preferences, appearance);
        this.textFontManager = new TextFontManager(preferences);
        this.recentFiles = new RecentFiles(preferences);
        this.sessionStore = new SessionStore(preferences);
        this.recoveryStore = new RecoveryStore(preferences.dataDirectory().resolve("recovery"));
        this.autosave = AutosaveService.create(recoveryStore);
    }

    /** The extensions with the import's file chooser starting in the user's home and remembering nothing (tests). */
    static List<EditorExtension> editorExtensions(LibraryService libraryService, LibraryDialogs libraryDialogs) {
        return editorExtensions(libraryService, libraryDialogs, () -> Path.of(System.getProperty("user.home")),
                path -> { });
    }

    /**
     * The extensions of every editor view, in install order. Package-private so a test can install them all.
     * {@code startDirectory} and {@code chosen} are the folder «Abrir» starts in and remembers, shared with the
     * table import's file chooser.
     */
    static List<EditorExtension> editorExtensions(LibraryService libraryService, LibraryDialogs libraryDialogs,
                                                  Supplier<Path> startDirectory, Consumer<Path> chosen) {
        List<EditorExtension> editorExtensions = new ArrayList<>();
        // M6: waveforms before ActionIcons, recents after PreferencesFeature, shortcuts last. M6.1: the step mode and
        // the truth table before ActionIcons, the examples after RecentFilesFeature (its submenu goes after «Recientes»).
        editorExtensions.add(new TemplatesExtension(libraryService, libraryDialogs));
        editorExtensions.add(new ComponentTreeExtension());
        editorExtensions.add(new WaveformsExtension());
        editorExtensions.add(new StepModeExtension());
        editorExtensions.add(new TruthTableExtension());
        editorExtensions.add(new ExportImageExtension());
        editorExtensions.add(new TidyWiresExtension());
        editorExtensions.add(new FramesExtension());
        editorExtensions.add(new WiresFollowExtension());
        editorExtensions.add(new KarnaughExtension(libraryService, startDirectory, chosen));
        editorExtensions.add(new FindExtension());
        editorExtensions.add(new TunnelsExtension());
        editorExtensions.add(new ActionIcons());
        editorExtensions.add(new PreferencesFeature());
        editorExtensions.add(new RecentFilesFeature());
        editorExtensions.add(new ExamplesFeature());
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

    public ComponentTypeRegistry registry() {
        return registry;
    }

    /**
     * M6-B order at start: recovery dialog → last session (a recovered copy replaces its file) → command line. The
     * recovery folder is scanned off the EDT (it reads every copy); the dialog and the windows come on the EDT.
     */
    public void start(List<Path> files) {
        themeManager.start();
        textFontManager.start();
        libraryService.start(); // spec §5.6: read the library before the first palette is shown
        // Not a daemon: it keeps the JVM alive until the EDT has the rest of the start queued.
        Thread scan = new Thread(() -> {
            Optional<RecoveryChoices<RecoveryCopy>> choices = Optional.empty();
            try {
                choices = scanRecoveryCopies();
            } finally {
                // Even after an Error on this thread the application starts, then without a recovery offer.
                Optional<RecoveryChoices<RecoveryCopy>> offered = choices;
                SwingUtilities.invokeLater(() -> finishStart(files, offered));
            }
        }, "vsim-recovery-scan");
        scan.start();
    }

    /**
     * The EDT half of the start. Each opening is guarded, and whatever fails the start ends with at least one window
     * and autosave running ({@link StartupSteps}).
     */
    private void finishStart(List<Path> files, Optional<RecoveryChoices<RecoveryCopy>> choices) {
        List<Path> components = files.stream().filter(LibraryDirectory::isComponentFile).toList();
        List<Path> circuits = files.stream().filter(file -> !components.contains(file)).toList();
        List<RecoveryCopy> reopened = new ArrayList<>();
        StartupSteps.run(() -> {
            List<RecoveryCopy> recovered = choices.map(this::askAboutRecoveryCopies).orElse(List.of());
            StartupSequence.Plan plan = StartupSequence.plan(recovered, preferences.restoreSession(), sessionStore.load(),
                    circuits, Files::isRegularFile);
            StartupSteps.openEach(plan.openings(), opening -> {
                switch (opening) {
                    case StartupSequence.Recovered copy -> openRecovered(copy).ifPresent(reopened::add);
                    case StartupSequence.FromSession session -> openFromSession(session);
                }
            });
            if (!plan.missing().isEmpty()) {
                JOptionPane.showMessageDialog(null, Messages.format("session.missing",
                                String.join(", ", plan.missing().stream().map(Path::toString).toList())),
                        Messages.text("session.missing.title"), JOptionPane.WARNING_MESSAGE);
            }
            // Through the already-open check (no file opens twice); one file each so a failure skips only that one.
            StartupSteps.openEach(plan.commandLineFiles(), file -> openPaths(List.of(file), null));
        }, () -> !windows.isEmpty(), this::newDocument, () -> {
            if (!components.isEmpty()) {
                // A component file on the command line is imported into the library, as a drop on the window would.
                windows.all().get(0).openOrImport(components);
            }
        }, () -> startAutosave(reopened));
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

    public CircuitWindow newDocument() {
        return show(OpenDocument.createEmpty(registry));
    }

    /** Ventana ▸ Nueva vista: another view of the same document, 30 px down-right of its source, same zoom and center. */
    public CircuitWindow newWindow(CircuitWindow source) {
        NewViewPlacement placement = NewViewPlacement.after(source.getLocation(), source.viewState());
        // setLocation also cancels the platform placement of the constructor
        return show(source.openDocument(), window -> placement.applyTo(window::setInitialViewState, window::setLocation));
    }

    public void open(CircuitWindow requester) {
        // A chosen component file is imported into the library instead of being read as a circuit (as a drop does).
        FileDialogs.chooseOpen(requester, initialDirectory()).ifPresent(path -> requester.openOrImport(List.of(path)));
    }

    /**
     * Opens each file in a new window, or focuses the window already showing it. When something new was opened
     * from a pristine window, that placeholder document and its windows go away.
     */
    public void openPaths(List<Path> paths, CircuitWindow requester) {
        boolean openedAny = false;
        for (Path path : paths) {
            Optional<CircuitWindow> showing = windows.firstShowing(path);
            if (showing.isPresent()) {
                focus(showing.get());
                continue;
            }
            Optional<OpenDocument> document = read(path, requester);
            if (document.isPresent()) {
                rememberDirectory(path);
                recentFiles.touch(path); // never throws: a Recientes failure never blocks opening (P3)
                show(document.get(), recentFiles.stateFor(path)); // Assumption 19: whenever the file is opened
                openedAny = true;
            }
        }
        if (openedAny) {
            windows.replacedBy(requester).forEach(CircuitWindow::closeQuietly);
        }
    }

    /**
     * A new window with an untitled document named {@code name} whose first undo step is {@code creation} (a circuit
     * built from a truth table). The requester stays: the wizard that asked is a dialog over it, so the new window is
     * brought to the front once the dialog's event is over.
     */
    public CircuitWindow openNewCircuit(String name, Command creation) {
        OpenDocument created = OpenDocument.createEmpty(registry);
        // Not a user edit: the name is what the window shows until the document is saved.
        created.document().setName(name);
        created.execute(creation);
        CircuitWindow window = show(created);
        SwingUtilities.invokeLater(() -> focus(window));
        return window;
    }

    /**
     * Archivo ▸ Ejemplos (M6.1-A): {@code document} in a new window as an untitled, unmodified document, so Guardar
     * asks for a file name and nothing is added to Recientes. Like Abrir, a pristine requester window goes away.
     */
    public void openUntitled(CircuitDocument document, CircuitWindow requester) {
        show(new OpenDocument(document, registry, null, false));
        windows.replacedBy(requester).forEach(CircuitWindow::closeQuietly);
    }

    /** Asks once per modified document; true when all windows were closed. The session is remembered first. */
    public boolean exit() {
        // a half-typed inline edit counts as a change, in whichever window of the document it is open
        boolean confirmed = CloseSequence.confirm(windows.all().stream().map(CircuitWindow::inlineEditor).toList(),
                () -> windows.documents().stream()
                        .allMatch(document -> windows.windowsOf(document).get(0).confirmSave()));
        if (!confirmed) {
            return false;
        }
        sessionEnding.exiting();
        windows.all().forEach(CircuitWindow::closeQuietly);
        return true;
    }

    public boolean isLastWindow(CircuitWindow window) {
        return windows.isLastWindow(window);
    }

    public List<WindowMenu.Entry> windowEntries(CircuitWindow current) {
        return windows.menuEntries(current, this::focus);
    }

    /** Runs {@code listener} after every window opens or closes (view numbers may have changed). */
    public void addWindowsListener(Runnable listener) {
        windows.addChangeListener(listener);
    }

    public void removeWindowsListener(Runnable listener) {
        windows.removeChangeListener(listener);
    }

    /** The number of {@code window} among the views of its document; 0 when it is the only one. */
    public int viewNumber(CircuitWindow window) {
        return windows.viewNumber(window);
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
     * Called by a window after disposing itself (its bounds and canvas size are still readable). The document stops
     * (simulation thread included) and loses its recovery copy with its last window; the last window of the
     * application also ends the session (Assumption 22) and, on a clean exit, this run's recovery folder goes.
     */
    public void windowClosed(CircuitWindow window) {
        sessionEnding.windowClosing(windows.all().equals(List.of(window)));
        rememberWindowState(window);
        windows.remove(window).ifPresent(document -> {
            autosave.untrack(document);
            document.close();
        });
        sessionEnding.windowClosed(windows.isEmpty());
        if (windows.isEmpty()) {
            libraryService.close();
            System.exit(0);
        }
    }

    private Optional<OpenDocument> read(Path path, CircuitWindow requester) {
        try {
            return Optional.of(OpenDocument.open(path, registry));
        } catch (CircuitFormatException exception) {
            LOGGER.warn("Could not open {}", path, exception);
            JOptionPane.showMessageDialog(requester, Messages.readError(exception), Messages.text("error.open.title"),
                    JOptionPane.ERROR_MESSAGE);
            return Optional.empty();
        }
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

    /** Opens a recovered copy as its modified original, with the session's views or else Recientes' state. */
    private Optional<RecoveryCopy> openRecovered(StartupSequence.Recovered recovered) {
        RecoveryCopy copy = recovered.copy();
        try {
            OpenDocument document = OpenDocument.openRecovered(copy.content(), copy.originalPath().orElse(null),
                    registry);
            List<WindowState> states = recovered.windows().isEmpty()
                    ? copy.originalPath().flatMap(recentFiles::stateFor).stream().toList()
                    : recovered.windows();
            showViews(document, states);
            autosave.afterCopied(document, () -> discardQuietly(copy));
            return Optional.of(copy);
        } catch (CircuitFormatException exception) {
            LOGGER.warn("Could not recover {}", copy.content(), exception);
            JOptionPane.showMessageDialog(null, Messages.readError(exception), Messages.text("error.open.title"),
                    JOptionPane.ERROR_MESSAGE);
            return Optional.empty(); // the copy stays for the next start
        }
    }

    /** A file of the last session; a session document without windows opens with Recientes' or default placement. */
    private void openFromSession(StartupSequence.FromSession session) {
        read(session.path(), null).ifPresent(document -> {
            rememberDirectory(session.path());
            showViews(document, session.windows().isEmpty()
                    ? recentFiles.stateFor(session.path()).stream().toList() : session.windows());
        });
    }

    /** One window per state, each placed and zoomed as it was; one plain window when there is no state. */
    private void showViews(OpenDocument document, List<WindowState> states) {
        if (states.isEmpty()) {
            show(document, Optional.empty());
            return;
        }
        states.forEach(state -> show(document, Optional.of(state)));
    }

    private void shutdownAutosave() {
        autosave.shutdown();
    }

    /** Capturing a window's state can fail (a window with no size); that must not stop the exit. */
    private void saveSession() {
        try {
            sessionStore.save(SessionDocument.capture(windows, CircuitWindow::windowState));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not remember the session", exception);
        }
    }

    /**
     * Recientes keeps where a file's window was when it closed, without moving the file in the list. Guarded for the
     * window's state, which can fail (a window with no size), so the window still closes.
     */
    private void rememberWindowState(CircuitWindow window) {
        try {
            window.openDocument().path().ifPresent(path -> recentFiles.updateState(path, window.windowState()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not remember where {} was", window.openDocument().displayName(), exception);
        }
    }

    private CircuitWindow show(OpenDocument document) {
        return show(document, window -> {
        });
    }

    /**
     * Creates a window for {@code document}, registers it, lets {@code beforeShowing} place it (location, size, the
     * view state it opens with) and only then shows it. M6 contract C6: B adds its saved-state overload on top.
     */
    private CircuitWindow show(OpenDocument document, Consumer<CircuitWindow> beforeShowing) {
        CircuitWindow window = new CircuitWindow(this, document, services);
        windows.add(document, window);
        autosave.track(document); // M6-B: every open document is copied while modified
        beforeShowing.accept(window);
        window.setVisible(true);
        return window;
    }

    /** A new window on {@code document}, placed, sized and zoomed as {@code state} says, clamped to the screens. */
    private CircuitWindow show(OpenDocument document, Optional<WindowState> state) {
        return show(document,
                window -> state.ifPresent(saved -> window.restoreWindowState(saved.clampedTo(screens()))));
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

    private void focus(CircuitWindow window) {
        if ((window.getExtendedState() & Frame.ICONIFIED) != 0) {
            window.setExtendedState(window.getExtendedState() & ~Frame.ICONIFIED);
        }
        window.toFront();
        window.requestFocus();
    }
}
