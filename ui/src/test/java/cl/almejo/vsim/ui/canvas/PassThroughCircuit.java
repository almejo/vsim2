package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Two top-level nets that the simulation merges into one: switch "1" → wire left → pin a of a 1-bit pass-through
 * template (a joined to y inside) → pin y → wires rightAcross + rightDown → switch "0". Grid: high switch pin (4, 20),
 * instance box (10..14, 18..22) with a (10, 20) and y (14, 20), low switch pin (24, 30).
 */
public record PassThroughCircuit(Component high, Component instance, Component low, Wire left, Wire rightAcross,
                                 Wire rightDown) {

    public static final String DEFINITION_ID = "pass-through";

    /** input-pin c1 (pin at 2,2) touching output-pin c2 (pin at 2,2): a 1-bit wire through the box. */
    public static ComponentDefinition definition() {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        Component input = standard.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 1), Rotation.R0, Map.of(), "a");
        Component output = standard.require(InterfacePins.OUTPUT_PIN).newComponent(new ElementId("c2"),
                new GridPoint(2, 1), Rotation.R0, Map.of(), "y");
        return new ComponentDefinition(DEFINITION_ID, "Paso", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, output), List.of(), List.of()));
    }

    /** Adds the definition and the circuit to {@code openDocument}, one undoable step each. */
    public static PassThroughCircuit addTo(OpenDocument openDocument) {
        openDocument.execute(new PutDefinitionsCommand(List.of(definition())));
        Component high = UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 0, 19, Map.of("value", "1"));
        Component instance = UiTestSupport.place(openDocument, SubcircuitTypes.typeIdOf(DEFINITION_ID), 10, 18,
                Map.of());
        Component low = UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 20, 29, Map.of("value", "0"));
        return new PassThroughCircuit(high, instance, low, wire(openDocument, 4, 20, 10, 20),
                wire(openDocument, 14, 20, 24, 20), wire(openDocument, 24, 20, 24, 30));
    }

    private static Wire wire(OpenDocument openDocument, int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        openDocument.execute(new AddWireCommand(wire));
        return wire;
    }

    public String leftNetId(OpenDocument openDocument) {
        return openDocument.netlist().netForWire(left.id()).orElseThrow().id().value();
    }

    public String rightNetId(OpenDocument openDocument) {
        return openDocument.netlist().netForWire(rightAcross.id()).orElseThrow().id().value();
    }

    /** The id the simulation reports the merged net under (its FlatNet root: one of the two top-level ids). */
    public String rootNetId(OpenDocument openDocument) {
        String leftId = leftNetId(openDocument);
        String rightId = rightNetId(openDocument);
        return new CircuitFlattener(openDocument.registry())
                .flatten(openDocument.document(), openDocument.netlist()).nets().stream()
                .filter(net -> net.id().equals(leftId) || net.id().equals(rightId))
                .findFirst().orElseThrow().id();
    }

    /** A snapshot of the current netlist with the short circuit the engine reports for this circuit. */
    public SimulationSnapshot shortSnapshot(OpenDocument openDocument) {
        ShortCircuit shortCircuit = new ShortCircuit(rootNetId(openDocument), List.of(
                new DriverRef(high.id().value(), 0, Value.ONE), new DriverRef(low.id().value(), 0, Value.ZERO)), 5);
        return new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(), Map.of(),
                List.of(shortCircuit), Optional.empty());
    }
}
