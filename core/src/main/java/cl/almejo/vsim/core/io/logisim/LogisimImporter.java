package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionIds;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A whole Logisim project as one VSim document (spec §3): every circuit but the main one becomes a custom component
 * definition embedded in the document, made leaves first ({@link LogisimCircuitOrder}) so each body can hold the
 * definitions of the circuits it uses; its body is the circuit laid out ({@link LogisimNets}, {@link LogisimLayout}),
 * its look the circuit's drawing ({@link LogisimAppearanceConverter}), its pins the circuit's Logisim {@code Pin}s
 * (named uniquely, and the body's interface pins labelled the same). The main circuit, laid out the same way, is the
 * document itself, named after it; memory contents travel in the parts' parameters. A partial import never fails:
 * whatever could not be taken is a note.
 */
public final class LogisimImporter {

    private LogisimImporter() {
    }

    /** Reads a Logisim file and imports it. */
    public static ImportResult importFrom(InputStream input, ComponentTypeRegistry registry)
            throws CircuitFormatException {
        return importProject(new LogisimReader().read(input), registry);
    }

    /**
     * {@code project} as one document: the main circuit with the definitions of the others; {@code registry} holds
     * VSim's parts (it is not changed: the definitions resolve through a registry extended with them). The notes
     * come sorted by circuit (the project's own first, then the circuits in file order), then by kind.
     */
    public static ImportResult importProject(LogisimProject project, ComponentTypeRegistry registry) {
        return importProject(project, registry, (analysis, laidOut, layoutRegistry) -> {
        });
    }

    /** Told each circuit as it is laid out (for the tests: Logisim's nets against VSim's, the problems' origin). */
    interface LayoutObserver {
        /** {@code laidOut} is {@code analysis}'s circuit; {@code registry} resolves its parts and instances. */
        void laidOut(LogisimNetAnalysis analysis, LaidOutCircuit laidOut, ComponentTypeRegistry registry);
    }

    /** Like {@link #importProject(LogisimProject, ComponentTypeRegistry)}, telling {@code observer} each circuit. */
    static ImportResult importProject(LogisimProject project, ComponentTypeRegistry registry,
                                      LayoutObserver observer) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(observer, "observer");
        return new Run(project, registry, observer).run();
    }

    /** One import. */
    private static final class Run {
        private final LogisimProject project;
        /** Holds the definitions made so far, so the layout's registry resolves their instances. */
        private final CircuitDocument definitions = new CircuitDocument();
        private final ComponentTypeRegistry registry;
        private final LogisimLayout layout;
        private final Map<String, SubcircuitPart> parts = new HashMap<>();
        private final Set<String> takenIds = new HashSet<>();
        private final List<ImportNote> notes = new ArrayList<>();
        private final LayoutObserver observer;
        /** Each defined circuit's definition id, by the circuit's name. */
        private final Map<String, String> definitionIds = new HashMap<>();
        /** The main circuit's placed parts by their Logisim location. */
        private Map<LogisimPoint, ElementId> mainParts = Map.of();

        Run(LogisimProject project, ComponentTypeRegistry baseRegistry, LayoutObserver observer) {
            this.project = project;
            this.observer = observer;
            this.registry = baseRegistry.extendedWith(SubcircuitTypes.resolverFor(definitions));
            this.layout = new LogisimLayout(registry);
        }

        ImportResult run() {
            notes.addAll(project.notes());
            LogisimCircuitOrder order = LogisimCircuitOrder.of(project);
            notes.addAll(order.notes());
            Set<String> used = usedCircuits();
            CircuitDocument document = new CircuitDocument();
            for (LogisimCircuit circuit : order.circuits()) {
                boolean main = circuit.name().equals(project.mainCircuit());
                LogisimNetAnalysis analysis = LogisimNets.analyze(circuit, project);
                notes.addAll(analysis.notes());
                noteMissingCircuits(circuit);
                LaidOutCircuit laidOut = layout.layOut(analysis,
                        component -> analysis.mappingContext(component, registry),
                        name -> Optional.ofNullable(parts.get(name)));
                notes.addAll(laidOut.notes());
                notes.addAll(LogisimWiringDifferences.notesFor(analysis, laidOut));
                observer.laidOut(analysis, laidOut, registry);
                if (!main || used.contains(circuit.name())) {
                    define(circuit, laidOut);
                }
                if (main) {
                    document = laidOut.document();
                    mainParts = placedByPoint(circuit, laidOut);
                }
            }
            document.setName(project.mainCircuit());
            for (ComponentDefinition definition : definitions.definitions()) {
                document.putDefinition(definition);
            }
            HoldingInstances holders = new HoldingInstances(document);
            List<ImportNote> located = notes.stream().map(note -> note.withElement(elementOf(note, holders)))
                    .toList();
            return new ImportResult(document, sorted(located));
        }

        /** The placed part at each Logisim location of {@code circuit}; the first in file order when several are. */
        private static Map<LogisimPoint, ElementId> placedByPoint(LogisimCircuit circuit, LaidOutCircuit laidOut) {
            Map<LogisimPoint, ElementId> placed = new HashMap<>();
            for (LogisimComponent component : circuit.components()) {
                ElementId id = laidOut.parts().get(component);
                if (id != null) {
                    placed.putIfAbsent(component.location(), id);
                }
            }
            return placed;
        }

        /**
         * Where {@code note} takes the user ({@link ImportNote#element}): in the main circuit, the element the layout
         * gave it (a text) or else the part at its location; in another circuit, the main circuit's first instance
         * that holds that circuit's component.
         */
        private Optional<ElementId> elementOf(ImportNote note, HoldingInstances holders) {
            if (note.circuit().equals(project.mainCircuit())) {
                return note.element().or(() -> note.where().map(mainParts::get));
            }
            return Optional.ofNullable(definitionIds.get(note.circuit())).flatMap(holders::firstHolderOf);
        }

        /** The names of the circuits some circuit has instances of. */
        private Set<String> usedCircuits() {
            Set<String> used = new HashSet<>();
            for (LogisimCircuit circuit : project.circuits()) {
                circuit.components().stream().filter(LogisimComponent::isSubcircuit)
                        .forEach(component -> used.add(component.name()));
            }
            return used;
        }

        /** An instance of a circuit with no definition (not in the file, or using itself) is left out: noted once. */
        private void noteMissingCircuits(LogisimCircuit circuit) {
            Set<String> noted = new HashSet<>();
            for (LogisimComponent component : circuit.components()) {
                if (component.isSubcircuit() && !parts.containsKey(component.name())
                        && noted.add(component.name())) {
                    notes.add(ImportNote.at(ImportNote.Kind.INVALID, circuit.name(), component.location(),
                            LogisimNotes.MISSING_CIRCUIT, component.name()));
                }
            }
        }

        /**
         * The definition of {@code circuit}: its laid-out body with the interface pins labelled as the component's
         * pins, its converted drawing; then the part its instances are.
         */
        private void define(LogisimCircuit circuit, LaidOutCircuit laidOut) {
            ConvertedAppearance converted = LogisimAppearanceConverter.convert(circuit);
            notes.addAll(converted.notes());
            CircuitDocument body = laidOut.document();
            Map<LogisimPoint, ElementId> interfacePins = interfacePins(circuit, laidOut);
            List<DefinitionPin> pinout = new ArrayList<>();
            Map<LogisimPoint, Integer> pinOfInnerPin = new LinkedHashMap<>();
            for (ConvertedAppearance.ConvertedPin pin : converted.pins()) {
                ElementId source = interfacePins.get(pin.source().location());
                Optional<Component> sourcePart = source == null ? Optional.empty() : body.component(source);
                if (sourcePart.isEmpty() || !InterfacePins.isInterface(sourcePart.get().typeId())) {
                    continue;
                }
                body.replace(sourcePart.get().withLabel(pin.spec().name()));
                pinOfInnerPin.put(pin.source().location(), pinout.size());
                pinout.add(new DefinitionPin(pin.spec(), source));
            }
            String id = DefinitionIds.slug(circuit.name(), takenIds);
            takenIds.add(id);
            definitionIds.put(circuit.name(), id);
            ComponentDefinition definition = new ComponentDefinition(id, circuit.name(), "", converted.size(), pinout,
                    new CircuitFragment(body.components(), body.wires(), body.labels()),
                    Optional.of(converted.drawing()), 1);
            definitions.putDefinition(definition);
            parts.put(circuit.name(), new SubcircuitPart(SubcircuitTypes.typeIdOf(id), Map.of(), pinOfInnerPin,
                    converted.facing(), Optional.of(converted.anchor())));
        }

        /** The VSim part each of the circuit's Logisim {@code Pin}s became, by the pin's location. */
        private static Map<LogisimPoint, ElementId> interfacePins(LogisimCircuit circuit, LaidOutCircuit laidOut) {
            Map<LogisimPoint, ElementId> pins = new HashMap<>();
            for (LogisimComponent component : circuit.components()) {
                ElementId placed = laidOut.parts().get(component);
                if (placed != null && component.library().equals(LogisimLibraries.WIRING)
                        && component.name().equals("Pin")) {
                    pins.putIfAbsent(component.location(), placed);
                }
            }
            return pins;
        }

        /** The project's notes first, then each circuit's in file order; within one, by kind, else as they came. */
        private List<ImportNote> sorted(List<ImportNote> unsorted) {
            Map<String, Integer> circuitOrder = new HashMap<>();
            circuitOrder.put("", -1);
            for (LogisimCircuit circuit : project.circuits()) {
                circuitOrder.putIfAbsent(circuit.name(), circuitOrder.size());
            }
            return unsorted.stream()
                    .sorted(Comparator.comparingInt((ImportNote note) ->
                                    circuitOrder.getOrDefault(note.circuit(), Integer.MAX_VALUE))
                            .thenComparing(ImportNote::kind))
                    .toList();
        }
    }
}
