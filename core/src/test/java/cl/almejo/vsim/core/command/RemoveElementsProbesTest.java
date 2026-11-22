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
import cl.almejo.vsim.core.simulation.SimulationSpeeds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemoveElementsProbesTest {

    private final ComponentType and = StandardComponentTypes.registry().require(StandardComponentTypes.AND);
    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);
    private final ElementId gate = new ElementId("c1");

    @Test
    void deletingAnElementRemovesItsProbesInTheSameStepAndUndoBringsThemBack() {
        Component component = and.newComponent(gate, new GridPoint(0, 0), Rotation.R0, Map.of(), "");
        history.execute(new AddElementsCommand(List.of(component)));
        Probe watching = new Probe("p1", new ProbeTarget.PinTarget(gate, 2), "out");
        Probe other = new Probe("p2", new ProbeTarget.WireTarget(new ElementId("w1")), "w");
        history.execute(new SetProbesCommand(List.of(), List.of(watching, other), "command.addProbes"));

        history.execute(new RemoveElementsCommand(List.of(gate)));
        assertThat(document.probes()).containsExactly(other);

        history.undo();
        assertThat(document.probes()).containsExactly(watching, other);
        assertThat(document.element(gate)).isPresent();
        history.redo();
        assertThat(document.probes()).containsExactly(other);
    }

    @Test
    void aCopyKeepsTheSimulationSpeed() {
        double speed = SimulationSpeeds.AVAILABLE.stream().filter(candidate -> candidate != SimulationSpeeds.DEFAULT)
                .findFirst().orElseThrow();
        document.setSimulationSpeed(speed);
        assertThat(document.copy().simulationSpeed()).isEqualTo(speed);
    }
}
