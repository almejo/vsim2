package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.problems.ProblemText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutdatedPiecesProblemsTest {

    @TempDir
    Path directory;

    private OpenDocument openDocument;
    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        openDocument.document().putDefinition(ExampleDefinitions.inverter());
        openDocument.document().putDefinition(ExampleDefinitions.doubleInverter());
        Component piece = openDocument.registry().require(
                SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER)).newComponent(new ElementId("u3"),
                new GridPoint(10, 10), Rotation.R0, Map.of(), "U3");
        openDocument.execute(new AddElementsCommand(List.of(piece)));
        fixture = new TemplateFixture(directory.resolve("library"), openDocument);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    void aLibraryVersionThatWouldLoosenAPiecesInnerConnectionsAddsAProblemsLine() throws Exception {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        DefinitionPin output = inverter.pinout().get(1);
        fixture.library.put(new DefinitionBundle(inverter.withGeometry(inverter.size(),
                List.of(inverter.pinout().get(0), new DefinitionPin(PinSpec.output("y", 4, 3), output.source())),
                inverter.appearance()), List.of()));
        flushEventQueue();

        assertThat(outdatedProblems()).singleElement().satisfies(problem ->
                assertThat(ProblemText.describe(problem, openDocument.document(), openDocument.registry()))
                        .isEqualTo("U3 usa una versión anterior de Inversor; actualizarla dejaría conexiones"
                                + " internas sin conectar."));
    }

    @Test
    void aLibraryVersionThatKeepsTheConnectionsAddsNothingAndTheLineGoesWhenTheLibraryEntryGoes() throws Exception {
        fixture.library.put(new DefinitionBundle(ExampleDefinitions.inverter().withName("Inversor NOT"),
                List.of()));
        flushEventQueue();
        assertThat(outdatedProblems()).isEmpty();

        ComponentDefinition inverter = ExampleDefinitions.inverter();
        DefinitionPin output = inverter.pinout().get(1);
        fixture.library.put(new DefinitionBundle(inverter.withGeometry(inverter.size(),
                List.of(inverter.pinout().get(0), new DefinitionPin(PinSpec.output("y", 4, 3), output.source())),
                inverter.appearance()), List.of()));
        flushEventQueue();
        assertThat(outdatedProblems()).hasSize(1);

        fixture.library.remove(ExampleDefinitions.INVERTER);
        flushEventQueue();
        assertThat(outdatedProblems()).isEmpty();
    }

    private List<ConnectivityProblem> outdatedProblems() {
        return fixture.view.problems().navigator().problems().stream()
                .filter(ConnectivityProblem.class::isInstance).map(ConnectivityProblem.class::cast)
                .filter(problem -> problem.cause() == ConnectivityProblem.Cause.OUTDATED_WOULD_BREAK)
                .toList();
    }

    /** Library listeners may fire off the EDT; the check hops onto it. */
    private static void flushEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
