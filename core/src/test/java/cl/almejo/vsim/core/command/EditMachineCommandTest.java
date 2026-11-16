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
import cl.almejo.vsim.core.library.RemovedPinWire;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineBody;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    @Test
    void turningTheInputsIntoABusTellsTheirWiresLooseAndUndoAttachesThemAgain() {
        history.execute(new EditMachineCommand(MACHINE_ID, withSelfLoop("x·y")));
        GridPoint xPin = pinLocation("x");
        GridPoint yPin = pinLocation("y");
        Wire xWire = switchWiredTo("s40", "w32", xPin);
        Wire yWire = switchWiredTo("s41", "w33", yPin);
        StateMachine wired = machineDefinition().machine().orElseThrow();

        EditMachineCommand bus = new EditMachineCommand(MACHINE_ID,
                MachinePinEdits.asBus(wired, MachinePinEdits.Side.INPUT, true));
        history.execute(bus);

        assertThat(pinNames(machineDefinition())).containsExactly(MachineBody.BUS_INPUT_NAME, "RST", "CLK");
        assertThat(machineDefinition().pinout().get(0).spec().width()).isEqualTo(2);
        assertThat(bus.removedPinWires()).as("x and y are gone: told loose by name, a unit off the box")
                .containsExactlyInAnyOrder(
                        new RemovedPinWire(Optional.empty(), piece.id(), "x", xPin.translate(-1, 0)),
                        new RemovedPinWire(Optional.empty(), piece.id(), "y", yPin.translate(-1, 0)));

        history.undo();
        assertThat(pinNames(machineDefinition())).containsExactly("x", "y", "RST", "CLK");
        assertThat(pinLocation("x")).isEqualTo(xPin);
        assertThat(pinLocation("y")).isEqualTo(yPin);
        assertThat(document.wires()).contains(xWire, yWire);
    }

    @Test
    void movingAnInputKeepsEachSwitchOnTheNetOfItsOwnPinAndUndoAndRedoToo() {
        history.execute(new EditMachineCommand(MACHINE_ID, withSelfLoop("x·y")));
        switchWiredTo("s40", "w32", pinLocation("x"));
        switchWiredTo("s41", "w33", pinLocation("y"));
        StateMachine wired = machineDefinition().machine().orElseThrow();

        history.execute(new EditMachineCommand(MACHINE_ID,
                MachinePinEdits.moved(wired, MachinePinEdits.Side.INPUT, 0, 1)));

        assertThat(pinNames(machineDefinition())).containsExactly("y", "x", "RST", "CLK");
        assertSwitchesOnTheirPins();
        history.undo();
        assertThat(pinNames(machineDefinition())).containsExactly("x", "y", "RST", "CLK");
        assertSwitchesOnTheirPins();
        history.redo();
        assertThat(pinNames(machineDefinition())).containsExactly("y", "x", "RST", "CLK");
        assertSwitchesOnTheirPins();
    }

    @Test
    void turningWiredInputsIntoABusGivesThePieceTheFreshFaceAndKeepsClockAndResetWired() {
        history.execute(new EditMachineCommand(MACHINE_ID, withSelfLoop("x·y")));
        switchWiredTo("s40", "w32", pinLocation("x"));
        switchWiredTo("s41", "w33", pinLocation("y"));
        switchWiredTo("s42", "w34", pinLocation("RST"));
        switchWiredTo("s43", "w35", pinLocation("CLK"));
        StateMachine wired = machineDefinition().machine().orElseThrow();

        EditMachineCommand bus = new EditMachineCommand(MACHINE_ID,
                MachinePinEdits.asBus(wired, MachinePinEdits.Side.INPUT, true));
        history.execute(bus);

        assertThat(document.component(piece.id()).orElseThrow().appearance()).as("drawn as the machine")
                .isEmpty();
        assertThat(pinLocation(MachineBody.BUS_INPUT_NAME)).isEqualTo(new GridPoint(10, 12));
        assertThat(pinLocation("RST")).isEqualTo(new GridPoint(10, 14));
        assertThat(pinLocation("CLK")).isEqualTo(new GridPoint(10, 16));
        assertThat(netOf(piecePin("RST"))).contains(new PinRef(new ElementId("s42"), 0));
        assertThat(netOf(piecePin("CLK"))).contains(new PinRef(new ElementId("s43"), 0));
        assertThat(netOf(new PinRef(new ElementId("s40"), 0))).as("x's switch came loose")
                .noneMatch(pin -> pin.componentId().equals(piece.id()));
        assertThat(netOf(new PinRef(new ElementId("s41"), 0))).as("y's switch came loose")
                .noneMatch(pin -> pin.componentId().equals(piece.id()));
        assertThat(bus.removedPinWires()).extracting(RemovedPinWire::pinName).containsExactlyInAnyOrder("x", "y");
        assertThat(bus.removedPinWires()).allSatisfy(loose -> assertThat(document.wires())
                .anyMatch(wire -> wire.start().equals(loose.position()) || wire.end().equals(loose.position())));

        history.undo();
        assertSwitchesOnTheirPins();
        assertThat(netOf(piecePin("RST"))).contains(new PinRef(new ElementId("s42"), 0));
        history.redo();
        assertThat(pinLocation("RST")).isEqualTo(new GridPoint(10, 14));
        assertThat(netOf(piecePin("RST"))).contains(new PinRef(new ElementId("s42"), 0));
    }

    @Test
    void turningAWiredBusBackIntoPinsGivesThePieceTheFreshFace() {
        StateMachine asBus = withSelfLoop("x·y").withBus(true, false);
        history.execute(new EditMachineCommand(MACHINE_ID, asBus));
        switchWiredTo("s40", "w32", pinLocation(MachineBody.BUS_INPUT_NAME));
        switchWiredTo("s42", "w34", pinLocation("RST"));
        switchWiredTo("s43", "w35", pinLocation("CLK"));
        StateMachine wired = machineDefinition().machine().orElseThrow();

        EditMachineCommand pins = new EditMachineCommand(MACHINE_ID,
                MachinePinEdits.asBus(wired, MachinePinEdits.Side.INPUT, false));
        history.execute(pins);

        assertThat(document.component(piece.id()).orElseThrow().appearance()).isEmpty();
        assertThat(pinLocation("x")).isEqualTo(new GridPoint(10, 12));
        assertThat(pinLocation("RST")).isEqualTo(new GridPoint(10, 16));
        assertThat(netOf(piecePin("RST"))).contains(new PinRef(new ElementId("s42"), 0));
        assertThat(netOf(piecePin("CLK"))).contains(new PinRef(new ElementId("s43"), 0));
        assertThat(netOf(new PinRef(new ElementId("s40"), 0)))
                .noneMatch(pin -> pin.componentId().equals(piece.id()));
        assertThat(pins.removedPinWires()).extracting(RemovedPinWire::pinName)
                .containsExactly(MachineBody.BUS_INPUT_NAME);
    }

    private void assertSwitchesOnTheirPins() {
        assertThat(netOf(piecePin("x"))).as("the switch wired to x").contains(new PinRef(new ElementId("s40"), 0));
        assertThat(netOf(piecePin("y"))).as("the switch wired to y").contains(new PinRef(new ElementId("s41"), 0));
    }

    private PinRef piecePin(String name) {
        return new PinRef(piece.id(), machineDefinition().pinIndexOf(name).orElseThrow());
    }

    private Set<PinRef> netOf(PinRef pin) {
        return Connectivity.of(document.wires(), document, registry).pinNets().stream()
                .filter(net -> net.contains(pin)).findFirst().orElse(Set.of(pin));
    }

    /** A switch whose output is wired to {@code pin} from 6 units to its left; returns the wire. */
    private Wire switchWiredTo(String switchId, String wireId, GridPoint pin) {
        GridPoint end = pin.plus(new GridPoint(-6, 0));
        Component probe = registry.require(StandardComponentTypes.SWITCH).newComponent(new ElementId(switchId),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint offset = registry.typeOf(probe).pinLocation(probe, 0);
        document.add(registry.require(StandardComponentTypes.SWITCH).newComponent(new ElementId(switchId),
                new GridPoint(end.x() - offset.x(), end.y() - offset.y()), Rotation.R0, Map.of(), ""));
        Wire wire = new Wire(new ElementId(wireId), end, pin);
        document.add(wire);
        return wire;
    }
}
