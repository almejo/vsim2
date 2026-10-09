package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionBuilderTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    DefinitionBuilderTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    private Component place(String typeId, int x, int y, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), label);
        document.add(component);
        return component;
    }

    private static List<ElementId> ids(Component... components) {
        return Arrays.stream(components).map(Component::id).toList();
    }

    @Test
    void defaultOrderPutsInputsOnTheLeftAndOutputsOnTheRightTopToBottom() {
        Component lowInput = place(InterfacePins.INPUT_PIN, 0, 8, "b");
        Component highInput = place(InterfacePins.INPUT_PIN, 4, 2, "a");
        Component sameRowLeft = place(InterfacePins.INPUT_PIN, 0, 2, "c");
        Component output = place(InterfacePins.OUTPUT_PIN, 20, 4, "y");
        Component gate = place(StandardComponentTypes.NOT, 10, 0, "");

        PinOrder order = DefinitionBuilder.defaultPinOrder(document,
                ids(lowInput, highInput, sameRowLeft, output, gate));

        assertThat(order.inputs()).containsExactly(sameRowLeft.id(), highInput.id(), lowInput.id());
        assertThat(order.outputs()).containsExactly(output.id());
        assertThat(DefinitionBuilder.hasInterfacePins(document, ids(gate))).isFalse();
        assertThat(DefinitionBuilder.hasInterfacePins(document, ids(gate, output))).isTrue();
    }

    @Test
    void theBoxGrowsWithTheLongerSide() {
        assertThat(DefinitionBuilder.boxSize(1, 1)).isEqualTo(new GridSize(6, 4));
        assertThat(DefinitionBuilder.boxSize(3, 1)).isEqualTo(new GridSize(6, 8));
        assertThat(DefinitionBuilder.boxSize(0, 2)).isEqualTo(new GridSize(6, 6));
    }

    @Test
    void aSelectionBecomesADefinitionWithItsBodyMovedToTheOrigin() {
        Component input = place(InterfacePins.INPUT_PIN, 10, 11, "a");
        Component gate = place(StandardComponentTypes.NOT, 12, 10, "");
        Component output = place(InterfacePins.OUTPUT_PIN, 16, 11, "y");
        List<ElementId> selection = ids(input, gate, output);

        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "negador", "Negador",
                DefinitionBuilder.defaultPinOrder(document, selection), registry);

        ComponentDefinition definition = bundle.root();
        assertThat(definition.id()).isEqualTo("negador");
        assertThat(definition.name()).isEqualTo("Negador");
        assertThat(definition.size()).isEqualTo(new GridSize(6, 4));
        assertThat(definition.pinout()).extracting(DefinitionPin::spec)
                .containsExactly(PinSpec.input("a", 0, 2), PinSpec.output("y", 6, 2));
        assertThat(definition.pinout()).extracting(DefinitionPin::source).containsExactly(input.id(), output.id());
        assertThat(definition.body().components()).extracting(Component::position)
                .containsExactly(new GridPoint(0, 1), new GridPoint(2, 0), new GridPoint(6, 1));
        assertThat(definition.body().definitions()).isEmpty();
        assertThat(bundle.dependencies()).isEmpty();
        assertThat(DefinitionBuilder.origin(document, selection, registry)).isEqualTo(new GridPoint(10, 10));
    }

    @Test
    void theChosenOrderSetsThePinPositionsAndBlankLabelsGetStableDefaultNames() {
        Component first = place(InterfacePins.INPUT_PIN, 0, 1, "");
        Component second = place(InterfacePins.INPUT_PIN, 0, 5, "b");
        Component output = place(InterfacePins.OUTPUT_PIN, 10, 1, " ");
        List<ElementId> selection = ids(first, second, output);
        assertThat(DefinitionBuilder.pinNames(document, DefinitionBuilder.defaultPinOrder(document, selection)))
                .containsExactly(Map.entry(first.id(), "in1"), Map.entry(second.id(), "b"),
                        Map.entry(output.id(), "out1"));

        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "par", "Par",
                new PinOrder(List.of(second.id(), first.id()), List.of(output.id())), registry);

        assertThat(bundle.root().pinout()).extracting(DefinitionPin::spec).containsExactly(
                PinSpec.input("b", 0, 2), PinSpec.input("in1", 0, 4), PinSpec.output("out1", 6, 2));
        assertThat(bundle.root().size()).isEqualTo(new GridSize(6, 6));
    }

    @Test
    void repeatedPinNamesAreNumberedInTheDefaultOrderSoTheDefinitionIsValid() {
        Component upper = place(InterfacePins.INPUT_PIN, 0, 1, "a");
        Component lower = place(InterfacePins.INPUT_PIN, 0, 5, " a ");
        Component output = place(InterfacePins.OUTPUT_PIN, 10, 1, "a");
        Component blank = place(InterfacePins.INPUT_PIN, 0, 9, "");
        Component labeledLikeAFallback = place(InterfacePins.INPUT_PIN, 0, 13, "in4");
        List<ElementId> selection = ids(output, lower, upper, blank, labeledLikeAFallback);
        PinOrder defaultOrder = DefinitionBuilder.defaultPinOrder(document, selection);

        assertThat(DefinitionBuilder.pinNames(document, defaultOrder)).containsExactly(
                Map.entry(upper.id(), "a"), Map.entry(lower.id(), "a2"), Map.entry(blank.id(), "in3"),
                Map.entry(labeledLikeAFallback.id(), "in4"), Map.entry(output.id(), "a3"));

        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "triple", "Triple",
                new PinOrder(List.of(labeledLikeAFallback.id(), blank.id(), lower.id(), upper.id()),
                        List.of(output.id())), registry);

        assertThat(bundle.root().pinout()).extracting(pin -> pin.spec().name())
                .containsExactly("in4", "in3", "a2", "a", "a3");
    }

    @Test
    void aBlankPinDoesNotTakeTheNameOfALabeledOne() {
        Component blank = place(InterfacePins.INPUT_PIN, 0, 1, "");
        Component labeled = place(InterfacePins.INPUT_PIN, 0, 5, "in1");
        List<ElementId> selection = ids(blank, labeled);

        Map<ElementId, String> names = DefinitionBuilder.pinNames(document,
                DefinitionBuilder.defaultPinOrder(document, selection));

        assertThat(names.values()).doesNotHaveDuplicates();
        assertThat(names).containsEntry(labeled.id(), "in1");
        DefinitionBuilder.fromSelection(document, selection, "dos", "Dos",
                DefinitionBuilder.defaultPinOrder(document, selection), registry);
    }

    @Test
    void nestedInstancesBringTheirDefinitionsAsDependencies() {
        document.putDefinition(ExampleDefinitions.inverter());
        Component input = place(InterfacePins.INPUT_PIN, 0, 1, "a");
        Component inner = place(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 2, 0, "");
        Component output = place(InterfacePins.OUTPUT_PIN, 6, 1, "y");
        List<ElementId> selection = ids(input, inner, output);

        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "envoltura", "Envoltura",
                DefinitionBuilder.defaultPinOrder(document, selection), registry);

        assertThat(bundle.dependencies()).containsExactly(ExampleDefinitions.inverter());
        assertThat(bundle.root().usedDefinitionIds()).containsExactly(ExampleDefinitions.INVERTER);
        assertThat(bundle.root().body().definitions()).isEmpty();
    }

    @Test
    void aSelectionWithoutInterfacePinsOrAWrongOrderIsRejected() {
        Component gate = place(StandardComponentTypes.NOT, 0, 0, "");
        Component input = place(InterfacePins.INPUT_PIN, 10, 1, "a");
        PinOrder none = new PinOrder(List.of(), List.of());
        assertThatThrownBy(() -> DefinitionBuilder.fromSelection(document, ids(gate), "x", "X", none, registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no interface pins");
        assertThatThrownBy(() -> DefinitionBuilder.fromSelection(document, ids(gate, input), "x", "X",
                new PinOrder(List.of(input.id(), input.id()), List.of()), registry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefinitionBuilder.fromSelection(document, ids(gate, input), "x", "X",
                new PinOrder(List.of(), List.of(input.id())), registry))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
