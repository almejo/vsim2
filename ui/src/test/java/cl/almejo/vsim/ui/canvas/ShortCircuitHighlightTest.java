package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCircuitHighlightTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Component high = place(0, 0, "1");
    private final Component low = place(0, 6, "0");
    private final Wire joint = wire(4, 1, 4, 7);
    private final Netlist netlist = new NetlistCompiler(registry).compile(document);
    private final Net shorted = netlist.netForPin(new PinRef(high.id(), 0)).orElseThrow();

    private Component place(int x, int y, String value) {
        Component component = registry.require(StandardComponentTypes.SWITCH)
                .newComponent(document.newComponentId(), new GridPoint(x, y), Rotation.R0, Map.of("value", value), "");
        document.add(component);
        return component;
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    private SimulationSnapshot snapshotWithShort(long token) {
        ShortCircuit shortCircuit = new ShortCircuit(shorted.id().value(), List.of(
                new DriverRef(high.id().value(), 0, Value.ONE), new DriverRef(low.id().value(), 0, Value.ZERO)), 5);
        return new SimulationSnapshot(1, token, 10, true, Map.of(shorted.id().value(), Value.ONE.withConflict()),
                Map.of(), List.of(shortCircuit), Optional.empty());
    }

    @Test
    void marksTheNetsWiresTheDriversAndTheirPins() {
        ShortCircuitHighlight highlight = ShortCircuitHighlight.of(snapshotWithShort(document.revision()), netlist,
                document.revision(), document, registry);
        assertThat(highlight.wires()).containsExactly(joint.id());
        assertThat(highlight.components()).containsExactlyInAnyOrder(high.id(), low.id());
        assertThat(highlight.driverPins()).containsExactlyInAnyOrder(new GridPoint(4, 1), new GridPoint(4, 7));
    }

    @Test
    void staleSnapshotHighlightsNothing() {
        assertThat(ShortCircuitHighlight.of(snapshotWithShort(document.revision() - 1), netlist, document.revision(),
                document, registry)).isEqualTo(ShortCircuitHighlight.NONE);
    }

    @Test
    void aDriverInsideATemplateOutlinesItsTopLevelInstance() {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(ExampleDefinitions.inverter());
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        Component instance = new Component(new ElementId("c5"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        document.add(instance);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, 7, 0, true, Map.of(), Map.of(),
                List.of(new ShortCircuit("c5/n:p:c2#1", List.of(new DriverRef("c5/c2", 1, Value.ONE),
                        new DriverRef("c5/c9", 0, Value.ZERO)), 0)),
                Optional.empty());

        ShortCircuitHighlight highlight = ShortCircuitHighlight.of(snapshot, Netlist.EMPTY, 7, document, registry);

        assertThat(highlight.components()).containsExactly(instance.id());
        assertThat(highlight.driverPins()).isEmpty();
    }
}
