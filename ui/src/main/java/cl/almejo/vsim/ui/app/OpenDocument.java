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
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.SimulationThread;
import cl.almejo.vsim.ui.i18n.Messages;

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

    public OpenDocument(CircuitDocument document, ComponentTypeRegistry registry, Path path, boolean importedFromVsim1) {
        this.document = document;
        this.registry = registry;
        this.path = path == null ? null : path.toAbsolutePath();
        this.importedFromVsim1 = importedFromVsim1;
        this.history = new History(document);
        this.compiler = new NetlistCompiler(registry);
        this.flattener = new CircuitFlattener(registry);
        this.simulation = new SimulationThread(new SimulationEngine());
        document.addListener((changed, change) -> synchronize());
        history.addListener(changed -> fireChanged());
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

    public CircuitDocument document() {
        return document;
    }

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

    public SimulationSnapshot latestSnapshot() {
        return simulation.latestSnapshot();
    }

    /** Writes format v2 atomically (temporary file + move); on failure nothing changes. */
    public void save(Path target) throws IOException {
        String content = new CircuitFormat(registry).write(document);
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

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            simulation.close();
        }
    }

    private void synchronize() {
        netlist = compiler.compile(document);
        netlistToken = document.revision();
        simulation.update(flattener.flatten(document, netlist));
    }

    private void fireChanged() {
        changeListeners.forEach(Runnable::run);
    }
}
