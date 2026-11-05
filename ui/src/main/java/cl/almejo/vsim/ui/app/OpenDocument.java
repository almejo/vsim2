package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.command.LoosensPins;
import cl.almejo.vsim.core.command.SetSwitchValueCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.SwitchValues;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.io.AtomicFiles;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.io.OpenedCircuit;
import cl.almejo.vsim.core.library.BodyEdits;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.ClockEdgeMode;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.InputAssignment;
import cl.almejo.vsim.core.simulation.MemoryMapFrame;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.MemoryMapRequest;
import cl.almejo.vsim.core.simulation.PauseNotice;
import cl.almejo.vsim.core.simulation.TerminalReading;
import cl.almejo.vsim.core.simulation.TerminalRequest;
import cl.almejo.vsim.core.simulation.TerminalWindowFrame;
import cl.almejo.vsim.core.simulation.RequestReport;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.SimulationThread;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.terminal.TypingEdit;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * One circuit being edited: document, undo history, compiled netlist and its own simulation thread.
 * Use it from the Swing event thread only.
 *
 * <p>A <em>body place</em> ({@link #openBody}, spec §10.3) is the same class in body mode: one definition's body
 * opened as a place of its document. Its {@link #document()} is a working copy of the body (shared by every place of
 * that definition, {@link BodyWorkingCopy}); history, registry, file and simulation are the document's; its snapshot
 * is the document's seen from inside the context instance ({@link #contextPath()}); inputs go to that instance by
 * path, never as edits; and every command is written back to the definition as one undo step of the document
 * ({@link EditInsideCommand}). The ~50 users of this class (canvas, tools, panels, extensions) work on either.
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
    /** See {@link #showUntitledAs}; blank when the window shows the circuit's name. */
    private String untitledName = "";
    private Netlist netlist = Netlist.EMPTY;
    private List<ConnectivityProblem> connectivityProblems = List.of();
    /** The pins each applied command left unconnected and that are still loose ({@link #loosenedPins()}). */
    private final Map<Command, Set<PinRef>> loosenedByCommand = new LinkedHashMap<>();
    /**
     * The value each switch was given (a click, an undo or redo of one, a value typed or tried in the circuit) until the
     * simulation publishes it: a click before that toggles from here, not from a snapshot that does not have the value
     * yet. Cleared from the simulation thread.
     */
    private final Map<ElementId, Value> unpublishedValues = new ConcurrentHashMap<>();
    /** The push buttons pressed and not yet let go (simulation ids), by any pane, window or place of this document. */
    private final Set<String> heldButtons = new LinkedHashSet<>();
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
    /** The working copies of the bodies opened from this document, by definition id (main document only). */
    private final Map<String, BodyWorkingCopy> workingCopies = new HashMap<>();
    /** The pieces each applied inside edit could not give a new pin where it goes ({@link #keptOldShapes()}). */
    private final Map<Command, List<KeptOldShape>> keptByCommand = new LinkedHashMap<>();
    /** Null for a document; for a body place, its document and working copy. */
    private final Body body;
    /** A body place's context instance path; empty is «Sin valores» (always empty for a document). */
    private volatile Optional<String> contextPath = Optional.empty();
    private final Listeners<Runnable> refreshListeners = new Listeners<>();
    private final Listeners<Runnable> bodyRemovedListeners = new Listeners<>();
    /** A body place's last remapped snapshot and what it was made from; replaced whole, so any thread may read it. */
    private volatile Remapped remapped;

    /** {@code result} is {@code from} seen under {@code context} with {@code token}. */
    private record Remapped(SimulationSnapshot from, Optional<String> context, long token, SimulationSnapshot result) {
        boolean isFor(SimulationSnapshot snapshot, Optional<String> path, long expectedToken) {
            return from == snapshot && context.equals(path) && token == expectedToken;
        }
    }

    /**
     * What a body place hangs on: its document, the working copy it shares, the document change listener it added,
     * and how far it has followed the copy's refreshes and the definition's removal.
     */
    private static final class Body {
        private final OpenDocument parent;
        private final BodyWorkingCopy workingCopy;
        private final Runnable parentListener;
        private long seenRefreshes;
        private boolean removalNotified;

        private Body(OpenDocument parent, BodyWorkingCopy workingCopy, Runnable parentListener) {
            this.parent = parent;
            this.workingCopy = workingCopy;
            this.parentListener = parentListener;
            this.seenRefreshes = workingCopy.refreshes();
        }
    }

    /** No token of a body's netlist: the snapshot of «Sin valores» never matches, so nothing shows a value. */
    private static final long NO_VALUES_TOKEN = Long.MIN_VALUE;

    /** The bundle key of the phase-1 refusal (spec §6.2 c). */
    private static final String INTERFACE_LOCKED = "inside.interfaceLocked";

    public OpenDocument(CircuitDocument document, ComponentTypeRegistry baseRegistry, Path path,
                        boolean importedFromVsim1) {
        this.body = null;
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
        document.addListener((changed, change) -> documentChanged(change));
        history.addListener(changed -> {
            changeCount++;
            Command top = changed.undoCommand().orElse(null);
            if (top != topOfHistory) {
                topOfHistory = top;
                if (!changedOnlySwitchValues(changed)) {
                    contentVersion++;
                }
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

    /** A body place of {@code parent} on {@code workingCopy}; see {@link #openBody}. No thread of its own. */
    private OpenDocument(OpenDocument parent, BodyWorkingCopy workingCopy, Optional<String> contextPath) {
        this.document = workingCopy.document();
        this.registry = parent.registry;
        this.history = parent.history;
        this.compiler = parent.compiler;
        this.flattener = parent.flattener;
        this.simulation = parent.simulation;
        this.contextPath = contextPath;
        Runnable parentListener = this::parentChanged;
        this.body = new Body(parent, workingCopy, parentListener);
        parent.addChangeListener(parentListener);
    }

    /**
     * Opens the body of {@code definitionId} as a place of this document, showing the values of the instance at
     * {@code contextPath} (empty: «Sin valores»; a path that is not one of its instances falls back to the first one,
     * else «Sin valores», as when an instance goes). Places of one definition share one working copy, made when the first
     * opens and dropped when the last {@linkplain #close() closes}. On a body place it opens from its document.
     *
     * @throws IllegalArgumentException when the document has no such definition
     */
    public OpenDocument openBody(String definitionId, Optional<String> contextPath) {
        if (body != null) {
            return body.parent.openBody(definitionId, contextPath);
        }
        ComponentDefinition definition = document.definition(definitionId).orElseThrow(() ->
                new IllegalArgumentException("the circuit has no definition " + definitionId));
        BodyWorkingCopy workingCopy = workingCopies.computeIfAbsent(definitionId,
                id -> new BodyWorkingCopy(this, definition));
        workingCopy.acquire();
        OpenDocument place = new OpenDocument(this, workingCopy, Objects.requireNonNull(contextPath, "contextPath"));
        // a path that is not an instance of the definition (any more: a remembered session tab) falls back
        contextPath.ifPresent(path -> place.fallBackIfGone(path, definitionId));
        return place;
    }

    /** True for a body place ({@link #openBody}), false for a document. */
    public boolean isBody() {
        return body != null;
    }

    /** A body place's document; empty for a document. */
    public Optional<OpenDocument> parent() {
        return body == null ? Optional.empty() : Optional.of(body.parent);
    }

    /** The definition a body place edits; empty for a document. */
    public Optional<String> definitionId() {
        return body == null ? Optional.empty() : Optional.of(body.workingCopy.definitionId());
    }

    /** The instance path whose values a body place shows; empty is «Sin valores» (and always so for a document). */
    public Optional<String> contextPath() {
        return contextPath;
    }

    /**
     * Shows the values of another instance (empty: «Sin valores»): not an edit; the change listeners hear it and the
     * next {@link #latestSnapshot()} is remapped under the new path. Ignored on a document.
     */
    public void setContextPath(Optional<String> newContextPath) {
        Objects.requireNonNull(newContextPath, "newContextPath");
        if (body == null || newContextPath.equals(contextPath)) {
            return;
        }
        contextPath = newContextPath;
        fireChanged();
    }

    /** What a tab of this place is called: a body place's definition name; a document's {@link #displayName()}. */
    public String definitionName() {
        if (body == null) {
            return displayName();
        }
        return body.parent.document.definition(body.workingCopy.definitionId()).map(ComponentDefinition::name)
                .orElse("");
    }

    /** Every place {@code definitionId} is used in the document, parents first (the contexts a body tab offers). */
    public List<InstancePaths.InstanceUse> instancePaths(String definitionId) {
        return InstancePaths.usesOf(main().document, definitionId);
    }

    /**
     * Phase 1 of spec §6.2 c: true for a body place whose definition is used inside another placed definition. Its
     * interface (the «Pin de entrada / salida» parts: adding, removing, renaming, widening) cannot change yet, and
     * {@link #execute} refuses such an edit before applying it.
     */
    public boolean interfaceLocked() {
        return body != null && InstancePaths.isUsedInsideAnother(body.parent.document,
                body.workingCopy.definitionId());
    }

    /** The document's pieces that could not take a new pin of an inside edit where it goes, while it is applied. */
    public List<KeptOldShape> keptOldShapes() {
        if (body != null) {
            return body.parent.keptOldShapes();
        }
        List<KeptOldShape> kept = new ArrayList<>();
        keptByCommand.values().forEach(kept::addAll);
        return List.copyOf(kept);
    }

    /** Runs after the working copy followed a change made elsewhere (the canvas cancels its gesture there). */
    public void addRefreshListener(Runnable listener) {
        refreshListeners.add(listener);
    }

    public void removeRefreshListener(Runnable listener) {
        refreshListeners.remove(listener);
    }

    /** For tests: views must remove the refresh listeners they add when disposed. */
    public int refreshListenerCount() {
        return refreshListeners.size();
    }

    /** Runs once when a body place's definition is removed from the document (its tab closes). */
    public void addBodyRemovedListener(Runnable listener) {
        bodyRemovedListeners.add(listener);
    }

    public void removeBodyRemovedListener(Runnable listener) {
        bodyRemovedListeners.remove(listener);
    }

    /** The document itself; for a body place, its document. */
    private OpenDocument main() {
        return body == null ? this : body.parent;
    }

    /**
     * A body place hears every change of its document: the working copy follows it (and the refresh listeners run
     * when it did), a removed definition is told once, a context instance that is gone falls back to the first
     * remaining use (or «Sin valores»), and the place's own change listeners run.
     */
    private void parentChanged() {
        BodyWorkingCopy workingCopy = body.workingCopy;
        workingCopy.synchronize();
        if (workingCopy.isRemoved()) {
            if (!body.removalNotified) {
                body.removalNotified = true;
                bodyRemovedListeners.fire(Runnable::run);
            }
            return;
        }
        if (workingCopy.refreshes() != body.seenRefreshes) {
            body.seenRefreshes = workingCopy.refreshes();
            refreshListeners.fire(Runnable::run);
        }
        contextPath.ifPresent(path -> fallBackIfGone(path, workingCopy.definitionId()));
        fireChanged();
    }

    private void fallBackIfGone(String path, String definitionId) {
        List<InstancePaths.InstanceUse> uses = instancePaths(definitionId);
        if (uses.stream().noneMatch(use -> use.path().equals(path))) {
            contextPath = uses.stream().findFirst().map(InstancePaths.InstanceUse::path);
        }
    }

    /**
     * A body place's edit: refused before anything changes when it would change a locked interface
     * ({@link #interfaceLocked()}), else one undo step of the document that writes the body back.
     */
    private void executeInside(Command command) {
        BodyWorkingCopy workingCopy = body.workingCopy;
        String definitionId = workingCopy.definitionId();
        if (interfaceLocked()) {
            refuseInterfaceChange(command, definitionId);
        }
        EditInsideCommand inside = new EditInsideCommand(definitionId, command, workingCopy.document(), registry);
        workingCopy.write(() -> body.parent.execute(inside));
    }

    /** Tries {@code command} on a copy of the working copy and refuses it when the interface would change. */
    private void refuseInterfaceChange(Command command, String definitionId) {
        ComponentDefinition definition = body.parent.document.definition(definitionId).orElseThrow();
        CircuitDocument scratch = document.copy();
        command.apply(scratch);
        if (BodyEdits.interfaceChanged(definition, BodyEdits.rebuilt(definition, scratch))) {
            List<String> containing = DefinitionUpdateSafety.containingNames(body.parent.document, definitionId);
            throw new InsideEditRefusedException(INTERFACE_LOCKED, List.of(definition.name(), containing.getFirst()));
        }
    }

    /** {@code id} as the simulation names it: under the context path in a body place, as it is in a document. */
    private String flatId(String id) {
        return contextPath.map(path -> InstancePath.child(path, id)).orElse(id);
    }

    private String flatId(ElementId id) {
        return flatId(id.value());
    }

    /** A body place with no context: nothing is simulated there, so its inputs go nowhere (spec §6.3). */
    private boolean showsNoValues() {
        return body != null && contextPath.isEmpty();
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
        return openRecovered(copy, originalPath, null, registry);
    }

    /**
     * As {@link #openRecovered(Path, Path, ComponentTypeRegistry)}; an untitled document (null {@code originalPath})
     * also gets back the window name it had ({@link #showUntitledAs}), which may differ from its circuit's name.
     */
    public static OpenDocument openRecovered(Path copy, Path originalPath, String untitledWindowName,
                                             ComponentTypeRegistry registry) throws CircuitFormatException {
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
        // Only a name really apart from the circuit's and from the localized untitled text is kept; the others follow
        // the circuit name (and the language) as before.
        if (originalPath == null && untitledWindowName != null
                && !untitledWindowName.isBlank()
                && !untitledWindowName.strip().equals(recoveredDocument.name())
                && !untitledWindowName.strip().equals(Messages.text("document.untitled"))) {
            openDocument.showUntitledAs(untitledWindowName);
        }
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
        return Optional.ofNullable(main().path);
    }

    /** True for the file this document was opened from (also a VSim 1 import) or last saved to, in any spelling. */
    public boolean isBackedBy(Path file) {
        OpenDocument main = main();
        return main.backingFile != null && main.backingFile.equals(FileNames.normalized(file));
    }

    public boolean isImportedFromVsim1() {
        return main().importedFromVsim1;
    }

    /** True when Save must ask for a file: never saved, or imported from VSim 1 (never overwrite the old file). */
    public boolean needsSaveAs() {
        return main().path == null;
    }

    /**
     * What the window of this document shows until it is saved, instead of the circuit's own name: an imported
     * Logisim project shows its {@code .circ} file's name while its circuit keeps Logisim's main circuit name. Blank
     * goes back to the circuit's name. Not an edit: nothing is recorded.
     */
    public void showUntitledAs(String windowName) {
        if (body != null) {
            body.parent.showUntitledAs(windowName);
            return;
        }
        untitledName = windowName == null ? "" : windowName.strip();
    }

    /**
     * The circuit's own name (Propiedades ▸ «Nombre del circuito»), kept in the file and apart from the file's name;
     * {@link #displayName()} while the circuit has none (a new one, until its first save).
     */
    public String circuitName() {
        if (body != null) {
            return body.parent.circuitName();
        }
        return document.name().isBlank() ? displayName() : document.name();
    }

    /** What the window shows: the file's name; before the first save, the name given to the window or the circuit's. */
    public String displayName() {
        if (body != null) {
            return body.parent.displayName();
        }
        if (path != null) {
            return fileStem(path);
        }
        if (!untitledName.isBlank()) {
            return untitledName;
        }
        return document.name().isBlank() ? Messages.text("document.untitled") : document.name();
    }

    private static String fileStem(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
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
        if (body != null) {
            return body.parent.canSave();
        }
        return isModified() || (path == null && !document.isEmpty());
    }

    /** A fresh, empty, untouched document — opening a file may replace its window. */
    public boolean isPristine() {
        if (body != null) {
            return body.parent.isPristine();
        }
        return path == null && !importedFromVsim1 && document.isEmpty() && !isModified();
    }

    /** Stable id of this document's recovery copy (M6-B); it never changes while the document is open. */
    public UUID recoveryId() {
        return main().recoveryId;
    }

    /**
     * Grows on every undo-history notification (execute, undo, redo, save, {@code markUnsaved}). Autosave copies a
     * modified document when this moved since its last copy, so every change made through {@link #execute} counts.
     */
    public long changeCount() {
        return main().changeCount;
    }

    /**
     * The version of the circuit the AI's edits are prepared against (IA.3): grows on every undo-history notification
     * that changes the command on top of the undo stack (execute, undo, redo, clear), not when the file is saved or
     * marked unsaved, so the user's Ctrl+S never makes the AI's next edit or a step waiting for «Siguiente» stale; nor
     * for a switch's value set by a click ({@link SetSwitchValueCommand}), an input like before.
     */
    public long contentVersion() {
        return main().contentVersion;
    }

    public Netlist netlist() {
        return body == null ? netlist : body.workingCopy.netlist();
    }

    /**
     * The {@linkplain CircuitDocument#structureRevision() structure revision} of the document the current netlist was
     * compiled from (switch clicks since do not change it); compare with {@code snapshot.token()}.
     */
    public long netlistToken() {
        return body == null ? netlistToken : body.workingCopy.netlistToken();
    }

    /**
     * Current wiring warnings, available immediately after a document edit without a simulation tick. A body place
     * has its document's that lie inside the context instance, with the path taken off (so they glow inside);
     * none for «Sin valores».
     */
    public List<ConnectivityProblem> connectivityProblems() {
        if (body == null) {
            return connectivityProblems;
        }
        if (contextPath.isEmpty()) {
            return List.of();
        }
        String prefix = contextPath.get() + InstancePath.SEPARATOR;
        return body.parent.connectivityProblems.stream()
                .filter(problem -> problem.elementId().startsWith(prefix))
                .map(problem -> new ConnectivityProblem(problem.netId().startsWith(prefix)
                        ? problem.netId().substring(prefix.length()) : problem.netId(),
                        problem.elementId().substring(prefix.length()), problem.pinIndex(), problem.position(),
                        problem.cause()))
                .toList();
    }

    /**
     * The pins of placed pieces that an accepted shape change or definition update left unconnected on purpose
     * («Aplicar y conectar a mano», spec §6.4): this session's to-do list, never saved. A pin leaves the set for good
     * once it is on a net with another pin again; undoing the command takes its pins away, redoing brings them back.
     * Up to date for every document listener told about the change (the marks are set inside the command's apply and
     * revert, and checked against the netlist compiled before the other listeners run). Read-only.
     */
    public Set<PinRef> loosenedPins() {
        if (body != null) {
            return Set.of(); // the document's, listed in Problemas
        }
        Set<PinRef> pins = new LinkedHashSet<>();
        loosenedByCommand.values().forEach(pins::addAll);
        pins.removeIf(pin -> !isPinOfThisCircuit(pin) || isOnANetWithAnotherPin(pin));
        return Collections.unmodifiableSet(pins);
    }

    /**
     * Whether the history change just notified only set switch values (a click, its undo): an input's value, which
     * never made the AI's prepared edits stale before a click was an edit, so it does not now.
     */
    private static boolean changedOnlySwitchValues(History changed) {
        return changed.lastChange().filter(SetSwitchValueCommand.class::isInstance).isPresent();
    }

    /**
     * Runs {@code command} as one undo step; one that may loosen pins gets its pins marked while it is applied. On a
     * body place the command edits the working copy and is written back to the definition as one undo step of the
     * document ({@link EditInsideCommand}).
     *
     * @throws InsideEditRefusedException on a body place, before anything changes, when the edit would change the
     *                                    interface of a definition used inside another one ({@link #interfaceLocked()})
     */
    public void execute(Command command) {
        if (body != null) {
            executeInside(command);
            return;
        }
        history.execute(command instanceof LoosensPins loosening ? new MarkingCommand(command, loosening) : command);
    }

    /**
     * The document revision of the last change to the circuit other than switch values (a click, its undo or redo;
     * {@link CircuitDocument#structureRevision()}): the netlist and its token stay across clicks. The truth table keeps
     * following the live inputs across clicks with it.
     */
    public long structureRevision() {
        return document.structureRevision();
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
            Set<PinRef> marks = new LinkedHashSet<>(loosening.loosenedPins());
            loosenedByCommand.put(this, marks);
            try {
                command.apply(target);
            } catch (RuntimeException exception) {
                loosenedByCommand.remove(this);
                throw exception;
            }
            // an inside edit plans its write-back while it applies: its pins are known only now
            marks.addAll(loosening.loosenedPins());
            if (command instanceof EditInsideCommand inside && !inside.keptOldShapes().isEmpty()) {
                keptByCommand.put(this, inside.keptOldShapes());
            }
        }

        @Override
        public void revert(CircuitDocument target) {
            keptByCommand.remove(this);
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

        /** Merges as the command it marks does (a run of nudges or of typing inside a component). */
        @Override
        public Optional<Command> mergedWith(Command next) {
            Command following = next instanceof MarkingCommand marking ? marking.command : next;
            return command.mergedWith(following).map(merged -> merged instanceof LoosensPins mergedLoosening
                    ? new MarkingCommand(merged, mergedLoosening) : merged);
        }
    }

    /**
     * The inside edit {@code command} is or carries (the history holds it inside the mark of its loosened pins), so
     * the undo items can name its place; empty for any other command.
     */
    public static Optional<EditInsideCommand> insideEditOf(Command command) {
        Command unwrapped = command instanceof OpenDocument.MarkingCommand marking ? marking.command : command;
        return unwrapped instanceof EditInsideCommand inside ? Optional.of(inside) : Optional.empty();
    }

    /**
     * A click on an interactive part while simulating: an input event at the current time (also while paused, as a
     * step). On a Switch it is also an edit of its «Valor» (decided 2026-10-28), so Propiedades, the file and the
     * simulation agree: one undo step for a run of clicks on that switch ({@link SetSwitchValueCommand}).
     *
     * <p>Inside a component (a body place) it is a live input of the context instance only: never an edit of the
     * definition, nothing for «Sin valores» (spec §6.4).
     */
    public void interact(ElementId componentId) {
        if (body != null) {
            if (!showsNoValues()) {
                simulation.interact(flatId(componentId));
            }
            return;
        }
        Optional<SwitchClick> click = switchClick(componentId, value -> Optional.of(SwitchValues.toggled(value)));
        simulation.interact(componentId.value());
        click.ifPresent(this::record);
    }

    /** Clicks bit {@code bit} of a multi-bit Switch: an input event like {@link #interact}, and an edit of «Valor». */
    public void interactBit(ElementId componentId, int bit) {
        if (body != null) {
            if (!showsNoValues()) {
                simulation.interactBit(flatId(componentId), bit);
            }
            return;
        }
        Optional<SwitchClick> click = switchClick(componentId, value -> SwitchValues.withBitFlipped(value, bit));
        simulation.interactBit(componentId.value(), bit);
        click.ifPresent(this::record);
    }

    /** What a click on a switch leaves: its new value and the «Valor» text that stores it. */
    private record SwitchClick(ElementId componentId, Value value, Optional<String> text) {
    }

    /**
     * What {@code click} does to {@code componentId} if it is a switch, worked out before the click is queued: the
     * simulation may publish the clicked value at any moment after that, and the click must start from the value
     * before it.
     */
    private Optional<SwitchClick> switchClick(ElementId componentId, Function<Value, Optional<Value>> click) {
        Optional<Component> clicked = document.component(componentId)
                .filter(component -> component.typeId().equals(StandardComponentTypes.SWITCH));
        if (clicked.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> parameters = registry.typeOf(clicked.get()).resolveParameters(clicked.get().parameters());
        return click.apply(valueBeforeClick(componentId, parameters))
                .map(after -> new SwitchClick(componentId, after, SwitchValues.textFor(after, parameters)));
    }

    /**
     * Writes the clicked value into the switch's «Valor», unless it is the one stored already (the simulation had
     * another value, from a truth-table row tried in the circuit or the AI's test): then there is nothing to undo. The
     * simulation already has the click (queued just before), so handing it the edit's value fires nothing.
     */
    private void record(SwitchClick click) {
        expectPublished(click.componentId(), click.value());
        Object stored = document.component(click.componentId())
                .map(component -> registry.typeOf(component).resolveParameters(component.parameters())
                        .get(StandardComponentTypes.VALUE))
                .orElse(null);
        click.text().filter(text -> !text.equals(stored))
                .ifPresent(text -> execute(SetSwitchValueCommand.click(click.componentId(), text)));
    }

    /** Switch {@code componentId} will show {@code value} once the requests queued so far are published. */
    private void expectPublished(ElementId componentId, Value value) {
        unpublishedValues.put(componentId, value);
        simulation.published().thenRun(() -> unpublishedValues.remove(componentId, value));
    }

    /**
     * A change of the document gave switches another «Valor» (a click, its undo or redo: {@code liveValuesOnly}; or,
     * for a switch still waiting to show a value, a value typed in Propiedades): the simulation is handed that one, so
     * a click before it is published starts from it (review I-4). A removed switch is forgotten.
     */
    private void followStoredSwitchValues(DocumentChange change, boolean liveValuesOnly) {
        for (ElementId id : change.removed()) {
            unpublishedValues.remove(id);
        }
        for (ElementId id : change.changed()) {
            if (!liveValuesOnly && !unpublishedValues.containsKey(id)) {
                continue;
            }
            document.component(id).filter(component -> component.typeId().equals(StandardComponentTypes.SWITCH))
                    .map(component -> SwitchValues.valueOf(registry.typeOf(component)
                            .resolveParameters(component.parameters())))
                    .ifPresentOrElse(value -> expectPublished(id, value), () -> unpublishedValues.remove(id));
        }
    }

    /**
     * The value the switch has for a click: the one it was last given while the simulation has not shown it yet, else
     * the one the simulation shows (a truth-table row tried in the circuit sets it without an edit), else its «Valor».
     */
    private Value valueBeforeClick(ElementId componentId, Map<String, Object> parameters) {
        Value stored = SwitchValues.valueOf(parameters);
        Value pending = unpublishedValues.get(componentId);
        if (pending != null) {
            return pending;
        }
        if (simulation.latestSnapshot().componentState(componentId.value())
                instanceof VisibleState.SwitchState live && live.value().width() == stored.width()) {
            return live.value();
        }
        return stored;
    }

    /**
     * «Restaurar contenido inicial»: queues a request putting the RAM {@code componentId} back to its initial
     * contents without restarting the rest of the simulation. Not a document change, no undo step.
     */
    public void restoreMemory(ElementId componentId) {
        if (!showsNoValues()) {
            simulation.restoreMemory(flatId(componentId));
        }
    }

    /**
     * The words the memory {@code componentId} holds now in the simulation (for «Volcar» and the editor's live grid):
     * completed on the simulation thread, empty when the engine has no such memory. Never wait for it on the EDT.
     */
    public CompletableFuture<Optional<MemoryContents>> requestMemoryContents(ElementId componentId) {
        if (showsNoValues()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return simulation.requestMemoryContents(flatId(componentId));
    }

    /**
     * What the Terminal {@code componentId} shows now, for a reader that is not its window (the AI): completed on the
     * simulation thread, empty when the engine has no such Terminal. Never wait for it on the EDT.
     */
    public CompletableFuture<Optional<TerminalReading>> requestTerminal(ElementId componentId, int historyLines,
                                                                        int byteCount) {
        if (showsNoValues()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return simulation.requestTerminalReading(flatId(componentId), historyLines, byteCount);
    }

    /** How many bytes the typing queue of {@code componentId} (a Teclado) holds now; empty without such a queue. */
    public CompletableFuture<OptionalInt> requestTypingQueueSize(ElementId componentId) {
        if (showsNoValues()) {
            return CompletableFuture.completedFuture(OptionalInt.empty());
        }
        return simulation.requestTypingQueueSize(flatId(componentId));
    }

    /**
     * Sets switches or input pins of the live simulation (M6.1-C: a truth-table row tried in the circuit): unlike a
     * click on a switch, not a document change and no undo step; the saved values are untouched.
     */
    public void setInputs(Map<ElementId, Value> values) {
        if (showsNoValues()) {
            return;
        }
        Map<String, Value> byId = new HashMap<>();
        values.forEach((componentId, value) -> byId.put(flatId(componentId), value));
        simulation.setInputs(byId);
        if (body == null) {
            values.forEach(this::expectPublishedIfSwitch);
        }
    }

    /** {@link #expectPublished} when {@code componentId} is a switch of the width of {@code value}. */
    private void expectPublishedIfSwitch(ElementId componentId, Value value) {
        document.component(componentId)
                .filter(component -> component.typeId().equals(StandardComponentTypes.SWITCH))
                .filter(component -> SwitchValues.valueOf(registry.typeOf(component)
                        .resolveParameters(component.parameters())).width() == value.width())
                .ifPresent(component -> expectPublished(componentId, value));
    }

    /**
     * M7: a value typed into a bus input pin. Like a click it is queued to the simulation thread (never applied from
     * here); unlike a click on a switch, not a document change, no undo step, kept by «Reiniciar»; the saved initial
     * value stays.
     */
    public void setInputValue(ElementId componentId, Value value) {
        if (!showsNoValues()) {
            simulation.setInputs(Map.of(flatId(componentId), value));
        }
    }

    /**
     * Push button {@code componentId} is held down: the simulation thread makes it drive 1. Not an edit (no undo step,
     * not modified, the file keeps no value for it) and never applied from here. Every press is let go by
     * {@link #releaseButton}, and by {@link #releaseButtons} when the simulation stops.
     */
    public void pressButton(ElementId componentId) {
        if (!showsNoValues()) {
            main().pressButtonAt(flatId(componentId));
        }
    }

    private void pressButtonAt(String flatId) {
        heldButtons.add(flatId);
        simulation.pressButton(flatId);
    }

    /** Push button {@code componentId} is let go: it drives 0 again. Nothing happens for one that is not held. */
    public void releaseButton(ElementId componentId) {
        if (!showsNoValues()) {
            main().releaseButtonAt(flatId(componentId));
        }
    }

    private void releaseButtonAt(String flatId) {
        if (heldButtons.remove(flatId)) {
            simulation.releaseButton(flatId);
        }
    }

    /**
     * Bytes typed into the part simulated as {@code componentId} (a Teclado with the keyboard captured): queued to the
     * simulation thread like a click, also while paused (as a step). Not an edit (no undo step, not modified).
     * Completes off the EDT with how many bytes the part took; never wait for it on the EDT.
     */
    public CompletableFuture<Integer> typeBytes(String componentId, byte[] bytes) {
        if (showsNoValues()) {
            return CompletableFuture.completedFuture(0);
        }
        return simulation.typeBytes(flatId(componentId), bytes);
    }

    /**
     * A pasted text, one array per character, typed into {@code componentId} in order until the part refuses one (its
     * queue is full); completes with how many characters it took. Like {@link #typeBytes}, not an edit.
     */
    public CompletableFuture<Integer> typeCharacters(String componentId, List<byte[]> characters) {
        if (showsNoValues()) {
            return CompletableFuture.completedFuture(0);
        }
        return simulation.typeCharacters(flatId(componentId), characters);
    }

    /** An edit of the typed text {@code componentId} has not read yet (its arrows, Inicio, Fin, Supr); not an edit. */
    public void editTyping(String componentId, TypingEdit edit) {
        if (!showsNoValues()) {
            simulation.editTyping(flatId(componentId), edit);
        }
    }

    /** Lets go every push button still held (the simulation pauses or restarts), so none stays stuck at 1. */
    public void releaseButtons() {
        OpenDocument main = main();
        List.copyOf(main.heldButtons).forEach(main::releaseButtonAt);
    }

    /** Iniciar: runs the simulation and leaves «Paso a paso» (M6.1-B). */
    public void resume() {
        if (body != null) {
            body.parent.resume();
            return;
        }
        setStepMode(false);
        simulation.resume();
    }

    /** Pausar: a push button held now is let go first, so the paused circuit does not keep it pressed. */
    public void pause() {
        if (body != null) {
            body.parent.pause();
            return;
        }
        releaseButtons();
        simulation.pause();
    }

    public void step(long duration) {
        simulation.step(duration);
    }

    /** The units per real millisecond the simulation achieves lately, 0 while paused (the status bar shows it). */
    public double achievedSpeed() {
        return simulation.achievedUnitsPerMillisecond();
    }

    public void setSpeed(double unitsPerMillisecond) {
        simulation.setSpeed(unitsPerMillisecond);
    }

    /** Session option, off by default and not saved: pause the simulation when a new short circuit appears. */
    public boolean pauseOnShortCircuit() {
        return main().pauseOnShortCircuit;
    }

    public void setPauseOnShortCircuit(boolean enabled) {
        if (body != null) {
            body.parent.setPauseOnShortCircuit(enabled);
            return;
        }
        pauseOnShortCircuit = enabled;
        simulation.setPauseOnShortCircuit(enabled);
    }

    /**
     * The latest published values. A body place shows its document's seen from inside the context instance
     * ({@link SnapshotRemap}), with its own netlist's token while both netlists are current; for «Sin valores» no
     * values and a token that never matches (nothing shows a value).
     */
    public SimulationSnapshot latestSnapshot() {
        SimulationSnapshot latest = simulation.latestSnapshot();
        if (body == null) {
            return latest;
        }
        Optional<String> path = contextPath;
        long token = path.isEmpty() || latest.token() != body.parent.netlistToken()
                ? NO_VALUES_TOKEN : netlistToken();
        Remapped cached = remapped;
        if (cached == null || !cached.isFor(latest, path, token)) {
            cached = new Remapped(latest, path, token,
                    SnapshotRemap.withToken(SnapshotRemap.under(latest, path), token));
            remapped = cached;
        }
        return cached.result();
    }

    /**
     * Why a part last paused this document's simulation (a Terminal received its «Detener al recibir» text); empty
     * before the first and after «Reiniciar». Read it after {@link #latestSnapshot()}: it is written first.
     */
    public Optional<PauseNotice> latestPauseNotice() {
        if (body != null) {
            return body.parent.latestPauseNotice();
        }
        return simulation.latestPauseNotice().map(this::withPartName);
    }

    /**
     * {@code notice} with the name of the part that asked first in its arguments, as the bundle texts of part pauses
     * expect it ({@code {0}}): core does not know labels. The name is the label of the component placed on the circuit
     * (the user component that holds a nested part), else its type's name, else the simulated id.
     */
    PauseNotice withPartName(PauseNotice notice) {
        String name = document.component(new ElementId(InstancePath.topLevel(notice.componentId())))
                .map(component -> component.label().isBlank() ? Messages.componentName(component.typeId(),
                        component.parameters()) : component.label())
                .orElse(notice.componentId());
        List<String> arguments = new ArrayList<>();
        arguments.add(name);
        arguments.addAll(notice.arguments());
        return new PauseNotice(notice.componentId(), notice.messageKey(), arguments, notice.time(), notice.sequence());
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
        if (showsNoValues()) {
            return;
        }
        simulation.setMemoryMapRequest(request.map(asked -> new MemoryMapRequest(flatId(asked.componentId()),
                asked.firstAddress(), asked.wordsPerCell(), asked.cells())));
    }

    /** The latest memory map frame (at most every 16 ms); safe to read from any thread. */
    public Optional<MemoryMapFrame> latestMemoryMap() {
        Optional<MemoryMapFrame> latest = simulation.latestMemoryMap();
        if (body == null) {
            return latest;
        }
        if (showsNoValues()) {
            return Optional.empty();
        }
        String prefix = contextPath.get() + InstancePath.SEPARATOR;
        return latest.filter(frame -> frame.request().componentId().startsWith(prefix)).map(frame -> {
            MemoryMapRequest asked = frame.request();
            return new MemoryMapFrame(new MemoryMapRequest(asked.componentId().substring(prefix.length()),
                    asked.firstAddress(), asked.wordsPerCell(), asked.cells()), frame.reads(), frame.writes(),
                    frame.values(), frame.recent(), frame.time(), frame.wordsPerCounter());
        });
    }

    /**
     * What a reader of the Terminal {@link TerminalRequest#componentId()} (its window, its saved output) wants
     * (spec §9): {@link #latestTerminalFrame(String)} answers this request whenever that screen changes. One request
     * per Terminal: the last one set for it wins, the other Terminals keep theirs.
     */
    public void setTerminalRequest(TerminalRequest request) {
        if (!showsNoValues()) {
            simulation.setTerminalRequest(new TerminalRequest(flatId(request.componentId()), request.historyLines(),
                    request.bytesSince()));
        }
    }

    /** Stops the frames of the Terminal {@code componentId}. */
    public void removeTerminalRequest(String componentId) {
        if (!showsNoValues()) {
            simulation.removeTerminalRequest(flatId(componentId));
        }
    }

    /** The latest frame of the Terminal {@code componentId} (at most every 16 ms); safe from any thread. */
    public Optional<TerminalWindowFrame> latestTerminalFrame(String componentId) {
        if (showsNoValues()) {
            return Optional.empty();
        }
        return simulation.latestTerminalFrame(flatId(componentId));
    }

    /** The reader of {@code componentId} has the bytes up to {@code sequence} of screen {@code owner}. */
    public void acknowledgeTerminalBytes(String componentId, long owner, long sequence) {
        if (!showsNoValues()) {
            simulation.acknowledgeTerminalBytes(flatId(componentId), owner, sequence);
        }
    }

    /**
     * «Limpiar» on the Terminal {@code componentId}: queues a request clearing its screen and history as CLR does.
     * Not a document change, no undo step.
     */
    public void clearTerminal(ElementId componentId) {
        if (!showsNoValues()) {
            simulation.clearTerminal(flatId(componentId));
        }
    }

    /**
     * «Releer la ROM de caracteres» on the Terminal {@code componentId}: queues a request that makes it forget the
     * glyphs it read and read the codes on its screen again. Not a document change, no undo step.
     */
    public void rereadTerminalGlyphs(ElementId componentId) {
        if (!showsNoValues()) {
            simulation.rereadTerminalGlyphs(flatId(componentId));
        }
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
        return main().stepMode;
    }

    /**
     * Turns «Paso a paso» on (which pauses the simulation) or off (which leaves it paused; Iniciar resumes, see
     * {@link #resume()}). The step-mode listeners hear only real changes.
     */
    public void setStepMode(boolean enabled) {
        if (body != null) {
            body.parent.setStepMode(enabled);
            return;
        }
        if (enabled == stepMode) {
            return;
        }
        stepMode = enabled;
        if (enabled) {
            releaseButtons();
            simulation.pause();
        }
        stepModeListeners.fire(Runnable::run);
    }

    public void addStepModeListener(Runnable listener) {
        main().stepModeListeners.add(listener);
    }

    public void removeStepModeListener(Runnable listener) {
        main().stepModeListeners.remove(listener);
    }

    /** For tests: views must remove the step-mode listeners they add when disposed. */
    public int stepModeListenerCount() {
        return main().stepModeListeners.size();
    }

    /** «Avanzar al próximo evento» (M6.1-B): ignored while the simulation runs. */
    public void advanceToNextEvent() {
        simulation.advanceToNextEvent();
    }

    /**
     * «Avanzar al próximo flanco de reloj» ({@link ClockEdgeMode#ANY}: half a cycle) or «Avanzar un ciclo de reloj»
     * ({@link ClockEdgeMode#FALLING}): ignored while the simulation runs.
     */
    public void advanceToNextClockEdge(ClockEdgeMode mode) {
        simulation.advanceToNextClockEdge(mode);
    }

    /** True when the circuit has a clock, also inside a user component; follows every edit (M6.1-B). */
    public boolean hasClock() {
        return main().hasClock;
    }

    /**
     * «Reiniciar» (M6.1-B): the simulation starts again at time 0 — flip-flops at Q = 0, clocks from their start,
     * waveforms cleared — with every switch where it is now. Not a document change (no undo step, not "modified");
     * running or paused and the step mode stay as they were. A push button held now is let go first.
     */
    public void restart() {
        if (body != null) {
            body.parent.restart();
            return;
        }
        releaseButtons();
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
        Set<String> pending = simulation.latestPendingComponents();
        if (body == null) {
            return pending;
        }
        if (showsNoValues()) {
            return Set.of();
        }
        String prefix = contextPath.get() + InstancePath.SEPARATOR;
        Set<String> inside = new LinkedHashSet<>();
        pending.stream().filter(id -> id.startsWith(prefix)).forEach(id -> inside.add(id.substring(prefix.length())));
        return Collections.unmodifiableSet(inside);
    }

    // --- IA.1: simulation requests with an event budget and one receipt each (SimulationThread, A§7) ---

    /**
     * Sets inputs in order, each reported accepted or rejected, within {@code eventBudget} events: the AI testing the
     * circuit, simulation only like a truth-table row tried in it (not a document change, no undo step). EDT only.
     */
    public CompletableFuture<RequestReport> setInputsWithin(List<InputAssignment> inputs, long eventBudget) {
        if (body != null) {
            return body.parent.setInputsWithin(showsNoValues() ? List.of() : inputs.stream()
                    .map(input -> new InputAssignment(flatId(input.componentId()), input.value())).toList(),
                    eventBudget);
        }
        CompletableFuture<RequestReport> report = simulation.setInputsWithin(inputs, eventBudget);
        inputs.forEach(input -> expectPublishedIfSwitch(new ElementId(input.componentId()), input.value()));
        return report;
    }

    /** {@code count} «Avanzar» within {@code eventBudget} events; refused while running. */
    public CompletableFuture<RequestReport> advanceToNextEventWithin(int count, long eventBudget) {
        return simulation.advanceToNextEventWithin(count, eventBudget);
    }

    /** {@code count} edge steps of {@code mode} within {@code eventBudget} events; refused while running. */
    public CompletableFuture<RequestReport> advanceToNextClockEdgeWithin(ClockEdgeMode mode, int count,
                                                                        long eventBudget) {
        return simulation.advanceToNextClockEdgeWithin(mode, count, eventBudget);
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
        if (body != null) {
            return body.parent.restartWithin(eventBudget);
        }
        releaseButtons();
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
        return body == null ? probeNets : Map.of();
    }

    /**
     * The document as format v2 text: what {@link #save} writes and what autosave copies, so the two never differ.
     * EDT only.
     *
     * @throws IllegalStateException when the document cannot be written (an instance whose definition is missing)
     */
    public String serialized() {
        if (body != null) {
            return body.parent.serialized();
        }
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
        if (body != null) {
            body.parent.save(target);
            return;
        }
        Path absolute = target.toAbsolutePath();
        String previousName = document.name();
        if (previousName.isBlank()) {
            // A circuit without a name of its own (a new one) takes its file's on the first save; Guardar como never
            // renames one that has it. Not an edit: the name is part of what this save writes, not an undo step.
            document.setName(fileStem(absolute));
        }
        String content;
        try {
            content = serializedFor(Optional.of(absolute));
        } catch (IllegalStateException exception) {
            document.setName(previousName);
            throw new IOException(exception.getMessage(), exception);
        }
        try {
            if (Files.exists(absolute) && !Files.isWritable(absolute)) {
                // M2 ruling: saving over a read-only file fails (with an error dialog), it does not replace it
                throw new AccessDeniedException(absolute.toString(), null, "the file is read-only");
            }
            AtomicFiles.writeString(absolute, content);
        } catch (IOException exception) {
            // On failure nothing changes, the name taken for this save included.
            document.setName(previousName);
            throw exception;
        }
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
        if (closed) {
            return;
        }
        closed = true;
        if (body == null) {
            simulation.close();
            return;
        }
        body.parent.removeChangeListener(body.parentListener);
        body.parent.releaseWorkingCopy(body.workingCopy);
    }

    /** One body place less on {@code workingCopy}; the last one drops it (a new place starts a fresh copy). */
    private void releaseWorkingCopy(BodyWorkingCopy workingCopy) {
        if (workingCopy.release()) {
            workingCopies.remove(workingCopy.definitionId(), workingCopy);
        }
    }

    /**
     * A document listener: a change of switch values only (a click, its undo or redo;
     * {@link CircuitDocument#changeLiveValues}) keeps the netlist, the flattened circuit and their token and only hands
     * the new values to the running switches, so wires never lose their colour for a snapshot and a click stays cheap
     * on a big circuit (decided 2026-10-28); any other change recompiles.
     */
    private void documentChanged(DocumentChange change) {
        boolean liveValuesOnly = document.structureRevision() == netlistToken;
        if (liveValuesOnly) {
            handOverLiveValues(change);
        } else {
            synchronize();
        }
        followStoredSwitchValues(change, liveValuesOnly);
    }

    /** The changed components' parameters to the simulation, whose switches drive the values they do not yet. */
    private void handOverLiveValues(DocumentChange change) {
        Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();
        for (ElementId id : change.changed()) {
            document.component(id).ifPresent(component -> parameters.put(id.value(), component.parameters()));
        }
        if (!parameters.isEmpty()) {
            simulation.takeLiveValues(parameters);
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
            netlistToken = document.structureRevision();
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
            if (netlistToken != document.structureRevision()) {
                netlist = Netlist.EMPTY;
                netlistToken = document.structureRevision();
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
