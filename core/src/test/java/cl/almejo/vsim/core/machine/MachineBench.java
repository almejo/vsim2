package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.MachineVisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A real circuit around one machine piece {@code m}: a switch per input pin (its id is the pin's name), a switch on
 * {@code RST} held at 0, a switch {@code clk} on {@code CLK} and an LED per output pin (id {@code led-<name>}), run
 * by a real {@link SimulationEngine}. What the test mode's run is compared with.
 */
final class MachineBench {

    private static final String MACHINE_ID = "maquina-1";
    private static final String PIECE_ID = "m";
    private static final String CLOCK_SWITCH = "clk";
    private static final String RESET_SWITCH = "rst";
    /** Long enough for every delay after an input change to settle. */
    private static final long SETTLE = 20;
    /** A switch starts at 1 by default; the bench starts every input at 0. */
    private static final Map<String, Object> SWITCH_OFF = Map.of("value", "0");
    private static final int WIRE_LENGTH = 6;

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();
    private final StateMachine machine;
    private int nextWire = 100;

    MachineBench(StateMachine machine) {
        this.machine = machine;
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        document.putDefinition(MachineDefinitions.create(MACHINE_ID, "Máquina 1").withMachine(machine));
        Component piece = new Component(new ElementId(PIECE_ID), SubcircuitTypes.typeIdOf(MACHINE_ID),
                new GridPoint(20, 0), Rotation.R0, Map.of(), "");
        document.add(piece);
        for (String input : machine.inputNames()) {
            attach(input, StandardComponentTypes.SWITCH, piece, input, -WIRE_LENGTH);
        }
        attach(RESET_SWITCH, StandardComponentTypes.SWITCH, piece, MachineLimits.RESET, -WIRE_LENGTH);
        attach(CLOCK_SWITCH, StandardComponentTypes.SWITCH, piece, MachineLimits.CLOCK, -WIRE_LENGTH);
        for (String output : machine.outputNames()) {
            attach(ledId(output), StandardComponentTypes.LED, piece, output, WIRE_LENGTH);
        }
        engine.update(new CircuitFlattener(registry).flatten(document, new NetlistCompiler(registry)
                .compile(document)));
        engine.advance(SETTLE);
    }

    /** Sets every input switch, by input name ({@code false} for an absent name), and lets the circuit settle. */
    void setInputs(Map<String, Boolean> valuesByName) {
        for (String input : machine.inputNames()) {
            set(input, valuesByName.getOrDefault(input, false));
        }
    }

    /** One clock pulse, up then down: the machine takes its edge on the way its {@code edge} says. */
    void pulse() {
        set(CLOCK_SWITCH, true);
        set(CLOCK_SWITCH, false);
    }

    /** The machine's state id now. */
    String stateId() {
        VisibleState state = engine.snapshot().componentState(PIECE_ID);
        return ((MachineVisibleState) state).stateId();
    }

    /** Every output's LED now, by output name; an LED that is not a clean 0 or 1 fails. */
    Map<String, Boolean> outputs() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (String output : machine.outputNames()) {
            Value value = ((VisibleState.LedState) engine.snapshot().componentState(ledId(output))).value();
            if (!value.isFullyDefined()) {
                throw new AssertionError("the LED of " + output + " shows " + value);
            }
            values.put(output, value.equals(Value.ONE));
        }
        return values;
    }

    private static String ledId(String output) {
        return "led-" + output.toLowerCase(Locale.ROOT);
    }

    private void set(String switchId, boolean value) {
        engine.setInput(switchId, value ? Value.ONE : Value.ZERO);
        engine.advance(SETTLE);
    }

    private void attach(String id, String typeId, Component piece, String pinName, int distance) {
        Map<String, Object> parameters = typeId.equals(StandardComponentTypes.SWITCH) ? SWITCH_OFF : Map.of();
        ComponentDefinition definition = document.definition(MACHINE_ID).orElseThrow();
        GridPoint pin = registry.typeOf(piece).pinLocation(piece, definition.pinIndexOf(pinName).orElseThrow());
        Component probe = registry.require(typeId).newComponent(new ElementId(id), GridPoint.ORIGIN, Rotation.R0,
                parameters, "");
        GridPoint offset = registry.typeOf(probe).pinLocation(probe, 0);
        GridPoint end = pin.translate(distance, 0);
        document.add(registry.require(typeId).newComponent(new ElementId(id),
                new GridPoint(end.x() - offset.x(), end.y() - offset.y()), Rotation.R0, parameters, ""));
        document.add(new Wire(new ElementId("w" + nextWire++), pin, end));
    }
}
