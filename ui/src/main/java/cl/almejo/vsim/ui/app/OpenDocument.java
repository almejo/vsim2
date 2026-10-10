package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.io.OpenedCircuit;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.SimulationThread;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One circuit being edited: document, undo history, compiled netlist and its own simulation thread.
 * Use it from the Swing event thread only.
 */
public final class OpenDocument implements AutoCloseable {

    private final CircuitDocument document;
    private final ComponentTypeRegistry registry;
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenDocument.class);

    private final History history;
    private final NetlistCompiler compiler;
    private final CircuitFlattener flattener;
    private final SimulationThread simulation;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private Path path;
    private Path backingFile;
    private boolean importedFromVsim1;
    private Netlist netlist = Netlist.EMPTY;
    private long netlistToken = -1;
    private boolean closed;
    private boolean pauseOnShortCircuit;
    private final UUID recoveryId = UUID.randomUUID();
    private long changeCount;

    public OpenDocument(CircuitDocument document, ComponentTypeRegistry baseRegistry, Path path,
                        boolean importedFromVsim1) {
        this.document = document;
        // Each document resolves the subcircuit:<id> types of its own definitions (spec §5.6 embedded copies).
        this.registry = baseRegistry.copy();
        this.registry.addResolver(SubcircuitTypes.resolverFor(document));
        this.path = path == null ? null : path.toAbsolutePath();
        this.importedFromVsim1 = importedFromVsim1;
        this.history = new History(document);
        this.compiler = new NetlistCompiler(this.registry);
        this.flattener = new CircuitFlattener(this.registry);
        this.simulation = new SimulationThread(new SimulationEngine());
        document.addListener((changed, change) -> synchronize());
        history.addListener(changed -> {
            changeCount++;
            fireChanged();
        });
        synchronize();
        simulation.start();
        simulation.resume();
    }

    public static OpenDocument createEmpty(ComponentTypeRegistry registry) {
        return new OpenDocument(new CircuitDocument(), registry, null, false);
    }

    public static OpenDocument open(Path path, ComponentTypeRegistry registry) throws CircuitFormatException {
        OpenedCircuit opened = new CircuitReader(registry).open(path);
        OpenDocument openDocument = new OpenDocument(opened.document(), registry,
                opened.importedFromVsim1() ? null : path, opened.importedFromVsim1());
        openDocument.backingFile = FileNames.normalized(path);
        return openDocument;
    }

    /**
     * A recovery copy (M6-B) reopened as the document it was copied from: {@code originalPath} (null for an untitled
     * document) is where Guardar writes, and the document reports {@link #isModified()} until it is saved.
     */
    public static OpenDocument openRecovered(Path copy, Path originalPath, ComponentTypeRegistry registry)
            throws CircuitFormatException {
        OpenedCircuit opened = new CircuitReader(registry).open(copy);
        if (originalPath == null) {
            // The reader names a nameless file after the copy's file; an untitled document stays untitled.
            String copyName = copy.getFileName().toString();
            int dot = copyName.lastIndexOf('.');
            if (opened.document().name().equals(dot > 0 ? copyName.substring(0, dot) : copyName)) {
                opened.document().setName("");
            }
        }
        OpenDocument openDocument = new OpenDocument(opened.document(), registry, originalPath, false);
        openDocument.backingFile = originalPath == null ? null : FileNames.normalized(originalPath);
        openDocument.history.markUnsaved();
        return openDocument;
    }

    public CircuitDocument document() {
        return document;
    }

    /**
     * This document's own registry: a copy of the base registry plus a resolver for the document's definitions
     * ({@link SubcircuitTypes#resolverFor}). Because it reads the document, use it from the EDT only; the base registry
     * stays thread-safe. Flattening happens on the EDT, so the simulation thread receives already-resolved components.
     */
    public ComponentTypeRegistry registry() {
        return registry;
    }

    public History history() {
        return history;
    }

    public Optional<Path> path() {
        return Optional.ofNullable(path);
    }

    /** True for the file this document was opened from (also a VSim 1 import) or last saved to, in any spelling. */
    public boolean isBackedBy(Path file) {
        return backingFile != null && backingFile.equals(FileNames.normalized(file));
    }

    public boolean isImportedFromVsim1() {
        return importedFromVsim1;
    }

    /** True when Save must ask for a file: never saved, or imported from VSim 1 (never overwrite the old file). */
    public boolean needsSaveAs() {
        return path == null;
    }

    public String displayName() {
        if (path != null) {
            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }
        return document.name().isBlank() ? Messages.text("document.untitled") : document.name();
    }

    public String suggestedFileName() {
        return displayName().replaceAll("[/\\\\:*?\"<>|]", "_") + FileNames.EXTENSION;
    }

    public boolean isModified() {
        return history.isModified();
    }

    /** A fresh, empty, untouched document — opening a file may replace its window. */
    public boolean isPristine() {
        return path == null && !importedFromVsim1 && document.isEmpty() && !isModified();
    }

    /** Stable id of this document's recovery copy (M6-B); it never changes while the document is open. */
    public UUID recoveryId() {
        return recoveryId;
    }

    /**
     * Grows on every undo-history notification (execute, undo, redo, save, {@code markUnsaved}). Autosave copies a
     * modified document when this moved since its last copy, so every change made through {@link #execute} counts.
     */
    public long changeCount() {
        return changeCount;
    }

    public Netlist netlist() {
        return netlist;
    }

    /** Revision of the document the current netlist was compiled from; compare with {@code snapshot.token()}. */
    public long netlistToken() {
        return netlistToken;
    }

    public void execute(Command command) {
        history.execute(command);
    }

    public void interact(ElementId componentId) {
        simulation.interact(componentId.value());
    }

    public void resume() {
        simulation.resume();
    }

    public void pause() {
        simulation.pause();
    }

    public void step(long duration) {
        simulation.step(duration);
    }

    public void setSpeed(double unitsPerMillisecond) {
        simulation.setSpeed(unitsPerMillisecond);
    }

    /** Session option, off by default and not saved: pause the simulation when a new short circuit appears. */
    public boolean pauseOnShortCircuit() {
        return pauseOnShortCircuit;
    }

    public void setPauseOnShortCircuit(boolean enabled) {
        pauseOnShortCircuit = enabled;
        simulation.setPauseOnShortCircuit(enabled);
    }

    public SimulationSnapshot latestSnapshot() {
        return simulation.latestSnapshot();
    }

    /**
     * Writes format v2 atomically (temporary file + move); on failure nothing changes. A document that could not be
     * reopened (an instance whose definition is missing) fails with an IOException before anything is written.
     */
    public void save(Path target) throws IOException {
        String content;
        try {
            content = new CircuitFormat(registry).write(document);
        } catch (IllegalStateException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        Path absolute = target.toAbsolutePath();
        Path temporary = absolute.getParent().resolve(".vsim2-" + UUID.randomUUID() + ".tmp");
        Files.createFile(temporary);
        try {
            copyPermissions(absolute, temporary);
            Files.writeString(temporary, content);
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        path = absolute;
        backingFile = FileNames.normalized(absolute);
        importedFromVsim1 = false;
        history.markSaved();
    }

    /** Keeps the permissions of the file being replaced; a new file keeps the umask-based ones of createFile. */
    private static void copyPermissions(Path existing, Path temporary) throws IOException {
        if (Files.exists(existing) && FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(existing));
        }
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    /** For tests: views and extensions must remove the change listeners they add when disposed. */
    public int changeListenerCount() {
        return changeListeners.size();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            simulation.close();
        }
    }

    /**
     * Recompiles and republishes the circuit. It runs as a document listener while a command is being applied, so it
     * never throws: a failure (for example sub-circuits nested too deep) pauses the simulation and shows up as the
     * snapshot's failure, and the command still reaches the undo history.
     */
    private void synchronize() {
        try {
            netlist = compiler.compile(document);
            netlistToken = document.revision();
            simulation.update(flattener.flatten(document, netlist));
        } catch (RuntimeException exception) {
            LOGGER.error("Could not prepare {} for simulation", displayName(), exception);
            if (netlistToken != document.revision()) {
                netlist = Netlist.EMPTY;
                netlistToken = document.revision();
            }
            simulation.reportFailure(exception);
        }
    }

    private void fireChanged() {
        changeListeners.forEach(Runnable::run);
    }
}
