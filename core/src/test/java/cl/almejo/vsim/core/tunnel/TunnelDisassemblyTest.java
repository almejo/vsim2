package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.Disassembly;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** «Desarmar» never joins the pasted body to the outside through a tunnel name (review I3). */
class TunnelDisassemblyTest {

    private final TunnelCircuit circuit = new TunnelCircuit();
    private final ComponentDefinition buffer = TunnelSimulationTest.tunnelBuffer("A");

    private Component bufferInstance() {
        circuit.document().putDefinition(buffer);
        return circuit.place(SubcircuitTypes.typeIdOf(TunnelSimulationTest.TUNNEL_BUFFER), 40, 40, Map.of());
    }

    private void disassemble(Component instance, Disassembly.Plan plan) {
        new History(circuit.document()).execute(new CompositeCommand("command.disassemble", List.of(
                new RemoveElementsCommand(List.of(instance.id())), new PasteCommand(plan.body(), plan.offset()))));
    }

    private static Map<String, Integer> tunnelCounts(Map<String, Tunnels.TunnelGroup> groups) {
        return groups.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().entrances().size() + entry.getValue().exits().size()));
    }

    @Test
    void withoutACollisionTheBodyIsPastedAsItIs() {
        Component instance = bufferInstance();

        Disassembly.Plan plan = Disassembly.plan(circuit.document(), instance, buffer, circuit.registry());

        assertThat(plan.renamedTunnels()).isEmpty();
        assertThat(plan.body()).isEqualTo(buffer.body());
    }

    @Test
    void aBodyTunnelWhoseNameIsUsedOutsideGetsAFreeNameAndKeepsItsPin() {
        Component instance = bufferInstance();
        circuit.entrance("A", new GridPoint(0, 0));
        circuit.exit("A", new GridPoint(0, 10));
        circuit.exit("A_2", new GridPoint(0, 20));

        Disassembly.Plan plan = Disassembly.plan(circuit.document(), instance, buffer, circuit.registry());

        assertThat(plan.blocked()).isFalse();
        assertThat(plan.renamedTunnels()).containsExactly(Map.entry("A", "A_3"));
        List<Component> pastedTunnels = plan.body().components().stream()
                .filter(component -> Tunnels.isTunnel(component.typeId())).toList();
        assertThat(pastedTunnels).extracting(Tunnels::nameOf).containsOnly("A_3");
        for (Component pasted : pastedTunnels) {
            Component original = buffer.body().components().stream()
                    .filter(component -> component.id().equals(pasted.id())).findFirst().orElseThrow();
            assertThat(circuit.pin(pasted, 0)).isEqualTo(circuit.pin(original, 0));
        }

        disassemble(instance, plan);
        Map<String, Tunnels.TunnelGroup> groups = Tunnels.groups(circuit.document());
        assertThat(tunnelCounts(groups)).containsOnly(Map.entry("A", 2), Map.entry("A_2", 1), Map.entry("A_3", 2));
    }

    @Test
    void disassemblingTwoInstancesOfOneComponentKeepsTheirTunnelsApart() {
        Component first = bufferInstance();
        Component second = circuit.place(SubcircuitTypes.typeIdOf(TunnelSimulationTest.TUNNEL_BUFFER), 40, 80,
                Map.of());

        disassemble(first, Disassembly.plan(circuit.document(), first, buffer, circuit.registry()));
        Disassembly.Plan plan = Disassembly.plan(circuit.document(), second, buffer, circuit.registry());
        disassemble(second, plan);

        assertThat(plan.renamedTunnels()).containsExactly(Map.entry("A", "A_2"));
        assertThat(tunnelCounts(Tunnels.groups(circuit.document())))
                .containsOnly(Map.entry("A", 2), Map.entry("A_2", 2));
    }
}
