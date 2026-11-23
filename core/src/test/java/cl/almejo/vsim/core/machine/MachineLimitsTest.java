package cl.almejo.vsim.core.machine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MachineLimitsTest {

    @Test
    void theLimitsAreSixteenInputsSixteenOutputsAndSixtyFourStates() {
        assertThat(MachineLimits.MAXIMUM_INPUTS).isEqualTo(16);
        assertThat(MachineLimits.MAXIMUM_OUTPUTS).isEqualTo(16);
        assertThat(MachineLimits.MAXIMUM_STATES).isEqualTo(64);
    }

    @Test
    void theClockAndResetAreReserved() {
        assertThat(MachineLimits.isReserved("CLK")).isTrue();
        assertThat(MachineLimits.isReserved("RST")).isTrue();
        assertThat(MachineLimits.isReserved("clk")).isFalse();
        assertThat(MachineLimits.isReserved("x")).isFalse();
    }

    @Test
    void aStateCanBeAddedUntilTheLimit() {
        assertThat(MachineLimits.canAddState(machineWithStates(MachineLimits.MAXIMUM_STATES - 1))).isTrue();
        assertThat(MachineLimits.canAddState(machineWithStates(MachineLimits.MAXIMUM_STATES))).isFalse();
    }

    private static StateMachine machineWithStates(int count) {
        List<MachineState> states = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            states.add(MachineExamples.state("s" + index, "S" + index, index * 10, List.of()));
        }
        return new StateMachine(states, List.of(), Optional.of("s0"), List.of(), List.of(), false, false,
                StateMachine.EDGE_FALLING, List.of());
    }
}
