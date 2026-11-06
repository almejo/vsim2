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
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionUpdateProblem;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void aPieceOfItselfIsRefusedAndLeavesTheWorkingCopyAsItWas() {
        List<DocumentElement> bodyBefore = workingCopy.elements();
        Component itself = new Component(workingCopy.newComponentId(), SubcircuitTypes.typeIdOf(inverter.id()),
                new GridPoint(30, 30), Rotation.R0, Map.of(), "");
        EditInsideCommand inside = inside(new AddElementsCommand(List.of(itself)));

        assertThatThrownBy(() -> history.execute(inside)).isInstanceOfSatisfying(InsideEditRefusedException.class,
                refusal -> assertThat(refusal.messageKey()).isEqualTo("inside.cannotNestItself"));

        assertThat(workingCopy.elements()).containsExactlyElementsOf(bodyBefore);
        assertThat(document.definition(inverter.id())).containsSame(inverter);
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void pastingAPieceThatContainsTheEditedComponentIsRefusedWithItsName() {
        // final review I2: a CPU piece pasted inside the ALU it contains
        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();
        List<DocumentElement> bodyBefore = workingCopy.elements();
        Component container = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(doubleInverter.id()),
                new GridPoint(0, 0), Rotation.R0, Map.of(), "");
        CircuitFragment fragment = new CircuitFragment(List.of(container), List.of(), List.of(),
                List.of(inverter, doubleInverter));
        EditInsideCommand inside = inside(new PasteCommand(fragment, new GridPoint(40, 40)));

        assertThatThrownBy(() -> history.execute(inside)).isInstanceOfSatisfying(InsideEditRefusedException.class,
                refusal -> {
                    assertThat(refusal.messageKey()).isEqualTo("inside.pieceContainsThisComponent");
                    assertThat(refusal.arguments()).containsExactly(doubleInverter.name());
                });

        assertThat(workingCopy.elements()).containsExactlyElementsOf(bodyBefore);
        assertThat(document.definition(inverter.id())).containsSame(inverter);
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void aPinAddedToANestedDefinitionReachesTheContainingBodies() {
        ComponentDefinition doubleInverter = placeDoubleInverter();
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(workingCopy.newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");

        history.execute(inside(new AddElementsCommand(List.of(pin))));

        // its pieces are drawn as the inverter: they follow through the definition, the body itself stays as it is
        ComponentDefinition container = document.definition(ExampleDefinitions.DOUBLE_INVERTER).orElseThrow();
        assertThat(container).isSameAs(doubleInverter);
        for (Component nested : container.body().components().subList(1, 3)) {
            assertThat(registry.typeOf(nested).pinCount()).as(nested.id().value()).isEqualTo(3);
        }
        assertThat(pinCount(first.id())).isEqualTo(3);
    }

    @Test
    void whatCannotFollowInsideIsListedWithItsContainer() {
        ComponentDefinition busy = placeBusyDoubleInverter();
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(workingCopy.newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");
        EditInsideCommand inside = inside(new AddElementsCommand(List.of(pin)));

        history.execute(inside);

        assertThat(inside.nestedProblems()).containsExactly(new DefinitionUpdateProblem(
                ExampleDefinitions.DOUBLE_INVERTER, new PinRef(new ElementId("c3"), 2),
                DefinitionUpdateProblem.Kind.PLACED_ELSEWHERE));
        ComponentDefinition rewritten = document.definition(ExampleDefinitions.DOUBLE_INVERTER).orElseThrow();
        assertThat(rewritten.revision()).isEqualTo(busy.revision() + 1);
        assertThat(rewritten.pinout()).isEqualTo(busy.pinout());
        assertThat(rewritten.size()).isEqualTo(busy.size());
        Component kept = rewritten.body().components().get(2);
        assertThat(kept.appearance()).isPresent();
        assertThat(registry.typeOf(kept).pinLocation(kept, 2)).isNotEqualTo(new GridPoint(6, 4));
    }

    @Test
    void undoRestoresEveryContainingDefinition() {
        ComponentDefinition doubleInverter = placeBusyDoubleInverter();
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(workingCopy.newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");
        history.execute(inside(new AddElementsCommand(List.of(pin))));
        ComponentDefinition rewritten = document.definition(ExampleDefinitions.DOUBLE_INVERTER).orElseThrow();

        assertThat(rewritten).isNotSameAs(doubleInverter);

        history.undo();
        assertThat(document.definition(ExampleDefinitions.DOUBLE_INVERTER)).containsSame(doubleInverter);
        assertThat(document.definition(inverter.id())).containsSame(inverter);

        history.redo();
        assertThat(document.definition(ExampleDefinitions.DOUBLE_INVERTER)).containsSame(rewritten);
    }

    @Test
    void theRewriteKeepsTheIdsAndTheMemoryContentsOfNestedPieces() {
        // the second inverter's new pin lands on a wire, so the container's body is rewritten
        ComponentDefinition busy = placeBusyDoubleInverter();
        CircuitDocument holderBody = busy.bodyDocument(document);
        Component nested = holderBody.component(new ElementId("c2")).orElseThrow();
        holderBody.replace(nested.withMemoryContents(Map.of("c10", "v3.0 hex words\n1")));
        document.putDefinition(withBody(busy, holderBody));
        Component outer = new Component(new ElementId("c31"), SubcircuitTypes.typeIdOf(ExampleDefinitions
                .DOUBLE_INVERTER), new GridPoint(10, 40), Rotation.R0, Map.of(), "")
                .withMemoryContents(Map.of("c2/c10", "v3.0 hex words\n2"));
        document.add(outer);
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(workingCopy.newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");

        history.execute(inside(new AddElementsCommand(List.of(pin))));

        ComponentDefinition container = document.definition(ExampleDefinitions.DOUBLE_INVERTER).orElseThrow();
        assertThat(container.body().components()).extracting(Component::id)
                .containsExactlyElementsOf(holderBody.components().stream().map(Component::id).toList());
        Component rewritten = container.body().components().get(1);
        assertThat(rewritten.id()).isEqualTo(new ElementId("c2"));
        assertThat(rewritten.memoryContents()).containsEntry("c10", "v3.0 hex words\n1");
        assertThat(registry.typeOf(rewritten).pinCount()).isEqualTo(3);
        assertThat(document.component(outer.id()).orElseThrow().memoryContents())
                .containsEntry("c2/c10", "v3.0 hex words\n2");
    }

    @Test
    void aMergeThatFailsLeavesTheDefinitionAndTheCopyWithTheFirstEditApplied() {
        // final review M1: the merged inside edit throws after the top was reverted
        ElementId gate = new ElementId("c2");
        history.execute(inside(new LabelThatMergesIntoAFailure(gate, "a")));
        ComponentDefinition afterFirst = document.definition(inverter.id()).orElseThrow();

        history.execute(inside(new SetLabelCommand(gate, "ab")));

        assertThat(workingCopy.component(gate).orElseThrow().label()).isEqualTo("ab");
        history.undo();
        assertThat(document.definition(inverter.id()).orElseThrow().sameContentAs(afterFirst)).isTrue();
        assertThat(workingCopy.component(gate).orElseThrow().label()).isEqualTo("a");
        history.undo();
        assertThat(document.definition(inverter.id()).orElseThrow().sameContentAs(inverter)).isTrue();
        assertThat(workingCopy.component(gate).orElseThrow().label()).isEmpty();
    }

    /** Sets a label; whatever follows it merges into a command that throws when applied. */
    private record LabelThatMergesIntoAFailure(ElementId id, String text) implements Command {
        @Override
        public void apply(CircuitDocument target) {
            target.replace(target.component(id).orElseThrow().withLabel(text));
        }

        @Override
        public void revert(CircuitDocument target) {
            target.replace(target.component(id).orElseThrow().withLabel(""));
        }

        @Override
        public String description() {
            return "command.label";
        }

        @Override
        public Optional<Command> mergedWith(Command next) {
            return Optional.of(new Command() {
                @Override
                public void apply(CircuitDocument target) {
                    throw new IllegalStateException("the merged edit cannot apply");
                }

                @Override
                public void revert(CircuitDocument target) {
                }

                @Override
                public String description() {
                    return "command.label";
                }
            });
        }
    }

    @Test
    void aWorkingCopyThatNoLongerMatchesIsBroughtToTheDefinitionOnUndoAndRedo() {
        // final review I4: a released copy that followed an outside change before its tab closed
        List<DocumentElement> bodyBefore = workingCopy.elements();
        history.execute(inside(new SetLabelCommand(new ElementId("c2"), "E1")));
        ComponentDefinition labelled = document.definition(inverter.id()).orElseThrow();
        workingCopy.remove(new ElementId("c2"));

        history.undo();

        assertOriginal(bodyBefore);
        workingCopy.remove(new ElementId("c3"));
        history.redo();
        assertThat(document.definition(inverter.id())).containsSame(labelled);
        assertThat(workingCopy.component(new ElementId("c2")).orElseThrow().label()).isEqualTo("E1");
        assertThat(workingCopy.component(new ElementId("c3"))).isPresent();
    }

    @Test
    void aChangeOfAnotherDefinitionMadeThroughTheWorkingCopyIsRefusedWithNothingChanged() {
        // the working copy holds copies of the document's definitions: changing one there would never reach the
        // document, so the edit is refused instead of being kept only in the tab (final review C1)
        ComponentDefinition doubleInverter = placeDoubleInverter();
        CircuitDocument containerCopy = doubleInverter.bodyDocument(document);
        EditInsideCommand inside = new EditInsideCommand(doubleInverter.id(),
                new ChangeDefinitionDescriptionCommand(inverter.id(), "nueva"), containerCopy, registry);

        assertThatThrownBy(() -> history.execute(inside)).isInstanceOfSatisfying(InsideEditRefusedException.class,
                refusal -> {
                    assertThat(refusal.messageKey()).isEqualTo("inside.otherDefinitionChanged");
                    assertThat(refusal.arguments()).containsExactly(inverter.name());
                });

        assertThat(containerCopy.definition(inverter.id())).contains(inverter);
        assertThat(document.definition(inverter.id())).containsSame(inverter);
        assertThat(document.definition(doubleInverter.id())).containsSame(doubleInverter);
        assertThat(history.canUndo()).isFalse();
    }

    /** The double inverter (two inverters inside) in the document, one piece of it placed; returns the definition. */
    private ComponentDefinition placeDoubleInverter() {
        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();
        document.putDefinition(doubleInverter);
        document.add(new Component(new ElementId("c30"), SubcircuitTypes.typeIdOf(doubleInverter.id()),
                new GridPoint(10, 40), Rotation.R0, Map.of(), ""));
        return document.definition(doubleInverter.id()).orElseThrow();
    }

    /**
     * {@link #placeDoubleInverter()} with a wire in its body ending at (6,4), where the second inner inverter (at 6,0)
     * puts a new input «b»: that piece cannot take it there.
     */
    private ComponentDefinition placeBusyDoubleInverter() {
        placeDoubleInverter();
        CircuitDocument holderBody = ExampleDefinitions.doubleInverter().bodyDocument(document);
        holderBody.add(new Wire(new ElementId("w90"), new GridPoint(6, 4), new GridPoint(8, 4)));
        document.putDefinition(withBody(ExampleDefinitions.doubleInverter(), holderBody));
        return document.definition(ExampleDefinitions.DOUBLE_INVERTER).orElseThrow();
    }

    private static ComponentDefinition withBody(ComponentDefinition definition, CircuitDocument body) {
        return new ComponentDefinition(definition.id(), definition.name(), definition.description(),
                definition.size(), definition.pinout(), new CircuitFragment(body.components(), body.wires(),
                body.labels()), definition.appearance(), definition.revision());
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
