package cl.almejo.vsim.core;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simulates one standard block ("dut") of any width: every input pin (except the ones left unconnected) is driven by
 * an input pin of its width ("source&lt;pin&gt;", all zeros at the start) plus a rival one ("rival&lt;pin&gt;", all Z) that
 * only drives to make a conflict. Each dut pin has its own net "net&lt;pin&gt;". Values are set at once through
 * {@link SimulationEngine#setInput}; the block reacts after its delay, so call {@link #settle} or {@link #advance}.
 */
public final class BlockHarness {

    /** Longer than any block's default delay chain; nothing in a lone block runs this long. */
    private static final long SETTLE_TIME = 1_000;

    private final ComponentType type;
    private final Map<String, Object> parameters;
    private final Set<Integer> unconnected = new HashSet<>();
    private final SimulationEngine engine = new SimulationEngine();
    private final FlatCircuit circuit;

    public BlockHarness(String typeId, Map<String, Object> parameters, int... unconnectedPins) {
        this.type = StandardComponentTypes.registry().require(typeId).specializedFor(parameters);
        this.parameters = parameters;
        for (int pinIndex : unconnectedPins) {
            unconnected.add(pinIndex);
        }
        this.circuit = buildCircuit();
        engine.update(circuit);
    }

    public ComponentType type() {
        return type;
    }

    /** Drives {@code value} on input pin {@code pinIndex} from now on (zero delay). */
    public BlockHarness set(int pinIndex, Value value) {
        // The source first: leaving a conflict never shows the source's old value on its own for an instant.
        engine.setInput("source" + pinIndex, value);
        engine.setInput("rival" + pinIndex, Value.highImpedance(width(pinIndex)));
        return this;
    }

    /** Drives the unsigned {@code bits} on input pin {@code pinIndex}, as wide as the pin. */
    public BlockHarness set(int pinIndex, long bits) {
        return set(pinIndex, Value.of(width(pinIndex), bits));
    }

    /** Stops driving input pin {@code pinIndex}: its net reads all Z, like an unconnected pin. */
    public BlockHarness release(int pinIndex) {
        return set(pinIndex, Value.highImpedance(width(pinIndex)));
    }

    /**
     * Two drivers disagree on every bit of input pin {@code pinIndex} (a short circuit). The rival drives zeros first,
     * so a pin that was at 0 goes straight to the conflict without a passing 1 (which would be a CLR pulse).
     */
    public BlockHarness conflict(int pinIndex) {
        int width = width(pinIndex);
        engine.setInput("rival" + pinIndex, Value.of(width, 0L));
        engine.setInput("source" + pinIndex, Value.of(width, Value.maskFor(width)));
        return this;
    }

    public BlockHarness advance(long duration) {
        engine.advance(duration);
        return this;
    }

    public BlockHarness settle() {
        return advance(SETTLE_TIME);
    }

    /** «Reiniciar»: the circuit from scratch at time 0; the sources keep their values. */
    public BlockHarness restart() {
        engine.restart(circuit, Map.of());
        return this;
    }

    /** The value on the net of pin {@code pinIndex} (inputs and outputs alike). */
    public Value output(int pinIndex) {
        return engine.snapshot().netValue("net" + pinIndex);
    }

    public VisibleState state() {
        return engine.snapshot().componentState("dut");
    }

    public long now() {
        return engine.now();
    }

    private int width(int pinIndex) {
        return type.pin(pinIndex).width();
    }

    private FlatCircuit buildCircuit() {
        List<FlatComponent> components = new ArrayList<>();
        components.add(new FlatComponent("dut", type, parameters));
        List<FlatNet> nets = new ArrayList<>();
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            PinSpec pin = type.pin(pinIndex);
            List<FlatPinRef> pins = new ArrayList<>();
            pins.add(new FlatPinRef("dut", pinIndex));
            if (pin.isInput() && !pin.isDriver() && !unconnected.contains(pinIndex)) {
                components.add(source("source" + pinIndex, pin.width(), "0"));
                components.add(source("rival" + pinIndex, pin.width(), "Z"));
                pins.add(new FlatPinRef("source" + pinIndex, 0));
                pins.add(new FlatPinRef("rival" + pinIndex, 0));
            }
            nets.add(new FlatNet("net" + pinIndex, pins, pin.width(), false));
        }
        return new FlatCircuit(components, nets);
    }

    private static FlatComponent source(String id, int width, String value) {
        Map<String, Object> sourceParameters = Map.of("value", value, InterfacePins.WIDTH, width);
        ComponentType sourceType = StandardComponentTypes.registry().require(InterfacePins.INPUT_PIN)
                .specializedFor(sourceParameters);
        return new FlatComponent(id, sourceType, sourceParameters);
    }
}
