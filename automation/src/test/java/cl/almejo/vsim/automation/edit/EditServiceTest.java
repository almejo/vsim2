package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.check.TextMeasure;
import cl.almejo.vsim.automation.edit.EditOperation.AddGuide;
import cl.almejo.vsim.automation.edit.EditOperation.AddNote;
import cl.almejo.vsim.automation.edit.EditOperation.AddStickyNote;
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
import cl.almejo.vsim.automation.edit.EditOperation.SetStickyItem;
import cl.almejo.vsim.automation.edit.EditOperation.TidyWires;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentPreset;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
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
import java.util.OptionalInt;
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
                place("first", "nand", 10, 0),
                place("second", "nand", 20, 0),
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
        EditRequest request = request(place("gate", "nand", 0, 0), connect("new:gate", "out", "new:ghost", "in0"));

        assertRefused(request, AutomationError.INVALID_REFERENCE);
        assertNothingChanged();
    }

    @Test
    void aParameterOutOfRangeOrUnknownIsRefused() {
        assertRefused(request(place("pin", "input-pin", 0, 0),
                new SetParameters("new:pin", Map.of("width", 99))), AutomationError.INVALID_PARAMETER);
        assertRefused(request(new PlacePart(Optional.empty(), "and", 0, 0, Rotation.R0, Map.of("colour", "red"),
                "")), AutomationError.INVALID_PARAMETER);
        assertRefused(request(place("x", "warp-drive", 0, 0)), AutomationError.INVALID_REFERENCE);
        assertNothingChanged();
    }

    @Test
    void aBooleanParameterIsSetFromAJsonBooleanOrItsTextAndARefusedValueChangesNothing() {
        circuit.registry().register(new ComponentType("test-flagged", ComponentCategory.BASIC, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)), List.of(ParameterSpec.bool("negate", false)),
                parameters -> (context, pinIndex) -> {
                }));
        service.edit(request(place("part", "test-flagged", 0, 0)));

        service.edit(request(new SetParameters("c1", Map.of("negate", true))));
        assertThat(circuit.document().components().getFirst().parameters()).containsEntry("negate", true);
        service.edit(request(new SetParameters("c1", Map.of("negate", "FALSE"))));
        assertThat(circuit.document().components().getFirst().parameters()).containsEntry("negate", false);

        assertRefused(request(new SetParameters("c1", Map.of("negate", "maybe"))), AutomationError.INVALID_PARAMETER);
        assertThat(circuit.document().components().getFirst().parameters()).containsEntry("negate", false);
    }

    @Test
    void aRetryWithTheSameOperationIdAndArgumentsGetsTheSameReceiptAndAddsNothing() {
        EditRequest request = request(place("gate", "nand", 0, 0));

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
                List.of(place("gate", "nand", 0, 0)), false));

        assertThatThrownBy(() -> service.edit(new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-same",
                Optional.empty(), List.of(place("gate", "nand", 9, 9)), false)))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.OPERATION_CONFLICT));
        assertThat(circuit.document().components()).hasSize(1);
    }

    @Test
    void validateOnlyChecksEverythingAndChangesNothing() {
        EditRequest request = new EditRequest(new DocumentId(DOCUMENT), circuit.version(), "op-check",
                Optional.empty(), List.of(place("gate", "nand", 0, 0), place("lamp", "led", 10, 0),
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
                place("gate", "nand", 10, 0),
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

    // --- styled notes (Pulido T5) ---

    @Test
    void anAddNoteWithAStyleMakesAStyledNote() {
        NoteStyle style = new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.RED);

        EditReceipt receipt = service.edit(request(
                new AddNote(Optional.of("note"), "Ojo: activo en bajo", 0, 10, style)));

        assertThat(circuit.document().label(new ElementId(receipt.aliases().get("note"))).orElseThrow().style())
                .isEqualTo(style);
    }

    // --- sticky notes ---

    @Test
    void anAddStickyNoteWithTextOrItemsMakesTheNoteInOneAiStepThatUndoRemoves() {
        EditReceipt receipt = service.edit(request(
                new AddStickyNote(Optional.of("memo"), 4, 5, OptionalInt.empty(), OptionalInt.empty(),
                        Optional.empty(), Optional.of("Falta: probar con Cin = 1"), Optional.empty(), false),
                new AddStickyNote(Optional.of("todo"), 20, 5, OptionalInt.of(10), OptionalInt.of(7),
                        Optional.of(StickyColor.GREEN), Optional.empty(), Optional.of(List.of("uno", "dos")), true)));

        StickyNote memo = circuit.document().stickyNote(new ElementId(receipt.aliases().get("memo"))).orElseThrow();
        StickyNote todo = circuit.document().stickyNote(new ElementId(receipt.aliases().get("todo"))).orElseThrow();
        assertThat(memo.content()).isEqualTo(new StickyText("Falta: probar con Cin = 1"));
        assertThat(memo.color()).isEqualTo(StickyColor.YELLOW);
        assertThat(memo.width()).isEqualTo(StickyNote.DEFAULT_WIDTH);
        assertThat(memo.position()).isEqualTo(new GridPoint(4, 5));
        assertThat(todo.content()).isEqualTo(new StickyChecklist(List.of(new StickyItem("uno", false),
                new StickyItem("dos", false))));
        assertThat(todo.width()).isEqualTo(10);
        assertThat(todo.folded()).isTrue();
        assertThat(receipt.undoDescription()).isEqualTo(AiEditCommand.DESCRIPTION);

        circuit.history().undo();

        assertThat(circuit.document().stickyNotes()).isEmpty();
        assertThat(circuit.history().canUndo()).isFalse();
    }

    @Test
    void aStickyNoteWithBothOrNeitherOfTextAndItemsIsRefusedAndNothingChanges() {
        assertRefused(request(new AddStickyNote(Optional.empty(), 0, 0, OptionalInt.empty(), OptionalInt.empty(),
                Optional.empty(), Optional.of("a"), Optional.of(List.of("b")), false)),
                AutomationError.INVALID_PARAMETER);
        assertRefused(request(new AddStickyNote(Optional.empty(), 0, 0, OptionalInt.empty(), OptionalInt.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), false)), AutomationError.INVALID_PARAMETER);
        assertNothingChanged();
    }

    @Test
    void aStickyNoteTextWithHtmlIsRefused() {
        assertRefused(request(new AddStickyNote(Optional.empty(), 0, 0, OptionalInt.empty(), OptionalInt.empty(),
                Optional.empty(), Optional.of("<html>x</html>"), Optional.empty(), false)),
                AutomationError.INVALID_PARAMETER);
    }

    @Test
    void setStickyItemTicksAndUnticksAndAnIndexOutOfRangeIsRefused() {
        EditReceipt receipt = service.edit(request(new AddStickyNote(Optional.of("todo"), 0, 0, OptionalInt.empty(),
                OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.of(List.of("uno", "dos")), false)));
        String id = receipt.aliases().get("todo");

        service.edit(request(new SetStickyItem(id, 1, true)));
        assertThat(items(id)).containsExactly(new StickyItem("uno", false), new StickyItem("dos", true));

        service.edit(request(new SetStickyItem(id, 1, false)));
        assertThat(items(id)).containsExactly(new StickyItem("uno", false), new StickyItem("dos", false));

        long version = circuit.version().value();
        assertThat(assertRefused(request(new SetStickyItem(id, 2, true)), AutomationError.INVALID_PARAMETER)
                .getMessage()).contains("2 items");
        assertRefused(request(new SetStickyItem(id, -1, true)), AutomationError.INVALID_PARAMETER);
        assertThat(circuit.version().value()).isEqualTo(version);
    }

    @Test
    void setStickyItemOnATextNoteOrAPartIsRefused() {
        Map<String, String> ids = inverterParts();
        EditReceipt receipt = service.edit(request(new AddStickyNote(Optional.of("memo"), 0, 0, OptionalInt.empty(),
                OptionalInt.empty(), Optional.empty(), Optional.of("texto"), Optional.empty(), false)));

        assertRefused(request(new SetStickyItem(receipt.aliases().get("memo"), 0, true)),
                AutomationError.INVALID_REFERENCE);
        assertRefused(request(new SetStickyItem(ids.get("not"), 0, true)), AutomationError.INVALID_REFERENCE);
    }

    @Test
    void setLabelOnAStickyNoteChangesTheTextOfATextNoteAndTheLinesOfAChecklistKeepingTicks() {
        EditReceipt receipt = service.edit(request(
                new AddStickyNote(Optional.of("memo"), 0, 0, OptionalInt.empty(), OptionalInt.empty(),
                        Optional.empty(), Optional.of("viejo"), Optional.empty(), false),
                new AddStickyNote(Optional.of("todo"), 20, 0, OptionalInt.empty(), OptionalInt.empty(),
                        Optional.empty(), Optional.empty(), Optional.of(List.of("uno", "dos")), false)));
        String memo = receipt.aliases().get("memo");
        String todo = receipt.aliases().get("todo");
        service.edit(request(new SetStickyItem(todo, 1, true)));

        service.edit(request(new SetLabel(memo, "nuevo\nen dos líneas"), new SetLabel(todo, "uno\ndos\ntres")));

        assertThat(circuit.document().stickyNote(new ElementId(memo)).orElseThrow().content())
                .isEqualTo(new StickyText("nuevo\nen dos líneas"));
        assertThat(items(todo)).containsExactly(new StickyItem("uno", false), new StickyItem("dos", true),
                new StickyItem("tres", false));
    }

    @Test
    void aStickyNoteGroupedInAnAiTaskUndoesWithTheTask() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Recordatorio", List.of());
        service.edit(taskRequest(task, new AddStickyNote(Optional.empty(), 0, 0, OptionalInt.empty(),
                OptionalInt.empty(), Optional.empty(), Optional.of("Falta probar"), Optional.empty(), false)));

        assertThat(service.undoTask(task.taskId())).isEqualTo(new UndoOutcome(1, false));
        assertThat(circuit.document().stickyNotes()).isEmpty();
    }

    private List<StickyItem> items(String id) {
        return ((StickyChecklist) circuit.document().stickyNote(new ElementId(id)).orElseThrow().content()).items();
    }

    @Test
    void aSetLabelWithOnlyAStyleChangesTheColoursItNamesAndKeepsTheText() {
        EditReceipt added = service.edit(request(new AddNote(Optional.of("note"), "nota", 0, 10,
                new NoteStyle(NoteColor.BLUE, NoteColor.NONE, NoteColor.NONE))));
        ElementId note = new ElementId(added.aliases().get("note"));

        service.edit(request(new SetLabel(note.value(), Optional.empty(), Optional.of(new NoteStyleChange(
                Optional.empty(), Optional.of(NoteColor.WHITE), Optional.of(NoteColor.BLACK))))));

        TextLabel styled = circuit.document().label(note).orElseThrow();
        assertThat(styled.text()).isEqualTo("nota");
        assertThat(styled.style()).isEqualTo(new NoteStyle(NoteColor.BLUE, NoteColor.WHITE, NoteColor.BLACK));
    }

    @Test
    void aStyleOnAnythingButANoteIsRefused() {
        Map<String, String> ids = inverterParts();
        long version = circuit.version().value();

        AutomationException refusal = assertRefused(request(new SetLabel(ids.get("not"), Optional.empty(),
                Optional.of(new NoteStyleChange(Optional.of(NoteColor.RED), Optional.empty(), Optional.empty())))),
                AutomationError.INVALID_PARAMETER);

        assertThat(refusal.getMessage()).contains("note");
        assertThat(circuit.version().value()).isEqualTo(version);
    }

    @Test
    void aSetLabelWithNeitherTextNorStyleIsRefused() {
        Map<String, String> ids = inverterParts();

        assertRefused(request(new SetLabel(ids.get("not"), Optional.empty(), Optional.empty())),
                AutomationError.INVALID_PARAMETER);
    }

    @Test
    void aGatedDLatchBuiltInTwoCallsIsTwoUndoSteps() {
        EditReceipt parts = service.edit(request(
                new PlacePart(Optional.of("data"), "switch", 0, 1, Rotation.R0, Map.of(), "D"),
                new PlacePart(Optional.of("enable"), "switch", 0, 15, Rotation.R0, Map.of(), "E"),
                place("inverter", "not", 8, 0),
                place("top", "nand", 16, 0),
                place("bottom", "nand", 16, 14),
                place("set", "nand", 28, 0),
                place("reset", "nand", 28, 14),
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
        String gateId = service.edit(request(place("gate", "nand", 0, 0))).aliases().get("gate");
        EditRequest prepared = request(place("lamp", "led", 10, 0));
        circuit.history().execute(new MoveElementsCommand(List.of(new ElementId(gateId)), 2, 0));
        List<DocumentElement> before = circuit.document().elements();

        AutomationException refusal = assertRefused(prepared, AutomationError.STALE_DOCUMENT);

        assertThat(refusal.recovery()).containsEntry("currentVersion", circuit.version().value());
        assertThat(circuit.document().elements()).isEqualTo(before);
    }

    @Test
    void anAiStepNeverMergesWithTheUserMovesAroundIt() {
        String gateId = service.edit(request(place("gate", "nand", 0, 0))).aliases().get("gate");
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
                new PlacePart(Optional.of("gate"), "and", 20, 20, Rotation.R90, Map.of("negate", true), ""),
                connect("new:source", "out", "new:gate", "in1")));
        String gateId = receipt.aliases().get("gate");

        assertThat(connected(pin(receipt.aliases().get("source"), 0), pin(gateId, 1))).isTrue();
        assertThat(connected(pin(receipt.aliases().get("source"), 0), pin(gateId, 0))).isFalse();
        assertThat(receipt.connections().getFirst().to()).isEqualTo(new PinReference(gateId, "in1", 0));
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
        EditReceipt parts = service.edit(request(place("source", "switch", 0, 0), place("gate", "nand", 10, -1),
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
        service.edit(request(place("gate", "nand", 0, 0)));

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
    void aComponentMadeFromItsPartsAloneTakesTheWiresBetweenThem() {
        Map<String, String> ids = inverterParts();

        EditReceipt receipt = service.edit(request(new CreateComponent(Optional.empty(), "Inversor",
                List.of(ids.get("a"), ids.get("not"), ids.get("y")), Map.of(), false)));

        assertThat(circuit.document().definitions()).singleElement()
                .satisfies(definition -> assertThat(definition.body().wires()).hasSize(2));
        assertThat(receipt.warnings()).isEmpty();
    }

    @Test
    void aComponentWhosePartsShareANetWithAPartOutsideSaysWhichPinsStayUnconnectedInside() {
        Map<String, String> ids = inverterParts();
        EditReceipt lamp = service.edit(request(place("lamp", "led", 30, -2),
                connect(ids.get("not"), "out", "new:lamp", "in")));

        EditReceipt receipt = service.edit(request(new CreateComponent(Optional.empty(), "Inversor",
                List.of(ids.get("a"), ids.get("not"), ids.get("y")), Map.of(), false)));

        assertThat(lamp.applied()).isTrue();
        assertThat(String.join(" ", receipt.warnings())).contains(ids.get("not") + ".out", "wires");
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
        assertThat(receipt.connections().getFirst().from()).isEqualTo(new PinReference(low, "Cout", 0));
    }

    // --- the other operations ---

    @Test
    void movingAPartTakesItsWiresAlong() {
        EditReceipt parts = service.edit(request(place("source", "switch", 0, 0), place("gate", "nand", 10, -1),
                connect("new:source", "out", "new:gate", "in0")));

        service.edit(request(new Move(List.of(parts.aliases().get("gate")), 0, 6)));

        assertThat(circuit.document().component(new ElementId(parts.aliases().get("gate"))).orElseThrow().position())
                .isEqualTo(new GridPoint(10, 5));
        assertThat(connected(pin(parts.aliases().get("source"), 0), pin(parts.aliases().get("gate"), 0))).isTrue();
    }

    @Test
    void deletingRemovesTheTargets() {
        EditReceipt parts = service.edit(request(place("gate", "nand", 0, 0), place("lamp", "led", 10, 0)));

        EditReceipt receipt = service.edit(request(new Delete(List.of(parts.aliases().get("gate")))));

        assertThat(circuit.elementIds()).containsExactly(parts.aliases().get("lamp"));
        assertThat(receipt.changes().removed()).containsExactly(parts.aliases().get("gate"));
    }

    @Test
    void aFrameWrapsItsTargetsWithRoomForItsTitle() {
        EditReceipt receipt = service.edit(request(place("gate", "nand", 10, 10),
                new MakeFrame(Optional.of("stage"), "Etapa 1: el latch", List.of("new:gate"), Optional.empty(),
                        false)));

        Frame frame = circuit.document().frame(new ElementId(receipt.aliases().get("stage"))).orElseThrow();
        assertThat(frame.title()).isEqualTo("Etapa 1: el latch");
        assertThat(frame.encloses(10, 10, 14, 14)).isTrue();
        assertThat(frame.topLeft().y()).isLessThan(10 - 2);
    }

    @Test
    void aFrameAroundAPartAndAThreeLineNoteHoldsTheWholeMeasuredNote() {
        EditReceipt receipt = service.edit(request(place("gate", "nand", 10, 10),
                new AddNote(Optional.of("note"), "La compuerta NAND invierte\nel AND de sus entradas\ny va aquí", 10,
                        18),
                new MakeFrame(Optional.of("stage"), "Etapa 1", List.of("new:gate", "new:note"), Optional.empty(),
                        false)));

        Frame frame = circuit.document().frame(new ElementId(receipt.aliases().get("stage"))).orElseThrow();
        TextLabel note = circuit.document().label(new ElementId(receipt.aliases().get("note"))).orElseThrow();
        assertEnclosesMeasured(frame, TextMeasure.APPROXIMATE.freeText(note));
        assertThat(frame.encloses(10, 10, 14, 14)).isTrue();
        assertThat(FrameContents.of(circuit.document(), circuit.registry(), frame))
                .contains(new ElementId(receipt.aliases().get("gate")), note.id());
    }

    @Test
    void aFrameAroundASingleNoteHoldsIt() {
        EditReceipt receipt = service.edit(request(
                new AddNote(Optional.of("note"), "Una nota sola\nen dos líneas", 0, 30),
                new MakeFrame(Optional.of("stage"), "Nota", List.of("new:note"), Optional.empty(), false)));

        Frame frame = circuit.document().frame(new ElementId(receipt.aliases().get("stage"))).orElseThrow();
        TextLabel note = circuit.document().label(new ElementId(receipt.aliases().get("note"))).orElseThrow();
        assertEnclosesMeasured(frame, TextMeasure.APPROXIMATE.freeText(note));
        assertThat(FrameContents.of(circuit.document(), circuit.registry(), frame)).containsExactly(note.id());
    }

    @Test
    void aFrameAroundAPartHoldsItsLongLabel() {
        EditReceipt receipt = service.edit(request(
                new PlacePart(Optional.of("gate"), "and", 10, 10, Rotation.R0, Map.of("negate", true),
                        "Compuerta NAND de la entrada larga"),
                new MakeFrame(Optional.of("stage"), "Etapa", List.of("new:gate"), Optional.empty(), false)));

        Frame frame = circuit.document().frame(new ElementId(receipt.aliases().get("stage"))).orElseThrow();
        Component gate = circuit.document().component(new ElementId(receipt.aliases().get("gate"))).orElseThrow();
        assertEnclosesMeasured(frame, TextMeasure.APPROXIMATE
                .componentLabel(circuit.registry().typeOf(gate), gate).orElseThrow());
    }

    private static void assertEnclosesMeasured(Frame frame, TextMeasure.TextBox box) {
        assertThat(frame.encloses((int) Math.floor(box.left()), (int) Math.floor(box.top()),
                (int) Math.ceil(box.left() + box.width()), (int) Math.ceil(box.top() + box.height())))
                .as("the frame %s encloses the text box %s", frame, box).isTrue();
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
        EditReceipt receipt = service.edit(taskRequest(task, place("gate", "nand", 0, 0)));

        service.endTask(task.taskId());

        assertThat(events).containsExactly("started " + task.title(),
                "ended " + task.title() + " " + Set.of(receipt.aliases().get("gate")));
    }

    @Test
    void undoingATaskUndoesItsStepsAndStopsAtTheUserEdit() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Dos compuertas", List.of());
        String gateId = service.edit(taskRequest(task, place("gate", "nand", 0, 0))).aliases().get("gate");
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
        service.edit(taskRequest(task, place("gate", "nand", 0, 0)));

        service.stopTask(task.taskId());

        assertRefused(taskRequest(task, place("lamp", "led", 10, 0)), AutomationError.NOT_ALLOWED);
        assertThat(circuit.document().components()).hasSize(1);
        assertThat(events).containsExactly("started Sumador", "stopped Sumador");
    }

    // --- guides used only to align (spec §7) ---

    @Test
    void guidesAddedOnlyToAlignGoAwayWhenTheTaskEndsAsOneMoreStepOfTheTask() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Alinear", List.of());
        service.edit(taskRequest(task, new AddGuide(Guide.Orientation.VERTICAL, 12, true),
                new AddGuide(Guide.Orientation.HORIZONTAL, 4, false), place("gate", "nand", 12, 4)));

        TaskEnding ending = service.endTask(task.taskId(), false);

        assertThat(ending.layoutGuidesRemoved()).isEqualTo(1);
        assertThat(circuit.document().guides()).containsExactly(new Guide(Guide.Orientation.HORIZONTAL, 4));
        assertThat(circuit.document().components()).hasSize(1);
        assertThat(service.undoTask(task.taskId())).isEqualTo(new UndoOutcome(2, false));
        assertThat(circuit.document().isEmpty()).isTrue();
        assertThat(circuit.document().guides()).isEmpty();
    }

    @Test
    void guidesAddedOnlyToAlignStayWhenTheAiOrTheUserAskedToKeepThem() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Alinear", List.of());
        service.edit(taskRequest(task, new AddGuide(Guide.Orientation.VERTICAL, 12, true)));
        long versionBefore = circuit.version().value();

        TaskEnding ending = service.endTask(task.taskId(), true);

        assertThat(ending.layoutGuidesRemoved()).isZero();
        assertThat(circuit.document().guides()).containsExactly(new Guide(Guide.Orientation.VERTICAL, 12));
        assertThat(circuit.version().value()).isEqualTo(versionBefore);
    }

    @Test
    void aGuideThatWasThereAlreadyOrThatIsGoneIsNeverTouchedAtTheEnd() {
        service.edit(request(new AddGuide(Guide.Orientation.VERTICAL, 12)));
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Alinear", List.of());
        service.edit(taskRequest(task, new AddGuide(Guide.Orientation.VERTICAL, 12, true),
                new AddGuide(Guide.Orientation.HORIZONTAL, 6, true)));
        service.edit(taskRequest(task, new RemoveGuide(Guide.Orientation.HORIZONTAL, 6)));
        long versionBefore = circuit.version().value();

        TaskEnding ending = service.endTask(task.taskId(), false);

        assertThat(ending.layoutGuidesRemoved()).isZero();
        assertThat(circuit.document().guides()).containsExactly(new Guide(Guide.Orientation.VERTICAL, 12));
        assertThat(circuit.version().value()).isEqualTo(versionBefore);
    }

    @Test
    void aStoppedTaskLeavesItsAlignmentGuidesAsItLeavesEverythingElse() {
        TaskInfo task = service.beginTask(new DocumentId(DOCUMENT), "Alinear", List.of());
        service.edit(taskRequest(task, new AddGuide(Guide.Orientation.VERTICAL, 12, true)));

        service.stopTask(task.taskId());
        service.endTask(task.taskId(), false);

        assertThat(circuit.document().guides()).containsExactly(new Guide(Guide.Orientation.VERTICAL, 12));
    }

    @Test
    void aTextSwingWouldRenderAsHtmlIsRefusedWhereverTheAiGivesOne() {
        String html = "<HTML><img src=\"http://example.invalid/x\">";
        Map<String, String> ids = inverterParts();
        long version = circuit.version().value();

        for (EditOperation operation : List.of(
                new PlacePart(Optional.empty(), "and", 40, 0, Rotation.R0, Map.of("negate", true), html),
                new SetLabel(ids.get("not"), html),
                new MakeFrame(Optional.empty(), html, List.of(ids.get("not")), Optional.empty(), false),
                new AddNote(Optional.empty(), html, 0, 30),
                new CreateComponent(Optional.empty(), html, List.copyOf(ids.values()), Map.of(), false),
                new CreateComponent(Optional.empty(), "Inversor", List.copyOf(ids.values()),
                        Map.of("A", "<html>A"), false))) {
            AutomationException refusal = assertRefused(request(operation), AutomationError.INVALID_PARAMETER);
            assertThat(refusal.getMessage()).contains("<html");
        }
        assertThat(circuit.version().value()).isEqualTo(version);
        service.edit(request(new SetLabel(ids.get("not"), "a <html> in the middle is plain text")));
    }

    @Test
    void oneCallCarriesAtMost200OperationsSoTheEditorNeverFreezesLong() {
        List<EditOperation> operations = new ArrayList<>();
        for (int index = 0; index <= 200; index++) {
            operations.add(new AddNote(Optional.empty(), "nota " + index, 0, index * 2));
        }

        AutomationException refusal = assertRefused(request(operations.toArray(EditOperation[]::new)),
                AutomationError.INVALID_PARAMETER);

        assertThat(refusal.recovery()).containsEntry("maximum", 200);
        assertNothingChanged();
        service.edit(request(operations.subList(0, 200).toArray(EditOperation[]::new)));
        assertThat(circuit.document().labels()).hasSize(200);
    }

    @Test
    void anUnexpectedFailureBeforeTheWriteSaysNothingWasChanged() {
        EditableCircuit broken = new EditableCircuit() {
            @Override
            public cl.almejo.vsim.core.document.CircuitDocument document() {
                return circuit.document();
            }

            @Override
            public cl.almejo.vsim.core.component.ComponentTypeRegistry registry() {
                throw new IllegalStateException("a broken registry");
            }

            @Override
            public cl.almejo.vsim.core.command.History history() {
                return circuit.history();
            }

            @Override
            public cl.almejo.vsim.automation.DocumentVersion version() {
                return circuit.version();
            }
        };
        EditService failing = new EditService(new cl.almejo.vsim.automation.EditHost() {
            @Override
            public <T> java.util.concurrent.CompletableFuture<T> onCircuit(DocumentId document,
                                                                         java.util.function.Function<EditableCircuit,
                                                                                 T> work) {
                return java.util.concurrent.CompletableFuture.completedFuture(work.apply(broken));
            }
        }, listeners, Duration.ofSeconds(5), Runnable::run);

        assertThatThrownBy(() -> failing.edit(request(place("gate", "nand", 0, 0))))
                .isInstanceOfSatisfying(AutomationException.class, refusal -> {
                    assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED);
                    assertThat(refusal.getMessage()).contains("Nothing was changed");
                });
        assertNothingChanged();
    }

    @Test
    void anEditForAClosedCircuitSaysSo() {
        EditRequest request = request(place("gate", "nand", 0, 0));
        host.close(DOCUMENT);

        assertRefused(request, AutomationError.DOCUMENT_CLOSED);
    }

    // --- the user's library («Mis componentes») ---

    @Test
    void anInstanceOfALibraryComponentBringsItsDefinitionsIntoTheCircuit() {
        circuit.useLibrary(List.of(new DefinitionBundle(ExampleDefinitions.doubleInverter(),
                List.of(ExampleDefinitions.inverter()))));

        EditReceipt receipt = service.edit(request(
                new PlaceInstance(Optional.of("twice"), "Doble inversor", 0, 0, Rotation.R0)));

        assertThat(circuit.document().definitions()).extracting(ComponentDefinition::id)
                .containsExactlyInAnyOrder(ExampleDefinitions.INVERTER, ExampleDefinitions.DOUBLE_INVERTER);
        assertThat(circuit.document().component(new ElementId(receipt.aliases().get("twice"))).orElseThrow().typeId())
                .isEqualTo(SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER));
        service.edit(request(new PlacePart(Optional.empty(),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 0, 20, Rotation.R0, Map.of(), "")));
        assertThat(circuit.document().components()).hasSize(2);
        circuit.history().undo();
        circuit.history().undo();
        assertThat(circuit.document().definitions()).isEmpty();
    }

    @Test
    void aFailedCallLeavesNoLibraryDefinitionBehind() {
        circuit.useLibrary(List.of(new DefinitionBundle(ExampleDefinitions.inverter(), List.of())));

        assertRefused(request(new PlaceInstance(Optional.empty(), ExampleDefinitions.INVERTER, 0, 0, Rotation.R0),
                place("gate", "flux-capacitor", 20, 0)), AutomationError.INVALID_REFERENCE);

        assertNothingChanged();
    }

    @Test
    void anUnknownComponentListsTheCircuitsAndTheLibrarysOnes() {
        circuit.useLibrary(List.of(new DefinitionBundle(ExampleDefinitions.inverter(), List.of())));

        AutomationException refusal = assertRefused(request(
                new PlaceInstance(Optional.empty(), "Sumador", 0, 0, Rotation.R0)), AutomationError.INVALID_REFERENCE);

        assertThat(refusal.recovery().get("components").toString()).contains(ExampleDefinitions.inverter().name());
    }

    @Test
    void aNewComponentNeverTakesTheIdOfOneInTheLibrary() {
        Map<String, String> ids = inverterParts();
        ComponentDefinition inLibrary = ExampleDefinitions.inverter();
        circuit.useLibrary(List.of(new DefinitionBundle(inLibrary, List.of())));

        EditReceipt receipt = service.edit(request(new CreateComponent(Optional.empty(), "Inverter",
                List.copyOf(ids.values()), Map.of(), false)));

        assertThat(receipt.components().getFirst().definitionId()).isNotEqualTo(inLibrary.id());
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

    /** A part of type {@code typeId}, or of a preset's type with its parameters ({@code nand}, {@code not}). */
    private static PlacePart place(String alias, String typeId, int x, int y) {
        Optional<ComponentPreset> preset = StandardComponentTypes.presets().stream()
                .filter(candidate -> candidate.id().equals(typeId)).findFirst();
        return new PlacePart(Optional.of(alias), preset.map(ComponentPreset::typeId).orElse(typeId), x, y,
                Rotation.R0, preset.map(ComponentPreset::parameters).orElse(Map.of()), "");
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
                place("xor1", "xor", 10, 0),
                place("xor2", "xor", 22, 2),
                place("and1", "and", 22, 10),
                place("and2", "and", 22, 18),
                place("or", "or", 32, 14),
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
