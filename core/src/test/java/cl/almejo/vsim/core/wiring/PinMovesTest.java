package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pin maps must be planned together, including swaps between two separately connected pins. */
class PinMovesTest {
    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void swappingTwoConnectedPinsPreservesBothNetsWithoutJoiningThem() {
        Component first = gate(10, 0);
        Component second = gate(10, 8);
        Component upper = gate(30, 0);
        Component lower = gate(30, 8);
        wire(14, 2, 30, 2);
        wire(14, 10, 30, 10);
        PinRef firstPin = new PinRef(first.id(), 1);
        PinRef secondPin = new PinRef(second.id(), 1);
        Map<PinRef, GridPoint> moved = Map.of(firstPin, new GridPoint(14, 10),
                secondPin, new GridPoint(14, 2));

        FollowPlan plan = WireFollow.plan(document, registry, new PinMoves(moved), document::newWireId);

        assertThat(plan.newContacts()).isEmpty();
        PointNets after = new PointNets(points(moved), afterWires(plan));
        assertThat(after.find(after.pinPoint(firstPin)))
                .isEqualTo(after.find(after.pinPoint(new PinRef(upper.id(), 0))));
        assertThat(after.find(after.pinPoint(secondPin)))
                .isEqualTo(after.find(after.pinPoint(new PinRef(lower.id(), 0))));
        assertThat(after.find(after.pinPoint(firstPin))).isNotEqualTo(after.find(after.pinPoint(secondPin)));
    }

    @Test
    void aPinLandingOnAFixedForeignWireReportsTheContact() {
        Component first = gate(0, 0);
        Component second = gate(30, 0);
        wire(34, 2, 40, 2);
        PinRef pin = new PinRef(first.id(), 1);

        FollowPlan plan = WireFollow.plan(document, registry,
                new PinMoves(Map.of(pin, new GridPoint(37, 2))), document::newWireId);

        assertThat(plan.newContacts()).isNotEmpty();
        assertThat(document.wires()).hasSize(1);
        assertThat(registry.typeOf(second).pinLocation(second, 1)).isEqualTo(new GridPoint(34, 2));
    }

    @Test
    void movingAPinFromTheInteriorOfAWireAddsABridgeAndKeepsItsJunction() {
        Component first = gate(10, 0);
        Component second = gate(30, 0);
        wire(0, 2, 30, 2);
        PinRef pin = new PinRef(first.id(), 1);
        Map<PinRef, GridPoint> moved = Map.of(pin, new GridPoint(14, 6));

        FollowPlan plan = WireFollow.plan(document, registry, new PinMoves(moved), document::newWireId);

        assertThat(plan.newContacts()).isEmpty();
        PointNets after = new PointNets(points(moved), afterWires(plan));
        assertThat(after.find(after.pinPoint(pin)))
                .isEqualTo(after.find(after.pinPoint(new PinRef(second.id(), 0))));
        assertThat(plan.removedWireIds()).isEmpty();
    }

    @Test
    void touchingAnotherTunnelOfTheSameElectricalNetIsNotAForeignContact() {
        Component first = gate(10, 0);
        Component entrance = new Component(document.newComponentId(), StandardComponentTypes.TUNNEL_ENTRANCE,
                new GridPoint(14, 1), Rotation.R0, Map.of("name", "CLK"), "");
        Component exit = new Component(document.newComponentId(), StandardComponentTypes.TUNNEL_EXIT,
                new GridPoint(30, 1), Rotation.R0, Map.of("name", "CLK"), "");
        document.add(entrance);
        document.add(exit);
        PinRef pin = new PinRef(first.id(), 1);
        GridPoint destination = registry.typeOf(exit).pinLocation(exit, 0);

        FollowPlan plan = WireFollow.plan(document, registry, new PinMoves(Map.of(pin, destination)),
                document::newWireId);

        assertThat(plan.newContacts()).isEmpty();
    }

    private Component gate(int x, int y) {
        Component component = new Component(document.newComponentId(), StandardComponentTypes.NOT,
                new GridPoint(x, y), Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    private void wire(int x1, int y1, int x2, int y2) {
        document.add(new Wire(document.newWireId(), new GridPoint(x1, y1), new GridPoint(x2, y2)));
    }

    private Map<PinRef, GridPoint> points(Map<PinRef, GridPoint> moved) {
        Map<PinRef, GridPoint> result = new LinkedHashMap<>();
        for (Component component : document.components()) {
            for (int pinIndex = 0; pinIndex < registry.typeOf(component).pinCount(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                result.put(pin, moved.getOrDefault(pin, registry.typeOf(component).pinLocation(component, pinIndex)));
            }
        }
        return result;
    }

    private List<Wire> afterWires(FollowPlan plan) {
        Map<ElementId, Wire> wires = new LinkedHashMap<>();
        document.wires().forEach(wire -> wires.put(wire.id(), wire));
        plan.removedWireIds().forEach(wires::remove);
        plan.replacedWires().forEach(wire -> wires.put(wire.id(), wire));
        plan.addedWires().forEach(wire -> wires.put(wire.id(), wire));
        return new ArrayList<>(wires.values());
    }
}
