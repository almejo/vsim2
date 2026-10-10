package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.library.ComponentLibrary;
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
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.theme.ThemeManager;
import cl.almejo.vsim.ui.tree.ComponentTreeExtension;
import cl.almejo.vsim.ui.window.CircuitWindow;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.NewViewPlacement;
import cl.almejo.vsim.ui.window.WindowMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
    private final DocumentWindows<CircuitWindow> windows = new DocumentWindows<>();
    private Path lastDirectory;

    public Application(AppPreferences preferences) {
        this.preferences = preferences;
        this.libraryService = new LibraryService(preferences, library, registry);
        this.services = new EditorServices(PainterRegistry.standard(), appearance, preferences, library,
                editorExtensions(libraryService, SwingLibraryDialogs.INSTANCE));
        // Lives as long as the application: it follows the preferences and is never disposed.
        this.themeManager = ThemeManager.forCurrentPlatform(preferences, appearance);
    }

    /** The extensions of every editor view, in install order. Package-private so a test can install them all. */
    static List<EditorExtension> editorExtensions(LibraryService libraryService, LibraryDialogs libraryDialogs) {
        List<EditorExtension> editorExtensions = new ArrayList<>();
        // M6: A's waveforms before ActionIcons, B's recents after PreferencesFeature, C's shortcuts last.
        editorExtensions.add(new TemplatesExtension(libraryService, libraryDialogs));
        editorExtensions.add(new ComponentTreeExtension());
        editorExtensions.add(new ActionIcons());
        editorExtensions.add(new PreferencesFeature());
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

    public void start(List<Path> files) {
        themeManager.start();
        libraryService.start(); // spec §5.6: read the library before the first palette is shown
        List<Path> components = files.stream().filter(LibraryDirectory::isComponentFile).toList();
        openPaths(files.stream().filter(file -> !components.contains(file)).toList(), null);
        if (windows.isEmpty()) {
            newDocument();
        }
        if (!components.isEmpty()) {
            // A component file on the command line is imported into the library, as a drop on the window would.
            windows.all().get(0).openOrImport(components);
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
                show(document.get());
                openedAny = true;
            }
        }
        if (openedAny && requester != null && requester.openDocument().isPristine()) {
            windows.windowsOf(requester.openDocument()).forEach(CircuitWindow::closeQuietly);
        }
    }

    /** Asks once per modified document; true when all windows were closed. */
    public boolean exit() {
        for (OpenDocument document : windows.documents()) {
            if (!windows.windowsOf(document).get(0).confirmSave()) {
                return false;
            }
        }
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

    /** Called by a window after disposing itself; the document stops (simulation thread included) with its last window. */
    public void windowClosed(CircuitWindow window) {
        windows.remove(window).ifPresent(OpenDocument::close);
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
        beforeShowing.accept(window);
        window.setVisible(true);
        return window;
    }

    private void focus(CircuitWindow window) {
        if ((window.getExtendedState() & Frame.ICONIFIED) != 0) {
            window.setExtendedState(window.getExtendedState() & ~Frame.ICONIFIED);
        }
        window.toFront();
        window.requestFocus();
    }
}
