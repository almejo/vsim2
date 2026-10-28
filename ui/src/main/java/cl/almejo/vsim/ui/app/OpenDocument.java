package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.LoosensPins;
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
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.InputAssignment;
import cl.almejo.vsim.core.simulation.MemoryMapFrame;
import cl.almejo.vsim.core.simulation.MemoryMapRequest;
import cl.almejo.vsim.core.simulation.RequestReport;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.SimulationThread;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.ui.waveform.ProbeNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private List<ConnectivityProblem> connectivityProblems = List.of();
    /** The pins each applied command left unconnected and that are still loose ({@link #loosenedPins()}). */
    private final Map<Command, Set<PinRef>> loosenedByCommand = new LinkedHashMap<>();
    private long netlistToken = -1;
    private boolean closed;
    private boolean pauseOnShortCircuit;
    private Map<String, String> probeNets = Map.of();
    private long changeCount;
    private long contentVersion;
    /** The command on top of the undo stack at the last history notification (for {@link #contentVersion()}). */
    private Command topOfHistory;
    private boolean stepMode;
    private String lastTunnelEntranceName;
    /** Whether the simulated circuit has a clock, also inside a user component (M6.1-B); set with each recompile. */
    private boolean hasClock;
    private final Listeners<Runnable> stepModeListeners = new Listeners<>();

    public OpenDocument(CircuitDocument document, ComponentTypeRegistry baseRegistry, Path path,
                        boolean importedFromVsim1) {
        this.document = document;
        // Each document resolves the subcircuit:<id> types of its own definitions (spec §5.6 embedded copies).
        this.registry = baseRegistry.copy();
        this.registry.addResolver(SubcircuitTypes.resolverFor(document));
        this.path = path == null ? null : path.toAbsolutePath();
        this.importedFromVsim1 = importedFromVsim1;
        // memory links are absolute while the circuit is open, relative to its folder in the file (Task 5)
        MemoryLinkPaths.absoluteLinks(document, Optional.ofNullable(this.path));
        this.history = new History(document);
        this.compiler = new NetlistCompiler(this.registry);
        this.flattener = new CircuitFlattener(this.registry);
        this.simulation = new SimulationThread(new SimulationEngine());
        document.addListener((changed, change) -> synchronize());
        history.addListener(changed -> {
            changeCount++;
            Command top = changed.undoCommand().orElse(null);
            if (top != topOfHistory) {
                topOfHistory = top;
                contentVersion++;
            }
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

    /** {@code preferred} when given, else the folder of this document's file, else the user's home. */
    public Path startDirectory(Optional<Path> preferred) {
        return preferred.or(() -> path().map(Path::getParent))
                .orElseGet(() -> Path.of(System.getProperty("user.home")));
    }

    public String suggestedFileName() {
        return FileNames.safeName(displayName()) + FileNames.EXTENSION;
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

    /**
     * The version of the circuit the AI's edits are prepared against (IA.3): grows on every undo-history notification
     * that changes the command on top of the undo stack (execute, undo, redo, clear), not when the file is saved or
     * marked unsaved, so the user's Ctrl+S never makes the AI's next edit or a step waiting for «Siguiente» stale.
     */
    public long contentVersion() {
        return contentVersion;
    }

    public Netlist netlist() {
        return netlist;
    }

    /** Revision of the document the current netlist was compiled from; compare with {@code snapshot.token()}. */
    public long netlistToken() {
        return netlistToken;
    }

    /** Current wiring warnings, available immediately after a document edit without a simulation tick. */
    public List<ConnectivityProblem> connectivityProblems() {
        return connectivityProblems;
    }

    /**
     * The pins of placed pieces that an accepted shape change or definition update left unconnected on purpose
     * («Aplicar y conectar a mano», spec §6.4): this session's to-do list, never saved. A pin leaves the set for good
     * once it is on a net with another pin again; undoing the command takes its pins away, redoing brings them back.
     * Up to date for every document listener told about the change (the marks are set inside the command's apply and
     * revert, and checked against the netlist compiled before the other listeners run). Read-only.
     */
    public Set<PinRef> loosenedPins() {
        Set<PinRef> pins = new LinkedHashSet<>();
        loosenedByCommand.values().forEach(pins::addAll);
        pins.removeIf(pin -> !isPinOfThisCircuit(pin) || isOnANetWithAnotherPin(pin));
        return Collections.unmodifiableSet(pins);
    }

    /** Runs {@code command} as one undo step; one that may loosen pins gets its pins marked while it is applied. */
    public void execute(Command command) {
        history.execute(command instanceof LoosensPins loosening ? new MarkingCommand(command, loosening) : command);
    }

    private boolean isPinOfThisCircuit(PinRef pin) {
        return document.component(pin.componentId())
                .map(component -> pin.pinIndex() < registry.typeOf(component).pinCount())
                .orElse(false);
    }

    private boolean isOnANetWithAnotherPin(PinRef pin) {
        return netlist.netForPin(pin).map(net -> net.pins().size() > 1).orElse(false);
    }

    /** Forgets for good the marks of pins that are connected to another pin again (after each recompile). */
    private void forgetReconnectedPins() {
        loosenedByCommand.values().forEach(pins -> pins.removeIf(this::isOnANetWithAnotherPin));
    }

    /**
     * {@code command} with its loosened pins marked from just before it applies until just before it reverts, so
     * the document listeners see the marks that belong to the change they are told about. Everything else is the
     * command's own.
     */
    private final class MarkingCommand implements Command {
        private final Command command;
        private final LoosensPins loosening;

        private MarkingCommand(Command command, LoosensPins loosening) {
            this.command = command;
            this.loosening = loosening;
        }

        @Override
        public void apply(CircuitDocument target) {
            loosenedByCommand.put(this, new LinkedHashSet<>(loosening.loosenedPins()));
            try {
                command.apply(target);
            } catch (RuntimeException exception) {
                loosenedByCommand.remove(this);
                throw exception;
            }
        }

        @Override
        public void revert(CircuitDocument target) {
            Set<PinRef> marks = loosenedByCommand.remove(this);
            try {
                command.revert(target);
            } catch (RuntimeException exception) {
                if (marks != null) {
                    loosenedByCommand.put(this, marks);
                }
                throw exception;
            }
        }

        @Override
        public String description() {
            return command.description();
        }

        @Override
        public boolean changesNothing() {
            return command.changesNothing();
        }
    }

    public void interact(ElementId componentId) {
        simulation.interact(componentId.value());
    }

    /** Clicks bit {@code bit} of a multi-bit Switch: an input event like {@link #interact}, not an edit. */
    public void interactBit(ElementId componentId, int bit) {
        simulation.interactBit(componentId.value(), bit);
    }

    /**
     * «Restaurar contenido inicial»: queues a request putting the RAM {@code componentId} back to its initial
     * contents without restarting the rest of the simulation. Not a document change, no undo step.
     */
    public void restoreMemory(ElementId componentId) {
        simulation.restoreMemory(componentId.value());
    }

    /**
     * The words the memory {@code componentId} holds now in the simulation (for «Volcar» and the editor's live grid):
     * completed on the simulation thread, empty when the engine has no such memory. Never wait for it on the EDT.
     */
    public CompletableFuture<Optional<MemoryContents>> requestMemoryContents(ElementId componentId) {
        return simulation.requestMemoryContents(componentId.value());
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
     * What the «Mapa de memoria» shows (spec §6): {@link #latestMemoryMap()} answers this request from now on; empty
     * stops the frames. One request per document: the last one set wins.
     */
    public void setMemoryMapRequest(Optional<MemoryMapRequest> request) {
        simulation.setMemoryMapRequest(request);
    }

    /** The latest memory map frame (at most every 16 ms); safe to read from any thread. */
    public Optional<MemoryMapFrame> latestMemoryMap() {
        return simulation.latestMemoryMap();
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
        stepModeListeners.fire(Runnable::run);
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

    // --- IA.1: simulation requests with an event budget and one receipt each (SimulationThread, A§7) ---

    /** Sets inputs in order, each reported accepted or rejected, within {@code eventBudget} events. */
    public CompletableFuture<RequestReport> setInputsWithin(List<InputAssignment> inputs, long eventBudget) {
        return simulation.setInputsWithin(inputs, eventBudget);
    }

    /** {@code count} «Avanzar» within {@code eventBudget} events; refused while running. */
    public CompletableFuture<RequestReport> advanceToNextEventWithin(int count, long eventBudget) {
        return simulation.advanceToNextEventWithin(count, eventBudget);
    }

    /** {@code count} «Avanzar al próximo flanco de reloj» within {@code eventBudget} events; refused while running. */
    public CompletableFuture<RequestReport> advanceToNextClockEdgeWithin(int count, long eventBudget) {
        return simulation.advanceToNextClockEdgeWithin(count, eventBudget);
    }

    /** «Avanzar N t» within {@code eventBudget} events; refused while running. */
    public CompletableFuture<RequestReport> stepWithin(long duration, long eventBudget) {
        return simulation.stepWithin(duration, eventBudget);
    }

    /**
     * {@link #restart()} within {@code eventBudget} events. A circuit that cannot be flattened fails the future (and,
     * as for the user, pauses the simulation with the failure).
     */
    public CompletableFuture<RequestReport> restartWithin(long eventBudget) {
        try {
            return simulation.restartWithin(flattener.flatten(document, netlist), updateProbeNets(), eventBudget);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not restart the simulation of {}", displayName(), exception);
            simulation.reportFailure(exception);
            return CompletableFuture.failedFuture(exception);
        }
    }

    /** A receipt of the simulation once every request made so far has run (nothing runs). */
    public CompletableFuture<RequestReport> simulationReport() {
        return simulation.report();
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
        return serializedFor(Optional.ofNullable(path));
    }

    /** The format v2 text of the document saved as {@code target}: memory links relative to its folder. */
    private String serializedFor(Optional<Path> target) {
        return new CircuitFormat(registry).write(document, MemoryLinkPaths.relativeLinks(target));
    }

    /**
     * Writes format v2 atomically (temporary file + move); on failure, a read-only target included, nothing changes. A document that could not be
     * reopened (an instance whose definition is missing) fails with an IOException before anything is written.
     */
    public void save(Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        String content;
        try {
            content = serializedFor(Optional.of(absolute));
        } catch (IllegalStateException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
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
            forgetReconnectedPins();
            // One request with the circuit, so no tick runs between the new circuit and its channels (spec §3.4).
            FlatCircuit circuit = flattener.flatten(document, netlist);
            connectivityProblems = circuit.connectivityProblems();
            hasClock = circuit.components().stream()
                    .anyMatch(component -> component.type().id().equals(StandardComponentTypes.CLOCK));
            simulation.update(circuit, updateProbeNets());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not prepare {} for simulation", displayName(), exception);
            probeNets = Map.of();
            hasClock = false;
            connectivityProblems = List.of();
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
