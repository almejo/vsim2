package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SetProbesCommandTest {

    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);
    private final Probe first = new Probe("p1", new ProbeTarget.PinTarget(new ElementId("c1"), 0), "c1.out");
    private final Probe second = new Probe("p2", new ProbeTarget.WireTarget(new ElementId("w2")), "cable w2");

    @Test
    void eachEditIsOneUndoStepAndMarksTheDocumentModified() {
        history.execute(new SetProbesCommand(List.of(), List.of(first), "command.addProbes"));
        history.execute(new SetProbesCommand(List.of(first), List.of(first.withName("reloj"), second),
                "command.renameProbe"));

        assertThat(document.probes()).containsExactly(first.withName("reloj"), second);
        assertThat(history.isModified()).isTrue();
        assertThat(history.undoDescription()).contains("command.renameProbe");

        history.undo();
        assertThat(document.probes()).containsExactly(first);
        history.undo();
        assertThat(document.probes()).isEmpty();
        assertThat(history.isModified()).isFalse();

        history.redo();
        history.redo();
        assertThat(document.probes()).containsExactly(first.withName("reloj"), second);
        assertThat(document.revision()).as("probes never bump the revision").isZero();
    }

    @Test
    void applyAndRevertSetTheGivenLists() {
        SetProbesCommand command = new SetProbesCommand(List.of(first), List.of(second), "command.removeProbe");
        document.setProbes(List.of(first));
        command.apply(document);
        assertThat(document.probes()).containsExactly(second);
        command.revert(document);
        assertThat(document.probes()).containsExactly(first);
        assertThat(command.description()).isEqualTo("command.removeProbe");
    }
}
