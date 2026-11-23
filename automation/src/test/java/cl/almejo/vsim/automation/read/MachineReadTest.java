package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** A state machine piece is read as a state machine (name, pins, kind, states, current state), never as an unknown. */
class MachineReadTest {

    private final ReadFixture fixture = new ReadFixture();
    private Component piece;

    @BeforeEach
    void placeAMachine() {
        MachineState red = new MachineState("red", "Rojo", DiagramPoint.ORIGIN,
                List.of(new OutputAssignment("stop", true)));
        MachineState green = new MachineState("green", "Verde", new DiagramPoint(100, 0),
                List.of(new OutputAssignment("stop", false)));
        StateMachine machine = new StateMachine(List.of(red, green), List.of(), Optional.of("red"), List.of(), List.of(), false, false,
                StateMachine.EDGE_FALLING, List.of())
                .withTransitions(List.of(new MachineTransition("t1", "red", "green", "go", List.of())))
                .withPinNames(List.of("go"), List.of("stop"));
        ComponentDefinition definition = MachineDefinitions.edited(MachineDefinitions.create("semaforo", "Semáforo"),
                machine);
        fixture.document.putDefinition(definition);
        piece = fixture.place(SubcircuitTypes.typeIdOf("semaforo"), 10, 10);
    }

    @Test
    void aMachinePieceIsDescribedAsAStateMachine() {
        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());

        CircuitView.PartView part = view.parts().get(0);
        assertThat(part.machine()).isPresent();
        CircuitView.MachineView machine = part.machine().get();
        assertThat(machine.name()).isEqualTo("Semáforo");
        assertThat(machine.kind()).isEqualTo("MOORE");
        assertThat(machine.states()).containsExactly("Rojo", "Verde");
        assertThat(machine.initialState()).contains("Rojo");
        assertThat(machine.arrows()).isEqualTo(1);
        assertThat(machine.inputs()).containsExactly("go");
        assertThat(machine.outputs()).containsExactly("stop");
        assertThat(machine.edge()).isEqualTo("falling");
        assertThat(machine.currentState()).isEmpty();
        assertThat(part.pins()).extracting(pin -> pin.pin().pinName()).contains("go", "stop", "RST", "CLK");
    }

    @Test
    void theCurrentStateShowsWhileSimulating() {
        fixture.useSnapshot(new SimulationSnapshot(0, 0, 0, true, Map.of(),
                Map.of(piece.id().value(), new VisibleState.MachineVisibleState("green", "Verde", Optional.empty(),
                        Optional.empty())), List.of(), Optional.empty()));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());

        assertThat(view.parts().get(0).machine().orElseThrow().currentState()).contains("Verde");
    }

    @Test
    void thePlainPartsHaveNoMachine() {
        fixture.place(StandardComponentTypes.BUFFER, 30, 30);

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.empty());

        assertThat(view.parts().get(1).machine()).isEmpty();
        assertThat(view.summary().userComponents()).containsExactly("Semáforo");
    }
}
