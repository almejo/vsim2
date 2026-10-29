package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The circuit «Exportar a PDF…» paints, frozen on the EDT when the dialog opens (spec §3.2): a private copy of the
 * document (elements in their paint order, component definitions, probes), a registry that resolves user components
 * against that copy, the netlist and simulation values only when they belong to the copied revision, the colour
 * scheme, the text font, the names and the date. The preview and the PDF paint only from it, so editing the circuit
 * in another view, changing the scheme or the font, or closing the circuit changes nothing until «Actualizar» takes
 * a new capture. Capturing creates no command, leaves «modificado», the selection and the simulation alone. The
 * dialog is modal, so today only the simulation moves while it is open (what «Actualizar» picks up); the copy still
 * keeps the export thread away from the live document, registry and font.
 *
 * <p>Nothing mutates the copy after the capture, so it may be read from the export thread too (the fields are final
 * and the registry's resolver only reads the copy's definitions). Never change {@link #document()}.
 */
public final class PrintInput {

    /** The live pieces a capture copies; package-private so tests can hand in a snapshot of another revision. */
    record LiveCircuit(CircuitDocument document, ComponentTypeRegistry registry, Netlist netlist, long netlistToken,
                       SimulationSnapshot snapshot, String circuitName, Optional<Path> path) {
    }

    private static final String VALUES_STALE = "print.values.stale";
    private static final String UNTITLED_FRAME = "print.frame.untitled";
    private static final String REPEATED_FRAME = "print.frame.repeated";

    private final CircuitDocument document;
    private final ComponentTypeRegistry registry;
    private final Netlist netlist;
    private final long netlistToken;
    private final SimulationSnapshot snapshot;
    private final Optional<String> valuesProblem;
    private final Set<ElementId> selection;
    private final ColorScheme scheme;
    private final TextFont textFont;
    private final String circuitName;
    private final Optional<String> fileName;
    private final LocalDate date;
    private final List<FrameEntry> frames;

    private PrintInput(LiveCircuit live, CircuitDocument copy, Set<ElementId> viewSelection, ColorScheme scheme,
                       TextFont textFont, Clock clock) {
        this.document = copy;
        this.registry = registryFor(live.registry(), copy);
        this.netlist = live.netlist();
        // The copy has its own revision, and the renderer trusts the netlist and the values only while their tokens
        // match the document's: carry over exactly which of the two matched the original (like CircuitImage).
        boolean netlistMatches = live.netlistToken() == live.document().structureRevision();
        boolean valuesMatch = netlistMatches && live.snapshot().token() == live.netlistToken();
        this.netlistToken = netlistMatches ? copy.structureRevision() : copy.structureRevision() + 1;
        this.snapshot = withToken(live.snapshot(), valuesMatch ? netlistToken : netlistToken + 1);
        this.valuesProblem = valuesMatch ? Optional.empty() : Optional.of(Messages.text(VALUES_STALE));
        this.selection = selectionIn(copy, viewSelection);
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.textFont = Objects.requireNonNull(textFont, "textFont");
        this.circuitName = live.circuitName();
        this.fileName = live.path().map(Path::getFileName).map(Path::toString);
        this.date = LocalDate.now(clock);
        this.frames = frameEntries(copy, registry);
    }

    /**
     * Freezes {@code openDocument} as it is now, with the selection of the view that opened the dialog (ids no
     * longer in the document are dropped), the scheme and font in use and today's date from {@code clock}. EDT only.
     */
    public static PrintInput capture(OpenDocument openDocument, Set<ElementId> viewSelection, ColorScheme scheme,
                                     TextFont textFont, Clock clock) {
        return capture(new LiveCircuit(openDocument.document(), openDocument.registry(), openDocument.netlist(),
                openDocument.netlistToken(), openDocument.latestSnapshot(), openDocument.displayName(),
                openDocument.path()), viewSelection, scheme, textFont, clock);
    }

    static PrintInput capture(LiveCircuit live, Set<ElementId> viewSelection, ColorScheme scheme, TextFont textFont,
                              Clock clock) {
        return new PrintInput(live, copyOf(live.document()), viewSelection, scheme, textFont, clock);
    }

    /** Same ids, paint order, definitions, probes and name as {@code original}; it shares only immutable values. */
    private static CircuitDocument copyOf(CircuitDocument original) {
        CircuitDocument copy = new CircuitDocument();
        copy.setName(original.name());
        original.definitions().forEach(copy::putDefinition);
        for (DocumentElement element : original.elements()) {
            copy.restore(element, original.orderOf(element.id()));
        }
        copy.setProbes(original.probes());
        return copy;
    }

    /** The registered types of {@code live}, with user components resolved through the copy's own definitions. */
    private static ComponentTypeRegistry registryFor(ComponentTypeRegistry live, CircuitDocument copy) {
        ComponentTypeRegistry registry = live.copy();
        registry.addResolver(SubcircuitTypes.resolverFor(copy));
        return registry;
    }

    private static SimulationSnapshot withToken(SimulationSnapshot snapshot, long token) {
        return new SimulationSnapshot(snapshot.revision(), token, snapshot.time(), snapshot.running(),
                snapshot.netValues(), snapshot.componentStates(), snapshot.problems(), snapshot.failure());
    }

    private static Set<ElementId> selectionIn(CircuitDocument copy, Set<ElementId> viewSelection) {
        Set<ElementId> present = new LinkedHashSet<>();
        for (ElementId id : viewSelection) {
            if (copy.element(id).isPresent()) {
                present.add(id);
            }
        }
        return Collections.unmodifiableSet(present);
    }

    /** Frames depth-first: each one followed by the frames nested in it, siblings in document order. */
    private static List<FrameEntry> frameEntries(CircuitDocument copy, ComponentTypeRegistry registry) {
        Map<ElementId, ElementId> parents = FrameContents.parents(copy, registry);
        Map<ElementId, List<Frame>> children = new HashMap<>();
        List<Frame> roots = new ArrayList<>();
        for (Frame frame : copy.frames()) {
            ElementId parent = parents.get(frame.id());
            if (parent == null) {
                roots.add(frame);
            } else {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(frame);
            }
        }
        List<Frame> ordered = new ArrayList<>();
        Map<ElementId, Integer> depths = new HashMap<>();
        for (Frame root : roots) {
            visit(root, 0, children, ordered, depths);
        }
        return entries(ordered, depths);
    }

    private static void visit(Frame frame, int depth, Map<ElementId, List<Frame>> children, List<Frame> ordered,
                              Map<ElementId, Integer> depths) {
        ordered.add(frame);
        depths.put(frame.id(), depth);
        for (Frame child : children.getOrDefault(frame.id(), List.of())) {
            visit(child, depth + 1, children, ordered, depths);
        }
    }

    /** «Marco sin título» for blank titles; every title that appears more than once gets its number in the list. */
    private static List<FrameEntry> entries(List<Frame> ordered, Map<ElementId, Integer> depths) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Frame frame : ordered) {
            totals.merge(baseTitle(frame), 1, Integer::sum);
        }
        Map<String, Integer> seen = new HashMap<>();
        List<FrameEntry> entries = new ArrayList<>();
        for (Frame frame : ordered) {
            String title = baseTitle(frame);
            int number = seen.merge(title, 1, Integer::sum);
            String displayTitle = totals.get(title) > 1 ? Messages.format(REPEATED_FRAME, title, number) : title;
            entries.add(new FrameEntry(frame.id(), displayTitle, depths.get(frame.id())));
        }
        return List.copyOf(entries);
    }

    private static String baseTitle(Frame frame) {
        String title = frame.title().strip();
        return title.isEmpty() ? Messages.text(UNTITLED_FRAME) : title;
    }

    /** The private copy; read it, never change it. */
    public CircuitDocument document() {
        return document;
    }

    /** Resolves the copy's user components; thread-safe because the copy never changes. */
    public ComponentTypeRegistry registry() {
        return registry;
    }

    /** The live netlist of the capture's moment (immutable); trusted only while {@link #netlistToken()} matches. */
    public Netlist netlist() {
        return netlist;
    }

    /** {@code document().structureRevision()} when the netlist belongs to the copied circuit, something else otherwise. */
    public long netlistToken() {
        return netlistToken;
    }

    /** The values; its token equals {@link #netlistToken()} only when {@link #hasValues()}. */
    public SimulationSnapshot snapshot() {
        return snapshot;
    }

    /** True when the simulation values belong to the copied revision, so «Con valores» can show them. */
    public boolean hasValues() {
        return valuesProblem.isEmpty();
    }

    /** Why there are no values («sin valores»), as the dialog shows it; empty when {@link #hasValues()}. */
    public Optional<String> valuesProblem() {
        return valuesProblem;
    }

    /** The selection of the view that opened the dialog, restricted to the copy, in its order; may be empty. */
    public Set<ElementId> selection() {
        return selection;
    }

    public ColorScheme scheme() {
        return scheme;
    }

    public TextFont textFont() {
        return textFont;
    }

    /** The name the window shows (the file name without extension, or the document's name, or «Sin título»). */
    public String circuitName() {
        return circuitName;
    }

    /** The circuit file's name with its extension; empty for a circuit never saved. */
    public Optional<String> fileName() {
        return fileName;
    }

    public LocalDate date() {
        return date;
    }

    /** The «Marco» list: every frame depth-first with a distinct display title. */
    public List<FrameEntry> frames() {
        return frames;
    }
}
