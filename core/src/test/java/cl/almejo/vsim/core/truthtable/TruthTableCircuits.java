package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.GateParameters;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small circuits for the truth-table tests, drawn on the grid. Wires join only at their ends and at pins, so the
 * layouts keep every wire end and pin of one net off the wires of the others.
 */
final class TruthTableCircuits {

    final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    final CircuitDocument document = new CircuitDocument();

    Component place(String typeId, int x, int y, String label) {
        return place(typeId, x, y, label, Map.of());
    }

    Component place(String typeId, int x, int y, String label, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, label);
        document.add(component);
        return component;
    }

    GridPoint pin(Component component, int pinIndex) {
        return registry.typeOf(component).pinLocation(component, pinIndex);
    }

    /** A wire through {@code points}, one segment per pair of consecutive points. */
    void wire(GridPoint... points) {
        for (int index = 1; index < points.length; index++) {
            document.add(new Wire(document.newWireId(), points[index - 1], points[index]));
        }
    }

    /** Joins {@code source} to each target: a feed to the vertical lane at {@code laneX}, the lane, one branch each. */
    void fanOut(GridPoint source, int laneX, GridPoint... targets) {
        int top = source.y();
        int bottom = source.y();
        for (GridPoint target : targets) {
            top = Math.min(top, target.y());
            bottom = Math.max(bottom, target.y());
        }
        wire(source, new GridPoint(laneX, source.y()));
        if (top < bottom) {
            wire(new GridPoint(laneX, top), new GridPoint(laneX, bottom));
        }
        for (GridPoint target : targets) {
            wire(new GridPoint(laneX, target.y()), target);
        }
    }

    Set<ElementId> ids() {
        Set<ElementId> ids = new LinkedHashSet<>();
        for (DocumentElement element : document.elements()) {
            ids.add(element.id());
        }
        return ids;
    }

    /** Switches A (top) and B; S = A xor B and C = A and B on LEDs. */
    static TruthTableCircuits halfAdder() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component first = circuits.place(StandardComponentTypes.SWITCH, 0, 0, "A");
        Component second = circuits.place(StandardComponentTypes.SWITCH, 0, 10, "B");
        Component sum = circuits.place(StandardComponentTypes.XOR, 20, 0, "");
        Component carry = circuits.place(StandardComponentTypes.AND, 20, 10, "");
        circuits.fanOut(circuits.pin(first, 0), 8, circuits.pin(sum, 0), circuits.pin(carry, 0));
        circuits.fanOut(circuits.pin(second, 0), 12, circuits.pin(sum, 1), circuits.pin(carry, 1));
        circuits.place(StandardComponentTypes.LED, 23, -1, "S");
        circuits.place(StandardComponentTypes.LED, 23, 9, "C");
        return circuits;
    }

    /** Switches A, B and Cin; S = A xor B xor Cin and Cout = AB + ACin + BCin on LEDs. */
    static TruthTableCircuits fullAdder() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component first = circuits.place(StandardComponentTypes.SWITCH, 0, 0, "A");
        Component second = circuits.place(StandardComponentTypes.SWITCH, 0, 20, "B");
        Component carryIn = circuits.place(StandardComponentTypes.SWITCH, 0, 40, "Cin");
        Component sum = circuits.place(StandardComponentTypes.XOR, 30, 0, "", GateParameters.inputs(3));
        Component firstAndSecond = circuits.place(StandardComponentTypes.AND, 30, 10, "");
        Component firstAndCarry = circuits.place(StandardComponentTypes.AND, 30, 20, "");
        Component secondAndCarry = circuits.place(StandardComponentTypes.AND, 30, 30, "");
        Component carryOut = circuits.place(StandardComponentTypes.OR, 50, 10, "", GateParameters.inputs(3));
        circuits.fanOut(circuits.pin(first, 0), 8, circuits.pin(sum, 0), circuits.pin(firstAndSecond, 0),
                circuits.pin(firstAndCarry, 0));
        circuits.fanOut(circuits.pin(second, 0), 12, circuits.pin(sum, 1), circuits.pin(firstAndSecond, 1),
                circuits.pin(secondAndCarry, 0));
        circuits.fanOut(circuits.pin(carryIn, 0), 16, circuits.pin(sum, 2), circuits.pin(firstAndCarry, 1),
                circuits.pin(secondAndCarry, 1));
        circuits.wire(circuits.pin(firstAndSecond, 2), circuits.pin(carryOut, 1));
        circuits.wire(circuits.pin(firstAndCarry, 2), new GridPoint(44, 22), new GridPoint(44, 14),
                circuits.pin(carryOut, 2));
        circuits.wire(circuits.pin(secondAndCarry, 2), new GridPoint(40, 32), new GridPoint(40, 10),
                circuits.pin(carryOut, 0));
        circuits.place(StandardComponentTypes.LED, 33, -1, "S");
        circuits.place(StandardComponentTypes.LED, 53, 9, "Cout");
        return circuits;
    }

    /** Adds switch «Z» wired to LED «W» below everything else: a part a selection can leave out. */
    void addUnrelatedPart() {
        Component source = place(StandardComponentTypes.SWITCH, 0, 60, "Z");
        place(StandardComponentTypes.LED, 5, 58, "W");
        wire(pin(source, 0), new GridPoint(6, 61));
    }

    /** One instance of {@link ExampleDefinitions#inverter()} (pins «a» and «y»); returns the instance. */
    Component inverterInstance() {
        document.putDefinition(ExampleDefinitions.inverter());
        Component instance = new Component(document.newComponentId(),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(10, 0), Rotation.R0, Map.of(),
                "");
        document.add(instance);
        return instance;
    }

    /** A 2-bit input pin «D» touching a 2-bit output pin «Q». */
    static TruthTableCircuits busPassThrough() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Map<String, Object> twoBits = Map.of(InterfacePins.WIDTH, 2);
        circuits.place(InterfacePins.INPUT_PIN, 0, 0, "D", twoBits);
        circuits.place(InterfacePins.OUTPUT_PIN, 2, 0, "Q", twoBits);
        return circuits;
    }

    /** An 8-bit Switch «A» whose pin touches the pin of an 8-bit LED «Y». */
    static TruthTableCircuits eightBitSwitchToLed() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Map<String, Object> eightBits = Map.of(StandardComponentTypes.WIDTH, 8);
        Component input = circuits.place(StandardComponentTypes.SWITCH, 0, 0, "A", eightBits);
        Component output = circuits.place(StandardComponentTypes.LED, 20, 0, "Y", eightBits);
        circuits.wire(circuits.pin(input, 0), circuits.pin(output, 0));
        return circuits;
    }

    /** Switch «A» and LED «Y», not connected. */
    static TruthTableCircuits disconnectedLed() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        circuits.place(StandardComponentTypes.SWITCH, 0, 0, "A");
        circuits.place(StandardComponentTypes.LED, 20, 0, "Y");
        return circuits;
    }

    /** Switches «A» and «B» on one net, read by LED «Y». */
    static TruthTableCircuits shortedSwitches() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component first = circuits.place(StandardComponentTypes.SWITCH, 0, 0, "A");
        Component second = circuits.place(StandardComponentTypes.SWITCH, 0, 4, "B");
        circuits.wire(circuits.pin(first, 0), circuits.pin(second, 0));
        circuits.place(StandardComponentTypes.LED, 3, 0, "Y");
        return circuits;
    }

    /**
     * NAND(A, feedback) → NOT → NOT → back into the NAND; LED «Y» on the NAND output. A = 0 holds Y at 1; A = 1
     * makes three inversions in a ring, which oscillates.
     */
    static TruthTableCircuits gatedRing() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component enable = circuits.place(StandardComponentTypes.SWITCH, 0, 0, "A");
        Component gate = circuits.place(StandardComponentTypes.AND, 10, 0, "", GateParameters.NEGATED);
        circuits.place(StandardComponentTypes.BUFFER, 14, 0, "");
        Component last = circuits.place(StandardComponentTypes.BUFFER, 18, 0, "");
        circuits.wire(circuits.pin(enable, 0), new GridPoint(6, 1), new GridPoint(6, 0), circuits.pin(gate, 0));
        circuits.wire(circuits.pin(last, 1), new GridPoint(24, 2), new GridPoint(24, 8), new GridPoint(8, 8),
                new GridPoint(8, 4), circuits.pin(gate, 1));
        circuits.place(StandardComponentTypes.LED, 13, -1, "Y");
        return circuits;
    }

    /**
     * Cross-coupled NOR gates: Q = NOR(R, Qn) on top, Qn = NOR(S, Q) below; switches «R» (top) and «S», LEDs «Q» (top)
     * and «Qn». Feedback without flip-flops.
     */
    static TruthTableCircuits norLatch() {
        TruthTableCircuits circuits = new TruthTableCircuits();
        Component reset = circuits.place(StandardComponentTypes.SWITCH, 0, 0, "R");
        Component set = circuits.place(StandardComponentTypes.SWITCH, 0, 20, "S");
        Component top = circuits.place(StandardComponentTypes.OR, 10, 0, "", GateParameters.NEGATED);
        Component bottom = circuits.place(StandardComponentTypes.OR, 10, 20, "", GateParameters.NEGATED);
        circuits.wire(circuits.pin(reset, 0), new GridPoint(6, 1), new GridPoint(6, 0), circuits.pin(top, 0));
        circuits.wire(circuits.pin(set, 0), new GridPoint(6, 21), new GridPoint(6, 24), circuits.pin(bottom, 1));
        circuits.wire(circuits.pin(top, 2), new GridPoint(16, 2), new GridPoint(16, 12), new GridPoint(8, 12),
                new GridPoint(8, 20), circuits.pin(bottom, 0));
        circuits.wire(circuits.pin(bottom, 2), new GridPoint(18, 22), new GridPoint(18, 8), new GridPoint(8, 8),
                new GridPoint(8, 4), circuits.pin(top, 1));
        circuits.place(StandardComponentTypes.LED, 15, -1, "Q");
        circuits.place(StandardComponentTypes.LED, 17, 19, "Qn");
        return circuits;
    }

    /** Puts a definition «pulso» whose body is a clock driving its only pin, and returns one instance of it. */
    Component clockTemplateInstance() {
        Component clock = registry.require(StandardComponentTypes.CLOCK).newComponent(new ElementId("c1"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        Component output = registry.require(InterfacePins.OUTPUT_PIN).newComponent(new ElementId("c2"),
                new GridPoint(2, 1), Rotation.R0, Map.of(), "y");
        document.putDefinition(new ComponentDefinition("pulse", "Pulso", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(clock, output), List.of(), List.of())));
        Component instance = new Component(document.newComponentId(), SubcircuitTypes.typeIdOf("pulse"),
                new GridPoint(10, 0), Rotation.R0, Map.of(), "");
        document.add(instance);
        return instance;
    }
}
