package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OutdatedPieceChecksTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentLibrary library = new ComponentLibrary();
    private final ComponentTypeRegistry baseRegistry = StandardComponentTypes.registry();

    OutdatedPieceChecksTest() {
        document.putDefinition(ExampleDefinitions.inverter());
        document.putDefinition(ExampleDefinitions.doubleInverter());
        ComponentTypeRegistry registry = baseRegistry.extendedWith(SubcircuitTypes.resolverFor(document));
        document.add(registry.require(SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER))
                .newComponent(new ElementId("u3"), new GridPoint(10, 10), Rotation.R0, Map.of(), "U3"));
    }

    @Test
    void aPieceWhoseOlderPartIsUsedInsideAnotherBodyIsReportedAsNotUpdatable() {
        library.put(new DefinitionBundle(movedOutput(ExampleDefinitions.inverter()), List.of()));

        List<ConnectivityProblem> problems = OutdatedPieceChecks.problems(document, library, baseRegistry);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.cause()).isEqualTo(ConnectivityProblem.Cause.OUTDATED_CANNOT_UPDATE);
            assertThat(problem.elementId()).isEqualTo("u3");
            assertThat(problem.netId()).isEqualTo("version:" + ExampleDefinitions.INVERTER);
        });
    }

    @Test
    void aNewerLibraryVersionThatKeepsTheConnectionsIsNotReported() {
        library.put(new DefinitionBundle(ExampleDefinitions.inverter().withName("Inversor NOT"), List.of()));

        assertThat(OutdatedPieceChecks.problems(document, library, baseRegistry)).isEmpty();
    }

    @Test
    void aLibraryVersionThatIsNotNewerIsNotReported() {
        ComponentDefinition older = movedOutput(ExampleDefinitions.inverter());
        library.put(new DefinitionBundle(older, List.of()));
        document.putDefinition(new ComponentDefinition(older.id(), older.name(), older.description(), older.size(),
                older.pinout(), older.body(), Optional.empty(), older.revision() + 1));

        assertThat(OutdatedPieceChecks.problems(document, library, baseRegistry)).isEmpty();
    }

    @Test
    void checkingNeverChangesTheDocument() {
        library.put(new DefinitionBundle(movedOutput(ExampleDefinitions.inverter()), List.of()));
        long revision = document.revision();

        OutdatedPieceChecks.problems(document, library, baseRegistry);

        assertThat(document.revision()).isEqualTo(revision);
        assertThat(document.definition(ExampleDefinitions.INVERTER)).contains(ExampleDefinitions.inverter());
    }

    /** The output terminal one unit lower on the box's right edge: the wires inside the parent no longer reach it. */
    private static ComponentDefinition movedOutput(ComponentDefinition definition) {
        DefinitionPin output = definition.pinout().get(1);
        DefinitionPin moved = new DefinitionPin(PinSpec.output(output.spec().name(), 4, 3), output.source());
        return definition.withGeometry(definition.size(), List.of(definition.pinout().get(0), moved),
                definition.appearance());
    }
}
