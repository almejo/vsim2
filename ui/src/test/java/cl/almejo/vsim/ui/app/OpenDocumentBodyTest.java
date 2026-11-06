package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.ReplaceDefinitionCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A definition's body opened as a place of its document (spec §6, §10.3–10.5): the working copy shares the document's
 * history and simulation, shows the context instance's values, writes every edit back as one undo step (rewriting the
 * bodies that contain the definition when its pins change), and follows changes made elsewhere.
 */
class OpenDocumentBodyTest {

    /** A definition «bloque» with a switch c2 inside (and an input pin c1, so it has an interface). */
    private static final String BLOCK = "bloque";
    private static final ElementId INNER_SWITCH = new ElementId("c2");
    private static final ElementId FIRST = new ElementId("c5");
    private static final ElementId SECOND = new ElementId("c9");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final OpenDocument openDocument = OpenDocument.createEmpty(registry);
    private final List<OpenDocument> bodies = new ArrayList<>();

    @AfterEach
    void closeAll() {
        bodies.forEach(OpenDocument::close);
        openDocument.close();
    }

    @Test
    void openBodyShowsTheWorkingCopyWithTheSameIds() {
        withTwoBlocks();

        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));

        assertThat(body.document().components()).containsExactlyElementsOf(block().body().components());
        assertThat(body.isBody()).isTrue();
        assertThat(body.parent()).containsSame(openDocument);
        assertThat(body.definitionId()).contains(BLOCK);
        assertThat(body.contextPath()).contains(FIRST.value());
        assertThat(body.history()).isSameAs(openDocument.history());
        assertThat(body.registry()).isSameAs(openDocument.registry());
        assertThat(body.definitionName()).isEqualTo("Bloque");
        assertThat(body.displayName()).isEqualTo(openDocument.displayName());
        assertThat(openDocument.isBody()).isFalse();
        assertThat(openDocument.definitionName()).isEqualTo(openDocument.displayName());
        assertThatThrownBy(() -> openDocument.openBody("missing", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeInsideIsOneUndoStepOnTheParent() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        List<DocumentElement> before = body.document().elements();
        Component gate = gate(body, 20, 20);

        body.execute(new AddElementsCommand(List.of(gate)));

        assertThat(openDocument.document().definition(BLOCK).orElseThrow().body().components()).contains(gate);
        assertThat(openDocument.document().definition(BLOCK).orElseThrow().revision()).isEqualTo(2);
        assertThat(openDocument.history().canUndo()).isTrue();
        assertThat(openDocument.history().undoCommand().orElseThrow().description()).isEqualTo("command.add");
        assertThat(OpenDocument.insideEditOf(openDocument.history().undoCommand().orElseThrow()))
                .map(EditInsideCommand::placeName).contains("Bloque");
        assertThat(openDocument.isModified()).isTrue();

        openDocument.history().undo();

        assertThat(body.document().elements()).containsExactlyElementsOf(before);
        assertThat(openDocument.document().definition(BLOCK)).contains(block());
        openDocument.history().redo();
        assertThat(body.document().component(gate.id())).contains(gate);
        openDocument.history().undo();
        assertThat(body.document().elements()).containsExactlyElementsOf(before);
    }

    @Test
    void nudgesInsideMergeIntoOneUndoStep() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        Command placed = openDocument.history().undoCommand().orElseThrow();

        body.execute(MoveElementsCommand.nudge(List.of(INNER_SWITCH), 1, 0));
        body.execute(MoveElementsCommand.nudge(List.of(INNER_SWITCH), 1, 0));
        openDocument.history().undo();

        assertThat(openDocument.history().undoCommand()).containsSame(placed);
        assertThat(openDocument.document().definition(BLOCK)).contains(block());
    }

    @Test
    void latestSnapshotIsRemappedUnderTheContext() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        awaitSimulation(openDocument);

        body.interact(INNER_SWITCH);
        awaitSimulation(openDocument);

        assertThat(body.latestSnapshot().componentState(INNER_SWITCH.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ONE));
        assertThat(body.latestSnapshot().token()).isEqualTo(body.netlistToken());
        AtomicInteger changes = new AtomicInteger();
        body.addChangeListener(changes::incrementAndGet);
        body.setContextPath(Optional.of(SECOND.value()));
        assertThat(changes).hasValue(1);
        assertThat(body.latestSnapshot().componentState(INNER_SWITCH.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ZERO));
    }

    @Test
    void sinValoresShowsNothingAndIgnoresInputs() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.empty());
        awaitSimulation(openDocument);

        body.interact(INNER_SWITCH);
        SimulationSnapshot parent = awaitSimulation(openDocument);

        assertThat(body.latestSnapshot().componentStates()).isEmpty();
        assertThat(body.latestSnapshot().netValues()).isEmpty();
        assertThat(body.latestSnapshot().token()).isNotEqualTo(body.netlistToken());
        assertThat(parent.componentState(FIRST.value() + "/" + INNER_SWITCH.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ZERO));
        assertThat(parent.componentState(SECOND.value() + "/" + INNER_SWITCH.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ZERO));
    }

    @Test
    void interactInsideDrivesOneInstanceOnlyAndEditsNothing() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        long revision = openDocument.document().revision();

        body.interact(INNER_SWITCH);
        SimulationSnapshot parent = awaitSimulation(openDocument);

        assertThat(parent.componentState(FIRST.value() + "/" + INNER_SWITCH.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ONE));
        assertThat(parent.componentState(SECOND.value() + "/" + INNER_SWITCH.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ZERO));
        assertThat(openDocument.document().revision()).isEqualTo(revision);
        assertThat(body.document().component(INNER_SWITCH)).contains(block().body().components().get(1));
        assertThat(openDocument.isModified()).isFalse();
    }

    @Test
    void twoBodiesOfOneDefinitionShareTheWorkingCopy() {
        withTwoBlocks();
        OpenDocument first = body(BLOCK, Optional.of(FIRST.value()));
        OpenDocument second = body(BLOCK, Optional.of(SECOND.value()));
        assertThat(second.document()).isSameAs(first.document());

        first.close();
        second.execute(new AddElementsCommand(List.of(gate(second, 20, 20))));
        assertThat(openDocument.document().definition(BLOCK).orElseThrow().revision()).isEqualTo(2);
        second.close();

        OpenDocument third = body(BLOCK, Optional.empty());
        assertThat(third.document()).isNotSameAs(first.document());
        assertThat(third.document().components()).hasSize(3);
    }

    @Test
    void refreshFromTheDocumentKeepsIdsAndNotifies() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        AtomicInteger refreshes = new AtomicInteger();
        body.addRefreshListener(refreshes::incrementAndGet);
        Component extra = registry.require(StandardComponentTypes.BUFFER).newComponent(new ElementId("c7"),
                new GridPoint(10, 10), Rotation.R0, Map.of(), "");
        List<Component> components = new ArrayList<>(block().body().components());
        components.add(extra);
        ComponentDefinition updated = new ComponentDefinition(BLOCK, "Bloque", "", block().size(), block().pinout(),
                new CircuitFragment(components, List.of(), List.of()), Optional.empty(), 2);

        openDocument.execute(ReplaceDefinitionCommand.prepare(openDocument.document(), openDocument.registry(),
                new DefinitionBundle(updated, List.of())));

        assertThat(body.document().components()).containsExactlyElementsOf(components);
        assertThat(refreshes).hasValue(1);
        body.execute(new AddElementsCommand(List.of(gate(body, 30, 30))));
        assertThat(refreshes).hasValue(1);
        openDocument.history().undo();
        assertThat(refreshes).hasValue(2);
    }

    @Test
    void contextFallsBackWhenItsInstanceIsDeleted() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        AtomicInteger changes = new AtomicInteger();
        body.addChangeListener(changes::incrementAndGet);

        openDocument.execute(new RemoveElementsCommand(List.of(FIRST)));
        assertThat(body.contextPath()).contains(SECOND.value());
        assertThat(changes.get()).isPositive();

        openDocument.execute(new RemoveElementsCommand(List.of(SECOND)));
        assertThat(body.contextPath()).isEmpty();
        assertThat(body.instancePaths(BLOCK)).isEmpty();
    }

    @Test
    void removedDefinitionNotifies() {
        openDocument.execute(new PutDefinitionsCommand(List.of(block())));
        OpenDocument body = body(BLOCK, Optional.empty());
        AtomicInteger removals = new AtomicInteger();
        body.addBodyRemovedListener(removals::incrementAndGet);

        openDocument.history().undo();

        assertThat(removals).hasValue(1);
    }

    @Test
    void anInterfaceChangeOfANestedDefinitionAppliesAsOneUndoStep() {
        withInverterInsideDoubleInverterAndOnItsOwn();
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c30"));
        ComponentDefinition inverter = openDocument.document().definition(ExampleDefinitions.INVERTER).orElseThrow();
        ComponentDefinition container = openDocument.document().definition(ExampleDefinitions.DOUBLE_INVERTER)
                .orElseThrow();

        body.execute(new RemoveElementsCommand(List.of(new ElementId("c1"))));

        assertThat(openDocument.document().definition(ExampleDefinitions.INVERTER).orElseThrow().pinout())
                .extracting(pin -> pin.spec().name()).containsExactly("y");
        Component nested = openDocument.document().definition(ExampleDefinitions.DOUBLE_INVERTER).orElseThrow()
                .body().components().get(1);
        assertThat(openDocument.registry().typeOf(nested).pinCount()).isEqualTo(1);
        assertThat(body.document().component(new ElementId("c1"))).isEmpty();

        openDocument.history().undo();

        assertThat(openDocument.document().definition(ExampleDefinitions.INVERTER)).containsSame(inverter);
        assertThat(openDocument.document().definition(ExampleDefinitions.DOUBLE_INVERTER)).containsSame(container);
        assertThat(body.document().component(new ElementId("c1"))).isPresent();
    }

    @Test
    void anEditRefusedWhileItAppliesChangesNothingAndIsThrownWithoutAListener() {
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        List<DocumentElement> before = body.document().elements();
        ComponentDefinition definition = block();
        Command placed = openDocument.history().undoCommand().orElseThrow();
        Command refusing = new Command() {
            @Override
            public void apply(CircuitDocument target) {
                throw new InsideEditRefusedException("inside.cannotNestItself", List.of());
            }

            @Override
            public void revert(CircuitDocument target) {
            }

            @Override
            public String description() {
                return "command.add";
            }
        };

        assertThatThrownBy(() -> body.execute(refusing)).isInstanceOf(InsideEditRefusedException.class);
        List<InsideEditRefusedException> told = new ArrayList<>();
        body.addRefusalListener(told::add);
        body.execute(refusing);

        assertThat(told).extracting(InsideEditRefusedException::messageKey).containsExactly("inside.cannotNestItself");
        assertThat(body.document().elements()).containsExactlyElementsOf(before);
        assertThat(openDocument.document().definition(BLOCK)).contains(definition);
        assertThat(openDocument.history().undoCommand()).containsSame(placed);
    }

    @Test
    void undoAndRedoGoOnAfterTheTabOfACopyThatFollowedAnOutsideChangeClosed() {
        // final review I4: the copy followed X, its tab closed, X was undone; the inside edit before X then goes back
        withTwoBlocks();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        body.execute(new SetLabelCommand(INNER_SWITCH, "E1"));
        ComponentDefinition labelled = openDocument.document().definition(BLOCK).orElseThrow();
        ComponentDefinition withoutSwitch = new ComponentDefinition(BLOCK, labelled.name(), "", labelled.size(),
                labelled.pinout(), new CircuitFragment(List.of(labelled.body().components().getFirst()), List.of(),
                List.of())).revisedFrom(labelled);
        openDocument.execute(ReplaceDefinitionCommand.prepare(openDocument.document(), openDocument.registry(),
                new DefinitionBundle(withoutSwitch, List.of())));
        assertThat(body.document().component(INNER_SWITCH)).isEmpty();
        body.close();

        openDocument.history().undo();
        openDocument.history().undo();

        assertThat(openDocument.document().definition(BLOCK)).contains(block());
        openDocument.history().redo();
        assertThat(openDocument.document().definition(BLOCK)).contains(labelled);
        OpenDocument reopened = body(BLOCK, Optional.of(FIRST.value()));
        assertThat(reopened.document().component(INNER_SWITCH).orElseThrow().label()).isEqualTo("E1");
    }

    @Test
    void pastingAPieceThatContainsThisComponentIsToldToTheTabNotThrown() {
        withInverterInsideDoubleInverterAndOnItsOwn();
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c30"));
        List<DocumentElement> before = body.document().elements();
        List<InsideEditRefusedException> told = new ArrayList<>();
        body.addRefusalListener(told::add);
        Component container = new Component(new ElementId("c1"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), new GridPoint(0, 0), Rotation.R0,
                Map.of(), "");
        CircuitFragment fragment = new CircuitFragment(List.of(container), List.of(), List.of(),
                List.of(ExampleDefinitions.inverter(), ExampleDefinitions.doubleInverter()));
        Command placed = openDocument.history().undoCommand().orElseThrow();

        body.execute(new PasteCommand(fragment, new GridPoint(40, 40)));

        assertThat(told).extracting(InsideEditRefusedException::messageKey)
                .containsExactly("inside.pieceContainsThisComponent");
        assertThat(body.document().elements()).containsExactlyElementsOf(before);
        assertThat(openDocument.history().undoCommand()).containsSame(placed);
    }

    @Test
    void aNestedOwnShapedPieceFollowsANewPin() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                parentWithOwnShapedInverter())));
        place("c40", SubcircuitTypes.typeIdOf("parent"), 0);
        ComponentDefinition parent = openDocument.document().definition("parent").orElseThrow();
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c40/c2"));
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(body.document().newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");

        body.execute(new AddElementsCommand(List.of(pin)));

        assertThat(body.document().component(pin.id())).isPresent();
        Component nested = openDocument.document().definition("parent").orElseThrow().body().components()
                .getFirst();
        assertThat(nested.appearance().orElseThrow().pins()).hasSize(3);
        openDocument.history().undo();
        assertThat(openDocument.document().definition("parent")).containsSame(parent);
    }

    @Test
    void whatCouldNotFollowInsideAContainingBodyIsListedUnderEveryPlaceOfIt() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                busyDoubleInverter())));
        place("c31", SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 30);
        place("c32", SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 60);
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c31/c2"));
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(body.document().newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");

        body.execute(new AddElementsCommand(List.of(pin)));

        assertThat(openDocument.insideProblems()).extracting(ConnectivityProblem::elementId,
                        ConnectivityProblem::pinIndex, ConnectivityProblem::cause)
                .containsExactly(
                        tuple("c31/c3", 2, ConnectivityProblem.Cause.PIN_PLACED_ELSEWHERE),
                        tuple("c32/c3", 2, ConnectivityProblem.Cause.PIN_PLACED_ELSEWHERE));
        assertThat(body.insideProblems()).isEqualTo(openDocument.insideProblems());
        openDocument.history().undo();
        assertThat(openDocument.insideProblems()).isEmpty();
    }

    @Test
    void keptOldShapesListsTheInstanceThatCouldNotTakeThePin() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component busy = place("c20", SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 10);
        openDocument.execute(new AddElementsCommand(List.of(new Wire(new ElementId("w90"), new GridPoint(6, 14),
                new GridPoint(10, 14)))));
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c20"));
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(body.document().newComponentId(),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "b");

        body.execute(new AddElementsCommand(List.of(pin)));

        assertThat(openDocument.keptOldShapes())
                .containsExactly(new KeptOldShape(busy.id(), ExampleDefinitions.INVERTER, List.of("b")));
        openDocument.history().undo();
        assertThat(openDocument.keptOldShapes()).isEmpty();
    }

    @Test
    void connectivityProblemsInsideAreTheParentsUnderTheContext() {
        withTwoBlocks();
        openDocument.execute(new PutDefinitionsCommand(List.of(leaf())));
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        Component nested = new Component(body.document().newComponentId(), SubcircuitTypes.typeIdOf("hoja"),
                new GridPoint(20, 20), Rotation.R0, Map.of(), "");

        body.execute(new AddElementsCommand(List.of(nested)));

        assertThat(openDocument.connectivityProblems()).extracting(ConnectivityProblem::elementId)
                .contains(FIRST.value() + "/" + nested.id().value(), SECOND.value() + "/" + nested.id().value());
        assertThat(body.connectivityProblems()).extracting(ConnectivityProblem::elementId)
                .containsExactly(nested.id().value());
        assertThat(body.connectivityProblems()).extracting(ConnectivityProblem::elementId)
                .noneMatch(id -> id.contains("/"));
        assertThat(body.loosenedPins()).isEmpty();
    }

    @Test
    void closeReleasesTheParentListeners() {
        withTwoBlocks();
        int baseline = openDocument.changeListenerCount();
        OpenDocument body = body(BLOCK, Optional.of(FIRST.value()));
        body.addRefreshListener(() -> { });
        assertThat(openDocument.changeListenerCount()).isGreaterThan(baseline);

        body.close();

        assertThat(openDocument.changeListenerCount()).isEqualTo(baseline);
        assertThat(openDocument.document().components()).hasSize(2);
    }

    @Test
    void openingWithAContextThatIsGoneFallsBackToTheFirstUseOrToNoValues() {
        withTwoBlocks();

        assertThat(body(BLOCK, Optional.of("c404")).contextPath()).contains(FIRST.value());
        openDocument.execute(new PutDefinitionsCommand(List.of(leaf())));
        assertThat(body("hoja", Optional.of("c404")).contextPath()).isEmpty();
    }

    private OpenDocument body(String definitionId, Optional<String> context) {
        OpenDocument body = openDocument.openBody(definitionId, context);
        bodies.add(body);
        return body;
    }

    /** «bloque» placed twice: c5 and c9. */
    private void withTwoBlocks() {
        openDocument.execute(new PutDefinitionsCommand(List.of(block())));
        place(FIRST.value(), SubcircuitTypes.typeIdOf(BLOCK), 0);
        place(SECOND.value(), SubcircuitTypes.typeIdOf(BLOCK), 30);
        openDocument.history().markSaved();
    }

    /** The inverter inside a placed double inverter (c31) and on its own (c30). */
    private void withInverterInsideDoubleInverterAndOnItsOwn() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        place("c30", SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0);
        place("c31", SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 30);
    }

    private Component place(String id, String typeId, int x) {
        Component component = new Component(new ElementId(id), typeId, new GridPoint(x, 10), Rotation.R0, Map.of(),
                "");
        openDocument.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    private Component gate(OpenDocument place, int x, int y) {
        return registry.require(StandardComponentTypes.BUFFER).newComponent(place.document().newComponentId(),
                new GridPoint(x, y), Rotation.R0, Map.of(), "");
    }

    private ComponentDefinition block() {
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "a");
        Component inner = registry.require(StandardComponentTypes.SWITCH).newComponent(INNER_SWITCH,
                new GridPoint(0, 0), Rotation.R0, Map.of(StandardComponentTypes.VALUE, "0"), "");
        return new ComponentDefinition(BLOCK, "Bloque", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id())),
                new CircuitFragment(List.of(input, inner), List.of(), List.of()));
    }

    /** «hoja»: an input pin «a» that nothing inside uses (each piece of it shows an unconnected terminal). */
    private ComponentDefinition leaf() {
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 10), Rotation.R0, Map.of(), "a");
        return new ComponentDefinition("hoja", "Hoja", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of()));
    }

    /** «parent» holding one inverter c2 with its own 4 x 4 shape. */
    /**
     * The double inverter with a wire in its body ending at (6,4), where its second inverter (at 6,0) puts a new input
     * «b»: that piece cannot take it there.
     */
    private static ComponentDefinition busyDoubleInverter() {
        ComponentDefinition plain = ExampleDefinitions.doubleInverter();
        CircuitFragment body = plain.body();
        return new ComponentDefinition(plain.id(), plain.name(), plain.description(), plain.size(), plain.pinout(),
                new CircuitFragment(body.components(), List.of(new Wire(new ElementId("w90"), new GridPoint(6, 4),
                        new GridPoint(8, 4))), body.labels()));
    }

    private static ComponentDefinition parentWithOwnShapedInverter() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        ComponentAppearance own = new ComponentAppearance(new GridSize(4, 4), Optional.empty(), List.of(
                new PinPlacement(inverter.pinout().getFirst().source(), new GridPoint(0, 2)),
                new PinPlacement(inverter.pinout().getLast().source(), new GridPoint(4, 2))));
        Component nested = new Component(new ElementId("c2"), SubcircuitTypes.typeIdOf(inverter.id()),
                new GridPoint(2, 0), Rotation.R0, Map.of(), "").withAppearance(Optional.of(own));
        return new ComponentDefinition("parent", "Padre", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(nested), List.of(), List.of()));
    }
}
