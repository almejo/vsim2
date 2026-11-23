package cl.almejo.vsim.core.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A machine no editor of VSim makes, as a hand-made file can hold it: one more than every limit (states, inputs,
 * outputs, tests). Shared by the tests of the checks, the files, the interface and the AI.
 */
public final class MachinesBeyondTheLimits {

    public static final int STATES = MachineLimits.MAXIMUM_STATES + 6;
    public static final int INPUTS = MachineLimits.MAXIMUM_INPUTS + 1;
    public static final int OUTPUTS = MachineLimits.MAXIMUM_OUTPUTS + 1;
    public static final int TESTS = MachineTests.MAXIMUM_TESTS + 1;
    private static final int STATES_PER_ROW = 10;
    private static final double SPACING = 120;

    private MachinesBeyondTheLimits() {
    }

    /**
     * {@link #STATES} states in rows, the first one initial and giving every output; one arrow from the first to the
     * second whose condition reads every input ({@code i0·i1·…}), and an «otherwise» arrow back; {@link #INPUTS}
     * inputs {@code i0…}, {@link #OUTPUTS} outputs {@code o0…} and {@link #TESTS} tests of two columns.
     */
    public static StateMachine everyLimit() {
        List<String> inputs = names("i", INPUTS);
        List<String> outputs = names("o", OUTPUTS);
        List<MachineState> states = new ArrayList<>();
        for (int index = 0; index < STATES; index++) {
            List<OutputAssignment> given = index == 0
                    ? outputs.stream().map(name -> new OutputAssignment(name, true)).toList() : List.of();
            states.add(new MachineState("s" + index, "S" + index, new DiagramPoint(
                    (index % STATES_PER_ROW) * SPACING, (index / STATES_PER_ROW) * SPACING), given));
        }
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "s0", "s1", String.join("·", inputs), List.of()),
                new MachineTransition("t2", "s0", "s0", "", List.of()),
                new MachineTransition("t3", "s1", "s0", "", List.of()));
        List<MachineTest> tests = new ArrayList<>();
        for (int index = 1; index <= TESTS; index++) {
            tests.add(MachineTest.empty("p" + index, "Prueba " + index, 2));
        }
        return new StateMachine(states, transitions, Optional.of("s0"), inputs, outputs, false, false,
                StateMachine.EDGE_FALLING, tests);
    }

    private static List<String> names(String prefix, int count) {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            names.add(prefix + index);
        }
        return names;
    }
}
