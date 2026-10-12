package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetProbesCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class OpenDocumentWaveformsTest {

    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        document.close();
    }

    private Optional<Value> currentValue(String probeId) {
        WaveformSnapshot waveforms = document.latestWaveforms();
        return waveforms.trace(probeId).flatMap(trace -> trace.valueAt(waveforms.time()));
    }

    private Probe addProbe(Component component, int pinIndex) {
        Probe probe = new Probe(document.document().newProbeId(),
                new ProbeTarget.PinTarget(component.id(), pinIndex), "");
        List<Probe> before = document.document().probes();
        List<Probe> after = new ArrayList<>(before);
        after.add(probe);
        document.execute(new SetProbesCommand(before, after, "command.addProbes"));
        return probe;
    }

    @Test
    void aProbeIsResolvedRecordedAndFollowsDeletionAndUndo() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());   // out (4,1), value 1
        Component gate = place(document, StandardComponentTypes.NOT, 4, -1, Map.of());       // (4,1) → (8,1)
        Probe probe = addProbe(gate, 1);
        String netId = document.netlist().netForPin(new PinRef(gate.id(), 1)).orElseThrow().id().value();

        assertThat(document.probeNets()).containsEntry(probe.id(), netId);
        waitUntil(() -> currentValue(probe.id()).equals(Optional.of(Value.ZERO)));

        document.interact(toggle.id());
        waitUntil(() -> currentValue(probe.id()).equals(Optional.of(Value.ONE)));

        document.execute(new RemoveElementsCommand(List.of(gate.id())));
        assertThat(document.probeNets()).doesNotContainKey(probe.id());
        waitUntil(() -> currentValue(probe.id()).isEmpty());
        assertThat(document.latestWaveforms().trace(probe.id())).as("the channel keeps its history").isPresent();

        document.history().undo();
        assertThat(document.probeNets()).containsEntry(probe.id(), netId);
        waitUntil(() -> currentValue(probe.id()).equals(Optional.of(Value.ONE)));
    }

    @Test
    void clearingDropsTheRecordedEventsButKeepsTheChannels() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Probe probe = addProbe(toggle, 0);
        waitUntil(() -> currentValue(probe.id()).equals(Optional.of(Value.ONE)));
        waitUntil(() -> document.latestWaveforms().time() > 50);
        long clearedAt = document.latestWaveforms().time();

        document.clearWaveforms();

        waitUntil(() -> document.latestWaveforms().trace(probe.id())
                .map(trace -> trace.firstTime().orElse(-1) >= clearedAt).orElse(false));
        assertThat(currentValue(probe.id())).contains(Value.ONE);
    }

    @Test
    void removingAProbeForgetsItsResolution() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Probe probe = addProbe(toggle, 0);
        document.execute(new SetProbesCommand(List.of(probe), List.of(), "command.removeProbe"));
        assertThat(document.probeNets()).isEmpty();
        waitUntil(() -> document.latestWaveforms().trace(probe.id()).isEmpty()
                && document.latestWaveforms().version() > 0);
    }

    @Test
    void aCircuitThatCannotBePreparedForgetsTheResolutions() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Probe probe = addProbe(toggle, 0);
        assertThat(document.probeNets()).containsKey(probe.id());

        // No command leads here; this stands for a circuit that cannot be prepared for simulation.
        document.document().add(new Component(new ElementId("c99"), SubcircuitTypes.typeIdOf("missing"),
                new GridPoint(20, 20), Rotation.R0, Map.of(), ""));

        assertThat(document.probeNets()).isEmpty();
        waitUntil(() -> document.latestSnapshot().failure().isPresent());
    }

    @Test
    void aFileSavedWithDefaultNamesOpensWithLiveNamesAndUnmodified(@TempDir Path folder) throws Exception {
        CircuitDocument saved = new CircuitDocument();
        Component gate = document.registry().require(StandardComponentTypes.NOT).newComponent(saved.newComponentId(),
                new GridPoint(4, -1), Rotation.R0, Map.of(), "");
        saved.add(gate);
        saved.setProbes(List.of(new Probe("p1", new ProbeTarget.PinTarget(gate.id(), 1), "c1.out"),
                new Probe("p2", new ProbeTarget.PinTarget(gate.id(), 0), "entrada")));
        Path file = folder.resolve("m6.vsim");
        Files.writeString(file, new CircuitFormat(StandardComponentTypes.registry()).write(saved));

        try (OpenDocument opened = OpenDocument.open(file, StandardComponentTypes.registry())) {
            assertThat(opened.document().probes()).extracting(Probe::name).containsExactly("", "entrada");
            assertThat(opened.isModified()).isFalse();
            assertThat(opened.history().canUndo()).isFalse();
            assertThat(opened.probeNets()).containsOnlyKeys("p1", "p2");
            assertThat(opened.serialized()).doesNotContain("c1.out").contains("entrada");
        }
    }

    @Test
    void aRecoveredCopyWithDefaultNamesGetsLiveNamesAndStaysModified(@TempDir Path folder) throws Exception {
        CircuitDocument saved = new CircuitDocument();
        Component gate = StandardComponentTypes.registry().require(StandardComponentTypes.NOT)
                .newComponent(saved.newComponentId(), new GridPoint(4, -1), Rotation.R0, Map.of(), "");
        saved.add(gate);
        saved.setProbes(List.of(new Probe("p1", new ProbeTarget.PinTarget(gate.id(), 1), "c1.out"),
                new Probe("p2", new ProbeTarget.PinTarget(gate.id(), 0), "entrada")));
        Path copy = folder.resolve("autosave-copy.vsim");
        Files.writeString(copy, new CircuitFormat(StandardComponentTypes.registry()).write(saved));

        try (OpenDocument recovered = OpenDocument.openRecovered(copy, null, StandardComponentTypes.registry())) {
            assertThat(recovered.document().probes()).extracting(Probe::name).containsExactly("", "entrada");
            assertThat(recovered.isModified()).as("a recovered copy still has to be saved").isTrue();
            assertThat(recovered.history().canUndo()).as("clearing the names is not an undo step").isFalse();
        }
    }
}
