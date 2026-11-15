package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.SetProbesCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.machine.MachineChannels;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every placed machine copy records its state, inputs and outputs from the start (hito 2, decision 2), without
 * being a probe: not in the document, not in {@link OpenDocument#probeNets()}.
 */
class MachineWaveformChannelsTest {

    private OpenDocument document;

    @AfterEach
    void close() {
        if (document != null) {
            document.close();
        }
    }

    @Test
    void aPlacedMachineRecordsItsChannelsWithoutBeingAProbe() {
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        String copy = MachineFixtures.copies(document).get(0);

        UiTestSupport.awaitSimulation(document);
        UiTestSupport.waitUntil(() -> !document.latestWaveforms().traces().isEmpty());

        WaveformSnapshot waveforms = document.latestWaveforms();
        assertThat(waveforms.traces()).containsKeys(MachineChannels.stateChannelId(copy),
                MachineChannels.inputChannelId(copy, "x"), MachineChannels.outputChannelId(copy, "Z"));
        assertThat(document.document().probes()).isEmpty();
        assertThat(document.probeNets()).isEmpty();
    }

    @Test
    void restartingKeepsTheMachineChannels() {
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        String copy = MachineFixtures.copies(document).get(0);
        UiTestSupport.awaitSimulation(document);

        document.restart();
        UiTestSupport.awaitSimulation(document);
        UiTestSupport.waitUntil(() -> !document.latestWaveforms().traces().isEmpty());

        assertThat(document.latestWaveforms().traces()).containsKey(MachineChannels.stateChannelId(copy));
    }

    @Test
    void addingAProbeKeepsTheMachineChannels() {
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        String copy = MachineFixtures.copies(document).get(0);
        UiTestSupport.awaitSimulation(document);

        Probe probe = new Probe(document.document().newProbeId(),
                new ProbeTarget.PinTarget(new ElementId(copy), 0), "");
        document.execute(new SetProbesCommand(List.of(), List.of(probe), "command.addProbes"));
        UiTestSupport.awaitSimulation(document);
        UiTestSupport.waitUntil(() -> document.latestWaveforms().trace(probe.id()).isPresent());

        assertThat(document.latestWaveforms().traces()).containsKeys(probe.id(),
                MachineChannels.stateChannelId(copy));
        assertThat(document.probeNets()).doesNotContainKey(MachineChannels.stateChannelId(copy));
    }
}
