package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.SetProbesCommand;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.machine.MachineChannels;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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

    @Test
    void copiesPastTheCapAreNotRecordedAndTheirStripSaysSo() {
        int recorded = MachineChannels.MAXIMUM_CHANNELS / 3;
        document = MachineFixtures.circuitWithGeneratedMachines(recorded + 1);
        List<String> copies = MachineFixtures.copies(document);
        UiTestSupport.awaitSimulation(document);

        List<String> unrecorded = copies.stream().filter(copy -> !document.recordsMachineCopy(copy)).toList();
        assertThat(unrecorded).hasSize(1);
        OpenDocument place = document.openBody(MachineFixtures.DEFINITION_ID, Optional.of(unrecorded.get(0)));
        try {
            MachineStripChannels strip = new MachineStripChannels(place, MachineFixtures.DEFINITION_ID, List.of());
            assertThat(strip.title()).contains(Messages.format("machine.strip.notRecorded",
                    MachineChannels.MAXIMUM_CHANNELS));
            place.setContextPath(Optional.of(copies.stream()
                    .filter(document::recordsMachineCopy).findFirst().orElseThrow()));
            assertThat(strip.title().orElseThrow()).doesNotContain(Messages.format("machine.strip.notRecorded",
                    MachineChannels.MAXIMUM_CHANNELS));
        } finally {
            place.close();
        }
    }
}
