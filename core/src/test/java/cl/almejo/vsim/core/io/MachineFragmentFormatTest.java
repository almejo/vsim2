package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Copied states and arrows travel on the clipboard as their own kind of VSim text, never taken for a circuit's. */
class MachineFragmentFormatTest {

    private static StateMachine piece() {
        return new StateMachine(List.of(
                new MachineState("s1", "AMARILLO", new DiagramPoint(10, 20), List.of(new OutputAssignment("A", true))),
                new MachineState("s2", "ROJO", new DiagramPoint(110, 20), List.of())),
                List.of(new MachineTransition("t1", "s1", "s2", "x", List.of(new OutputAssignment("Z", true)))),
                Optional.empty(), List.of(), List.of(), false, false, StateMachine.EDGE_FALLING, List.of());
    }

    @Test
    void aPieceComesBackAsItWasCopied() {
        String json = MachineFragmentFormat.write(piece());

        assertThat(MachineFragmentFormat.isMachineFragment(json)).isTrue();
        assertThat(MachineFragmentFormat.read(json)).contains(piece());
    }

    @Test
    void aCircuitFragmentOrAnyOtherTextIsNotAMachinePiece() {
        String circuit = new FragmentFormat(StandardComponentTypes.registry())
                .write(new CircuitFragment(List.of(), List.of(), List.of()));

        assertThat(MachineFragmentFormat.isMachineFragment(circuit)).isFalse();
        assertThat(MachineFragmentFormat.read(circuit)).isEmpty();
        assertThat(MachineFragmentFormat.read("hola")).isEmpty();
        assertThat(MachineFragmentFormat.isCircuitFragment(circuit)).isTrue();
        assertThat(MachineFragmentFormat.isCircuitFragment(MachineFragmentFormat.write(piece()))).isFalse();
    }

    @Test
    void aMachinePieceIsNotReadAsACircuitFragment() {
        String json = MachineFragmentFormat.write(piece());

        assertThatThrownBy(
                () -> new FragmentFormat(StandardComponentTypes.registry()).read(json))
                .isInstanceOf(CircuitFormatException.class);
    }
}
