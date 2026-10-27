package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class DefinitionBuilderTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    DefinitionBuilderTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    private static List<ElementId> ids(Component... components) {
        return Arrays.stream(components).map(Component::id).toList();
    }

    @Test
    void defaultOrderPutsInputsOnTheLeftAndOutputsOnTheRightTopToBottom() {
        Component lowInput = place(registry, document, InterfacePins.INPUT_PIN, 0, 8, "b");
        Component highInput = place(registry, document, InterfacePins.INPUT_PIN, 4, 2, "a");
        Component sameRowLeft = place(registry, document, InterfacePins.INPUT_PIN, 0, 2, "c");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 20, 4, "y");
        Component gate = place(registry, document, StandardComponentTypes.NOT, 10, 0, "");

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
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 10, 11, "a");
        Component gate = place(registry, document, StandardComponentTypes.NOT, 12, 10, "");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 16, 11, "y");
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
    void aStickyNoteInTheSelectionStaysOutOfTheDefinitionAndItsBounds() {
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 10, 11, "a");
        Component gate = place(registry, document, StandardComponentTypes.NOT, 12, 10, "");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 16, 11, "y");
        List<ElementId> selection = ids(input, gate, output);
        DefinitionBundle without = DefinitionBuilder.fromSelection(document, selection, "negador", "Negador",
                DefinitionBuilder.defaultPinOrder(document, selection), registry);
        StickyNote note = new StickyNote(document.newStickyNoteId(), new GridPoint(0, 0), 8, 6, StickyColor.BLUE,
                new StickyText("recordar"), false);
        document.add(note);
        List<ElementId> withNote = new java.util.ArrayList<>(selection);
        withNote.add(note.id());

        DefinitionBundle with = DefinitionBuilder.fromSelection(document, withNote, "negador", "Negador",
                DefinitionBuilder.defaultPinOrder(document, withNote), registry);

        assertThat(with).isEqualTo(without);
        assertThat(DefinitionBuilder.origin(document, withNote, registry)).isEqualTo(new GridPoint(10, 10));
    }

    @Test
    void renamedPinsNameTheDefinitionsPinsAndItsBodyButNotTheUsersCircuit() {
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 10, 11, "a");
        Component blank = place(registry, document, InterfacePins.INPUT_PIN, 10, 13, "");
        Component gate = place(registry, document, StandardComponentTypes.AND2, 12, 10, "");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 16, 11, "y");
        List<ElementId> selection = ids(input, blank, gate, output);

        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "y-logico", "Y lógico",
                DefinitionBuilder.defaultPinOrder(document, selection),
                Map.of(input.id(), "Entrada", output.id(), "Salida"), registry);

        assertThat(bundle.root().pinout()).extracting(pin -> pin.spec().name())
                .containsExactly("Entrada", "in2", "Salida");
        assertThat(bundle.root().body().components()).extracting(Component::label)
                .containsExactly("Entrada", "", "", "Salida");
        assertThat(document.component(input.id()).orElseThrow().label()).isEqualTo("a");
        assertThat(document.component(output.id()).orElseThrow().label()).isEqualTo("y");
    }

    @Test
    void aBlankPinNameIsRefused() {
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 10, 11, "a");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 16, 11, "y");
        List<ElementId> selection = ids(input, output);

        assertThatThrownBy(() -> DefinitionBuilder.fromSelection(document, selection, "x", "X",
                DefinitionBuilder.defaultPinOrder(document, selection), Map.of(input.id(), "  "), registry))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theChosenOrderSetsThePinPositionsAndBlankLabelsGetStableDefaultNames() {
        Component first = place(registry, document, InterfacePins.INPUT_PIN, 0, 1, "");
        Component second = place(registry, document, InterfacePins.INPUT_PIN, 0, 5, "b");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 10, 1, " ");
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
        Component upper = place(registry, document, InterfacePins.INPUT_PIN, 0, 1, "a");
        Component lower = place(registry, document, InterfacePins.INPUT_PIN, 0, 5, " a ");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 10, 1, "a");
        Component blank = place(registry, document, InterfacePins.INPUT_PIN, 0, 9, "");
        Component labeledLikeAFallback = place(registry, document, InterfacePins.INPUT_PIN, 0, 13, "in4");
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
    void defaultNamesDifferEvenIgnoringCaseSoTheyAreValidInTheDialog() {
        Component lower = place(registry, document, InterfacePins.INPUT_PIN, 0, 1, "a");
        Component upper = place(registry, document, InterfacePins.INPUT_PIN, 0, 5, "A");
        Component labeledLikeAFallback = place(registry, document, InterfacePins.INPUT_PIN, 0, 9, "IN4");
        Component blank = place(registry, document, InterfacePins.INPUT_PIN, 0, 13, "");
        Component converted = place(registry, document, InterfacePins.INPUT_PIN, 0, 17, "");
        List<ElementId> selection = ids(lower, upper, labeledLikeAFallback, blank, converted);

        Map<ElementId, String> names = DefinitionBuilder.pinNames(document,
                DefinitionBuilder.defaultPinOrder(document, selection), java.util.Set.of(converted.id()));

        assertThat(names).containsExactly(entry(lower.id(), "a"), entry(upper.id(), "A2"),
                entry(labeledLikeAFallback.id(), "IN4"), entry(blank.id(), "in5"), entry(converted.id(), "B"));
    }

    @Test
    void aBlankPinDoesNotTakeTheNameOfALabeledOne() {
        Component blank = place(registry, document, InterfacePins.INPUT_PIN, 0, 1, "");
        Component labeled = place(registry, document, InterfacePins.INPUT_PIN, 0, 5, "in1");
        List<ElementId> selection = ids(blank, labeled);

        Map<ElementId, String> names = DefinitionBuilder.pinNames(document,
                DefinitionBuilder.defaultPinOrder(document, selection));

        assertThat(names).containsExactly(entry(blank.id(), "in2"), entry(labeled.id(), "in1"));
        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "dos", "Dos",
                DefinitionBuilder.defaultPinOrder(document, selection), registry);
        assertThat(bundle.root().pinout()).extracting(pin -> pin.spec().name()).containsExactly("in2", "in1");
    }

    @Test
    void nestedInstancesBringTheirDefinitionsAsDependencies() {
        document.putDefinition(ExampleDefinitions.inverter());
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 0, 1, "a");
        Component inner = place(registry, document, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 2, 0, "");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 6, 1, "y");
        List<ElementId> selection = ids(input, inner, output);

        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "envoltura", "Envoltura",
                DefinitionBuilder.defaultPinOrder(document, selection), registry);

        assertThat(bundle.dependencies()).containsExactly(ExampleDefinitions.inverter());
        assertThat(bundle.root().usedDefinitionIds()).containsExactly(ExampleDefinitions.INVERTER);
        assertThat(bundle.root().body().definitions()).isEmpty();
    }

    @Test
    void aSelectionWithoutInterfacePinsOrAWrongOrderIsRejected() {
        Component gate = place(registry, document, StandardComponentTypes.NOT, 0, 0, "");
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 10, 1, "a");
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

    @Test
    void pinsTakeTheWidthOfTheirInterfacePins() {
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(document.newComponentId(),
                new GridPoint(0, 1), Rotation.R0, Map.of(InterfacePins.WIDTH, 8), "dato");
        Component output = registry.require(InterfacePins.OUTPUT_PIN).newComponent(document.newComponentId(),
                new GridPoint(2, 1), Rotation.R0, Map.of(InterfacePins.WIDTH, 8), "copia");
        document.add(input);
        document.add(output);
        List<ElementId> selection = ids(input, output);

        DefinitionBundle bundle = DefinitionBuilder.fromSelection(document, selection, "copia", "Copia",
                DefinitionBuilder.defaultPinOrder(document, selection), registry);

        assertThat(bundle.root().pinout()).extracting(DefinitionPin::spec)
                .containsExactly(PinSpec.input("dato", 0, 2, 8), PinSpec.output("copia", 6, 2, 8));
    }
}
