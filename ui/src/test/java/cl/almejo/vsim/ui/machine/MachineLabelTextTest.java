package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** An arrow's whole label as the inline editor writes and reads it: {@code x / Z=1}. */
class MachineLabelTextTest {

    private static MachineTransition arrow(String condition, OutputAssignment... outputs) {
        return new MachineTransition("t1", "s0", "s1", condition, List.of(outputs));
    }

    @Test
    void writesTheConditionAndTheOutputs() {
        assertThat(MachineLabelText.of(arrow("x", new OutputAssignment("Z", true)))).isEqualTo("x / Z=1");
        assertThat(MachineLabelText.of(arrow("", new OutputAssignment("Z", true)))).isEqualTo("/ Z=1");
        assertThat(MachineLabelText.of(arrow("x"))).isEqualTo("x");
        assertThat(MachineLabelText.of(arrow("x", new OutputAssignment("Z", true), new OutputAssignment("Y", false))))
                .isEqualTo("x / Z=1, Y=0");
    }

    @Test
    void readsItBackSplittingAtTheFirstSlash() {
        MachineLabelText.Reading reading = MachineLabelText.read("x + y / Z=1, Y=0");

        assertThat(reading.condition()).isEqualTo("x + y");
        assertThat(reading.outputs()).containsExactly(new OutputAssignment("Z", true),
                new OutputAssignment("Y", false));
        assertThat(reading.problem()).isEmpty();
    }

    @Test
    void aLabelWithoutSlashIsJustACondition() {
        MachineLabelText.Reading reading = MachineLabelText.read("x'");

        assertThat(reading.condition()).isEqualTo("x'");
        assertThat(reading.outputs()).isEmpty();
        assertThat(reading.problem()).isEmpty();
    }

    @Test
    void aLabelThatStartsWithTheSlashIsAnOtherwiseArrowWithOutputs() {
        MachineLabelText.Reading reading = MachineLabelText.read("/ Z=1");

        assertThat(reading.condition()).isEmpty();
        assertThat(reading.outputs()).containsExactly(new OutputAssignment("Z", true));
    }

    @Test
    void aBadOutputsPartGivesItsProblem() {
        MachineLabelText.Reading reading = MachineLabelText.read("x / Z=");

        assertThat(reading.problem().orElseThrow().key()).isEqualTo("machine.outputs.badValue");
    }

    @Test
    void aSlashWithNothingAfterItMeansNoOutputs() {
        MachineLabelText.Reading reading = MachineLabelText.read("x /");

        assertThat(reading.condition()).isEqualTo("x");
        assertThat(reading.outputs()).isEmpty();
        assertThat(reading.problem()).isEmpty();
    }
}
