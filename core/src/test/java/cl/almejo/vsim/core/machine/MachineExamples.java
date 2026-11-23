package cl.almejo.vsim.core.machine;

import java.util.List;
import java.util.Optional;

/** Machines shared by the state machine tests. */
public final class MachineExamples {

    private MachineExamples() {
    }

    /**
     * The mockup's detector of two 1s in a row: {@code S0 -x-> S1 -x-> S2 -x-> S2}, any 0 goes back to {@code S0}
     * ({@code S1}'s by an «otherwise» arrow); Moore output {@code Z=1} in {@code S2}. Pins {@code x} and {@code Z}.
     */
    public static StateMachine twoOnesDetector() {
        List<MachineState> states = List.of(
                state("s0", "S0", 0, List.of(new OutputAssignment("Z", false))),
                state("s1", "S1", 100, List.of(new OutputAssignment("Z", false))),
                state("s2", "S2", 200, List.of(new OutputAssignment("Z", true))));
        List<MachineTransition> transitions = List.of(
                arrow("t0", "s0", "s0", "x'"),
                arrow("t1", "s0", "s1", "x"),
                arrow("t2", "s1", "s2", "x"),
                arrow("t3", "s1", "s0", ""),
                arrow("t4", "s2", "s2", "x"),
                arrow("t5", "s2", "s0", "x'"));
        return new StateMachine(states, transitions, Optional.of("s0"), List.of("x"), List.of("Z"), false, false,
                StateMachine.EDGE_FALLING, List.of());
    }

    public static MachineState state(String id, String name, double x, List<OutputAssignment> outputs) {
        return new MachineState(id, name, new DiagramPoint(x, 0), outputs);
    }

    public static MachineTransition arrow(String id, String fromId, String toId, String condition) {
        return new MachineTransition(id, fromId, toId, condition, List.of());
    }
}
