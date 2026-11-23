package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import cl.almejo.vsim.core.wiring.Connectivity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Ver estados y salidas en formas de onda» (hito 2, task 7): a probe can watch a machine piece's state; it resolves
 * to the state net the flattener gives that copy, is saved as {@code "watch": "state"} and never disturbs the
 * wiring's connectivity signature.
 */
class MachineStateProbeTest {

    private static final String MACHINE_ID = "maquina-1";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    MachineStateProbeTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        document.putDefinition(MachineDefinitions.regenerated(MachineDefinitions.create(MACHINE_ID, "Máquina 1")
                .withMachine(MachineExamples.twoOnesDetector())));
        document.add(new Component(new ElementId("m"), SubcircuitTypes.typeIdOf(MACHINE_ID), new GridPoint(20, 0),
                Rotation.R0, Map.of(), ""));
        document.add(new Component(new ElementId("s"), StandardComponentTypes.SWITCH, GridPoint.ORIGIN, Rotation.R0,
                Map.of(), ""));
    }

    private Netlist netlist() {
        return new NetlistCompiler(registry).compile(document);
    }

    @Test
    void aStateProbeWatchesTheCopysStateNet() {
        assertThat(ProbeResolver.netOf(new ProbeTarget.MachineStateTarget(new ElementId("m")), document, netlist()))
                .contains(StateMachineTypes.stateNetId("m"));
        assertThat(ProbeResolver.netOf(new ProbeTarget.MachineStateTarget(new ElementId("s")), document, netlist()))
                .as("a switch has no state").isEmpty();
        assertThat(ProbeResolver.netOf(new ProbeTarget.MachineStateTarget(new ElementId("gone")), document,
                netlist())).as("a deleted piece").isEmpty();
    }

    @Test
    void aStateProbeIsSavedAsWatchStateAndReadBack() throws CircuitFormatException {
        CircuitFormat format = new CircuitFormat(registry);
        List<Probe> probes = List.of(new Probe("p1", new ProbeTarget.MachineStateTarget(new ElementId("m")), ""),
                new Probe("p2", new ProbeTarget.PinTarget(new ElementId("m"), 0), "x"));
        document.setProbes(probes);

        String json = format.write(document);

        assertThat(json).contains("\"watch\" : \"state\"");
        assertThat(format.read(json).probes()).containsExactlyElementsOf(probes);
    }

    @Test
    void aWatchThatIsNotAStateOrHasAPinIsRefused() {
        CircuitFormat format = new CircuitFormat(registry);
        document.setProbes(List.of(new Probe("p1", new ProbeTarget.MachineStateTarget(new ElementId("m")), "")));
        String json = format.write(document);

        assertThatThrownBy(() -> format.read(json.replace("\"watch\" : \"state\"", "\"watch\" : \"color\"")))
                .isInstanceOf(CircuitFormatException.class);
        assertThatThrownBy(() -> format.read(json.replace("\"watch\" : \"state\"",
                "\"watch\" : \"state\", \"pin\" : 0"))).isInstanceOf(CircuitFormatException.class);
    }

    @Test
    void aStateProbeLeavesTheWiringSignatureAlone() {
        document.setProbes(List.of(new Probe("p1", new ProbeTarget.MachineStateTarget(new ElementId("m")), "")));

        Connectivity connectivity = Connectivity.of(document.wires(), document, registry);

        assertThat(connectivity.probePins()).doesNotContainKey("p1");
        assertThat(connectivity.unresolvedProbes()).doesNotContain("p1");
    }
}
