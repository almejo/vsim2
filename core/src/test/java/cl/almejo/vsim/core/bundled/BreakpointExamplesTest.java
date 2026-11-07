package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.BreakpointTypes;
import cl.almejo.vsim.core.simulation.PauseNotice;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «Punto de parada» is wired and explained cleanly and pauses at 9, again at the next 9, never for the off one. */
class BreakpointExamplesTest {

    private static final long LONG_ENOUGH = 100 * 500L;
    private static final long FULL_COUNT = 16 * 500L;

    private static PauseNotice pause(ExampleSimulation simulation) {
        assertThat(simulation.engine().advanceUntilPause(LONG_ENOUGH)).isTrue();
        return simulation.engine().takePauseRequest().orElseThrow();
    }

    @Test
    void theExampleIsWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(BreakpointExamples.puntoDeParada());
    }

    @Test
    void theRunPausesWhenTheCountReachesNineAndAgainOneFullCountLater() {
        ExampleSimulation simulation = new ExampleSimulation(BreakpointExamples.puntoDeParada(), Map.of());

        PauseNotice first = pause(simulation);

        assertThat(first.messageKey()).isEqualTo(BreakpointTypes.STOPPED_EQUALS_MESSAGE);
        assertThat(simulation.led("Cuenta").bits()).isEqualTo(BreakpointExamples.NINE);
        PauseNotice second = pause(simulation);
        assertThat(second.time() - first.time()).isEqualTo(FULL_COUNT);
        assertThat(simulation.led("Cuenta").bits()).isEqualTo(BreakpointExamples.NINE);
    }

    @Test
    void theSwitchedOffBreakpointNeverSpeaks() {
        ExampleSimulation simulation = new ExampleSimulation(BreakpointExamples.puntoDeParada(), Map.of());

        for (int pauses = 0; pauses < 3; pauses++) {
            assertThat(pause(simulation).messageKey()).isEqualTo(BreakpointTypes.STOPPED_EQUALS_MESSAGE);
        }
    }
}
