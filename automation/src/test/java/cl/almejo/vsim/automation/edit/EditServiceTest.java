package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.edit.EditOperation.AddGuide;
import cl.almejo.vsim.automation.edit.EditOperation.AddNote;
import cl.almejo.vsim.automation.edit.EditOperation.AddWire;
import cl.almejo.vsim.automation.edit.EditOperation.Connect;
import cl.almejo.vsim.automation.edit.EditOperation.CreateComponent;
import cl.almejo.vsim.automation.edit.EditOperation.Delete;
import cl.almejo.vsim.automation.edit.EditOperation.MakeFrame;
import cl.almejo.vsim.automation.edit.EditOperation.Move;
import cl.almejo.vsim.automation.edit.EditOperation.PlaceInstance;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.RemoveGuide;
import cl.almejo.vsim.automation.edit.EditOperation.SetLabel;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.automation.edit.EditOperation.TidyWires;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditServiceTest {

    private static final String DOCUMENT = "circuit-1";

    private final FakeCircuitHost host = new FakeCircuitHost();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<AutomationListener> listeners = new CopyOnWriteArrayList<>();
    private EditService service;
    private FakeCircuitHost.Circuit circuit;
    private int nextOperationId = 1;

    @BeforeEach
    void openACircuit() {
        circuit = host.open(DOCUMENT);
        listeners.add(new RecordingListener());
        service = new EditService(host, listeners, Duration.ofSeconds(5), Runnable::run);
    }

    // --- all or nothing (Review Focus 1) ---

    @Test
    void aBatchWhoseThirdOperationNamesAMissingPinChangesNothing() {
        EditRequest request = request(
                place("first", "nand2", 10, 0),
                place("second", "nand2", 20, 0),
                connect("new:first", "out", "new:second", "nowhere"));

        assertRefused(request, AutomationError.INVALID_PIN);
        assertNothingChanged();
    }

    @Test
    void aWireOfTheWrongWidthIsRefusedAfterValidPlacements() {
        EditRequest request = request(
                new PlacePart(Optional.of("bus"), "input-pin", 0, 0, Rotation.R0, Map.of("width", 8), "A"),
                place("lamp", "led", 10, 0),
                connect("new:bus", "pin", "new:lamp", "in"));

        AutomationException refusal = assertRefused(request, AutomationError.INVALID_PIN);
        assertThat(refusal.getMessage()).contains("8").contains("1");
        assertThat(refusal.recovery()).containsEntry("operation", 2);
        assertNothingChanged();
    }

    @Test
    void aBatchThatFailsAfterMakingAComponentAndAGuideLeavesNeitherBehind() {
        Map<String, String> ids = inverterParts();
        List<DocumentElement> before = circuit.document().elements();

        assertRefused(request(
                new AddGuide(Guide.Orientation.HORIZONTAL, 8),
                new CreateComponent(Optional.of("inverter"), "Inversor", List.copyOf(ids.values()), Map.of(), true),
                new PlaceInstance(Optional.empty(), "Sumador", 40, 0, Rotation.R0)), AutomationError.INVALID_REFERENCE);

        assertThat(circuit.document().elements()).isEqualTo(before);
        assertThat(circuit.document().definitions()).isEmpty();
        assertThat(circuit.document().guides()).isEmpty();
        circuit.history().undo();
        assertThat(circuit.history().canUndo()).isFalse();
    }

    @Test
    void anUnknownAliasIsAnInvalidReferenceAndNothingIsPlaced() {
        EditRequest request = request(place("gate", "nand2", 0, 0), connect("new:gate", "out", "new:ghost", "in0"));

        assertRefused(request, AutomationError.INVALID_REFERENCE);
        assertNothingChanged();
    }

    @Test
    void aParameterOutOfRangeOrUnknownIsRefused() {
        assertRefused(request(place("pin", "input-pin", 0, 0),
                new SetParameters("new:pin", Map.of("width", 99))), AutomationError.INVALID_PARAMETER);
        assertRefused(request(new PlacePart(Optional.empty(), "nand2", 0, 0, Rotation.R0, Map.of("colour", "red"),
                "")), AutomationError.INVALID_PARAMETER);
        assertRefused(request(place("x", "warp-drive", 0, 0)), AutomationError.INVALID_REFERENCE);
        assertNothingChanged();
    }

    @Test
    void aRetryWithTheSameOperationIdAndArgumentsGetsTheSameReceiptAndAddsNothing() {
        EditRequest request = request(place("gate", "nand2", 0, 0));

        EditReceipt first = service.edit(request);
        EditReceipt retried = service.edit(request);

        assertThat(retried).isEqualTo(first);
        assertThat(circuit.document().components()).hasSize(1);
        circuit.history().undo();
        assertThat(circuit.history().canUndo()).isFalse();
    }

    @Test
    void theSameOperationIdWithOtherArgumentsIsAConflict() {
        service.edit(new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-same", Optional.empty(),
                List.of(place("gate", "nand2", 0, 0)), false));

        assertThatThrownBy(() -> service.edit(new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-same",
                Optional.empty(), List.of(place("gate", "nand2", 9, 9)), false)))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.OPERATION_CONFLICT));
        assertThat(circuit.document().components()).hasSize(1);
    }

    @Test
    void validateOnlyChecksEverythingAndChangesNothing() {
        EditRequest request = new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-check",
                Optional.empty(), List.of(place("gate", "nand2", 0, 0), place("lamp", "led", 10, 0),
                connect("new:gate", "out", "new:lamp", "in")), true);

        EditReceipt receipt = service.edit(request);

        assertThat(receipt.applied()).isFalse();
        assertThat(receipt.aliases()).containsOnlyKeys("gate", "lamp");
        assertThat(receipt.connections()).hasSize(1);
        assertNothingChanged();
    }

    // --- aliases, receipts, undo steps ---

    @Test
    void aliasesMapToTheRealIdsAndLaterOperationsCanUseThem() {
        EditReceipt receipt = service.edit(request(
                place("gate", "nand2", 10, 0),
                new SetLabel("new:gate", "U1"),
                new AddNote(Optional.of("note"), "Prueba: la salida es 1", 0, 10)));

        String gateId = receipt.aliases().get("gate");
        assertThat(circuit.document().component(new ElementId(gateId)).orElseThrow().label()).isEqualTo("U1");
        assertThat(circuit.document().label(new ElementId(receipt.aliases().get("note")))).isPresent();
        assertThat(receipt.applied()).isTrue();
        assertThat(receipt.version()).isEqualTo(circuit.version());
        assertThat(receipt.changes().added()).contains(gateId, receipt.aliases().get("note"));
        assertThat(receipt.undoDescription()).isEqualTo(AiEditCommand.DESCRIPTION);
    }

    @Test
    void aGatedDLatchBuiltInTwoCallsIsTwoUndoSteps() {
        EditReceipt parts = service.edit(request(
                new PlacePart(Optional.of("data"), "switch", 0, 1, Rotation.R0, Map.of(), "D"),
                new PlacePart(Optional.of("enable"), "switch", 0, 15, Rotation.R0, Map.of(), "E"),
                place("inverter", "not", 8, 0),
                place("top", "nand2", 16, 0),
                place("bottom", "nand2", 16, 14),
                place("set", "nand2", 28, 0),
                place("reset", "nand2", 28, 14),
                place("output", "led", 40, -1)));
        Map<String, String> ids = parts.aliases();

        service.edit(request(
                connect(ids.get("data"), "out", ids.get("top"), "in0"),
                connect(ids.get("data"), "out", ids.get("inverter"), "in"),
                connect(ids.get("inverter"), "out", ids.get("bottom"), "in1"),
                connect(ids.get("enable"), "out", ids.get("top"), "in1"),
                connect(ids.get("enable"), "out", ids.get("bottom"), "in0"),
                connect(ids.get("top"), "out", ids.get("set"), "in0"),
                connect(ids.get("bottom"), "out", ids.get("reset"), "in1"),
                connect(ids.get("set"), "out", ids.get("output"), "in")));

        assertThat(connected(pin(ids.get("data"), 0), pin(ids.get("top"), 0))).isTrue();
        assertThat(connected(pin(ids.get("enable"), 0), pin(ids.get("bottom"), 0))).isTrue();
        assertThat(connected(pin(ids.get("set"), 2), pin(ids.get("output"), 0))).isTrue();
        circuit.history().undo();
        assertThat(circuit.document().wires()).isEmpty();
        assertThat(circuit.document().components()).hasSize(8);
        circuit.history().undo();
        assertThat(circuit.document().isEmpty()).isTrue();
        assertThat(circuit.history().canUndo()).isFalse();
    }

    // --- stale edits (Review Focus 2) ---

    @Test
    void anEditPreparedBeforeTheUserMovedSomethingIsStaleAndNothingIsApplied() {
        String gateId = service.edit(request(place("gate", "nand2", 0, 0))).aliases().get("gate");
        EditRequest prepared = request(place("lamp", "led", 10, 0));
        circuit.history().execute(new MoveElementsCommand(List.of(new ElementId(gateId)), 2, 0));
        List<DocumentElement> before = circuit.document().elements();

        AutomationException refusal = assertRefused(prepared, AutomationError.STALE_DOCUMENT);

        assertThat(refusal.recovery()).containsEntry("currentVersion", circuit.version().value());
        assertThat(circuit.document().elements()).isEqualTo(before);
    }

    @Test
    void anAiStepNeverMergesWithTheUserMovesAroundIt() {
        String gateId = service.edit(request(place("gate", "nand2", 0, 0))).aliases().get("gate");
        Set<ElementId> gate = Set.of(new ElementId(gateId));
        circuit.history().execute(MoveElementsCommand.nudge(gate, 1, 0));
        service.edit(request(place("lamp", "led", 10, 0)));
        circuit.history().execute(MoveElementsCommand.nudge(gate, 1, 0));
        circuit.history().execute(MoveElementsCommand.nudge(gate, 1, 0));

        circuit.history().undo();
        assertThat(circuit.history().undoCommand()).containsInstanceOf(AiEditCommand.class);
        assertThat(circuit.document().components()).hasSize(2);
        circuit.history().undo();
        assertThat(circuit.document().components()).hasSize(1);
        circuit.history().undo();
        assertThat(circuit.history().undoCommand()).containsInstanceOf(AiEditCommand.class);
    }

    // --- wrong pin, wrong place (Review Focus 4) ---

    @Test
    void aRotatedPartIsConnectedAtItsRotatedPin() {
        EditReceipt receipt = service.edit(request(
                place("source", "switch", 0, 0),
                new PlacePart(Optional.of("gate"), "nand2", 20, 20, Rotation.R90, Map.of(), ""),
                connect("new:source", "out", "new:gate", "in1")));
        String gateId = receipt.aliases().get("gate");

        assertThat(connected(pin(receipt.aliases().get("source"), 0), pin(gateId, 1))).isTrue();
        assertThat(connected(pin(receipt.aliases().get("source"), 0), pin(gateId, 0))).isFalse();
        assertThat(receipt.connections().getFirst().to()).isEqualTo(new PinReference(gateId, "in1", 1));
        assertThat(circuit.document().wires()).anyMatch(wire -> wire.contains(new GridPoint(16, 20)));
    }

    @Test
    void anEightBitBusJoinsTwoEightBitPins() {
        EditReceipt receipt = service.edit(request(
                new PlacePart(Optional.of("in"), "input-pin", 0, 0, Rotation.R0, Map.of("width", 8), "A"),
                new PlacePart(Optional.of("out"), "output-pin", 12, 4, Rotation.R0, Map.of("width", 8), "Y"),
                connect("new:in", "pin", "new:out", "pin")));

        Net net = netOf(pin(receipt.aliases().get("in"), 0));
        assertThat(net.width()).isEqualTo(8);
        assertThat(net.pins()).contains(pin(receipt.aliases().get("out"), 0));
        assertThat(receipt.connections().getFirst().joinedNets()).hasSize(2);
    }

    @Test
    void aRouteThatWouldTouchAnotherNetIsRefusedWithTheNetsItWouldJoin() {
        EditReceipt parts = service.edit(request(
                place("source", "switch", -4, -1),
                place("target", "led", 9, -3),
                place("bystander", "led", 4, -3)));
        String bystander = parts.aliases().get("bystander");

        AutomationException refusal = assertRefused(request(connect(parts.aliases().get("source"), "out",
                parts.aliases().get("target"), "in")), AutomationError.ROUTE_REJECTED);

        assertThat(refusal.recovery().get("wouldJoin").toString()).contains(bystander);
        assertThat(circuit.document().wires()).isEmpty();
    }

    @Test
    void viaPointsLeadARouteAroundAnotherNet() {
        EditReceipt parts = service.edit(request(
                place("source", "switch", -4, -1),
                place("target", "led", 9, -3),
                place("bystander", "led", 4, -3)));

        service.edit(request(new Connect(PinTarget.named(parts.aliases().get("source"), "out"),
                PinTarget.named(parts.aliases().get("target"), "in"),
                List.of(new GridPoint(2, 6), new GridPoint(10, 6)))));

        assertThat(connected(pin(parts.aliases().get("source"), 0), pin(parts.aliases().get("target"), 0))).isTrue();
        assertThat(connected(pin(parts.aliases().get("source"), 0), pin(parts.aliases().get("bystander"), 0)))
                .isFalse();
    }

    @Test
    void connectingPinsThatAreConnectedAlreadyAddsNoWire() {
        EditReceipt parts = service.edit(request(place("source", "switch", 0, 0), place("gate", "nand2", 10, -1),
                connect("new:source", "out", "new:gate", "in0")));
        int wires = circuit.document().wires().size();

        EditReceipt again = service.edit(request(connect(parts.aliases().get("source"), "out",
                parts.aliases().get("gate"), "in0")));

        assertThat(again.connections().getFirst().alreadyConnected()).isTrue();
        assertThat(circuit.document().wires()).hasSize(wires);
        assertThat(again.warnings()).isNotEmpty();
    }

    @Test
    void anAmbiguousOrUnknownPinListsThePinsThePartHas() {
        service.edit(request(place("gate", "nand2", 0, 0)));

        AutomationException refusal = assertRefused(request(new Connect(
                new PinTarget("c1", Optional.of("in"), java.util.OptionalInt.empty()), PinTarget.named("c1", "out"),
                List.of())), AutomationError.INVALID_PIN);

        assertThat(refusal.recovery().get("pins").toString()).contains("in0", "in1", "out");
    }

    // --- components and instances ---

    @Test
    void creatingAComponentWithoutReplacingKeepsThePartsAndAddsTheDefinition() {
        Map<String, String> ids = inverterParts();
        int elements = circuit.document().elements().size();

        EditReceipt receipt = service.edit(request(new CreateComponent(Optional.of("inverter"), "Inversor",
                List.of(ids.get("a"), ids.get("not"), ids.get("y"), ids.get("w1"), ids.get("w2")), Map.of(), false)));

        assertThat(circuit.document().elements()).hasSize(elements);
        assertThat(circuit.document().definitions()).singleElement()
                .satisfies(definition -> assertThat(definition.name()).isEqualTo("Inversor"));
        EditReceipt.CreatedComponent created = receipt.components().getFirst();
        assertThat(created.inputs()).containsExactly("A");
        assertThat(created.outputs()).containsExactly("Y");
        assertThat(created.instanceId()).isEmpty();
        assertThat(receipt.aliases()).containsEntry("inverter", created.definitionId());
    }

    @Test
    void creatingAComponentReplacingTheSelectionLeavesOneInstance() {
        Map<String, String> ids = inverterParts();

        EditReceipt receipt = service.edit(request(new CreateComponent(Optional.empty(), "Inversor",
                List.of(ids.get("a"), ids.get("not"), ids.get("y"), ids.get("w1"), ids.get("w2")), Map.of(), true)));

        String instanceId = receipt.components().getFirst().instanceId().orElseThrow();
        assertThat(circuit.document().components()).singleElement()
                .satisfies(component -> assertThat(component.id().value()).isEqualTo(instanceId));
    }

    @Test
    void twoInstancesOfAFullAdderAreChainedByTheirCarry() {
        Map<String, String> ids = service.edit(request(fullAdder())).aliases();
        EditReceipt receipt = service.edit(request(
                new CreateComponent(Optional.of("adder"), "Sumador completo", fullAdderIds(ids), Map.of(), false),
                new PlaceInstance(Optional.of("low"), "new:adder", 60, 0, Rotation.R0),
                new PlaceInstance(Optional.of("high"), "Sumador completo", 60, 30, Rotation.R0),
                connect("new:low", "Cout", "new:high", "Cin")));

        EditReceipt.CreatedComponent adder = receipt.components().getFirst();
        assertThat(adder.inputs()).containsExactly("A", "B", "Cin");
        assertThat(adder.outputs()).containsExactly("S", "Cout");
        String low = receipt.aliases().get("low");
        String high = receipt.aliases().get("high");
        assertThat(connected(pin(low, adder.inputs().size() + 1), pin(high, 2))).isTrue();
        assertThat(receipt.connections().getFirst().from()).isEqualTo(new PinReference(low, "Cout", 4));
    }

    // --- the other operations ---

    @Test
    void movingAPartTakesItsWiresAlong() {
        EditReceipt parts = service.edit(request(place("source", "switch", 0, 0), place("gate", "nand2", 10, -1),
                connect("new:source", "out", "new:gate", "in0")));

        service.edit(request(new Move(List.of(parts.aliases().get("gate")), 0, 6)));

        assertThat(circuit.document().component(new ElementId(parts.aliases().get("gate"))).orElseThrow().position())
                .isEqualTo(new GridPoint(10, 5));
        assertThat(connected(pin(parts.aliases().get("source"), 0), pin(parts.aliases().get("gate"), 0))).isTrue();
    }

    @Test
    void deletingRemovesTheTargets() {
        EditReceipt parts = service.edit(request(place("gate", "nand2", 0, 0), place("lamp", "led", 10, 0)));

        EditReceipt receipt = service.edit(request(new Delete(List.of(parts.aliases().get("gate")))));

        assertThat(circuit.elementIds()).containsExactly(parts.aliases().get("lamp"));
        assertThat(receipt.changes().removed()).containsExactly(parts.aliases().get("gate"));
    }

    @Test
    void aFrameWrapsItsTargetsWithRoomForItsTitle() {
        EditReceipt receipt = service.edit(request(place("gate", "nand2", 10, 10),
                new MakeFrame(Optional.of("stage"), "Etapa 1: el latch", List.of("new:gate"), Optional.empty(),
                        false)));

        Frame frame = circuit.document().frame(new ElementId(receipt.aliases().get("stage"))).orElseThrow();
        assertThat(frame.title()).isEqualTo("Etapa 1: el latch");
        assertThat(frame.encloses(10, 10, 14, 14)).isTrue();
        assertThat(frame.topLeft().y()).isLessThan(10 - 2);
    }

    @Test
    void guidesComeAndGo() {
        service.edit(request(new AddGuide(Guide.Orientation.VERTICAL, 12)));
        assertThat(circuit.document().guides()).containsExactly(new Guide(Guide.Orientation.VERTICAL, 12));

        service.edit(request(new RemoveGuide(Guide.Orientation.VERTICAL, 12)));
        assertThat(circuit.document().guides()).isEmpty();
        assertRefused(request(new RemoveGuide(Guide.Orientation.VERTICAL, 12)), AutomationError.INVALID_REFERENCE);
    }

    @Test
    void wiresDrawnInPiecesAreTidiedIntoOne() {
        service.edit(request(place("source", "switch", 0, 0), place("lamp", "led", 11, -2),
                new AddWire(List.of(new GridPoint(4, 1), new GridPoint(8, 1))),
                new AddWire(List.of(new GridPoint(8, 1), new GridPoint(12, 1)))));
        assertThat(circuit.document().wires()).hasSize(2);

        service.edit(request(new TidyWires(List.of())));

        assertThat(circuit.document().wires()).singleElement()
                .satisfies(wire -> assertThat(wire.end()).isEqualTo(new GridPoint(12, 1)));
    }

    @Test
    void aDiagonalWireIsRefused() {
        assertRefused(request(new AddWire(List.of(new GridPoint(0, 0), new GridPoint(4, 4)))),
                AutomationError.INVALID_PARAMETER);
    }

    // --- tasks ---

    @Test
    void aTaskIsAnnouncedAndItsEndNamesWhatItChanged() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Flip-flop D",
                List.of(new TaskStage("Latch", List.of("Coloco las NAND"))));
        EditReceipt receipt = service.edit(taskRequest(task, place("gate", "nand2", 0, 0)));

        service.endTask(task.taskId());

        assertThat(events).containsExactly("started " + task.title(),
                "ended " + task.title() + " " + Set.of(receipt.aliases().get("gate")));
    }

    @Test
    void undoingATaskUndoesItsStepsAndStopsAtTheUserEdit() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Dos compuertas", List.of());
        String gateId = service.edit(taskRequest(task, place("gate", "nand2", 0, 0))).aliases().get("gate");
        service.edit(taskRequest(task, place("lamp", "led", 10, 0)));
        circuit.history().execute(new MoveElementsCommand(List.of(new ElementId(gateId)), 2, 0));

        assertThat(service.undoTask(task.taskId())).isEqualTo(new UndoOutcome(0, true));
        assertThat(circuit.document().components()).hasSize(2);

        circuit.history().undo();
        assertThat(service.undoTask(task.taskId())).isEqualTo(new UndoOutcome(2, false));
        assertThat(circuit.document().isEmpty()).isTrue();
        assertThat(service.undoTask(task.taskId())).isEqualTo(new UndoOutcome(0, false));
    }

    @Test
    void aStoppedTaskKeepsWhatItAppliedAndRefusesItsNextEdit() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Sumador", List.of());
        service.edit(taskRequest(task, place("gate", "nand2", 0, 0)));

        service.stopTask(task.taskId());

        assertRefused(taskRequest(task, place("lamp", "led", 10, 0)), AutomationError.NOT_ALLOWED);
        assertThat(circuit.document().components()).hasSize(1);
        assertThat(events).containsExactly("started Sumador", "stopped Sumador");
    }

    @Test
    void anEditForAClosedCircuitSaysSo() {
        EditRequest request = request(place("gate", "nand2", 0, 0));
        host.close(DOCUMENT);

        assertRefused(request, AutomationError.DOCUMENT_CLOSED);
    }

    // --- helpers ---

    private EditRequest request(EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.empty(), List.of(operations), false);
    }

    private EditRequest taskRequest(TaskInfo task, EditOperation... operations) {
        return new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-" + nextOperationId++,
                Optional.of(new TaskStep(task.taskId(), "1", "1", "Paso")), List.of(operations), false);
    }

    private static PlacePart place(String alias, String typeId, int x, int y) {
        return new PlacePart(Optional.of(alias), typeId, x, y, Rotation.R0, Map.of(), "");
    }

    private static Connect connect(String fromTarget, String fromPin, String toTarget, String toPin) {
        return new Connect(PinTarget.named(fromTarget, fromPin), PinTarget.named(toTarget, toPin), List.of());
    }

    private AutomationException assertRefused(EditRequest request, AutomationError error) {
        List<AutomationException> refusals = new ArrayList<>();
        assertThatThrownBy(() -> service.edit(request))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> {
                    assertThat(refusal.error()).isEqualTo(error);
                    refusals.add(refusal);
                });
        return refusals.getFirst();
    }

    private void assertNothingChanged() {
        assertThat(circuit.document().isEmpty()).isTrue();
        assertThat(circuit.document().definitions()).isEmpty();
        assertThat(circuit.document().guides()).isEmpty();
        assertThat(circuit.history().canUndo()).isFalse();
        assertThat(circuit.version().value()).isZero();
    }

    private static PinRef pin(String componentId, int index) {
        return new PinRef(new ElementId(componentId), index);
    }

    private Net netOf(PinRef pin) {
        Netlist netlist = new NetlistCompiler(circuit.registry()).compile(circuit.document());
        return netlist.netForPin(pin).orElseThrow();
    }

    private boolean connected(PinRef first, PinRef second) {
        return netOf(first).pins().contains(second);
    }

    /** An input pin A, an inverter and an output pin Y, wired: the ids by alias (w1, w2 are the wires). */
    private Map<String, String> inverterParts() {
        EditReceipt receipt = service.edit(request(
                new PlacePart(Optional.of("a"), "input-pin", 0, 1, Rotation.R0, Map.of(), "A"),
                place("not", "not", 8, 0),
                new PlacePart(Optional.of("y"), "output-pin", 18, 1, Rotation.R0, Map.of(), "Y"),
                connect("new:a", "pin", "new:not", "in"),
                connect("new:not", "out", "new:y", "pin")));
        Map<String, String> ids = new java.util.HashMap<>(receipt.aliases());
        ids.put("w1", receipt.connections().get(0).wireIds().getFirst());
        ids.put("w2", receipt.connections().get(1).wireIds().getFirst());
        return ids;
    }

    /** A full adder of two XOR, two AND and one OR, with interface pins A, B, Cin, S and Cout. */
    private static EditOperation[] fullAdder() {
        return new EditOperation[]{
                new PlacePart(Optional.of("a"), "input-pin", 0, 0, Rotation.R0, Map.of(), "A"),
                new PlacePart(Optional.of("b"), "input-pin", 0, 6, Rotation.R0, Map.of(), "B"),
                new PlacePart(Optional.of("cin"), "input-pin", 0, 12, Rotation.R0, Map.of(), "Cin"),
                place("xor1", "xor2", 10, 0),
                place("xor2", "xor2", 22, 2),
                place("and1", "and2", 22, 10),
                place("and2", "and2", 22, 18),
                place("or", "or2", 32, 14),
                new PlacePart(Optional.of("s"), "output-pin", 40, 3, Rotation.R0, Map.of(), "S"),
                new PlacePart(Optional.of("cout"), "output-pin", 44, 15, Rotation.R0, Map.of(), "Cout"),
                connect("new:a", "pin", "new:xor1", "in0"),
                connect("new:b", "pin", "new:xor1", "in1"),
                connect("new:xor1", "out", "new:xor2", "in0"),
                connect("new:cin", "pin", "new:xor2", "in1"),
                connect("new:xor2", "out", "new:s", "pin"),
                connect("new:xor1", "out", "new:and1", "in0"),
                connect("new:cin", "pin", "new:and1", "in1"),
                // The lanes between the inputs are taken: these two go round by the points the AI chose.
                new Connect(PinTarget.named("new:a", "pin"), PinTarget.named("new:and2", "in0"),
                        List.of(new GridPoint(8, 1), new GridPoint(8, 18))),
                new Connect(PinTarget.named("new:b", "pin"), PinTarget.named("new:and2", "in1"),
                        List.of(new GridPoint(4, 7), new GridPoint(4, 22))),
                connect("new:and1", "out", "new:or", "in0"),
                connect("new:and2", "out", "new:or", "in1"),
                connect("new:or", "out", "new:cout", "pin")};
    }

    /** Every element the full adder call made: its parts and its wires. */
    private List<String> fullAdderIds(Map<String, String> ids) {
        List<String> all = new ArrayList<>();
        circuit.document().elements().forEach(element -> all.add(element.id().value()));
        assertThat(all).containsAll(ids.values());
        return all;
    }

    private final class RecordingListener implements AutomationListener {

        @Override
        public void taskStarted(TaskInfo task) {
            events.add("started " + task.title());
        }

        @Override
        public void taskEnded(TaskInfo task, Set<String> changedElementIds) {
            events.add("ended " + task.title() + " " + changedElementIds);
        }

        @Override
        public void taskStopped(TaskInfo task) {
            events.add("stopped " + task.title());
        }
    }
}
