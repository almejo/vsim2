package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One inside edit = one undo step of the document that writes the body back (spec §6.1, §10.4). */
class EditInsideCommandTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final History history = new History(document);
    private final Component first;
    private final Component second;
    private final CircuitDocument workingCopy;

    EditInsideCommandTest() {
        document.putDefinition(inverter);
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        first = instance("c20", 10);
        second = instance("c21", 40);
        workingCopy = inverter.bodyDocument(document);
    }

    @Test
    void applyWritesTheBodyBackAndRevertRestoresEverything() {
        List<DocumentElement> bodyBefore = workingCopy.elements();
        Component gate = gateIn(workingCopy, 20, 20);
        EditInsideCommand inside = inside(new AddElementsCommand(List.of(gate)));

        history.execute(inside);

        ComponentDefinition written = document.definition(inverter.id()).orElseThrow();
        assertThat(written.body().components()).contains(gate);
        assertThat(written.revision()).isEqualTo(inverter.revision() + 1);
        assertThat(document.components()).containsExactly(first, second);
        assertThat(inside.placeName()).isEqualTo(inverter.name());
        assertThat(inside.description()).isEqualTo("command.add");

        history.undo();
        assertOriginal(bodyBefore);

        history.redo();
        assertThat(document.definition(inverter.id())).containsSame(written);
        assertThat(workingCopy.component(gate.id())).contains(gate);

        history.undo();
        assertOriginal(bodyBefore);
    }

    @Test
    void applyOfANoOpChangesNothing() {
        EditInsideCommand inside = inside(new MoveElementsCommand(List.of(new ElementId("c2")), 0, 0));

        inside.apply(document);

        assertThat(inside.changesNothing()).isTrue();
        assertThat(document.definition(inverter.id())).containsSame(inverter);
    }

    @Test
    void applyAddingAPinUpdatesEveryInstance() {
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(workingCopy.newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");

        history.execute(inside(new AddElementsCommand(List.of(pin))));

        assertThat(pinCount(first.id())).isEqualTo(3);
        assertThat(pinCount(second.id())).isEqualTo(3);
        history.undo();
        assertThat(pinCount(first.id())).isEqualTo(2);
    }

    @Test
    void anInstanceThatCannotTakeTheNewPinIsListed() {
        // the new input lands at (0,4) of the box grown to 4 x 6: a wire ends there at the first instance
        document.add(new Wire(document.newWireId(), new GridPoint(6, 14), new GridPoint(10, 14)));
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(workingCopy.newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");
        EditInsideCommand inside = inside(new AddElementsCommand(List.of(pin)));

        history.execute(inside);

        assertThat(inside.keptOldShapes()).containsExactly(new KeptOldShape(first.id(), inverter.id(), List.of("b")));
    }

    @Test
    void mergedWithMergesTypingInTheSameDefinition() {
        List<ElementId> gate = List.of(new ElementId("c2"));
        history.execute(inside(MoveElementsCommand.nudge(gate, 1, 0)));
        history.execute(inside(MoveElementsCommand.nudge(gate, 1, 0)));

        assertThat(workingCopy.component(gate.getFirst()).orElseThrow().position()).isEqualTo(new GridPoint(4, 0));
        history.undo();
        assertThat(history.canUndo()).isFalse();
        assertThat(workingCopy.component(gate.getFirst()).orElseThrow().position()).isEqualTo(new GridPoint(2, 0));
        assertThat(document.definition(inverter.id())).containsSame(inverter);
    }

    @Test
    void mergedWithRefusesAnotherDefinition() {
        document.putDefinition(ExampleDefinitions.byteBuffer());
        CircuitDocument otherCopy = ExampleDefinitions.byteBuffer().bodyDocument(document);
        List<ElementId> gate = List.of(new ElementId("c2"));
        EditInsideCommand here = inside(MoveElementsCommand.nudge(gate, 1, 0));
        EditInsideCommand there = new EditInsideCommand(ExampleDefinitions.BYTE_BUFFER,
                MoveElementsCommand.nudge(gate, 1, 0), otherCopy, registry);

        assertThat(here.mergedWith(there)).isEmpty();
        assertThat(here.mergedWith(inside(MoveElementsCommand.nudge(gate, 1, 0)))).isPresent();
    }

    @Test
    void aReplacementThatFailsLeavesTheWorkingCopyAsItWas() {
        List<DocumentElement> bodyBefore = workingCopy.elements();
        Component itself = new Component(workingCopy.newComponentId(), SubcircuitTypes.typeIdOf(inverter.id()),
                new GridPoint(30, 30), Rotation.R0, Map.of(), "");
        EditInsideCommand inside = inside(new AddElementsCommand(List.of(itself)));

        assertThatThrownBy(() -> history.execute(inside)).isInstanceOf(IllegalArgumentException.class);

        assertThat(workingCopy.elements()).containsExactlyElementsOf(bodyBefore);
        assertThat(document.definition(inverter.id())).containsSame(inverter);
        assertThat(history.canUndo()).isFalse();
    }

    private void assertOriginal(List<DocumentElement> bodyBefore) {
        assertThat(document.definition(inverter.id())).containsSame(inverter);
        assertThat(workingCopy.elements()).containsExactlyElementsOf(bodyBefore);
        assertThat(document.components()).containsExactly(first, second);
    }

    private EditInsideCommand inside(Command bodyCommand) {
        return new EditInsideCommand(inverter.id(), bodyCommand, workingCopy, registry);
    }

    private Component instance(String id, int x) {
        Component placed = new Component(new ElementId(id), SubcircuitTypes.typeIdOf(inverter.id()),
                new GridPoint(x, 10), Rotation.R0, Map.of(), "");
        document.add(placed);
        return placed;
    }

    private Component gateIn(CircuitDocument target, int x, int y) {
        return registry.require(StandardComponentTypes.BUFFER).newComponent(target.newComponentId(),
                new GridPoint(x, y), Rotation.R0, Map.of(), "");
    }

    private int pinCount(ElementId id) {
        return registry.typeOf(document.component(id).orElseThrow()).pinCount();
    }
}
