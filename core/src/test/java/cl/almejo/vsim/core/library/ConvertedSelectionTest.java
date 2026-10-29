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
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        Component gate = place(StandardComponentTypes.BUFFER, 6, 0, "");
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
        Component gate = place(StandardComponentTypes.BUFFER, 10, 0, "");
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
    void renamedConvertedPinsNameTheDefinitionOnly() {
        drawSwitchNotLed();
        Map<ElementId, DocumentElement> before = elementsOf(document);
        ConvertedSelection converted = convert(allParts());
        PinOrder order = converted.defaultPinOrder();

        DefinitionBundle bundle = converted.definition("negador", "Negador", order,
                Map.of(order.inputs().get(0), "Entrada", order.outputs().get(0), "Salida"), registry);

        assertThat(bundle.root().pinout()).extracting(pin -> pin.spec().name()).containsExactly("Entrada", "Salida");
        assertThat(bundle.root().body().components()).filteredOn(component -> InterfacePins.isInterface(component.typeId()))
                .extracting(Component::label).containsExactlyInAnyOrder("Entrada", "Salida");
        assertThat(converted.pinNames()).containsValues("Cin", "S");
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
    void aCheckedPushButtonBecomesAnInputPinAtZeroAndAnUncheckedOneAConstantOfItsCurrentValue() {
        Component button = place(StandardComponentTypes.PUSH_BUTTON, 0, 1, "Go");
        wire(4, 2, 6, 2);
        place(StandardComponentTypes.BUFFER, 6, 0, "");

        ConvertedSelection checked = convert(Set.of(button.id()));
        ConvertedSelection unchecked = convert(Set.of());
        ConvertedSelection uncheckedWhileHeld = convert(Set.of(), Map.of(button.id(), Value.ONE));

        Component input = selected(checked, InterfacePins.INPUT_PIN).get(0);
        assertThat(input.label()).isEqualTo("Go");
        assertThat(input.parameters()).containsEntry("value", "0");
        assertThat(pinPoint(input, 0)).isEqualTo(new GridPoint(4, 2));
        assertThat(selected(checked, StandardComponentTypes.PUSH_BUTTON)).isEmpty();
        assertThat(selected(unchecked, StandardComponentTypes.CONSTANT)).singleElement()
                .satisfies(constant -> assertThat(constant.parameters()).containsEntry("value", "0"));
        assertThat(selected(uncheckedWhileHeld, StandardComponentTypes.CONSTANT)).singleElement()
                .satisfies(constant -> assertThat(constant.parameters()).containsEntry("value", "1"));
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
                InterfacePins.INPUT_PIN, StandardComponentTypes.BUFFER, InterfacePins.OUTPUT_PIN);
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

    /**
     * Replaces the selection in {@code document} the way «Reemplazar la selección» does: definitions in, the replaced
     * ids out, the instance and its bridges in. Returns the instance.
     */
    private Component replace(ConvertedSelection converted, String id) {
        DefinitionBundle bundle = converted.definition(id, id, converted.defaultPinOrder(), registry);
        GridPoint origin = converted.instanceOrigin(registry);
        PinBridges.Bridging bridging = converted.bridges(bundle.root(), origin, registry);
        assertThat(bridging.blockedPins()).isEmpty();
        assertThat(bridging.unconnectedPins()).isEmpty();
        bundle.all().forEach(document::putDefinition);
        converted.replacedIds().forEach(document::remove);
        Component instance = new Component(document.newComponentId(), SubcircuitTypes.typeIdOf(id), origin,
                Rotation.R0, Map.of(), "");
        document.add(instance);
        bridging.wires().forEach(document::add);
        return instance;
    }

    private Net netOf(Netlist netlist, Component component, int pinIndex) {
        return netlist.netForPin(new PinRef(component.id(), pinIndex)).orElseThrow();
    }

    private static int pinIndex(ComponentDefinition definition, String name) {
        List<String> names = definition.pinout().stream().map(pin -> pin.spec().name()).toList();
        return names.indexOf(name);
    }

    @Test
    void theCheckedPartsThatGivePinsStayInTheCircuitAndTheRestIsReplaced() {
        List<Component> parts = drawSwitchNotLed();
        Set<ElementId> body = new HashSet<>(selection);
        body.removeAll(Set.of(parts.get(0).id(), parts.get(2).id()));

        ConvertedSelection converted = convert(allParts());
        ConvertedSelection unchecked = convert(Set.of(parts.get(2).id()));

        assertThat(converted.keptParts()).containsExactlyInAnyOrder(parts.get(0).id(), parts.get(2).id());
        assertThat(converted.replacedIds()).containsExactlyInAnyOrderElementsOf(body);
        assertThat(converted.instanceOrigin(registry)).isEqualTo(DefinitionBuilder.origin(document, body, registry));
        // The unchecked switch went inside as a constant: it goes with the rest.
        assertThat(unchecked.keptParts()).containsExactly(parts.get(2).id());
        assertThat(unchecked.replacedIds()).contains(parts.get(0).id()).doesNotContain(parts.get(2).id());
    }

    @Test
    void theKeptPartsAreConnectedToTheInstancePins() {
        List<Component> parts = drawSwitchNotLed();
        ConvertedSelection converted = convert(allParts());

        Component instance = replace(converted, "negador");

        ComponentDefinition definition = document.definition("negador").orElseThrow();
        Netlist netlist = compile();
        assertThat(document.component(parts.get(0).id())).contains(parts.get(0));
        assertThat(document.component(parts.get(2).id())).contains(parts.get(2));
        assertThat(document.component(parts.get(1).id())).isEmpty();
        assertThat(netOf(netlist, parts.get(0), 0).pins())
                .contains(new PinRef(instance.id(), pinIndex(definition, "Cin")));
        assertThat(netOf(netlist, parts.get(2), 0).pins())
                .contains(new PinRef(instance.id(), pinIndex(definition, "S")));
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void aRotatedKeptSwitchIsConnectedToItsInstancePin(Rotation rotation) {
        Component gate = place(StandardComponentTypes.BUFFER, 6, 0, "");
        wire(10, 2, 12, 2);
        place(StandardComponentTypes.LED, 11, -1, "S");
        Component input = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(-12, 12), rotation, Map.of(), "A");
        document.add(input);
        selection.add(input.id());
        GridPoint point = pinPoint(input, 0);
        wire(point.x(), point.y(), point.x(), 2);
        wire(point.x(), 2, 6, 2);

        Component instance = replace(convert(allParts()), "negador");

        ComponentDefinition definition = document.definition("negador").orElseThrow();
        assertThat(netOf(compile(), input, 0).pins())
                .contains(new PinRef(instance.id(), pinIndex(definition, "A")));
        assertThat(gate).isNotNull();
    }

    @Test
    void aKeptDisplayStaysOnItsBusOutputWithTheSameWidth() {
        Component source = place(InterfacePins.INPUT_PIN, 10, 19, "x", Map.of(InterfacePins.WIDTH, 4));
        Component display = place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 10, "D");
        GridPoint sourcePoint = pinPoint(source, 0);
        wire(sourcePoint.x(), sourcePoint.y(), 22, sourcePoint.y());
        wire(22, sourcePoint.y(), 22, 16);

        Component instance = replace(convert(Set.of(display.id())), "copia");

        ComponentDefinition definition = document.definition("copia").orElseThrow();
        Net net = netOf(compile(), display, StandardComponentTypes.sevenSegmentsBusPin(registry.typeOf(display)
                .forComponent(display)));
        assertThat(net.pins()).contains(new PinRef(instance.id(), pinIndex(definition, "D")));
        assertThat(net.width()).isEqualTo(4);
        assertThat(net.widthMismatch()).isFalse();
    }

    @Test
    void eachPartReportsThePinsItGivesOrWhyItGivesNone() {
        Component firstLed = place(StandardComponentTypes.LED, 23, 3, "L");
        Component secondLed = place(StandardComponentTypes.LED, 25, 3, "M");
        wire(24, 6, 26, 6);
        Component lonely = place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 40, 10, "D");
        Component display = place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 10, "E");
        wire(24, 10, 24, 8);
        wire(22, 10, 22, 8);
        Component output = place(InterfacePins.OUTPUT_PIN, 60, 0, "y");
        Component watcher = place(StandardComponentTypes.LED, 63, -2, "W");
        wire(58, 1, 64, 1);

        ConvertedSelection converted = convert(allParts());

        Map<ElementId, ConvertedSelection.PartPins> parts = converted.parts();
        ElementId firstPin = parts.get(firstLed.id()).pins().get(0);
        assertThat(converted.pinNames().get(firstPin)).isEqualTo("L");
        assertThat(parts.get(secondLed.id()).pins()).isEmpty();
        assertThat(parts.get(secondLed.id()).sharedWith()).contains(firstPin);
        assertThat(parts.get(lonely.id())).isEqualTo(new ConvertedSelection.PartPins(List.of(), Optional.empty()));
        assertThat(parts.get(display.id()).pins()).extracting(converted.pinNames()::get)
                .containsExactly("E bit 0", "E bit 2");
        assertThat(parts.get(watcher.id()).sharedWith()).contains(output.id());
        assertThat(converted.keptParts()).containsExactlyInAnyOrder(firstLed.id(), display.id());
        // A checked part that gives no pin goes with the selection.
        assertThat(converted.replacedIds()).contains(secondLed.id(), lonely.id(), watcher.id());
    }

    @Test
    void withOnlyKeptPartsTheInstanceGoesRightOfTheSelection() {
        Component clock = place(StandardComponentTypes.CLOCK, 0, 0, "CLK");
        Component led = place(StandardComponentTypes.LED, 1, -1, "");
        ConvertedSelection converted = convert(allParts());

        Component instance = replace(converted, "parpadeo");

        assertThat(converted.replacedIds()).isEmpty();
        assertThat(instance.position().x()).isGreaterThan(pinPoint(clock, 0).x());
        assertThat(document.component(clock.id())).isPresent();
        assertThat(document.component(led.id())).isPresent();
        ComponentDefinition definition = document.definition("parpadeo").orElseThrow();
        assertThat(netOf(compile(), clock, 0).pins())
                .contains(new PinRef(instance.id(), pinIndex(definition, "CLK")));
    }

    private Netlist compile() {
        return new NetlistCompiler(registry).compile(document);
    }

    @Test
    void aFourBitSwitchBecomesAFourBitInputPinAndAnEightBitLedAnOutputPinOfEightBits() {
        Component input = place(StandardComponentTypes.SWITCH, 0, 0, "D", Map.of("width", 4, "value", "10"));
        Component led = place(StandardComponentTypes.LED, 20, 0, "Q", Map.of("width", 8));

        ConvertedSelection converted = convert(List.of(input.id(), led.id()));

        List<Component> pins = converted.document().components().stream()
                .filter(component -> InterfacePins.isInterface(component.typeId())).toList();
        assertThat(pins).extracting(Component::typeId)
                .containsExactlyInAnyOrder(InterfacePins.INPUT_PIN, InterfacePins.OUTPUT_PIN);
        assertThat(pins).filteredOn(pin -> pin.typeId().equals(InterfacePins.INPUT_PIN))
                .allSatisfy(pin -> assertThat(registry.typeOf(pin).pin(0).width()).isEqualTo(4));
        assertThat(pins).filteredOn(pin -> pin.typeId().equals(InterfacePins.OUTPUT_PIN))
                .allSatisfy(pin -> assertThat(registry.typeOf(pin).pin(0).width()).isEqualTo(8));
    }

    @Test
    void anUncheckedMultiBitSwitchBecomesAConstantOfItsWidthAndCurrentValue() {
        Component input = place(StandardComponentTypes.SWITCH, 0, 0, "D", Map.of("width", 4, "value", "10"));

        ConvertedSelection converted = convert(List.of(), Map.of(input.id(), Value.of(4, 6)));

        Component constant = converted.document().components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.CONSTANT)).findFirst()
                .orElseThrow();
        assertThat(registry.typeOf(constant).pin(0).width()).isEqualTo(4);
        assertThat(ConstantValue.bitsOf((String) constant.parameters().get("value"), 4)).hasValue(6L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0xFF", "0b11111111", "255"})
    void aMultiBitSwitchSavedInAnyBaseAsAllOnesBecomesAnInputPinOfOnes(String saved) {
        Component input = place(StandardComponentTypes.SWITCH, 0, 0, "D", Map.of("width", 8, "value", saved));

        ConvertedSelection converted = convert(List.of(input.id()));

        assertThat(inputPinDefault(converted)).isEqualTo("1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x00", "0b0", "0", "0x12"})
    void aMultiBitSwitchSavedAsZeroOrMixedBecomesAnInputPinOfZeros(String saved) {
        Component input = place(StandardComponentTypes.SWITCH, 0, 0, "D", Map.of("width", 8, "value", saved));

        ConvertedSelection converted = convert(List.of(input.id()));

        assertThat(inputPinDefault(converted)).isEqualTo("0");
    }

    @ParameterizedTest
    @CsvSource({"0b1,1", "0x1,1", "0b0,0", "0x0,0", "1,1", "Z,Z"})
    void aOneBitSwitchWithAValueInAnyBaseBecomesAnInputPinWithItsBit(String saved, String expected) {
        Component input = place(StandardComponentTypes.SWITCH, 0, 0, "D", Map.of("value", saved));

        ConvertedSelection converted = convert(List.of(input.id()));

        assertThat(inputPinDefault(converted)).isEqualTo(expected);
    }

    private String inputPinDefault(ConvertedSelection converted) {
        return converted.document().components().stream()
                .filter(component -> component.typeId().equals(InterfacePins.INPUT_PIN)).findFirst().orElseThrow()
                .parameters().get("value").toString();
    }
}
