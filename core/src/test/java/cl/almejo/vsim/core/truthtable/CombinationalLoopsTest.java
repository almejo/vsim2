package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CombinationalLoopsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<FlatComponent> components = new ArrayList<>();
    private final Map<String, List<FlatPinRef>> nets = new LinkedHashMap<>();

    private void add(String id, String typeId) {
        components.add(new FlatComponent(id, registry.require(typeId), Map.of()));
    }

    /** Puts pin {@code pinIndex} of {@code componentId} on net {@code netId}. */
    private void connect(String netId, String componentId, int pinIndex) {
        nets.computeIfAbsent(netId, key -> new ArrayList<>()).add(new FlatPinRef(componentId, pinIndex));
    }

    private FlatCircuit circuit() {
        List<FlatNet> flatNets = new ArrayList<>();
        nets.forEach((id, pins) -> flatNets.add(new FlatNet(id, pins, 1, false)));
        return new FlatCircuit(components, flatNets);
    }

    @Test
    void aRingOfThreeNotGatesIsALoop() {
        for (int index = 0; index < 3; index++) {
            add("not" + index, StandardComponentTypes.NOT);
            connect("n" + index, "not" + index, 0);
            connect("n" + (index + 1) % 3, "not" + index, 1);
        }
        assertThat(CombinationalLoops.exists(circuit())).isTrue();
    }

    @Test
    void crossCoupledNorGatesAreALoop() {
        add("s", StandardComponentTypes.SWITCH);
        add("r", StandardComponentTypes.SWITCH);
        add("top", StandardComponentTypes.NOR2);
        add("bottom", StandardComponentTypes.NOR2);
        connect("set", "s", 0);
        connect("set", "top", 0);
        connect("reset", "r", 0);
        connect("reset", "bottom", 1);
        connect("q", "top", 2);
        connect("q", "bottom", 0);
        connect("notQ", "bottom", 2);
        connect("notQ", "top", 1);
        assertThat(CombinationalLoops.exists(circuit())).isTrue();
    }

    @Test
    void aGateReadingItsOwnOutputIsALoop() {
        add("gate", StandardComponentTypes.AND2);
        connect("out", "gate", 0);
        connect("out", "gate", 2);
        assertThat(CombinationalLoops.exists(circuit())).isTrue();
    }

    @Test
    void aChainWithFanOutAndReconvergenceIsNotALoop() {
        add("a", StandardComponentTypes.SWITCH);
        add("first", StandardComponentTypes.NOT);
        add("second", StandardComponentTypes.NOT);
        add("both", StandardComponentTypes.AND2);
        add("led", StandardComponentTypes.LED);
        connect("in", "a", 0);
        connect("in", "first", 0);
        connect("in", "second", 0);
        connect("x", "first", 1);
        connect("x", "both", 0);
        connect("y", "second", 1);
        connect("y", "both", 1);
        connect("out", "both", 2);
        connect("out", "led", 0);
        assertThat(CombinationalLoops.exists(circuit())).isFalse();
    }

    @Test
    void aVeryLongChainDoesNotOverflowTheStack() {
        int length = 20_000;
        for (int index = 0; index < length; index++) {
            add("not" + index, StandardComponentTypes.NOT);
            connect("n" + index, "not" + index, 0);
            connect("n" + (index + 1), "not" + index, 1);
        }
        assertThat(CombinationalLoops.exists(circuit())).isFalse();
        connect("n" + length, "not0", 0);
        nets.remove("n0");
        assertThat(CombinationalLoops.exists(circuit())).isTrue();
    }

    @Test
    void anEmptyCircuitHasNoLoop() {
        assertThat(CombinationalLoops.exists(FlatCircuit.EMPTY)).isFalse();
    }
}
