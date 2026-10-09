package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.LibraryDirectory;
import cl.almejo.vsim.core.io.LibraryLoad;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * The user's component library for the whole application (spec §5.6): the directory chosen in the preferences,
 * mirrored into the shared {@link ComponentLibrary}. File operations run on the caller's thread (the EDT); external
 * changes are loaded on the watcher's thread and applied on {@code uiExecutor}, so library listeners run on the EDT.
 */
public final class LibraryService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryService.class);

    private final AppPreferences preferences;
    private final ComponentLibrary library;
    private final ComponentTypeRegistry registry;
    private final Executor uiExecutor;
    private final List<Runnable> problemListeners = new CopyOnWriteArrayList<>();
    private final Runnable preferencesListener = this::followDirectoryPreference;
    private volatile LibraryDirectory directory;
    private volatile List<LibraryProblem> problems = List.of();
    private LibraryWatcher watcher;
    private boolean started;

    public LibraryService(AppPreferences preferences, ComponentLibrary library, ComponentTypeRegistry registry) {
        this(preferences, library, registry, SwingUtilities::invokeLater);
    }

    public LibraryService(AppPreferences preferences, ComponentLibrary library, ComponentTypeRegistry registry,
                          Executor uiExecutor) {
        this.preferences = preferences;
        this.library = library;
        this.registry = registry;
        this.uiExecutor = uiExecutor;
        this.directory = new LibraryDirectory(preferences.libraryDirectory(), registry);
    }

    public ComponentLibrary library() {
        return library;
    }

    public Path directory() {
        return directory.directory();
    }

    /** The files skipped by the last load. */
    public List<LibraryProblem> problems() {
        return problems;
    }

    public void addProblemsListener(Runnable listener) {
        problemListeners.add(listener);
    }

    public void removeProblemsListener(Runnable listener) {
        problemListeners.remove(listener);
    }

    /** Reads the directory, starts watching it and follows the directory preference. Call once, on the EDT. */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        reload();
        startWatcher();
        preferences.addListener(preferencesListener);
    }

    /** Reads the whole directory now and makes the library and the problem list match it. */
    public void reload() {
        LibraryDirectory current = directory;
        apply(current, current.load());
    }

    /** Writes the file (creating the directory) and puts the bundle in the library. */
    public void save(DefinitionBundle bundle) throws IOException {
        directory.write(bundle);
        library.put(bundle);
    }

    public void remove(String id) throws IOException {
        directory.delete(id);
        library.remove(id);
    }

    public DefinitionBundle rename(String id, String newName) throws IOException {
        DefinitionBundle renamed = directory.rename(require(id), newName);
        library.put(renamed);
        return renamed;
    }

    public void export(String id, Path target) throws IOException {
        directory.export(require(id), target);
    }

    public DefinitionBundle read(Path file) throws IOException, CircuitFormatException {
        return directory.read(file);
    }

    /** The file that holds {@code id} (or would hold it once saved). */
    public Path fileOf(String id) {
        LibraryDirectory current = directory;
        return current.locate(id).orElse(current.fileOf(id));
    }

    /** The visible text for a failed write of {@code id}: its file and the reason. */
    String writeError(String id, IOException exception) {
        return Messages.format("library.error.write", fileOf(id), String.valueOf(exception.getMessage()));
    }

    /** Stops watching the directory and following the preference; the library keeps its entries. */
    @Override
    public synchronized void close() {
        preferences.removeListener(preferencesListener);
        stopWatcher();
        started = false;
    }

    /** For tests: the watcher of the current directory, empty when not started or closed. */
    synchronized Optional<LibraryWatcher> watcher() {
        return Optional.ofNullable(watcher);
    }

    private DefinitionBundle require(String id) {
        return library.find(id).orElseThrow(() -> new IllegalArgumentException("not in the library: " + id));
    }

    private synchronized void apply(LibraryDirectory source, LibraryLoad load) {
        if (source != directory) {
            return; // a load of a directory that is no longer the library
        }
        library.replaceAll(load.bundles());
        if (!load.problems().equals(problems)) {
            problems = load.problems();
            problemListeners.forEach(Runnable::run);
        }
    }

    private synchronized void followDirectoryPreference() {
        Path next = preferences.libraryDirectory().toAbsolutePath().normalize();
        if (next.equals(directory.directory())) {
            return;
        }
        stopWatcher();
        directory = new LibraryDirectory(next, registry);
        reload();
        startWatcher();
    }

    private void startWatcher() {
        LibraryDirectory watched = directory;
        LibraryWatcher next = new LibraryWatcher(watched.directory(), LibraryWatcher.DEBOUNCE, () -> {
            LibraryLoad load = watched.load();
            uiExecutor.execute(() -> apply(watched, load));
        });
        try {
            next.start();
            watcher = next;
        } catch (IOException exception) {
            LOGGER.warn("Cannot watch the component library {}", watched.directory(), exception);
            next.close();
        }
    }

    private void stopWatcher() {
        if (watcher != null) {
            watcher.close();
            watcher = null;
        }
    }
}
