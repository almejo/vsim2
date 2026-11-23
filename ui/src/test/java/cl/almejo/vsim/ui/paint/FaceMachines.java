package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Machines for the face's tests: {@code S0 … Sn-1} (ids {@code s0 …}) joined by the given arrows. */
final class FaceMachines {

    private FaceMachines() {
    }

    /** {@code stateCount} states in a row, S0 initial, and {@code transitions}; input {@code x}, output {@code Z}. */
    static StateMachine withStates(int stateCount, MachineTransition... transitions) {
        return withStates(stateCount, List.of(transitions));
    }

    static StateMachine withStates(int stateCount, List<MachineTransition> transitions) {
        List<MachineState> states = new ArrayList<>();
        for (int index = 0; index < stateCount; index++) {
            states.add(new MachineState("s" + index, "S" + index, new DiagramPoint(index * 160, 0), List.of()));
        }
        return new StateMachine(states, transitions, stateCount == 0 ? Optional.empty() : Optional.of("s0"),
                List.of("x"), List.of("Z"), false, false, StateMachine.EDGE_FALLING, List.of());
    }

    /** An arrow {@code id} from {@code fromId} to {@code toId} on {@code x}. */
    static MachineTransition arrow(String id, String fromId, String toId) {
        return new MachineTransition(id, fromId, toId, "x", List.of());
    }

    /** One self loop on every one of the first {@code stateCount} states. */
    static List<MachineTransition> loopsOnEveryState(int stateCount) {
        List<MachineTransition> loops = new ArrayList<>();
        for (int index = 0; index < stateCount; index++) {
            loops.add(arrow("loop" + index, "s" + index, "s" + index));
        }
        return loops;
    }
}
