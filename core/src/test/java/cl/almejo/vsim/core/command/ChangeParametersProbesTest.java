package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeParametersProbesTest {

    private final ComponentType and = StandardComponentTypes.registry().require(StandardComponentTypes.AND);
    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);
    private final ElementId gate = new ElementId("c1");

    private void placeGateWithProbes(int inputs, Probe... probes) {
        Component component = and.newComponent(gate, new GridPoint(0, 0), Rotation.R0,
                Map.of(StandardComponentTypes.GATE_INPUTS, inputs, StandardComponentTypes.NEGATE, true), "");
        history.execute(new AddElementsCommand(List.of(component)));
        history.execute(new SetProbesCommand(List.of(), List.of(probes), "command.addProbes"));
    }

    @Test
    void aProbeOnTheOutputFollowsItWhenTheInputsChangeAndUndoRestores() {
        Probe output = new Probe("p1", new ProbeTarget.PinTarget(gate, 2), "out");
        placeGateWithProbes(2, output);

        history.execute(new ChangeParametersCommand(gate, and, Map.of(StandardComponentTypes.GATE_INPUTS, 3,
                StandardComponentTypes.NEGATE, true)));
        assertThat(document.probes()).containsExactly(
                new Probe("p1", new ProbeTarget.PinTarget(gate, 3), "out"));

        history.undo();
        assertThat(document.probes()).containsExactly(output);
        history.redo();
        assertThat(document.probes().get(0).target()).isEqualTo(new ProbeTarget.PinTarget(gate, 3));
    }

    @Test
    void aProbeOnAnInputThatNoLongerExistsIsDropped() {
        Probe input = new Probe("p1", new ProbeTarget.PinTarget(gate, 2), "in2");
        Probe other = new Probe("p2", new ProbeTarget.WireTarget(new ElementId("w1")), "w");
        placeGateWithProbes(3, input, other);

        history.execute(new ChangeParametersCommand(gate, and, Map.of(StandardComponentTypes.GATE_INPUTS, 2)));
        assertThat(document.probes()).containsExactly(other);
        history.undo();
        assertThat(document.probes()).containsExactly(input, other);
    }
}
