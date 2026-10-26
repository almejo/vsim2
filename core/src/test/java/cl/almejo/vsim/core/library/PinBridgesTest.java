package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.place;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class PinBridgesTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static List<ElementId> ids(Component... components) {
        return Arrays.stream(components).map(Component::id).toList();
    }

    /** The bridging for replacing {@code selection} by an instance at the selection's top-left. */
    private PinBridges.Bridging bridging(List<ElementId> selection, PinOrder order) {
        ComponentDefinition definition = DefinitionBuilder.fromSelection(document, selection, "box", "Box", order,
                registry).root();
        return PinBridges.wires(document, selection, definition,
                DefinitionBuilder.origin(document, selection, registry), registry);
    }

    private PinBridges.Bridging bridging(List<ElementId> selection) {
        return bridging(selection, DefinitionBuilder.defaultPinOrder(document, selection));
    }

    private List<Wire> bridges(List<ElementId> selection) {
        PinBridges.Bridging bridging = bridging(selection);
        assertThat(bridging.unconnectedPins()).isEmpty();
        assertThat(bridging.blockedPins()).isEmpty();
        return bridging.wires();
    }

    private static List<String> segments(List<Wire> wires) {
        return wires.stream().map(wire -> wire.start() + "-" + wire.end()).toList();
    }

    private static String segment(int x1, int y1, int x2, int y2) {
        return new Wire(new ElementId("w"), new GridPoint(x1, y1), new GridPoint(x2, y2)).start() + "-"
                + new Wire(new ElementId("w"), new GridPoint(x1, y1), new GridPoint(x2, y2)).end();
    }

    /** input-pin "a" at (10,11) (point (12,12)) → NOT (12,10) → output-pin "y" at (16,11) (point (16,12)). */
    private List<ElementId> inverter() {
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 10, 11, "a");
        Component gate = place(registry, document, StandardComponentTypes.NOT, 12, 10, "");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 16, 11, "y");
        return ids(input, gate, output);
    }

    @Test
    void anOutsideWireOnAnInterfacePinGetsABridgeToTheInstancePin() {
        List<ElementId> selection = inverter();
        wire(document, 12, 4, 12, 12);
        wire(document, 16, 12, 20, 12);

        List<Wire> bridges = bridges(selection);

        // the instance at (10,10) has its input at (10,12); its output (16,12) is already where the wire ends
        assertThat(segments(bridges)).containsExactly(segment(10, 12, 12, 12));
        assertThat(bridges).extracting(Wire::id).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(document.element(id)).isEmpty());
    }

    @Test
    void pinsWithNothingOutsideOrAWireThroughTheInstancePinNeedNoBridge() {
        List<ElementId> selection = inverter();
        assertThat(bridges(selection)).isEmpty();

        wire(document, 4, 12, 12, 12);

        assertThat(bridges(selection)).isEmpty();
    }

    @Test
    void anOutsidePinTouchingAnInterfacePinCountsAsAConnection() {
        List<ElementId> selection = inverter();
        place(registry, document, StandardComponentTypes.LED, 11, 9, ""); // its input pin sits on (12,12)

        assertThat(segments(bridges(selection))).containsExactly(segment(10, 12, 12, 12));
    }

    @Test
    void aBendGoesVerticalFirstUnlessThatRouteTouchesSomethingElse() {
        // points (2,1), (2,5) and (6,1) → instance pins (0,2), (0,4) and (6,2)
        Component first = place(registry, document, InterfacePins.INPUT_PIN, 0, 0, "a");
        Component second = place(registry, document, InterfacePins.INPUT_PIN, 0, 4, "b");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 6, 0, "y");
        List<ElementId> selection = ids(first, second, output);
        wire(document, 2, -4, 2, 1);
        wire(document, 2, 5, 2, 10);
        wire(document, 6, 1, 12, 1);
        wire(document, 1, 2, 1, 3); // a stub ending on the first route of "a": (2,1)-(2,2)-(0,2)

        assertThat(segments(bridges(selection))).containsExactly(
                segment(0, 1, 2, 1), segment(0, 1, 0, 2),
                segment(2, 4, 2, 5), segment(0, 4, 2, 4),
                segment(6, 1, 6, 2));
    }

    @Test
    void aPinWithoutAClearRouteIsLeftAloneAndReported() {
        Component first = place(registry, document, InterfacePins.INPUT_PIN, 0, 0, "a");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 6, 0, "y");
        List<ElementId> selection = ids(first, output);
        wire(document, 2, -4, 2, 1);
        wire(document, 1, 2, 1, 3);   // blocks (2,1)-(2,2)-(0,2)
        wire(document, -3, 1, 0, 1);  // blocks (2,1)-(0,1)-(0,2)

        assertThat(bridging(selection)).isEqualTo(new PinBridges.Bridging(List.of(), List.of("a"), List.of()));
    }

    @Test
    void anInstancePinOnAnotherPinsOutsideNetIsBlockedAndNeverBridgedIntoIt() {
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 0, 0, "a");
        // points (6,4) and (6,6) → instance pins (6,2) and (6,4)
        Component first = place(registry, document, InterfacePins.OUTPUT_PIN, 6, 3, "y1");
        Component second = place(registry, document, InterfacePins.OUTPUT_PIN, 6, 5, "y2");
        wire(document, 6, 4, 12, 4);
        wire(document, 6, 6, 12, 6);

        // "y2" lands on y1's outside wire (blocked), so y1's net already reaches the wrong pin: no bridge for y1
        assertThat(bridging(ids(input, first, second)))
                .isEqualTo(new PinBridges.Bridging(List.of(), List.of("y1"), List.of("y2")));
    }

    @Test
    void anInstancePinLandingOnAnUnrelatedOutsideWireIsBlocked() {
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 10, 11, "a");
        Component gate = place(registry, document, StandardComponentTypes.NOT, 12, 10, "");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 16, 11, "y");
        wire(document, 10, 12, 10, 20); // runs down from where the instance input will be; nothing touched "a" before

        assertThat(bridging(ids(input, gate, output)))
                .isEqualTo(new PinBridges.Bridging(List.of(), List.of(), List.of("a")));
    }

    @Test
    void swappedInputsGetWhatRoutesFitAndTheRestIsReported() {
        Component first = place(registry, document, InterfacePins.INPUT_PIN, 0, 0, "a");   // point (2,1)
        Component second = place(registry, document, InterfacePins.INPUT_PIN, 0, 4, "b");  // point (2,5)
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 6, 0, "y");
        wire(document, 2, -4, 2, 1);
        wire(document, 2, 5, 2, 10);

        // b now goes first, to (0,2); a goes to (0,4) and every route of a crosses b's bridge
        PinBridges.Bridging bridging = bridging(ids(first, second, output),
                new PinOrder(List.of(second.id(), first.id()), List.of(output.id())));

        assertThat(segments(bridging.wires())).containsExactly(segment(2, 2, 2, 5), segment(0, 2, 2, 2));
        assertThat(bridging.unconnectedPins()).containsExactly("a");
    }

    @Test
    void busPinsAreBridgedLikeOneBitPins() {
        Map<String, Object> eightBits = Map.of(InterfacePins.WIDTH, 8);
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(document.newComponentId(),
                new GridPoint(10, 11), Rotation.R0, eightBits, "a"); // pin at (12, 12)
        Component output = registry.require(InterfacePins.OUTPUT_PIN).newComponent(document.newComponentId(),
                new GridPoint(16, 11), Rotation.R0, eightBits, "y"); // pin at (16, 12)
        document.add(input);
        document.add(output);
        Wire inside = new Wire(document.newWireId(), new GridPoint(12, 12), new GridPoint(16, 12));
        document.add(inside);
        wire(document, 12, 4, 12, 12);
        wire(document, 16, 12, 20, 12);
        List<ElementId> selection = List.of(input.id(), output.id(), inside.id());

        // the box goes at the selection's top-left (10, 11): pin a at (10, 13), pin y at (16, 13)
        assertThat(segments(bridges(selection))).containsExactly(segment(12, 12, 12, 13), segment(12, 13, 10, 13),
                segment(16, 12, 16, 13));
    }
}
