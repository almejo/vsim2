package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.GateHarness;
import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusComponentsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void theSplitterAndTheJoinerHaveAPinPerBitAndOneForTheBus() {
        ComponentType splitter = registry.require(StandardComponentTypes.BUS_SPLITTER);
        assertThat(splitter.size()).isEqualTo(new GridSize(2, 8));
        assertThat(splitter.pinCount()).isEqualTo(9);
        assertThat(splitter.pin(0)).isEqualTo(new PinSpec("bus", GridPoint.ORIGIN, PinDirection.INPUT, 8));
        assertThat(splitter.pin(1)).isEqualTo(PinSpec.output("b0", 2, 1));
        assertThat(splitter.pin(8)).isEqualTo(PinSpec.output("b7", 2, 8));
        ComponentType narrow = splitter.specializedFor(Map.of(StandardComponentTypes.WIDTH, 3));
        assertThat(narrow.size()).isEqualTo(new GridSize(2, 3));
        assertThat(narrow.pins()).extracting(PinSpec::name).containsExactly("bus", "b0", "b1", "b2");
        ComponentType joiner = registry.require(StandardComponentTypes.BUS_JOINER)
                .specializedFor(Map.of(StandardComponentTypes.WIDTH, 2));
        assertThat(joiner.pins()).containsExactly(new PinSpec("bus", new GridPoint(2, 0), PinDirection.OUTPUT, 2),
                PinSpec.input("b0", 0, 1), PinSpec.input("b1", 0, 2));
        assertThatThrownBy(() -> splitter.specializedFor(Map.of(StandardComponentTypes.WIDTH, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> splitter.specializedFor(Map.of(StandardComponentTypes.WIDTH, 33)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theJoinerPacksItsBitsLeastSignificantFirstAndKeepsZ() {
        GateHarness harness = new GateHarness(StandardComponentTypes.BUS_JOINER, Map.of(StandardComponentTypes.WIDTH, 4))
                .set(1, Value.ONE).set(2, Value.ZERO).set(3, Value.ONE).advance(5);
        assertThat(harness.output(0)).isEqualTo(new Value(4, 0b0101, 0b1000, false));
    }

    @Test
    void aJoinerAndASplitterPassEveryBitThrough() {
        SimulationSnapshot snapshot = joinerToSplitter(new String[]{"1", "0", "Z", "1"}, false);
        assertThat(snapshot.netValue("bus")).isEqualTo(new Value(4, 0b1001, 0b0100, false));
        assertThat(snapshot.netValue("out0")).isEqualTo(Value.ONE);
        assertThat(snapshot.netValue("out1")).isEqualTo(Value.ZERO);
        assertThat(snapshot.netValue("out2")).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(snapshot.netValue("out3")).isEqualTo(Value.ONE);
    }

    @Test
    void aMismatchedBusGivesZBits() {
        SimulationSnapshot snapshot = joinerToSplitter(new String[]{"1", "1", "1", "1"}, true);
        for (int bit = 0; bit < 4; bit++) {
            assertThat(snapshot.netValue("out" + bit)).as("bit %d", bit).isEqualTo(Value.HIGH_IMPEDANCE);
        }
    }

    /** Four test sources → 4-bit joiner → net "bus" → 4-bit splitter → nets out0..out3. */
    private SimulationSnapshot joinerToSplitter(String[] bits, boolean busMismatched) {
        Map<String, Object> fourBits = Map.of(StandardComponentTypes.WIDTH, 4);
        ComponentType joiner = registry.require(StandardComponentTypes.BUS_JOINER).specializedFor(fourBits);
        ComponentType splitter = registry.require(StandardComponentTypes.BUS_SPLITTER).specializedFor(fourBits);
        ComponentType source = TestComponentTypes.registry().require(TestComponentTypes.SOURCE);
        List<FlatComponent> components = new ArrayList<>(List.of(new FlatComponent("joiner", joiner, fourBits),
                new FlatComponent("splitter", splitter, fourBits)));
        List<FlatNet> nets = new ArrayList<>();
        for (int bit = 0; bit < 4; bit++) {
            components.add(new FlatComponent("source" + bit, source, Map.of("value", bits[bit])));
            nets.add(new FlatNet("in" + bit, List.of(new FlatPinRef("source" + bit, 0),
                    new FlatPinRef("joiner", bit + 1)), 1, false));
            nets.add(new FlatNet("out" + bit, List.of(new FlatPinRef("splitter", bit + 1)), 1, false));
        }
        nets.add(new FlatNet("bus", List.of(new FlatPinRef("joiner", 0), new FlatPinRef("splitter", 0)), 4,
                busMismatched));
        SimulationEngine engine = new SimulationEngine();
        engine.update(new FlatCircuit(components, nets));
        engine.advance(5);
        return engine.snapshot();
    }
}
