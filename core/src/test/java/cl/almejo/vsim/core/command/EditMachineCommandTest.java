package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A machine edit is one undo step; a new name in a condition adds a pin and the placed pieces' wires follow. */
class EditMachineCommandTest {

    private static final String MACHINE_ID = "maquina-1";

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final History history = new History(document);
    private final ComponentDefinition original = MachineDefinitions.create(MACHINE_ID, "Máquina 1");
    private final Component piece;

    EditMachineCommandTest() {
        document.putDefinition(original);
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        piece = new Component(new ElementId("c20"), SubcircuitTypes.typeIdOf(MACHINE_ID), new GridPoint(10, 10),
                Rotation.R0, Map.of(), "");
        document.add(piece);
    }

    @Test
    void aNewNameInAConditionAddsAPinAndTheWiresFollow() {
        GridPoint resetBefore = pinLocation("RST");
        document.add(new Wire(new ElementId("w30"), resetBefore.plus(new GridPoint(-4, 0)), resetBefore));
        List<Wire> wiresBefore = document.wires();

        history.execute(new EditMachineCommand(MACHINE_ID, withSelfLoop("x")));

        ComponentDefinition edited = machineDefinition();
        assertThat(edited.machine().orElseThrow().inputNames()).containsExactly("x");
        assertThat(pinNames(edited)).containsExactly("x", "RST", "CLK");
        GridPoint resetAfter = pinLocation("RST");
        assertThat(resetAfter).isNotEqualTo(resetBefore);
        assertThat(document.wires()).anySatisfy(wire ->
                assertThat(List.of(wire.start(), wire.end())).contains(resetAfter));

        history.undo();
        assertThat(document.definition(MACHINE_ID)).containsSame(original);
        assertThat(document.wires()).containsExactlyInAnyOrderElementsOf(wiresBefore);
        assertThat(pinLocation("RST")).isEqualTo(resetBefore);

        history.redo();
        assertThat(pinNames(machineDefinition())).containsExactly("x", "RST", "CLK");
    }

    @Test
    void renamingAStateDoesNotTouchTheBody() {
        StateMachine renamed = original.machine().orElseThrow().withState("s0", state -> state.withName("Inicio"));

        history.execute(new EditMachineCommand(MACHINE_ID, renamed));

        ComponentDefinition edited = machineDefinition();
        assertThat(edited.machine()).contains(renamed);
        assertThat(edited.body()).isEqualTo(original.body());
        assertThat(edited.pinout()).isEqualTo(original.pinout());
        assertThat(edited.revision()).isEqualTo(original.revision() + 1);
        assertThat(document.component(piece.id())).contains(piece);

        history.undo();
        assertThat(document.definition(MACHINE_ID)).containsSame(original);
    }

    @Test
    void anEditFromTheMachinesTabIsOneUndoStepOfTheDocumentAndTheTabsCopyFollows() {
        CircuitDocument workingCopy = original.bodyDocument(document);
        EditInsideCommand inside = new EditInsideCommand(MACHINE_ID, new EditMachineCommand(MACHINE_ID,
                withSelfLoop("x")), workingCopy, registry, Optional.of("c20"));

        history.execute(inside);

        assertThat(pinNames(machineDefinition())).containsExactly("x", "RST", "CLK");
        assertThat(workingCopy.components()).extracting(Component::label).contains("x");
        assertThat(inside.description()).isEqualTo("command.editMachine");

        history.undo();
        assertThat(document.definition(MACHINE_ID)).containsSame(original);
        assertThat(history.canUndo()).isFalse();
        assertThat(workingCopy.components()).extracting(Component::label).doesNotContain("x");
    }

    @Test
    void aCircuitEditInsideAMachineIsRefusedWhoeverMakesItAndNothingChanges() {
        CircuitDocument workingCopy = original.bodyDocument(document);
        Component lamp = new Component(new ElementId("c99"), StandardComponentTypes.LED, new GridPoint(40, 40),
                Rotation.R0, Map.of(), "");
        EditInsideCommand inside = new EditInsideCommand(MACHINE_ID, new AddElementsCommand(List.of(lamp)),
                workingCopy, registry);

        assertThatThrownBy(() -> history.execute(inside)).isInstanceOfSatisfying(InsideEditRefusedException.class,
                refusal -> assertThat(refusal.messageKey()).isEqualTo(EditInsideCommand.MACHINE_REFUSAL));

        assertThat(document.definition(MACHINE_ID)).containsSame(original);
        assertThat(workingCopy.components()).doesNotContain(lamp);
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void anotherMachinesEditInsideAMachineIsRefusedToo() {
        ComponentDefinition other = MachineDefinitions.create("maquina-otra", "Máquina 2");
        document.putDefinition(other);
        EditInsideCommand inside = new EditInsideCommand(MACHINE_ID, new EditMachineCommand(other.id(),
                withSelfLoop("y")), original.bodyDocument(document), registry);

        assertThatThrownBy(() -> history.execute(inside)).isInstanceOfSatisfying(InsideEditRefusedException.class,
                refusal -> assertThat(refusal.messageKey()).isEqualTo(EditInsideCommand.MACHINE_REFUSAL));

        assertThat(document.definition(other.id())).containsSame(other);
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    void theDescriptionIsEditMachine() {
        assertThat(new EditMachineCommand(MACHINE_ID, StateMachine.initial()).description())
                .isEqualTo("command.editMachine");
    }

    private StateMachine withSelfLoop(String condition) {
        return original.machine().orElseThrow().withTransitions(List.of(
                new MachineTransition("t0", "s0", "s0", condition, List.of())));
    }

    private ComponentDefinition machineDefinition() {
        return document.definition(MACHINE_ID).orElseThrow();
    }

    private GridPoint pinLocation(String name) {
        Component current = document.component(piece.id()).orElseThrow();
        int index = machineDefinition().pinIndexOf(name).orElseThrow();
        return registry.typeOf(current).pinLocation(current, index);
    }

    private static List<String> pinNames(ComponentDefinition definition) {
        return definition.pinout().stream().map(pin -> pin.spec().name()).toList();
    }

    @Test
    void renamingAnInputKeepsTheWireOnItsPinAndUndoGivesTheOldNameBack() {
        history.execute(new EditMachineCommand(MACHINE_ID, withSelfLoop("x")));
        GridPoint inputPin = pinLocation("x");
        Wire wire = new Wire(new ElementId("w31"), inputPin.plus(new GridPoint(-4, 0)), inputPin);
        document.add(wire);
        StateMachine wired = machineDefinition().machine().orElseThrow();

        EditMachineCommand rename = new EditMachineCommand(MACHINE_ID,
                MachinePinEdits.renamed(wired, MachinePinEdits.Side.INPUT, "x", "a"));
        history.execute(rename);

        assertThat(pinNames(machineDefinition())).containsExactly("a", "RST", "CLK");
        assertThat(machineDefinition().machine().orElseThrow().transition("t0").orElseThrow().condition())
                .isEqualTo("a");
        assertThat(pinLocation("a")).isEqualTo(inputPin);
        assertThat(document.wires()).contains(wire);
        assertThat(rename.loosenedPins()).isEmpty();

        history.undo();
        assertThat(pinNames(machineDefinition())).containsExactly("x", "RST", "CLK");
        assertThat(document.wires()).contains(wire);
    }
}
