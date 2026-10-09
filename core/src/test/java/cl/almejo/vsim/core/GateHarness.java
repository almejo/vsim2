package cl.almejo.vsim.core;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Simulates a single standard component ("dut") with test sources wired to chosen pins. */
public final class GateHarness {

    private final ComponentType type;
    private final ComponentType sourceType;
    private final Map<String, Object> parameters;
    private final Map<Integer, Value> inputs = new TreeMap<>();
    private final SimulationEngine engine = new SimulationEngine();

    public GateHarness(String typeId) {
        this(typeId, Map.of());
    }

    public GateHarness(String typeId, Map<String, Object> parameters) {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        this.type = registry.require(typeId).specializedFor(parameters);
        this.sourceType = TestComponentTypes.registry().require(TestComponentTypes.SOURCE);
        this.parameters = parameters;
        rebuild();
    }

    public GateHarness set(int pinIndex, Value value) {
        inputs.put(pinIndex, value);
        rebuild();
        return this;
    }

    public GateHarness advance(long duration) {
        engine.advance(duration);
        return this;
    }

    public Value output(int pinIndex) {
        return engine.snapshot().netValue("net" + pinIndex);
    }

    public VisibleState state() {
        return engine.snapshot().componentState("dut");
    }

    public void interact() {
        engine.interact("dut");
    }

    private void rebuild() {
        List<FlatComponent> components = new ArrayList<>();
        components.add(new FlatComponent("dut", type, parameters));
        inputs.forEach((pinIndex, value) -> components.add(
                new FlatComponent("source" + pinIndex, sourceType, Map.of("value", value.toString()))));
        List<FlatNet> nets = new ArrayList<>();
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            List<FlatPinRef> pins = new ArrayList<>();
            pins.add(new FlatPinRef("dut", pinIndex));
            if (inputs.containsKey(pinIndex)) {
                pins.add(new FlatPinRef("source" + pinIndex, 0));
            }
            nets.add(new FlatNet("net" + pinIndex, pins, type.pin(pinIndex).width(), false));
        }
        engine.update(new FlatCircuit(components, nets));
    }
}
