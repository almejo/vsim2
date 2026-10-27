package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.memory.MemoryFile;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Memories linked to a text file (memory spec §8.2): «Enlazar…», «Desenlazar…» and «Abrir en editor externo» for the
 * content editor and the inspector, the one {@link LinkedMemoryWatcher} of each open document, and where the link
 * points.
 * <p>
 * While a circuit is open its memories keep their links <b>absolute</b>; the file keeps them relative to the
 * circuit's folder ({@link #storedLinkPath}), written by {@link #relativeLinks} when saving and read back by
 * {@link #absoluteLinks} when opening. So «Guardar como» to another folder, an unsaved circuit and a part pasted into
 * another circuit all keep pointing at the same file. VSim never writes a linked file.
 */
public final class MemoryLinks implements MemoryLinkActions {

    /** How often each document's linked files are looked at, besides when a window gets the focus. */
    static final int POLL_INTERVAL_MILLISECONDS = 1000;

    /** Starts polling a document's linked files; returns what stops it. Injectable so tests poll by hand. */
    public interface PollScheduler {

        /** Never polls by itself: tests call {@link LinkedMemoryWatcher#poll()}. */
        PollScheduler MANUAL = poll -> () -> { };

        Runnable start(Runnable poll);

        /** Every second and whenever a VSim window gets the focus, on the EDT; the first poll right after starting. */
        static PollScheduler swing() {
            return poll -> {
                Timer timer = new Timer(POLL_INTERVAL_MILLISECONDS, event -> poll.run());
                timer.setRepeats(true);
                timer.start();
                PropertyChangeListener focus = event -> {
                    if (event.getNewValue() != null) {
                        poll.run();
                    }
                };
                KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                focusManager.addPropertyChangeListener("activeWindow", focus);
                SwingUtilities.invokeLater(poll);
                return () -> {
                    timer.stop();
                    focusManager.removePropertyChangeListener("activeWindow", focus);
                };
            };
        }
    }

    /** A document's watcher and how many views of it are open. */
    private static final class Watched {
        private final LinkedMemoryWatcher watcher;
        private final Runnable stop;
        private int views;

        private Watched(LinkedMemoryWatcher watcher, Runnable stop) {
            this.watcher = watcher;
            this.stop = stop;
        }
    }

    private final MemoryLinkPrompts prompts;
    private final ExternalEditor externalEditor;
    private final PollScheduler scheduler;
    private final MemoryFileImport fileImport;
    private final Map<OpenDocument, Watched> watched = new IdentityHashMap<>();

    public MemoryLinks(MemoryLinkPrompts prompts, ExternalEditor externalEditor, PollScheduler scheduler) {
        this.prompts = prompts;
        this.externalEditor = externalEditor;
        this.scheduler = scheduler;
        this.fileImport = new MemoryFileImport(prompts);
    }

    /** The application's: real file choosers, the system's editor, polling every second and on focus. */
    public static MemoryLinks standard() {
        return new MemoryLinks(new SwingMemoryLinkPrompts(), ExternalEditor.system(), PollScheduler.swing());
    }

    /** Making a memory of a file (Archivo ▸ Importar, a drop). */
    public MemoryFileImport fileImport() {
        return fileImport;
    }

    // Link actions

    @Override
    public void link(EditorView view, ElementId componentId) {
        prompts.chooseLinkFile(view).ifPresent(file ->
                link(view.openDocument(), componentId, file).ifPresent(message -> prompts.inform(view, message)));
    }

    /**
     * Links the memory {@code componentId} to {@code file}: its contents become the file's (a copy kept in the
     * circuit), one undo step. Empty when done; the message for the user when the file cannot be read (nothing
     * changes then).
     */
    Optional<String> link(OpenDocument document, ElementId componentId, Path file) {
        Optional<Component> component = memory(document, componentId);
        if (component.isEmpty()) {
            return Optional.empty();
        }
        MemoryFile read;
        try {
            read = MemoryFileIo.read(file);
        } catch (MemoryFileIo.Unreadable problem) {
            return Optional.of(problem.getMessage());
        }
        document.execute(changing(document, component.get(), Map.of(MemoryTypes.CONTENTS, read.contents().encode(),
                MemoryTypes.LINK, storedLinkPath(file, Optional.empty())), "command.linkMemory"));
        return Optional.empty();
    }

    /** The content editor asked first: the link goes, the contents stay in the circuit. One undo step. */
    @Override
    public void unlink(EditorView view, ElementId componentId) {
        memory(view.openDocument(), componentId).filter(component -> !linkOf(component).isEmpty())
                .ifPresent(component -> view.openDocument().execute(changing(view.openDocument(), component,
                        Map.of(MemoryTypes.LINK, ""), "command.unlinkMemory")));
    }

    @Override
    public void openExternal(EditorView view, ElementId componentId) {
        Optional<Component> component = memory(view.openDocument(), componentId);
        Optional<Path> file = component.flatMap(memory -> linkedFile(memory, view.openDocument().path()));
        if (file.isEmpty()) {
            return;
        }
        String shown = displayPath(linkOf(component.get()), view.openDocument().path());
        if (!Files.isRegularFile(file.get())) {
            prompts.inform(view, Messages.format("memory.link.missing", shown));
            return;
        }
        if (!externalEditor.open(file.get(), view.services().preferences().externalEditor())) {
            prompts.inform(view, Messages.format("memory.external.failed", shown));
        }
    }

    // Watchers

    /** The watcher of {@code document}, made (and its polling started) by the first of its views. */
    LinkedMemoryWatcher acquireWatcher(OpenDocument document) {
        Watched entry = watched.computeIfAbsent(document, key -> {
            LinkedMemoryWatcher watcher = new LinkedMemoryWatcher(key);
            return new Watched(watcher, scheduler.start(watcher::poll));
        });
        entry.views++;
        return entry.watcher;
    }

    /** One view of {@code document} closed; the last one stops its watcher. */
    void releaseWatcher(OpenDocument document) {
        Watched entry = watched.get(document);
        if (entry != null && --entry.views == 0) {
            watched.remove(document);
            entry.stop.run();
        }
    }

    /** The watcher of {@code document} while one of its views is open. */
    public Optional<LinkedMemoryWatcher> watcherOf(OpenDocument document) {
        return Optional.ofNullable(watched.get(document)).map(entry -> entry.watcher);
    }

    // Paths

    /**
     * How a link to {@code file} is written: relative to the folder of {@code circuitFile} with {@code /} between
     * names («programas/rom.hex», «../datos/rom.hex»); absolute when the circuit is unsaved or the file is on another
     * drive.
     */
    public static String storedLinkPath(Path file, Optional<Path> circuitFile) {
        Path absolute = file.toAbsolutePath().normalize();
        Optional<Path> folder = circuitFile.map(circuit -> circuit.toAbsolutePath().normalize().getParent());
        if (folder.isPresent() && folder.get() != null) {
            try {
                return slashes(folder.get().relativize(absolute));
            } catch (IllegalArgumentException otherDrive) {
                return slashes(absolute);
            }
        }
        return slashes(absolute);
    }

    /** The file a stored link points at: {@code stored} against the folder of {@code circuitFile} when relative. */
    public static Path resolve(String stored, Optional<Path> circuitFile) {
        Path path = Path.of(stored);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Optional<Path> folder = circuitFile.map(circuit -> circuit.toAbsolutePath().normalize().getParent());
        return (folder.isPresent() && folder.get() != null ? folder.get().resolve(path) : path.toAbsolutePath())
                .normalize();
    }

    /** The link as the user reads it: relative to the circuit when it is saved («programas/rom.hex»). */
    public static String displayPath(String stored, Optional<Path> circuitFile) {
        try {
            return storedLinkPath(resolve(stored, circuitFile), circuitFile);
        } catch (InvalidPathException unusable) {
            return stored;
        }
    }

    /** The file {@code memory} is linked to, empty when it is not linked (or the link is not a path here). */
    public static Optional<Path> linkedFile(Component memory, Optional<Path> circuitFile) {
        String link = linkOf(memory);
        if (link.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(resolve(link, circuitFile));
        } catch (InvalidPathException unusable) {
            return Optional.empty();
        }
    }

    /** Whether {@code first} and {@code second} name the same file (also through links, when both exist). */
    public static boolean isSameFile(Path first, Path second) {
        Path firstAbsolute = first.toAbsolutePath().normalize();
        Path secondAbsolute = second.toAbsolutePath().normalize();
        if (firstAbsolute.equals(secondAbsolute)) {
            return true;
        }
        try {
            return Files.exists(firstAbsolute) && Files.exists(secondAbsolute)
                    && Files.isSameFile(firstAbsolute, secondAbsolute);
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Makes every link of {@code document}'s memories absolute against {@code circuitFile} (a circuit just read; not
     * an edit: no undo step, the document stays unmodified).
     */
    public static void absoluteLinks(CircuitDocument document, Optional<Path> circuitFile) {
        for (Component component : List.copyOf(document.components())) {
            Optional<Path> file = isMemory(component) ? linkedFile(component, circuitFile) : Optional.empty();
            if (file.isPresent()) {
                String absolute = storedLinkPath(file.get(), Optional.empty());
                if (!absolute.equals(linkOf(component))) {
                    document.replace(withLink(component, absolute));
                }
            }
        }
    }

    /** What {@link cl.almejo.vsim.core.io.CircuitFormat} writes for each component: links relative to the circuit. */
    public static UnaryOperator<Component> relativeLinks(Optional<Path> circuitFile) {
        return component -> {
            Optional<Path> file = isMemory(component) ? linkedFile(component, Optional.empty()) : Optional.empty();
            return file.map(path -> withLink(component, storedLinkPath(path, circuitFile))).orElse(component);
        };
    }

    // Helpers shared by the memory package

    static String linkOf(Component component) {
        return String.valueOf(component.parameters().getOrDefault(MemoryTypes.LINK, ""));
    }

    /**
     * One undo step named {@code descriptionKey} that sets {@code changes} on the parameters {@code component} has
     * when it runs.
     */
    static Command changing(OpenDocument document, Component component, Map<String, Object> changes,
                            String descriptionKey) {
        ComponentType type = document.registry().typeOf(component);
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.putAll(changes);
        return new CompositeCommand(descriptionKey,
                List.of(new ChangeParametersCommand(component.id(), type, parameters)));
    }

    static Optional<Component> memory(OpenDocument document, ElementId componentId) {
        return document.document().component(componentId).filter(MemoryLinks::isMemory);
    }

    private static boolean isMemory(Component component) {
        return MemoryTypes.kindOf(component.typeId()).isPresent();
    }

    private static Component withLink(Component component, String link) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put(MemoryTypes.LINK, link);
        return component.withParameters(parameters);
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }
}
