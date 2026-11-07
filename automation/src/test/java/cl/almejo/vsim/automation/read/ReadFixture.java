package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.simulation.ListedProblems;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A circuit as the editor would hand it to the readers, with the standard types and its own user components. */
final class ReadFixture {

    static final DocumentId ID = new DocumentId("circuit-1");
    static final ComponentNames NAMES = new ComponentNames() {
        @Override
        public String spanish(ComponentType type) {
            return "es " + type.id();
        }

        @Override
        public String english(ComponentType type) {
            return "en " + type.id();
        }

        @Override
        public String description(ComponentType type, Map<String, Object> parameters) {
            return "reglas de " + type.id() + (Boolean.TRUE.equals(parameters.get("negate")) ? " negada" : "");
        }
    };

    final CircuitDocument document = new CircuitDocument();
    final ComponentTypeRegistry registry =
            StandardComponentTypes.registry().extendedWith(SubcircuitTypes.resolverFor(document));
    private SimulationSnapshot snapshot = SimulationSnapshot.EMPTY;
    private long version = 7;
    private Optional<Path> circuitFile = Optional.empty();
    private List<DefinitionBundle> library = List.of();
    /** What the editor would list besides the simulation; empty: a source of no editor (its default). */
    private Optional<ListedProblems.Sources> problemSources = Optional.empty();
    private List<LibraryProblem> libraryProblems = List.of();

    Component place(String typeId, int x, int y) {
        return place(typeId, x, y, Rotation.R0, Map.of(), "");
    }

    Component place(String typeId, int x, int y, Rotation rotation, Map<String, Object> parameters, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                rotation, parameters, label);
        document.add(component);
        return component;
    }

    Wire wire(GridPoint start, GridPoint end) {
        Wire wire = new Wire(document.newWireId(), start, end);
        document.add(wire);
        return wire;
    }

    GridPoint pin(Component component, int index) {
        return registry.typeOf(component).pinLocation(component, index);
    }

    void useSnapshot(SimulationSnapshot newSnapshot) {
        snapshot = newSnapshot;
    }

    void useLibrary(List<DefinitionBundle> newLibrary) {
        library = List.copyOf(newLibrary);
    }

    void useCircuitFile(Path file) {
        circuitFile = Optional.of(file);
    }

    void useVersion(long newVersion) {
        version = newVersion;
    }

    /** The source as the editor gives it: these rows besides the simulation's, and the library's load problems. */
    void useEditorProblems(ListedProblems.Sources sources, List<LibraryProblem> loadProblems) {
        problemSources = Optional.of(sources);
        libraryProblems = List.copyOf(loadProblems);
    }

    static ElementId id(String value) {
        return new ElementId(value);
    }

    ReadSource source() {
        if (problemSources.isPresent()) {
            return new ReadSource(ID, new DocumentVersion(version), "Prueba", document, registry,
                    new NetlistCompiler(registry).compile(document), document.revision(), snapshot, NAMES, library,
                    TextMeasure.APPROXIMATE, circuitFile, memory -> Optional.empty(), problemSources.get(),
                    libraryProblems);
        }
        return new ReadSource(ID, new DocumentVersion(version), "Prueba", document, registry,
                new NetlistCompiler(registry).compile(document), document.revision(), snapshot, NAMES, library,
                TextMeasure.APPROXIMATE, circuitFile);
    }
}
