package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BusLabelsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    /** A 4-bit splitter's bus pin (10, 0) ends wire w2 from (0, 0); wire w1 runs down from (0, 0) to (0, 4). */
    private String busOfFourBits() {
        document.add(registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(new ElementId("c1"),
                new GridPoint(10, 0), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), ""));
        document.add(new Wire(new ElementId("w1"), new GridPoint(0, 4), new GridPoint(0, 0)));
        document.add(new Wire(new ElementId("w2"), new GridPoint(0, 0), new GridPoint(10, 0)));
        return netlist().netForWire(new ElementId("w2")).orElseThrow().id().value();
    }

    private Netlist netlist() {
        return new NetlistCompiler(registry).compile(document);
    }

    private List<BusLabels.BusLabel> labels(long token, Map<String, Value> values, List<Problem> problems) {
        SimulationSnapshot snapshot = new SimulationSnapshot(1, token, 0, true, values, Map.of(), problems,
                Optional.empty());
        return BusLabels.of(new RenderState(document, netlist(), document.revision(), snapshot, Set.of(), 0));
    }

    @Test
    void aShortedBusIsMarkedAsAConflictInsteadOfShowingTheFirstDriversBits() {
        String bus = busOfFourBits();
        Value shorted = Value.resolve(4, List.of(Value.of(4, 0x5), Value.of(4, 0xA)));
        assertThat(shorted.conflict()).isTrue();
        assertThat(labels(document.revision(), Map.of(bus, shorted), List.of()))
                .singleElement().satisfies(label -> assertThat(label.text()).isEqualTo("X"));
        assertThat(BusValueText.of(shorted)).isEqualTo("X");
    }

    @Test
    void aBusWritesItsValueInHexAtTheMiddleOfItsLongestWire() {
        String bus = busOfFourBits();
        assertThat(labels(document.revision(), Map.of(bus, Value.of(4, 0xA)), List.of()))
                .singleElement().satisfies(label -> {
                    assertThat(label.text()).isEqualTo("0xA");
                    assertThat(label.x()).isEqualTo(40.0);
                    assertThat(label.y()).isEqualTo(0.0);
                    assertThat(label.vertical()).isFalse();
                });
    }

    @Test
    void nothingIsWrittenForAStaleSnapshotAMissingValueAMismatchedBusOrAOneBitNet() {
        String bus = busOfFourBits();
        assertThat(labels(document.revision() - 1, Map.of(bus, Value.of(4, 0xA)), List.of())).isEmpty();
        assertThat(labels(document.revision(), Map.of(), List.of())).isEmpty();
        assertThat(labels(document.revision(), Map.of(bus, Value.highImpedance(4)),
                List.of(new WidthMismatch(bus, List.of())))).isEmpty();
        document.add(new Wire(new ElementId("w3"), new GridPoint(20, 20), new GridPoint(30, 20)));
        String lone = netlist().netForWire(new ElementId("w3")).orElseThrow().id().value();
        assertThat(labels(document.revision(), Map.of(lone, Value.ONE), List.of())).isEmpty();
    }

    @Test
    void zAndUnknownDigitsAreShown() {
        assertThat(BusValueText.of(new Value(8, 0x0F, 0x30, false))).isEqualTo("0x?F");
        assertThat(BusValueText.of(Value.highImpedance(4))).isEqualTo("0xZ");
        assertThat(BusValueText.of(Value.of(16, 0xBEEF))).isEqualTo("0xBEEF");
    }

    @Test
    void binaryGroupsBitsInFoursFromTheLeastSignificantEnd() {
        assertThat(BusValueText.binary(new Value(8, 0x8F, 0x70, false))).isEqualTo("1ZZZ 1111");
        assertThat(BusValueText.binary(new Value(6, 0x0F, 0x30, false))).isEqualTo("ZZ 1111");
        assertThat(BusValueText.binary(Value.of(5, 0x15))).isEqualTo("1 0101");
        assertThat(BusValueText.binary(Value.of(4, 0x9))).isEqualTo("1001");
    }

    @Test
    void binaryOfAnAllHighImpedanceValueIsAllZ() {
        assertThat(BusValueText.binary(Value.highImpedance(8))).isEqualTo("ZZZZ ZZZZ");
    }

    @Test
    void binaryWorksUpToSixtyFourBits() {
        assertThat(BusValueText.binary(Value.of(64, -1L)))
                .isEqualTo("1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111");
        assertThat(BusValueText.binary(Value.of(64, Long.MIN_VALUE)))
                .isEqualTo("1000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000");
    }

    @Test
    void binaryOfAConflictedValueIsTheConflictMarker() {
        assertThat(BusValueText.binary(Value.of(8, 0xFF).withConflict())).isEqualTo("X");
    }
}
