package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.io.AtomicFiles;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.io.OpenedCircuit;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.SimulationThread;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.ProbeNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private final UUID recoveryId = UUID.randomUUID();
    private Path path;
    private Path backingFile;
    private boolean importedFromVsim1;
    private Netlist netlist = Netlist.EMPTY;
    private long netlistToken = -1;
    private boolean closed;
    private boolean pauseOnShortCircuit;
    private Map<String, String> probeNets = Map.of();
    private long changeCount;
    private boolean stepMode;
    private String lastTunnelEntranceName;
    /** Whether the simulated circuit has a clock, also inside a user component (M6.1-B); set with each recompile. */
    private boolean hasClock;
    private final List<Runnable> stepModeListeners = new CopyOnWriteArrayList<>();

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
        document.addProbeListener(() -> simulation.setProbes(updateProbeNets()));
        synchronize();
        simulation.start();
        simulation.resume();
        // M6.1-E: names an older VSim stored by default become live; not a command, the document stays unmodified.
        ProbeNames.forgetDefaultNames(document, this.registry);
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
        // Not CircuitReader.open: it would name a nameless document after the copy file.
        String content;
        try {
            content = Files.readString(copy);
        } catch (IOException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, copy.toString(), exception);
        }
        CircuitDocument recoveredDocument = new CircuitReader(registry).read(content);
        OpenDocument openDocument = new OpenDocument(recoveredDocument, registry, originalPath, false);
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

    /**
     * «Guardar» has something to do: unsaved changes, or content that has no file of its own yet (an example opened as
     * an untitled copy, a VSim 1 file that must be saved in the new format).
     */
    public boolean canSave() {
        return isModified() || (path == null && !document.isEmpty());
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

    /**
     * Sets switches or input pins of the live simulation, as clicks do (M6.1-C: a truth-table row tried in the
     * circuit): not a document change and no undo step; the saved initial values are untouched.
     */
    public void setInputs(Map<ElementId, Value> values) {
        Map<String, Value> byId = new HashMap<>();
        values.forEach((componentId, value) -> byId.put(componentId.value(), value));
        simulation.setInputs(byId);
    }

    /**
     * M7: a value typed into a bus input pin. Like a click on a switch it is queued to the simulation thread (never
     * applied from here): not a document change, no undo step, kept by «Reiniciar»; the saved initial value stays.
     */
    public void setInputValue(ElementId componentId, Value value) {
        simulation.setInputs(Map.of(componentId.value(), value));
    }

    /** Iniciar: runs the simulation and leaves «Paso a paso» (M6.1-B). */
    public void resume() {
        setStepMode(false);
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
     * Completes with the snapshot published once every simulation request made so far (edits, clicks, typed values,
     * pause, «Avanzar», «Reiniciar», …) has been applied, with the step, next event time and pending components
     * published with it. For tests: a known state to wait for instead of polling. Never block the EDT on it.
     */
    public CompletableFuture<SimulationSnapshot> simulationPublished() {
        return simulation.published();
    }

    /** The recorded waveforms of this document's channels (spec §3.3); safe to read from any thread. */
    public WaveformSnapshot latestWaveforms() {
        return simulation.latestWaveforms();
    }

    /** «Limpiar trazas»: drops the recorded events and keeps the channels. */
    public void clearWaveforms() {
        simulation.clearWaveforms();
    }

    /**
     * The name of the last tunnel entrada placed or renamed in this document during this session, which a new salida
     * proposes (spec §2); empty until there is one.
     */
    public Optional<String> lastTunnelEntranceName() {
        return Optional.ofNullable(lastTunnelEntranceName);
    }

    public void rememberTunnelEntranceName(String name) {
        lastTunnelEntranceName = Objects.requireNonNull(name);
    }

    /** «Paso a paso» (M6.1-B): a session mode of the document, shared by all its views; off when opened. */
    public boolean stepMode() {
        return stepMode;
    }

    /**
     * Turns «Paso a paso» on (which pauses the simulation) or off (which leaves it paused; Iniciar resumes, see
     * {@link #resume()}). The step-mode listeners hear only real changes.
     */
    public void setStepMode(boolean enabled) {
        if (enabled == stepMode) {
            return;
        }
        stepMode = enabled;
        if (enabled) {
            simulation.pause();
        }
        stepModeListeners.forEach(Runnable::run);
    }

    public void addStepModeListener(Runnable listener) {
        stepModeListeners.add(listener);
    }

    public void removeStepModeListener(Runnable listener) {
        stepModeListeners.remove(listener);
    }

    /** For tests: views must remove the step-mode listeners they add when disposed. */
    public int stepModeListenerCount() {
        return stepModeListeners.size();
    }

    /** «Avanzar al próximo evento» (M6.1-B): ignored while the simulation runs. */
    public void advanceToNextEvent() {
        simulation.advanceToNextEvent();
    }

    /** «Avanzar al próximo flanco de reloj» (M6.1-B): ignored while the simulation runs. */
    public void advanceToNextClockEdge() {
        simulation.advanceToNextClockEdge();
    }

    /** True when the circuit has a clock, also inside a user component; follows every edit (M6.1-B). */
    public boolean hasClock() {
        return hasClock;
    }

    /**
     * «Reiniciar» (M6.1-B): the simulation starts again at time 0 — flip-flops at Q = 0, clocks from their start,
     * waveforms cleared — with every switch where it is now. Not a document change (no undo step, not "modified");
     * running or paused and the step mode stay as they were.
     */
    public void restart() {
        try {
            simulation.restart(flattener.flatten(document, netlist), updateProbeNets());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not restart the simulation of {}", displayName(), exception);
            simulation.reportFailure(exception);
        }
    }

    /** The last «Avanzar» or click on a switch while paused; safe to read from any thread. */
    public Optional<StepReport> latestStep() {
        return simulation.latestStep();
    }

    /** The next pending event time as of the latest snapshot (empty: the circuit is stable); read after the snapshot. */
    public OptionalLong latestNextEventTime() {
        return simulation.latestNextEventTime();
    }

    /** The components (flat ids) with an event at the next event time; empty while running (M6.1-B). */
    public Set<String> latestPendingComponents() {
        return simulation.latestPendingComponents();
    }

    /** Probe id → top-level net id for the probes that resolve in the current netlist; the others are absent. */
    public Map<String, String> probeNets() {
        return probeNets;
    }

    /**
     * The document as format v2 text: what {@link #save} writes and what autosave copies, so the two never differ.
     * EDT only.
     *
     * @throws IllegalStateException when the document cannot be written (an instance whose definition is missing)
     */
    public String serialized() {
        return new CircuitFormat(registry).write(document);
    }

    /**
     * Writes format v2 atomically (temporary file + move); on failure, a read-only target included, nothing changes. A document that could not be
     * reopened (an instance whose definition is missing) fails with an IOException before anything is written.
     */
    public void save(Path target) throws IOException {
        String content;
        try {
            content = serialized();
        } catch (IllegalStateException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        Path absolute = target.toAbsolutePath();
        if (Files.exists(absolute) && !Files.isWritable(absolute)) {
            // M2 ruling: saving over a read-only file fails (with an error dialog), it does not replace it
            throw new AccessDeniedException(absolute.toString(), null, "the file is read-only");
        }
        AtomicFiles.writeString(absolute, content);
        path = absolute;
        backingFile = FileNames.normalized(absolute);
        importedFromVsim1 = false;
        history.markSaved();
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
            // One request with the circuit, so no tick runs between the new circuit and its channels (spec §3.4).
            FlatCircuit circuit = flattener.flatten(document, netlist);
            hasClock = circuit.components().stream()
                    .anyMatch(component -> component.type().id().equals(StandardComponentTypes.CLOCK));
            simulation.update(circuit, updateProbeNets());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not prepare {} for simulation", displayName(), exception);
            probeNets = Map.of();
            hasClock = false;
            if (netlistToken != document.revision()) {
                netlist = Netlist.EMPTY;
                netlistToken = document.revision();
            }
            simulation.reportFailure(exception);
        }
    }

    /**
     * Resolves the probes against the current netlist into {@link #probeNets()} and returns the channels for the
     * simulation: every probe, an unresolved one with an empty net id, so its channel records a gap and keeps its
     * history until it resolves again.
     */
    private Map<String, String> updateProbeNets() {
        List<Probe> probes = document.probes();
        Map<String, String> resolved = ProbeResolver.resolve(probes, document, netlist);
        probeNets = resolved;
        Map<String, String> channels = new LinkedHashMap<>();
        for (Probe probe : probes) {
            channels.put(probe.id(), resolved.getOrDefault(probe.id(), ""));
        }
        return channels;
    }

    private void fireChanged() {
        changeListeners.forEach(Runnable::run);
    }
}
