package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Exports a VSim document as a Logisim 2.7 project (beta; spec decision 5, «si podemos leer, debemos poder escribir»):
 * the document is the main circuit and every custom component it uses (nested ones too, those first) is one more
 * circuit, drawn with the component's own look and pins ({@link ExportAppearance}). Each circuit is laid out by
 * {@link ExportLayout}; parts without a Logisim equivalent are left out and everything Logisim cannot express is
 * noted, like the import's notes. {@link LogisimWriter} then writes the project.
 */
public final class LogisimExporter {

    /** The main circuit's name when the document has none. */
    private static final String UNNAMED_MAIN = "main";

    private LogisimExporter() {
    }

    /** {@code document} as a Logisim project, with {@code registry}'s standard parts. */
    public static ExportResult exportDocument(CircuitDocument document, ComponentTypeRegistry registry) {
        return exportDocument(document, registry, exported -> {
        });
    }

    /** The same, telling {@code observer} about each circuit as it is done (for the tests' net checks). */
    static ExportResult exportDocument(CircuitDocument document, ComponentTypeRegistry registry,
                                       Consumer<ExportedCircuit> observer) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(observer, "observer");
        return new Run(document, registry, observer).export();
    }

    /** The export of one document. */
    private static final class Run {
        private final CircuitDocument document;
        private final ComponentTypeRegistry registry;
        private final Consumer<ExportedCircuit> observer;
        private final List<LogisimCircuit> definitions = new ArrayList<>();
        private final Map<String, SubcircuitExport> exports = new HashMap<>();
        private final Set<String> names = new HashSet<>();
        private final List<ImportNote> notes = new ArrayList<>();
        private final HoldingInstances holders;

        Run(CircuitDocument document, ComponentTypeRegistry registry, Consumer<ExportedCircuit> observer) {
            this.document = document;
            this.holders = new HoldingInstances(document);
            this.registry = registry;
            this.observer = observer;
        }

        ExportResult export() {
            for (ComponentDefinition definition : document.usedDefinitions()) {
                exportDefinition(definition);
            }
            String mainName = uniqueName(document.name().isBlank() ? UNNAMED_MAIN : document.name());
            ComponentTypeRegistry mainRegistry = registry.extendedWith(SubcircuitTypes.resolverFor(document));
            ExportLayout.Result main = layOut(mainName, true, document, mainRegistry, Map.of());
            notes.addAll(main.notes());
            observer.accept(new ExportedCircuit(document, mainRegistry, main.circuit(), main.ends()));
            int annotations = document.stickyNotes().size() + document.frames().size();
            if (annotations > 0) {
                notes.add(ImportNote.about(ImportNote.Kind.APPROXIMATED, mainName, LogisimNotes.EXPORT_ANNOTATIONS,
                        String.valueOf(annotations)));
            }
            List<LogisimCircuit> circuits = new ArrayList<>();
            circuits.add(main.circuit());
            circuits.addAll(definitions);
            return new ExportResult(new LogisimProject(mainName, Map.of(), circuits, List.of()), notes);
        }

        /** A custom component as a circuit: its body laid out, its interface pins named after the box's pins. */
        private void exportDefinition(ComponentDefinition definition) {
            String name = uniqueName(definition.name());
            CircuitDocument body = definition.bodyDocument(document);
            ComponentTypeRegistry bodyRegistry = registry.extendedWith(SubcircuitTypes.resolverFor(body));
            Map<ElementId, String> pinLabels = new HashMap<>();
            definition.pinout().forEach(pin -> pinLabels.put(pin.source(), pin.spec().name()));
            ExportLayout.Result laidOut = layOut(name, false, body, bodyRegistry, pinLabels);
            Optional<ElementId> holder = holders.firstHolderOf(definition.id());
            laidOut.notes().forEach(note -> notes.add(note.withElement(holder)));
            Map<Integer, LogisimPoint> innerPins = new HashMap<>();
            List<DefinitionPin> pinout = definition.pinout();
            for (int index = 0; index < pinout.size(); index++) {
                LogisimPoint inner = laidOut.pinLocations().get(pinout.get(index).source());
                if (inner != null) {
                    innerPins.put(index, inner);
                }
            }
            LogisimCircuit circuit = new LogisimCircuit(name, laidOut.circuit().components(),
                    laidOut.circuit().wires(), ExportAppearance.of(definition, innerPins));
            definitions.add(circuit);
            exports.put(definition.id(), new SubcircuitExport(name, innerPins));
            observer.accept(new ExportedCircuit(body, bodyRegistry, circuit, laidOut.ends()));
        }

        private ExportLayout.Result layOut(String name, boolean main, CircuitDocument circuit,
                                           ComponentTypeRegistry circuitRegistry, Map<ElementId, String> pinLabels) {
            LogisimProject soFar = new LogisimProject(name, Map.of(), definitions, List.of());
            ExportLayout.Result result = ExportLayout.layOut(new ExportLayout.Request(name, main, circuit,
                    circuitRegistry, soFar, definitionId -> Optional.ofNullable(exports.get(definitionId)),
                    pinLabels));
            return result;
        }

        /** Logisim tells circuits apart by name: a repeated one gets a number. */
        private String uniqueName(String wanted) {
            String name = wanted.strip();
            for (int number = 2; !names.add(name); number++) {
                name = wanted.strip() + " " + number;
            }
            return name;
        }
    }
}
