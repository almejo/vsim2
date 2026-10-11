package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WireCrossingsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    private Component notGate(int x, int y, Rotation rotation) {
        Component gate = registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                new GridPoint(x, y), rotation, Map.of(), "");
        document.add(gate);
        return gate;
    }

    private GridPoint pin(Component component, int pinIndex) {
        ComponentType type = registry.typeOf(component);
        return type.pinLocation(component, pinIndex);
    }

    @Test
    void twoWiresThatCrossWithoutAnInterestPointMakeOneCrossing() {
        Wire horizontal = wire(0, 5, 10, 5);
        Wire vertical = wire(5, 0, 5, 10);
        assertThat(WireCrossings.of(document, registry))
                .containsExactly(new Crossing(new GridPoint(5, 5), horizontal.id(), vertical.id()));
    }

    @Test
    void aTeeIsAConnectionNotACrossing() {
        wire(0, 5, 10, 5);
        wire(5, 5, 5, 10);
        assertThat(WireCrossings.of(document, registry)).isEmpty();
    }

    @Test
    void aHorizontalEndOnTheInteriorOfAVerticalIsAConnection() {
        wire(0, 5, 5, 5);
        wire(5, 0, 5, 10);
        assertThat(WireCrossings.of(document, registry)).isEmpty();
    }

    @Test
    void theCornerOfAnLIsNotACrossing() {
        wire(0, 5, 5, 5);
        wire(5, 5, 5, 10);
        assertThat(WireCrossings.of(document, registry)).isEmpty();
    }

    @Test
    void anEndOfAThirdWireAtThePointConnectsThem() {
        wire(0, 5, 10, 5);
        wire(5, 0, 5, 10);
        wire(5, 5, 8, 5);
        assertThat(WireCrossings.of(document, registry)).isEmpty();
    }

    @Test
    void wiresCrossingOnAPinLocationAreConnected() {
        GridPoint input = pin(notGate(10, 10, Rotation.R0), 0);
        wire(input.x() - 3, input.y(), input.x() + 3, input.y());
        wire(input.x(), input.y() - 3, input.x(), input.y() + 3);
        assertThat(WireCrossings.of(document, registry)).isEmpty();
    }

    @Test
    void collinearOverlappingWiresDoNotCross() {
        wire(0, 5, 10, 5);
        wire(3, 5, 12, 5);
        wire(20, 0, 20, 10);
        wire(20, 4, 20, 12);
        assertThat(WireCrossings.of(document, registry)).isEmpty();
    }

    @Test
    void overlappingCollinearHorizontalsCrossAVerticalOnlyOnce() {
        Wire first = wire(0, 5, 10, 5);
        Wire second = wire(3, 5, 12, 5);
        Wire vertical = wire(6, 0, 6, 10);

        List<Crossing> crossings = WireCrossings.of(document, registry);

        assertThat(crossings).hasSize(1);
        assertThat(crossings.get(0).point()).isEqualTo(new GridPoint(6, 5));
        assertThat(crossings.get(0).verticalWire()).isEqualTo(vertical.id());
        assertThat(crossings.get(0).horizontalWire()).isIn(first.id(), second.id());
    }

    @Test
    void overlappingCollinearVerticalsEachKeepTheirCrossing() {
        Wire horizontal = wire(0, 5, 10, 5);
        Wire upper = wire(6, 0, 6, 8);
        Wire lower = wire(6, 2, 6, 10);

        assertThat(WireCrossings.of(document, registry)).containsExactlyInAnyOrder(
                new Crossing(new GridPoint(6, 5), horizontal.id(), upper.id()),
                new Crossing(new GridPoint(6, 5), horizontal.id(), lower.id()));
    }

    @Test
    void aHorizontalCrossedByTwoVerticalsGivesTwoCrossingsInOrder() {
        Wire horizontal = wire(0, 5, 20, 5);
        Wire right = wire(15, 0, 15, 10);
        Wire left = wire(5, 0, 5, 10);
        assertThat(WireCrossings.of(document, registry)).containsExactly(
                new Crossing(new GridPoint(5, 5), horizontal.id(), left.id()),
                new Crossing(new GridPoint(15, 5), horizontal.id(), right.id()));
    }

    @Test
    void theRotatedPinLocationIsTheOneThatConnects() {
        Component rotated = notGate(10, 10, Rotation.R90);
        GridPoint rotatedPin = pin(rotated, 0);
        GridPoint unrotatedPin = registry.typeOf(rotated).pinLocation(rotated.withRotation(Rotation.R0), 0);
        assertThat(rotatedPin).isNotEqualTo(unrotatedPin);

        // one unit each way, so the two small crosses (two units apart) do not cross each other
        wire(rotatedPin.x() - 1, rotatedPin.y(), rotatedPin.x() + 1, rotatedPin.y());
        wire(rotatedPin.x(), rotatedPin.y() - 1, rotatedPin.x(), rotatedPin.y() + 1);
        Wire horizontal = wire(unrotatedPin.x() - 1, unrotatedPin.y(), unrotatedPin.x() + 1, unrotatedPin.y());
        Wire vertical = wire(unrotatedPin.x(), unrotatedPin.y() - 1, unrotatedPin.x(), unrotatedPin.y() + 1);

        assertThat(WireCrossings.of(document, registry))
                .containsExactly(new Crossing(unrotatedPin, horizontal.id(), vertical.id()));
    }
}
