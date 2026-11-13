package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;

/**
 * The circuits of the signal card's tests, built pin to pin with straight wires: the mockup's example (switches A
 * and B into AND «U1», whose output goes to LED «L1» and NOT «U2») and a net through tunnel «LISTO».
 */
final class NetCardCircuits {

    private static final Map<String, Object> NOT = Map.of(StandardComponentTypes.NEGATE, true);

    private NetCardCircuits() {
    }

    /** The AND example; {@code andOutput} is the first wire of the AND's output net. */
    record AndExample(Component switchA, Component switchB, Component and, Component ledL1, Component not,
                      Wire andOutput, Wire notInput, Wire inputA) {
    }

    /** A net from the AND «U1» into tunnel «LISTO», and on the far side from the «LISTO» exit to LED «L3». */
    record TunnelExample(Component and, Component entrance, Component exit, Component ledL3, Wire nearWire,
                         Wire farWire) {
    }

    static AndExample andExample(OpenDocument document) {
        Component and = placeWithPinAt(document, StandardComponentTypes.AND, Map.of(), "U1", 2, 30, 10);
        ComponentType andType = document.registry().typeOf(and);
        GridPoint firstInput = andType.pinLocation(and, 0);
        GridPoint secondInput = andType.pinLocation(and, 1);
        Component switchA = placeWithPinAt(document, StandardComponentTypes.SWITCH, Map.of("value", "1"), "A", 0,
                firstInput.x() - 6, firstInput.y());
        Component switchB = placeWithPinAt(document, StandardComponentTypes.SWITCH, Map.of("value", "1"), "B", 0,
                secondInput.x() - 6, secondInput.y());
        Wire inputA = addWire(document, firstInput.x() - 6, firstInput.y(), firstInput.x(), firstInput.y());
        addWire(document, secondInput.x() - 6, secondInput.y(), secondInput.x(), secondInput.y());
        Component ledL1 = placeWithPinAt(document, StandardComponentTypes.LED, Map.of(), "L1", 0, 40, 10);
        Component not = placeWithPinAt(document, StandardComponentTypes.BUFFER, NOT, "U2", 0, 40, 20);
        Wire andOutput = addWire(document, 30, 10, 35, 10);
        addWire(document, 35, 10, 40, 10);
        addWire(document, 35, 10, 35, 20);
        Wire notInput = addWire(document, 35, 20, 40, 20);
        return new AndExample(switchA, switchB, and, ledL1, not, andOutput, notInput, inputA);
    }

    static TunnelExample tunnelExample(OpenDocument document) {
        Component and = placeWithPinAt(document, StandardComponentTypes.AND, Map.of(), "U1", 2, 30, 10);
        Component entrance = placeWithPinAt(document, StandardComponentTypes.TUNNEL_ENTRANCE,
                Map.of(Tunnels.NAME, "LISTO"), "", 0, 40, 10);
        Wire nearWire = addWire(document, 30, 10, 40, 10);
        Component exit = placeWithPinAt(document, StandardComponentTypes.TUNNEL_EXIT,
                Map.of(Tunnels.NAME, "LISTO"), "", 0, 30, 40);
        Component ledL3 = placeWithPinAt(document, StandardComponentTypes.LED, Map.of(), "L3", 0, 40, 40);
        Wire farWire = addWire(document, 30, 40, 40, 40);
        return new TunnelExample(and, entrance, exit, ledL3, nearWire, farWire);
    }

    /** Places a {@code typeId} part labeled {@code label} with its pin {@code pinIndex} at ({@code x}, {@code y}). */
    static Component placeWithPinAt(OpenDocument document, String typeId, Map<String, Object> parameters,
                                    String label, int pinIndex, int x, int y) {
        ComponentType type = document.registry().require(typeId);
        Component probe = type.newComponent(document.document().newComponentId(), new GridPoint(0, 0), Rotation.R0,
                parameters, label);
        GridPoint pin = type.pinLocation(probe, pinIndex);
        Component placed = type.newComponent(document.document().newComponentId(),
                new GridPoint(x - pin.x(), y - pin.y()), Rotation.R0, parameters, label);
        document.execute(new AddElementsCommand(List.of(placed)));
        return placed;
    }

    /** A snapshot of the current netlist with {@code values} by net id. */
    static SimulationSnapshot snapshot(OpenDocument document, Map<String, Value> values) {
        return new SimulationSnapshot(1, document.netlistToken(), 10, true, values, Map.of(), List.of(),
                Optional.empty());
    }

    /** The net id of {@code wire} in the current netlist. */
    static String netOf(OpenDocument document, Wire wire) {
        return document.netlist().netForWire(wire.id()).orElseThrow().id().value();
    }
}
