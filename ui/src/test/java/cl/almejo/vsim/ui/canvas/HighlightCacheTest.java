package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HighlightCacheTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final HighlightCache cache = new HighlightCache(registry);

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    private SimulationSnapshot shortedSnapshot(Netlist netlist, Component driver, Wire wire) {
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        return new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(new ShortCircuit(netId, List.of(new DriverRef(driver.id().value(), 0, Value.ONE)), 0)),
                Optional.empty());
    }

    private Component highSwitch() {
        Component high = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                GridPoint.ORIGIN, Rotation.R0, Map.of("value", "1"), ""); // pin at (4, 1)
        document.add(high);
        return high;
    }

    @Test
    void theSameFrameReusesItsHighlightsAndANewSnapshotOrNetlistRecomputesThem() {
        Component high = highSwitch();
        Wire wire = wire(4, 1, 10, 1);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot shorted = shortedSnapshot(netlist, high, wire);

        ShortCircuitHighlight first = cache.shortCircuits(document, netlist, document.revision(), shorted);
        WidthMismatchHighlight mismatches = cache.widthMismatches(document, netlist, document.revision(), shorted);

        assertThat(first.wires()).containsExactly(wire.id());
        assertThat(cache.shortCircuits(document, netlist, document.revision(), shorted)).isSameAs(first);
        assertThat(cache.widthMismatches(document, netlist, document.revision(), shorted)).isSameAs(mismatches);
        assertThat(cache.computations()).isEqualTo(1);

        SimulationSnapshot healthy = new SimulationSnapshot(2, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        assertThat(cache.shortCircuits(document, netlist, document.revision(), healthy))
                .isEqualTo(ShortCircuitHighlight.NONE);
        assertThat(cache.computations()).isEqualTo(2);

        wire(10, 1, 10, 5);
        Netlist next = new NetlistCompiler(registry).compile(document);
        // the short belongs to the old netlist: stale for the new one
        assertThat(cache.shortCircuits(document, next, document.revision(), shorted))
                .isEqualTo(ShortCircuitHighlight.NONE);
        assertThat(cache.computations()).isEqualTo(3);
    }

    @Test
    void eachHighlightIsWorkedOutOnlyWhenAskedAndTheMergedNetsOncePerNetlist() {
        Component high = highSwitch();
        Wire wire = wire(4, 1, 10, 1);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot shorted = shortedSnapshot(netlist, high, wire);
        SimulationSnapshot again = shortedSnapshot(netlist, high, wire);

        cache.shortCircuits(document, netlist, document.revision(), shorted);
        assertThat(cache.highlightComputations()).isEqualTo(1);

        cache.widthMismatches(document, netlist, document.revision(), shorted);
        cache.shortCircuits(document, netlist, document.revision(), again);
        assertThat(cache.highlightComputations()).isEqualTo(3);
        assertThat(cache.mergedComputations()).isEqualTo(1);
    }

    @Test
    void busPointsFollowTheNetlist() {
        document.add(registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(new ElementId("c1"),
                new GridPoint(10, 0), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), "")); // bus pin (10, 0)
        Netlist plain = new NetlistCompiler(registry).compile(document);
        Set<GridPoint> before = cache.busPoints(plain);
        assertThat(cache.busPoints(plain)).isSameAs(before);
        assertThat(before).doesNotContain(new GridPoint(0, 0));

        wire(0, 0, 10, 0);
        Netlist withBus = new NetlistCompiler(registry).compile(document);

        assertThat(cache.busPoints(withBus)).contains(new GridPoint(0, 0), new GridPoint(10, 0));
    }
}
