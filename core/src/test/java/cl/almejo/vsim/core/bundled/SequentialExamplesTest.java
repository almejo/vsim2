package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The sequential examples are wired and explained cleanly and step through their states (flip-flops start at 0). */
class SequentialExamplesTest {

    private static final String[] Q = {"Q0", "Q1", "Q2", "Q3"};

    @Test
    void everySequentialExampleIsWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(SequentialExamples.register());
        ExampleChecks.assertExample(SequentialExamples.shiftRegister());
        ExampleChecks.assertExample(SequentialExamples.counter());
        ExampleChecks.assertExample(SequentialExamples.johnsonCounter());
    }

    @Test
    void theRegisterLoadsOnTheFallingEdgeOfCargarOnly() {
        ExampleSimulation simulation = new ExampleSimulation(SequentialExamples.register(), Map.of()).settle();
        assertThat(simulation.leds(Q)).as("nothing loaded yet: flip-flops start at 0").containsOnly(Value.ZERO);

        simulation.toggle("Cargar").settle();
        assertThat(simulation.leds(Q)).containsExactly(Value.ONE, Value.ZERO, Value.ONE, Value.ONE);

        simulation.toggle("D0").toggle("D1").settle();
        assertThat(simulation.leds(Q)).as("the data alone changes nothing")
                .containsExactly(Value.ONE, Value.ZERO, Value.ONE, Value.ONE);
        simulation.toggle("Cargar").settle();
        assertThat(simulation.leds(Q)).as("a rising edge changes nothing")
                .containsExactly(Value.ONE, Value.ZERO, Value.ONE, Value.ONE);

        simulation.toggle("Cargar").settle();
        assertThat(simulation.leds(Q)).containsExactly(Value.ZERO, Value.ONE, Value.ONE, Value.ONE);
    }

    @Test
    void theShiftRegisterMovesTheDataOneStagePerFallingEdge() {
        // Clock 500/500: low first, falling edges at 1000, 2000, 3000, …
        ExampleSimulation simulation = new ExampleSimulation(SequentialExamples.shiftRegister(), Map.of());
        simulation.runUntil(100);
        assertThat(simulation.number("Q", 4)).isZero();
        simulation.toggle("Dato");
        int[] filling = {0b0001, 0b0011, 0b0111, 0b1111};
        for (int edge = 1; edge <= 4; edge++) {
            simulation.runUntil(1000L * edge + 100);
            assertThat(simulation.number("Q", 4)).as("after %d edges", edge).isEqualTo(filling[edge - 1]);
        }
        simulation.toggle("Dato");
        int[] emptying = {0b1110, 0b1100, 0b1000, 0b0000};
        for (int edge = 5; edge <= 8; edge++) {
            simulation.runUntil(1000L * edge + 100);
            assertThat(simulation.number("Q", 4)).as("after %d edges", edge).isEqualTo(emptying[edge - 5]);
        }
    }

    @Test
    void theRippleCounterCountsOnePerPeriodFromZeroAndWrapsAtSixteen() {
        // Clock 250/250: falling edges at 500, 1000, …; the ripple settles within a few units after each edge.
        ExampleSimulation simulation = new ExampleSimulation(SequentialExamples.counter(), Map.of());
        for (int periods = 0; periods <= 17; periods++) {
            simulation.runUntil(500L * periods + 100);
            assertThat(simulation.number("Q", 4)).as("after %d periods", periods).isEqualTo(periods % 16);
            assertThat(simulation.display(0)).as("display after %d periods", periods)
                    .isEqualTo(Value.of(4, periods % 16));
        }
    }

    @Test
    void theJohnsonCounterGoesThroughItsEightStates() {
        List<String> states = List.of("0000", "1000", "1100", "1110", "1111", "0111", "0011", "0001");
        ExampleSimulation simulation = new ExampleSimulation(SequentialExamples.johnsonCounter(), Map.of());
        for (int periods = 0; periods <= 16; periods++) {
            simulation.runUntil(500L * periods + 100);
            StringBuilder state = new StringBuilder();
            for (String label : Q) {
                state.append(simulation.led(label).equals(Value.ONE) ? '1' : '0');
            }
            assertThat(state.toString()).as("Q0..Q3 after %d periods", periods).isEqualTo(states.get(periods % 8));
        }
    }
}
