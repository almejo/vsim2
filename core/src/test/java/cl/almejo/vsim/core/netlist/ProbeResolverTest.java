package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ProbeResolverTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Component add(String typeId, int x, int y) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    private Wire wire(int x1, int y1, int x2, int y2) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(x1, y1), new GridPoint(x2, y2));
        document.add(wire);
        return wire;
    }

    private Netlist netlist() {
        return new NetlistCompiler(registry).compile(document);
    }

    private static Probe pinProbe(String id, ElementId componentId, int pinIndex) {
        return new Probe(id, new ProbeTarget.PinTarget(componentId, pinIndex), "");
    }

    @Test
    void aPinProbeResolvesToTheNetOfItsPin() {
        Component toggle = add(StandardComponentTypes.SWITCH, 0, 0);
        Netlist netlist = netlist();
        String netId = netlist.netForPin(new PinRef(toggle.id(), 0)).orElseThrow().id().value();

        assertThat(ProbeResolver.resolve(List.of(pinProbe("p1", toggle.id(), 0)), document, netlist))
                .containsExactly(entry("p1", netId));
    }

    @Test
    void aWireProbeAnchorsToADriverPinOfItsNetFirst() {
        add(StandardComponentTypes.LED, 7, -2);                        // c1, input at (8,1)
        Component toggle = add(StandardComponentTypes.SWITCH, 0, 0);   // c2, output at (4,1)
        Wire wire = wire(4, 1, 8, 1);

        assertThat(ProbeResolver.anchor(wire.id(), document, netlist(), registry))
                .contains(new ProbeTarget.PinTarget(toggle.id(), 0));
    }

    @Test
    void withoutADriverAWireProbeAnchorsToTheFirstPinInDocumentOrder() {
        Component gate = add(StandardComponentTypes.NOT, 4, -1);   // c1, input at (4,1), output at (8,1)
        add(StandardComponentTypes.LED, 3, 2);                     // c2, input at (4,5)
        Wire wire = wire(4, 1, 4, 5);

        assertThat(ProbeResolver.anchor(wire.id(), document, netlist(), registry))
                .contains(new ProbeTarget.PinTarget(gate.id(), 0));
    }

    @Test
    void aWireOfANetWithoutPinsStaysAWireProbeAndResolvesThroughTheWire() {
        Wire wire = wire(20, 20, 24, 20);
        Netlist netlist = netlist();

        ProbeTarget target = ProbeResolver.anchor(wire.id(), document, netlist, registry).orElseThrow();

        assertThat(target).isEqualTo(new ProbeTarget.WireTarget(wire.id()));
        assertThat(ProbeResolver.netOf(target, document, netlist))
                .contains(netlist.netForWire(wire.id()).orElseThrow().id().value());
    }

    @Test
    void probesThatDoNotResolveAreAbsent() {
        Component toggle = add(StandardComponentTypes.SWITCH, 0, 0);
        Netlist netlist = netlist();
        List<Probe> probes = List.of(pinProbe("p1", new ElementId("c99"), 0), pinProbe("p2", toggle.id(), 5),
                new Probe("p3", new ProbeTarget.WireTarget(new ElementId("w99")), ""), pinProbe("p4", toggle.id(), 0));

        assertThat(ProbeResolver.resolve(probes, document, netlist).keySet()).containsExactly("p4");
        assertThat(ProbeResolver.anchor(new ElementId("w99"), document, netlist, registry)).isEmpty();
    }
}
