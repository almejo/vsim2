package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.InterfacePins;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SetLabelOffsetCommandTest {

    private static final LabelOffset OFFSET = new LabelOffset(3, -2);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Component place(String typeId, int x, int y, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), label);
        document.add(component);
        return component;
    }

    private Component current(Component component) {
        return document.component(component.id()).orElseThrow();
    }

    @Test
    void aNewComponentHasNoOffsetAndTheCopyingMethodsKeepOne() {
        Component gate = place(StandardComponentTypes.NOT, 4, 4, "U1");
        assertThat(gate.labelOffset()).isEmpty();
        Component moved = gate.withLabelOffset(Optional.of(OFFSET));
        assertThat(moved.translate(2, 3).labelOffset()).contains(OFFSET);
        assertThat(moved.withRotation(Rotation.R90).labelOffset()).contains(OFFSET);
        assertThat(moved.withPosition(GridPoint.ORIGIN).labelOffset()).contains(OFFSET);
        assertThat(moved.withLabel("otro").labelOffset()).contains(OFFSET);
        assertThat(moved.withParameters(Map.of("delay", 2)).labelOffset()).contains(OFFSET);
        assertThat(moved.withLabelOffset(Optional.empty()).labelOffset()).isEmpty();
        assertThat(new Component(gate.id(), gate.typeId(), gate.position(), gate.rotation(), gate.parameters(),
                gate.label(), null).labelOffset()).isEmpty();
    }

    @Test
    void settingAnOffsetIsUndoneAndRedoneExactly() {
        Component gate = place(StandardComponentTypes.NOT, 4, 4, "U1");
        List<DocumentElement> before = List.copyOf(document.elements());
        SetLabelOffsetCommand command = new SetLabelOffsetCommand(gate.id(), Optional.of(OFFSET));
        assertThat(command.description()).isEqualTo("command.moveLabel");
        command.apply(document);
        assertThat(current(gate).labelOffset()).contains(OFFSET);
        assertThat(current(gate).position()).isEqualTo(gate.position());
        command.revert(document);
        assertThat(document.elements()).isEqualTo(before);
        command.apply(document);
        assertThat(current(gate).labelOffset()).contains(OFFSET);
    }

    @Test
    void resettingRemovesTheOffsetAndUndoBringsItBack() {
        Component gate = place(StandardComponentTypes.NOT, 4, 4, "U1");
        document.replace(gate.withLabelOffset(Optional.of(OFFSET)));
        SetLabelOffsetCommand command = new SetLabelOffsetCommand(gate.id(), Optional.empty());
        assertThat(command.description()).isEqualTo("command.resetLabelPosition");
        command.apply(document);
        assertThat(current(gate).labelOffset()).isEmpty();
        command.revert(document);
        assertThat(current(gate).labelOffset()).contains(OFFSET);
    }

    @Test
    void rotatingKeepsTheOffsetInTheComponentsOwnFrame() {
        Component gate = place(StandardComponentTypes.NOT, 4, 4, "U1");
        document.replace(gate.withLabelOffset(Optional.of(OFFSET)));
        new RotateElementsCommand(List.of(gate.id()), true, registry).apply(document);
        assertThat(current(gate).labelOffset()).contains(OFFSET);
        for (int turn = 0; turn < 3; turn++) {
            new RotateElementsCommand(List.of(gate.id()), true, registry).apply(document);
        }
        assertThat(current(gate)).isEqualTo(gate.withLabelOffset(Optional.of(OFFSET)));
    }

    @Test
    void pasteKeepsTheOffsetOnTheNewComponent() {
        Component gate = place(StandardComponentTypes.NOT, 4, 4, "U1");
        document.replace(gate.withLabelOffset(Optional.of(OFFSET)));
        CircuitFragment fragment = CircuitFragment.of(document, List.of(gate.id()), registry);
        assertThat(fragment.components().getFirst().labelOffset()).contains(OFFSET);
        PasteCommand paste = new PasteCommand(fragment, new GridPoint(20, 20));
        paste.apply(document);
        Component pasted = document.component(paste.pastedIds().getFirst()).orElseThrow();
        assertThat(pasted.id()).isNotEqualTo(gate.id());
        assertThat(pasted.labelOffset()).contains(OFFSET);
    }

    @Test
    void aDefinitionBodyBuiltFromASelectionKeepsTheOffset() {
        Component input = place(InterfacePins.INPUT_PIN, 10, 11, "a");
        Component gate = place(StandardComponentTypes.NOT, 12, 10, "");
        Component output = place(InterfacePins.OUTPUT_PIN, 16, 11, "y");
        document.replace(gate.withLabelOffset(Optional.of(OFFSET)));
        List<ElementId> selection = List.of(input.id(), gate.id(), output.id());
        CircuitFragment body = DefinitionBuilder.fromSelection(document, selection, "box", "Box",
                DefinitionBuilder.defaultPinOrder(document, selection), registry).root().body();
        assertThat(body.components()).filteredOn(component -> component.id().equals(gate.id()))
                .singleElement().satisfies(component -> assertThat(component.labelOffset()).contains(OFFSET));
    }
}
