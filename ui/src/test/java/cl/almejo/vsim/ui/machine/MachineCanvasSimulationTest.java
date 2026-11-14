package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The machine tab reads the active state from the real simulation (review M5): a machine copy {@code m} inside a
 * user component placed as {@code w}, the tab entered at {@code w/m}, no fake state source.
 */
class MachineCanvasSimulationTest {

    private static final String WRAPPER = "envoltura";

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private OpenDocument place;
    private MachineCanvas canvas;

    @AfterEach
    void close() {
        if (canvas != null) {
            canvas.dispose();
        }
        if (place != null) {
            place.close();
        }
        document.close();
    }

    @Test
    void theTabEnteredInsideAComponentShowsTheSimulatedStateOfThatCopy() {
        ComponentDefinition machine = MachineDefinitions.regenerated(
                MachineFixtures.definition(MachineFixtures.detector()));
        Component copy = new Component(new ElementId("m"), SubcircuitTypes.typeIdOf(machine.id()), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "");
        ComponentDefinition wrapper = new ComponentDefinition(WRAPPER, "Envoltura", "", new GridSize(6, 4), List.of(),
                new CircuitFragment(List.of(copy), List.of(), List.of()), Optional.empty(), 1);
        document.execute(new PutDefinitionsCommand(List.of(machine, wrapper)));
        document.execute(new AddElementsCommand(List.of(new Component(new ElementId("w"),
                SubcircuitTypes.typeIdOf(WRAPPER), new GridPoint(10, 10), Rotation.R0, Map.of(), ""))));
        place = document.openBody(machine.id(), Optional.of("w/m"));
        canvas = new MachineCanvas(place, machine.id(), new MachineSelection(), () -> ColorScheme.LIGHT);

        UiTestSupport.awaitSimulation(document);
        canvas.showLatestState();

        assertThat(place.contextPath()).contains("w/m");
        assertThat(canvas.activeState()).hasValueSatisfying(state -> assertThat(state.stateId()).isEqualTo("s0"));
    }
}
