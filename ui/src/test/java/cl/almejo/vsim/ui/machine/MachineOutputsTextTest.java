package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.OutputAssignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The Moore outputs of a state as the right panel writes them: {@code Z=1, Y=0}. */
class MachineOutputsTextTest {

    @Test
    void readsAssignmentsSeparatedByCommasOrSpaces() {
        MachineOutputsText.Reading reading = MachineOutputsText.read(" Z=1, Y = 0  Listo=1 ");

        assertThat(reading.problem()).isEmpty();
        assertThat(reading.outputs()).containsExactly(new OutputAssignment("Z", true),
                new OutputAssignment("Y", false), new OutputAssignment("Listo", true));
    }

    @Test
    void anEmptyTextGivesNoOutputs() {
        assertThat(MachineOutputsText.read("  ").outputs()).isEmpty();
        assertThat(MachineOutputsText.read("  ").problem()).isEmpty();
    }

    @Test
    void writesThemBackTheSameWay() {
        assertThat(MachineOutputsText.write(List.of(new OutputAssignment("Z", true), new OutputAssignment("Y", false))))
                .isEqualTo("Z=1, Y=0");
    }

    @Test
    void aBadTextSaysWhatIsWrong() {
        assertThat(MachineOutputsText.read("Z").problem().orElseThrow().key())
                .isEqualTo("machine.outputs.notAssignment");
        assertThat(MachineOutputsText.read("Z=2").problem().orElseThrow().key())
                .isEqualTo("machine.outputs.badValue");
        assertThat(MachineOutputsText.read("1Z=1").problem().orElseThrow().key())
                .isEqualTo("machine.outputs.badName");
        assertThat(MachineOutputsText.read("CLK=1").problem().orElseThrow().key())
                .isEqualTo("machine.outputs.reserved");
        assertThat(MachineOutputsText.read("Z=1, Z=0").problem().orElseThrow().key())
                .isEqualTo("machine.outputs.repeated");
        assertThat(MachineOutputsText.read("Z=1, Z=0").outputs()).isEmpty();
    }
}
