package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A machine's body place refuses every circuit edit by itself, without the machine tab (review C2): the AI opens such
 * places for each call addressed to the machine.
 */
class MachineBodyPlaceTest {

    private final OpenDocument document = MachineFixtures.circuitWithGeneratedMachines(1);
    private final OpenDocument place = document.openBody(MachineFixtures.DEFINITION_ID, Optional.empty());

    @AfterEach
    void close() {
        place.close();
        document.close();
    }

    @Test
    void aBarePlaceRefusesACircuitEditAndNothingChanges() {
        ComponentDefinition before = machine();
        int partsBefore = place.document().components().size();
        boolean couldUndoBefore = document.history().canUndo();
        Component lamp = StandardComponentTypes.registry().require(StandardComponentTypes.LED)
                .newComponent(new ElementId("c99"), new GridPoint(40, 40), Rotation.R0, Map.of(), "");

        assertThatThrownBy(() -> place.execute(new AddElementsCommand(List.of(lamp))))
                .isInstanceOfSatisfying(InsideEditRefusedException.class,
                        refusal -> assertThat(refusal.messageKey()).isEqualTo("machine.refused.circuitEdit"));

        assertThat(machine()).isSameAs(before);
        assertThat(place.document().components()).hasSize(partsBefore);
        assertThat(document.history().canUndo()).isEqualTo(couldUndoBefore);
    }

    @Test
    void aBarePlaceLetsTheMachinesOwnEditThroughAsOneUndoStep() {
        StateMachine looped = machine().machine().orElseThrow().withTransitions(List.of(
                new MachineTransition("t9", "s0", "s0", "y", List.of())));

        place.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID, looped));

        assertThat(machine().machine().orElseThrow().inputNames()).contains("y");
        document.history().undo();
        assertThat(machine().machine().orElseThrow().inputNames()).doesNotContain("y");
    }

    private ComponentDefinition machine() {
        return document.document().definition(MachineFixtures.DEFINITION_ID).orElseThrow();
    }
}
