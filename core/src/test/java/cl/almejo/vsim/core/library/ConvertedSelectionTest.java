package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertedSelectionTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<ElementId> selection = new ArrayList<>();

    ConvertedSelectionTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    private Component place(String typeId, int x, int y, String label) {
        return place(typeId, x, y, label, Map.of());
    }

    private Component place(String typeId, int x, int y, String label, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, label);
        document.add(component);
        selection.add(component.id());
        return component;
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        selection.add(wire.id());
        return wire;
    }

    /** Switch «Cin» (0, 1) → NOT (6, 0) → LED «S» (11, -1): the switch's pin at (4, 2), the LED's at (12, 2). */
    private List<Component> drawSwitchNotLed() {
        Component input = place(StandardComponentTypes.SWITCH, 0, 1, "Cin", Map.of("value", "1"));
        wire(4, 2, 6, 2);
        Component gate = place(StandardComponentTypes.NOT, 6, 0, "");
        wire(10, 2, 12, 2);
        Component led = place(StandardComponentTypes.LED, 11, -1, "S");
        return List.of(input, gate, led);
    }

    private ConvertedSelection convert(Collection<ElementId> converted) {
        return convert(converted, Map.of());
    }

    private ConvertedSelection convert(Collection<ElementId> converted, Map<ElementId, Value> currentValues) {
        return ConvertedSelection.of(document, selection, Set.copyOf(converted), currentValues, registry);
    }

    private Set<ElementId> allParts() {
        Set<ElementId> parts = new HashSet<>();
        TestParts.of(document, selection, registry).forEach(part -> parts.add(part.id()));
        return parts;
    }

    private static List<Component> selected(ConvertedSelection converted, String typeId) {
        return converted.document().components().stream()
                .filter(component -> converted.ids().contains(component.id()))
                .filter(component -> component.typeId().equals(typeId))
                .toList();
    }

    private GridPoint pinPoint(Component component, int pinIndex) {
        return registry.typeOf(component).pinLocation(component, pinIndex);
    }

    private Map<ElementId, DocumentElement> elementsOf(CircuitDocument source) {
        Map<ElementId, DocumentElement> elements = new HashMap<>();
        source.elements().forEach(element -> elements.put(element.id(), element));
        return elements;
    }

    @Test
    void theTestPartsAreListedByPositionWithTheirLabelAndTheConstantsValue() {
        Component led = place(StandardComponentTypes.LED, 0, 20, "");
        Component carry = place(StandardComponentTypes.CONSTANT, 0, 0, " Cin ", Map.of("width", 1, "value", "0"));
        Component gate = place(StandardComponentTypes.NOT, 10, 0, "");
        Component pin = place(InterfacePins.INPUT_PIN, 0, 5, "a");
        Component clock = place(StandardComponentTypes.CLOCK, 0, 10, "CLK");

        List<TestParts.TestPart> parts = TestParts.of(document, selection, registry);

        assertThat(parts).extracting(TestParts.TestPart::id).containsExactly(carry.id(), clock.id(), led.id());
        assertThat(parts.get(0)).isEqualTo(new TestParts.TestPart(carry.id(), StandardComponentTypes.CONSTANT,
                TestParts.Kind.CONSTANT, "Cin", ConstantValue.display(0, 1, ConstantValue.AUTO)));
        assertThat(parts.get(1).kind()).isEqualTo(TestParts.Kind.CLOCK);
        assertThat(parts.get(1).value()).isEmpty();
        assertThat(parts.get(2).kind()).isEqualTo(TestParts.Kind.LED);
        assertThat(TestParts.of(document, List.of(gate.id(), pin.id()), registry)).isEmpty();
    }

    @Test
    void withoutTestPartsTheSelectionIsTheUsersOwn() {
        Component input = place(InterfacePins.INPUT_PIN, 0, 0, "a");
        place(InterfacePins.OUTPUT_PIN, 2, 0, "y");

        ConvertedSelection converted = convert(Set.of());

        assertThat(converted.document()).isSameAs(document);
        assertThat(converted.ids()).containsExactlyInAnyOrderElementsOf(selection);
        assertThat(converted.defaultPinOrder()).isEqualTo(DefinitionBuilder.defaultPinOrder(document, selection));
        assertThat(converted.pinNames()).containsEntry(input.id(), "a");
    }

    @Test
    void aCheckedSwitchBecomesAnInputPinAndALedAnOutputPinOnTheSamePoints() {
        List<Component> parts = drawSwitchNotLed();
        Map<ElementId, DocumentElement> before = elementsOf(document);

        ConvertedSelection converted = convert(allParts());

        Component input = selected(converted, InterfacePins.INPUT_PIN).get(0);
        Component output = selected(converted, InterfacePins.OUTPUT_PIN).get(0);
        assertThat(input.label()).isEqualTo("Cin");
        assertThat(InterfacePins.widthOf(input)).isEqualTo(1);
        assertThat(pinPoint(input, 0)).isEqualTo(new GridPoint(4, 2));
        assertThat(output.label()).isEqualTo("S");
        assertThat(pinPoint(output, 0)).isEqualTo(new GridPoint(12, 2));
        assertThat(selected(converted, StandardComponentTypes.SWITCH)).isEmpty();
        assertThat(selected(converted, StandardComponentTypes.LED)).isEmpty();
        assertThat(converted.ids()).contains(parts.get(1).id()).doesNotContain(parts.get(0).id(), parts.get(2).id());
        assertThat(elementsOf(document)).isEqualTo(before);
    }

    @Test
    void anUncheckedSwitchStaysInsideAsAConstantOfItsCurrentValue() {
        Component input = drawSwitchNotLed().get(0);

        ConvertedSelection now = convert(Set.of(), Map.of(input.id(), Value.ZERO));
        ConvertedSelection asSaved = convert(Set.of());
        ConvertedSelection floating = convert(Set.of(), Map.of(input.id(), Value.HIGH_IMPEDANCE));

        Component constant = selected(now, StandardComponentTypes.CONSTANT).get(0);
        assertThat(constant.parameters()).containsEntry("width", 1).containsEntry("value", "0");
        assertThat(constant.label()).isEqualTo("Cin");
        assertThat(pinPoint(constant, 0)).isEqualTo(new GridPoint(4, 2));
        assertThat(selected(now, StandardComponentTypes.SWITCH)).isEmpty();
        assertThat(selected(now, InterfacePins.INPUT_PIN)).isEmpty();
        assertThat(selected(asSaved, StandardComponentTypes.CONSTANT).get(0).parameters()).containsEntry("value", "1");
        // A constant cannot float: a switch left at Z stays a switch at Z.
        assertThat(selected(floating, StandardComponentTypes.CONSTANT)).isEmpty();
        assertThat(selected(floating, StandardComponentTypes.SWITCH)).singleElement()
                .satisfies(kept -> assertThat(kept.parameters()).containsEntry("value", "Z"));
    }

    @Test
    void aCheckedConstantBecomesAnInputPinOfItsWidthAndAnUncheckedOneStays() {
        Component constant = place(StandardComponentTypes.CONSTANT, 0, 0, "K",
                Map.of("width", 8, "value", "0x0F", "format", ConstantValue.HEX));
        wire(registry.typeOf(constant).pinLocation(constant, 0).x(), 1, 20, 1);

        ConvertedSelection checked = convert(Set.of(constant.id()));
        ConvertedSelection unchecked = convert(Set.of());

        Component input = selected(checked, InterfacePins.INPUT_PIN).get(0);
        assertThat(InterfacePins.widthOf(input)).isEqualTo(8);
        assertThat(input.label()).isEqualTo("K");
        assertThat(input.parameters()).containsEntry(ConstantValue.FORMAT, ConstantValue.HEX);
        assertThat(pinPoint(input, 0))
                .isEqualTo(registry.typeOf(constant).pinLocation(constant, 0));
        assertThat(selected(unchecked, StandardComponentTypes.CONSTANT)).containsExactly(constant);
        assertThat(selected(unchecked, InterfacePins.INPUT_PIN)).isEmpty();
    }

    @Test
    void aClockIsAnInputWhenCheckedAndAnInternalClockOtherwise() {
        Component clock = place(StandardComponentTypes.CLOCK, 0, 0, "");
        place(StandardComponentTypes.LED, 1, -1, "");

        ConvertedSelection asInput = convert(Set.of(clock.id()));
        ConvertedSelection internal = convert(Set.of());

        assertThat(selected(asInput, InterfacePins.INPUT_PIN)).singleElement()
                .satisfies(pin -> assertThat(pinPoint(pin, 0)).isEqualTo(new GridPoint(2, 2)));
        assertThat(selected(asInput, StandardComponentTypes.CLOCK)).isEmpty();
        assertThat(selected(internal, StandardComponentTypes.CLOCK)).containsExactly(clock);
    }

    @Test
    void anUncheckedLedIsLeftOutButTheWiresThatFedItStay() {
        List<Component> parts = drawSwitchNotLed();
        Wire feeding = document.wires().get(1);

        ConvertedSelection converted = convert(Set.of(parts.get(0).id()));

        assertThat(selected(converted, StandardComponentTypes.LED)).isEmpty();
        assertThat(selected(converted, InterfacePins.OUTPUT_PIN)).isEmpty();
        assertThat(converted.ids()).contains(feeding.id());
        assertThat(converted.document().component(parts.get(2).id())).isEmpty();
        assertThat(document.component(parts.get(2).id())).isPresent();
    }

    @Test
    void aDisplayGivesAnOutputPinPerConnectedBitNamedAfterIt() {
        // in0..in3 at (24, 10), (23, 10), (22, 10), (21, 10); the bus at (22, 16).
        Component display = place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 10, "D");
        wire(24, 10, 24, 6);
        wire(22, 10, 22, 6);

        ConvertedSelection converted = convert(Set.of(display.id()));

        List<Component> outputs = selected(converted, InterfacePins.OUTPUT_PIN);
        assertThat(outputs).extracting(output -> pinPoint(output, 0))
                .containsExactlyInAnyOrder(new GridPoint(24, 10), new GridPoint(22, 10));
        assertThat(outputs).allSatisfy(output -> assertThat(InterfacePins.widthOf(output)).isEqualTo(1));
        assertThat(converted.pinNames().values())
                .containsExactlyInAnyOrder("D bit 0", "D bit 2");
        assertThat(selected(converted, StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY)).isEmpty();
    }

    @Test
    void anUnlabeledDisplaysBitsAreNamedY1Y2ByPosition() {
        Component display = place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 10, "");
        wire(24, 10, 24, 6);
        wire(22, 10, 22, 6);

        ConvertedSelection converted = convert(Set.of(display.id()));

        PinOrder order = converted.defaultPinOrder();
        Map<ElementId, String> names = converted.pinNames();
        assertThat(order.outputs()).extracting(names::get).containsExactly("Y1", "Y2");
        assertThat(converted.document().component(order.outputs().get(0)).map(output ->
                pinPoint(output, 0))).contains(new GridPoint(22, 10));
    }

    @Test
    void aDisplayWithItsBusConnectedGivesOneBusOutputPin() {
        Component display = place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 10, "D");
        wire(24, 10, 24, 6);
        wire(22, 16, 22, 20);

        ConvertedSelection converted = convert(Set.of(display.id()));

        assertThat(selected(converted, InterfacePins.OUTPUT_PIN)).singleElement().satisfies(output -> {
            assertThat(InterfacePins.widthOf(output)).isEqualTo(4);
            assertThat(output.label()).isEqualTo("D");
            assertThat(pinPoint(output, 0)).isEqualTo(new GridPoint(22, 16));
        });
    }

    @Test
    void aTimeDiagramGivesAnOutputPinPerConnectedChannel() {
        // Channels in0..in3 at (30, 0), (30, 2), (30, 4), (30, 6).
        Component diagram = place(StandardComponentTypes.TIME_DIAGRAM, 30, 0, "T");
        wire(26, 2, 30, 2);
        wire(26, 6, 30, 6);

        ConvertedSelection converted = convert(Set.of(diagram.id()));
        ConvertedSelection leftOut = convert(Set.of());

        assertThat(selected(converted, InterfacePins.OUTPUT_PIN))
                .extracting(output -> pinPoint(output, 0))
                .containsExactlyInAnyOrder(new GridPoint(30, 2), new GridPoint(30, 6));
        assertThat(converted.pinNames().values())
                .containsExactlyInAnyOrder("T in1", "T in3");
        assertThat(selected(leftOut, StandardComponentTypes.TIME_DIAGRAM)).isEmpty();
        assertThat(selected(leftOut, InterfacePins.OUTPUT_PIN)).isEmpty();
    }

    @Test
    void unlabeledPartsAreNamedLikeTheTruthTableAndInterfacePinsAsBefore() {
        Component lowSwitch = place(StandardComponentTypes.SWITCH, 0, 8, "");
        Component highSwitch = place(StandardComponentTypes.SWITCH, 0, 4, "");
        Component pin = place(InterfacePins.INPUT_PIN, 0, 0, "");
        Component firstLed = place(StandardComponentTypes.LED, 20, 0, "");
        Component secondLed = place(StandardComponentTypes.LED, 20, 6, "");

        ConvertedSelection converted = convert(allParts());

        PinOrder order = converted.defaultPinOrder();
        assertThat(order.inputs().get(0)).isEqualTo(pin.id());
        assertThat(order.inputs()).extracting(converted.pinNames()::get).containsExactly("in1", "A", "B");
        assertThat(order.outputs()).extracting(converted.pinNames()::get).containsExactly("Y1", "Y2");
        assertThat(List.of(lowSwitch, highSwitch, firstLed, secondLed))
                .allSatisfy(part -> assertThat(converted.ids()).doesNotContain(part.id()));
    }

    @Test
    void twoPartsWatchingOneNetGiveASingleOutputPin() {
        Component led = place(StandardComponentTypes.LED, 23, 3, "L");
        Component display = place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 10, "D");
        wire(24, 6, 24, 10);
        place(InterfacePins.INPUT_PIN, 22, 5, "a");

        ConvertedSelection converted = convert(Set.of(led.id(), display.id()));

        assertThat(selected(converted, InterfacePins.OUTPUT_PIN)).singleElement()
                .satisfies(output -> assertThat(output.label()).isEqualTo("L"));
    }

    @Test
    void aPartOnTheNetOfAnOutputPinAddsNoOtherPin() {
        Component output = place(InterfacePins.OUTPUT_PIN, 20, 0, "y");
        Component led = place(StandardComponentTypes.LED, 23, -2, "L");
        wire(18, 1, 24, 1);

        ConvertedSelection converted = convert(Set.of(led.id()));

        assertThat(selected(converted, InterfacePins.OUTPUT_PIN)).containsExactly(output);
    }

    @Test
    void theConvertedSelectionBecomesADefinitionWithoutTheTestParts() {
        drawSwitchNotLed();
        Map<ElementId, DocumentElement> before = elementsOf(document);
        ConvertedSelection converted = convert(allParts());

        DefinitionBundle bundle = converted.definition("negador", "Negador", converted.defaultPinOrder(), registry);

        ComponentDefinition definition = bundle.root();
        assertThat(definition.pinout()).extracting(DefinitionPin::spec)
                .containsExactly(PinSpec.input("Cin", 0, 2), PinSpec.output("S", 6, 2));
        assertThat(definition.body().components()).extracting(Component::typeId).containsExactlyInAnyOrder(
                InterfacePins.INPUT_PIN, StandardComponentTypes.NOT, InterfacePins.OUTPUT_PIN);
        assertThat(definition.body().wires()).hasSize(2);
        assertThat(elementsOf(document)).isEqualTo(before);
    }

    @Test
    void withEveryPartLeftInsideTheDefinitionHasNoPins() {
        place(StandardComponentTypes.CLOCK, 0, 0, "");
        place(StandardComponentTypes.LED, 1, -1, "");

        ConvertedSelection converted = convert(Set.of());

        assertThat(converted.defaultPinOrder().isEmpty()).isTrue();
        ComponentDefinition definition = converted.definition("parpadeo", "Parpadeo", converted.defaultPinOrder(),
                registry).root();
        assertThat(definition.pinout()).isEmpty();
        assertThat(definition.body().components()).extracting(Component::typeId)
                .containsExactly(StandardComponentTypes.CLOCK);
    }

    @Test
    void outsideWiresOnAConvertedPartAreBridgedToTheInstancePin() {
        List<Component> parts = drawSwitchNotLed();
        Wire outside = new Wire(document.newWireId(), new GridPoint(4, 2), new GridPoint(4, 8));
        document.add(outside);
        ConvertedSelection converted = convert(allParts());
        ComponentDefinition definition = converted.definition("negador", "Negador", converted.defaultPinOrder(),
                registry).root();
        GridPoint origin = DefinitionBuilder.origin(document, selection, registry);
        GridPoint instancePin = origin.plus(definition.pinout().get(0).spec().position());

        PinBridges.Bridging bridging = converted.bridges(definition, origin, registry);

        assertThat(bridging.blockedPins()).isEmpty();
        assertThat(bridging.unconnectedPins()).isEmpty();
        assertThat(bridging.wires()).isNotEmpty();
        assertThat(bridging.wires()).anySatisfy(bridge -> assertThat(bridge.contains(new GridPoint(4, 2))).isTrue());
        assertThat(bridging.wires()).anySatisfy(bridge -> assertThat(bridge.contains(instancePin)).isTrue());
        assertThat(bridging.wires()).allSatisfy(bridge -> assertThat(document.element(bridge.id())).isEmpty());
        assertThat(document.component(parts.get(0).id())).isPresent();
    }
}
