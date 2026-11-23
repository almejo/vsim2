package cl.almejo.vsim.core.machine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MachineKindTest {

    private static final OutputAssignment Z_ON = new OutputAssignment("Z", true);
    private static final OutputAssignment Y_ON = new OutputAssignment("Y", true);

    private static StateMachine machine(List<OutputAssignment> inFirstState, List<OutputAssignment> inSecondState,
                                        List<OutputAssignment> onArrow, List<OutputAssignment> onOtherArrow) {
        List<MachineState> states = List.of(
                new MachineState("a", "A", new DiagramPoint(0, 0), inFirstState),
                new MachineState("b", "B", new DiagramPoint(100, 0), inSecondState));
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "a", "b", "x", onArrow),
                new MachineTransition("t2", "b", "a", "", onOtherArrow));
        return new StateMachine(states, transitions, Optional.of("a"), List.of("x"), List.of("Z", "Y"), false, false,
                StateMachine.EDGE_FALLING, List.of());
    }

    @Test
    void outputsOnlyInStatesAreMoore() {
        MachineKind kind = MachineKind.of(machine(List.of(Z_ON), List.of(Z_ON, Y_ON), List.of(), List.of()));

        assertThat(kind.moore()).isTrue();
        assertThat(kind.mealy()).isFalse();
        assertThat(kind.outputsInStates()).containsExactly("Z", "Y");
        assertThat(kind.outputsOnArrows()).isEmpty();
    }

    @Test
    void outputsOnlyOnArrowsAreMealy() {
        MachineKind kind = MachineKind.of(machine(List.of(), List.of(), List.of(Z_ON), List.of(Z_ON)));

        assertThat(kind.moore()).isFalse();
        assertThat(kind.mealy()).isTrue();
        assertThat(kind.outputsOnArrows()).containsExactly("Z");
    }

    @Test
    void bothAreMixedAndNamesAreDistinctInPinOrder() {
        MachineKind kind = MachineKind.of(machine(List.of(Y_ON), List.of(Y_ON), List.of(Z_ON), List.of()));

        assertThat(kind.moore()).isTrue();
        assertThat(kind.mealy()).isTrue();
        assertThat(kind.mixed()).isTrue();
        assertThat(kind.outputsInStates()).containsExactly("Y");
        assertThat(kind.outputsOnArrows()).containsExactly("Z");
    }

    @Test
    void noOutputsAtAllIsNeither() {
        MachineKind kind = MachineKind.of(machine(List.of(), List.of(), List.of(), List.of()));

        assertThat(kind.moore()).isFalse();
        assertThat(kind.mealy()).isFalse();
        assertThat(kind.hasOutputs()).isFalse();
    }
}
