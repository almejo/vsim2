package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
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

import static cl.almejo.vsim.ui.UiTestSupport.wire;
import static org.assertj.core.api.Assertions.assertThat;

class HighlightCacheTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final HighlightCache cache = new HighlightCache(registry);

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
        Wire wire = wire(document, 4, 1, 10, 1);
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

        wire(document, 10, 1, 10, 5);
        Netlist next = new NetlistCompiler(registry).compile(document);
        // the short belongs to the old netlist: stale for the new one
        assertThat(cache.shortCircuits(document, next, document.revision(), shorted))
                .isEqualTo(ShortCircuitHighlight.NONE);
        assertThat(cache.computations()).isEqualTo(3);
    }

    @Test
    void eachHighlightIsWorkedOutOnlyWhenAskedAndTheMergedNetsOncePerNetlist() {
        Component high = highSwitch();
        Wire wire = wire(document, 4, 1, 10, 1);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot shorted = shortedSnapshot(netlist, high, wire);
        SimulationSnapshot again = shortedSnapshot(netlist, high, wire);

        cache.shortCircuits(document, netlist, document.revision(), shorted);
        assertThat(cache.highlightComputations()).isEqualTo(1);

        cache.widthMismatches(document, netlist, document.revision(), shorted);
        cache.shortCircuits(document, netlist, document.revision(), again);
        assertThat(cache.highlightComputations()).as("a snapshot with the same problems keeps them").isEqualTo(2);
        assertThat(cache.topLevelNets(document, netlist)).isSameAs(cache.topLevelNets(document, netlist));
        assertThat(cache.mergedComputations()).isEqualTo(1);
    }

    @Test
    void newSnapshotsWithTheSameProblemsKeepTheFrameWhileNewProblemsOrEditsRecomputeIt() {
        Component high = highSwitch();
        Wire wire = wire(document, 4, 1, 10, 1);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot shorted = shortedSnapshot(netlist, high, wire);
        ShortCircuitHighlight first = cache.shortCircuits(document, netlist, document.revision(), shorted);

        for (int step = 0; step < 5; step++) { // a running simulation: new snapshot, new values, same short
            SimulationSnapshot running = new SimulationSnapshot(10 + step, document.revision(), step, true,
                    Map.of("n", Value.of(1, step % 2)), Map.of(), shorted.problems(), Optional.empty());
            assertThat(cache.shortCircuits(document, netlist, document.revision(), running)).isSameAs(first);
        }
        assertThat(cache.computations()).isEqualTo(1);

        SimulationSnapshot healed = new SimulationSnapshot(20, document.revision(), 9, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        assertThat(cache.shortCircuits(document, netlist, document.revision(), healed))
                .isEqualTo(ShortCircuitHighlight.NONE);
        assertThat(cache.computations()).isEqualTo(2);

        assertThat(cache.shortCircuits(document, netlist, document.revision(), shorted).wires())
                .containsExactly(wire.id());
        long revision = document.revision();
        document.add(new TextLabel(document.newLabelId(), new GridPoint(30, 30), "nota"));
        assertThat(document.revision()).isNotEqualTo(revision);
        cache.shortCircuits(document, netlist, revision, shorted);
        assertThat(cache.computations()).as("an edit of the document recomputes").isEqualTo(4);
    }

    /** A 4-bit splitter's bus pin (10, 0) ends wire (0, 0)-(10, 0). */
    private String busOfFourBits() {
        document.add(registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(new ElementId("c1"),
                new GridPoint(10, 0), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), ""));
        Wire bus = wire(document, 0, 0, 10, 0);
        return new NetlistCompiler(registry).compile(document).netForWire(bus.id()).orElseThrow().id().value();
    }

    private RenderState state(Netlist netlist, Map<String, Value> values) {
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, values, Map.of(),
                List.of(), Optional.empty());
        return new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0);
    }

    @Test
    void busLabelsPlaceTheirBusesOncePerRevisionAndNetlistAndFollowEverySnapshotsValues() {
        String bus = busOfFourBits();
        Netlist netlist = new NetlistCompiler(registry).compile(document);

        RenderState first = state(netlist, Map.of(bus, Value.of(4, 0x3)));
        assertThat(cache.busLabels(first)).singleElement()
                .satisfies(label -> assertThat(label.text()).isEqualTo("0x3"));
        assertThat(cache.busLabels(first)).isSameAs(cache.busLabels(first));
        assertThat(cache.busLabels(state(netlist, Map.of(bus, Value.of(4, 0xA))))).singleElement()
                .satisfies(label -> assertThat(label.text()).isEqualTo("0xA"));
        assertThat(cache.busAnchorComputations()).isEqualTo(1);

        Wire longer = wire(document, 0, 0, 0, 30);
        Netlist next = new NetlistCompiler(registry).compile(document);
        String nextBus = next.netForWire(longer.id()).orElseThrow().id().value();
        RenderState edited = state(next, Map.of(nextBus, Value.of(4, 0x5)));
        assertThat(cache.busLabels(edited)).singleElement().satisfies(label -> {
            assertThat(label.text()).isEqualTo("0x5");
            assertThat(label.vertical()).as("moved to the new longest wire").isTrue();
        });
        assertThat(cache.busLabels(edited)).isEqualTo(BusLabels.of(edited));
        assertThat(cache.busAnchorComputations()).isEqualTo(2);
    }

    @Test
    void busPointsFollowTheNetlist() {
        document.add(registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(new ElementId("c1"),
                new GridPoint(10, 0), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), "")); // bus pin (10, 0)
        Netlist plain = new NetlistCompiler(registry).compile(document);
        Set<GridPoint> before = cache.busPoints(plain);
        assertThat(cache.busPoints(plain)).isSameAs(before);
        assertThat(before).doesNotContain(new GridPoint(0, 0));

        wire(document, 0, 0, 10, 0);
        Netlist withBus = new NetlistCompiler(registry).compile(document);

        assertThat(cache.busPoints(withBus)).contains(new GridPoint(0, 0), new GridPoint(10, 0));
    }
}
