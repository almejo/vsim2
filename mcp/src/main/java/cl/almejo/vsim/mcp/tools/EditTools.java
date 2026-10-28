package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.NoteStyleChange;
import cl.almejo.vsim.automation.edit.PinTarget;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.automation.present.PresentedEdit;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** {@code vsim_edit}: one call, a list of typed operations, all or nothing, one undo step (spec §10, A§6). */
final class EditTools {

    static final String EDIT = "vsim_edit";
    /** At most this many operations per call (as the planner allows). */
    static final int MAXIMUM_OPERATIONS = 200;

    private static final List<String> ROTATIONS = List.of("R0", "R90", "R180", "R270");
    private static final List<String> ORIENTATIONS = List.of("HORIZONTAL", "VERTICAL");
    private static final List<String> TEMPLATES = Arrays.stream(SymbolTemplates.Template.values())
            .map(SymbolTemplates.Template::name).toList();
    private static final List<String> STICKY_COLORS = Arrays.stream(StickyColor.values()).map(StickyColor::name)
            .toList();
    private static final List<String> NOTE_COLORS = Arrays.stream(NoteColor.values()).map(NoteColor::name).toList();

    private EditTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(edit(api));
    }

    // --- schema ---

    /** What every target and alias means, said once for all the operations (fewer tokens). */
    private static final String OPERATIONS_DESCRIPTION = "The operations, carried out in order on a copy and applied "
            + "together or not at all. Positions are grid units. A target is a real id (c12, w3, f1, t2) or "
            + "new:<alias> for something an earlier operation of this call made; an alias names what an operation "
            + "makes: later operations refer to it as new:<alias> and the receipt maps it to the real id";

    private static Map<String, Object> target(String description) {
        return Schema.nonEmptyString(description);
    }

    private static Map<String, Object> targets(String description) {
        return Schema.array(description, Schema.nonEmptyString(""));
    }

    private static Map<String, Object> alias() {
        return Schema.nonEmptyString("");
    }

    private static Map<String, Object> rotation() {
        return Schema.enumeration("Clockwise on screen (default R0)", ROTATIONS);
    }

    private static Map<String, Object> coordinate() {
        return Schema.integer("");
    }

    /** A pin of a part; {@code described} false for a second copy whose properties need no words again. */
    private static Map<String, Object> pinTarget(String description, boolean described) {
        return Schema.object(description)
                .property("target", target(described ? "The part" : ""))
                .property("pinName", Schema.nonEmptyString(described ? "As vsim_describe_component lists it" : ""))
                .property("index", Schema.integerFrom(described ? "With pinName: which pin of that name (0, the "
                        + "default; only names that repeat need it). Without pinName: the pin's place on the part"
                        : "", 0))
                .required("target").build();
    }

    /** A note's colours by name, the same list for the three. */
    private static Map<String, Object> noteStyle(String description) {
        Map<String, Object> color = Schema.enumeration("", NOTE_COLORS);
        return Schema.object(description).property("background", color).property("text", color)
                .property("border", color).build();
    }

    private static Schema.ObjectSchema operation(String kind, String description) {
        return Schema.object(description).property("op", Schema.constant(kind)).required("op");
    }

    private static Map<String, Object> operations() {
        Map<String, Object> parameters = Schema.mapOf("Parameter values by name (a number, a text or true/false)",
                Schema.anyValue(""));
        List<Map<String, Object>> kinds = List.of(
                operation("PlacePart", "Places a part of typeId with its origin at (x, y)")
                        .property("alias", alias())
                        .property("typeId", Schema.nonEmptyString("From vsim_describe_components"))
                        .property("x", coordinate())
                        .property("y", coordinate())
                        .property("rotation", rotation())
                        .property("parameters", parameters)
                        .property("label", Schema.string("Inputs, outputs and blocks should have one"))
                        .required("typeId", "x", "y").build(),
                operation("SetParameters", "Changes some parameters of a part; the others keep their value")
                        .property("target", target("The part"))
                        .property("parameters", parameters)
                        .required("target", "parameters").build(),
                operation("SetLabel", "The label of a part, the text of a note or the title of a frame; text, "
                        + "style or both")
                        .property("target", target(""))
                        .property("text", Schema.string("A note may have several lines: use \\n for a new line "
                                + "(a label or a title stays one line)"))
                        .property("style", noteStyle("Notes only: the colours given change"))
                        .required("target").build(),
                operation("Connect", "Joins two pins with an orthogonal route through the via points, if any; a "
                        + "route that would touch another net is refused (ROUTE_REJECTED): retry with via points "
                        + "around it")
                        .property("from", pinTarget("One pin", true))
                        .property("to", pinTarget("The other pin", false))
                        .property("via", Schema.array("In order; each leg goes across first, then down",
                                Schema.point("")))
                        .required("from", "to").build(),
                operation("AddWire", "A wire through points, each straight across or down from the one before")
                        .property("points", Schema.array("", Schema.point(""), 2, 1000))
                        .required("points").build(),
                operation("Move", "Moves the targets; their wires follow as when the user drags them")
                        .property("targets", targets(""))
                        .property("deltaX", coordinate())
                        .property("deltaY", coordinate())
                        .required("targets", "deltaX", "deltaY").build(),
                operation("Delete", "Deletes the targets (a locked frame with what it holds)")
                        .property("targets", targets(""))
                        .required("targets").build(),
                operation("MakeFrame", "A titled frame around the targets, or exactly at bounds")
                        .property("alias", alias())
                        .property("title", Schema.string(""))
                        .property("targets", targets(""))
                        .property("bounds", Schema.bounds("Instead of wrapping targets"))
                        .property("locked", Schema.bool("Moves and copies with its contents"))
                        .required("title").build(),
                operation("AddNote", "A free text, such as a «Prueba: …» line")
                        .property("alias", alias())
                        .property("text", Schema.nonEmptyString("One note of several lines, not several notes: "
                                + "use \\n for a new line"))
                        .property("x", Schema.integer("Top-left corner"))
                        .property("y", coordinate())
                        .property("style", noteStyle("Colours; NONE or left out: a plain note"))
                        .required("text", "x", "y").build(),
                operation("AddStickyNote", "A reminder for the user: text or a to-do list")
                        .property("alias", alias())
                        .property("x", Schema.integer("Top-left corner"))
                        .property("y", coordinate())
                        .property("width", Schema.integer("Default 8", StickyNote.MINIMUM_WIDTH, 100))
                        .property("height", Schema.integer("Default 6", StickyNote.MINIMUM_HEIGHT, 100))
                        .property("color", Schema.enumeration("Default YELLOW", STICKY_COLORS))
                        .property("text", Schema.nonEmptyString("Text (\\n: new line), or items"))
                        .property("items", Schema.array("To-do list", Schema.nonEmptyString(""), 1, 100))
                        .property("folded", Schema.bool(""))
                        .required("x", "y").build(),
                operation("SetStickyItem", "Ticks or unticks a sticky note's to-do item")
                        .property("target", target(""))
                        .property("index", Schema.integerFrom("From 0", 0))
                        .property("done", Schema.bool(""))
                        .required("target", "index", "done").build(),
                operation("AddGuide", "An alignment guide; layoutOnly true: VSim removes it when the task ends")
                        .property("orientation", Schema.enumeration("HORIZONTAL (y = position) or VERTICAL "
                                + "(x = position)", ORIENTATIONS))
                        .property("position", coordinate())
                        .property("layoutOnly", Schema.bool(""))
                        .required("orientation", "position").build(),
                operation("RemoveGuide", "Takes an alignment guide away")
                        .property("orientation", Schema.enumeration("", ORIENTATIONS))
                        .property("position", coordinate())
                        .required("orientation", "position").build(),
                operation("CreateComponent", "«Crear componente desde selección» on the targets: their "
                        + "input-pin/output-pin and test parts (switches, LEDs…) become its pins. replaceSelection "
                        + "false keeps the parts («cómo se hizo»); true replaces them by one instance. It stays in "
                        + "the circuit, never in the user's library")
                        .property("alias", Schema.nonEmptyString("PlaceInstance takes it as new:<alias>"))
                        .property("name", Schema.nonEmptyString("New in the circuit"))
                        .property("targets", targets("The wires that join only these parts come along"))
                        .property("pinNames", Schema.mapOf("Renamed pins, keyed by interface pin or test part",
                                Schema.nonEmptyString("")))
                        .property("replaceSelection", Schema.bool(""))
                        .required("name", "targets", "replaceSelection").build(),
                operation("PlaceInstance", "Places an instance of a user component: the circuit's own or one of "
                        + "the user's library")
                        .property("alias", alias())
                        .property("definition", Schema.nonEmptyString("Its name, its id or new:<alias> of a "
                                + "CreateComponent of this call"))
                        .property("x", coordinate())
                        .property("y", coordinate())
                        .property("rotation", rotation())
                        .required("definition", "x", "y").build(),
                operation("SetComponentShape", "«Cambiar forma» of a user component: one placed instance (instance, "
                        + "its own shape) or the circuit's definition (definition, instances without their own). "
                        + "Refused whole if a wire could not follow a moved pin")
                        .property("instance", Schema.nonEmptyString("Id or new:<alias>"))
                        .property("definition", Schema.nonEmptyString("Name, id or new:<alias>"))
                        .property("template", Schema.enumeration("", TEMPLATES))
                        .property("pins", Schema.array("Grid units from the box's top-left corner",
                                Schema.object("")
                                .property("name", Schema.nonEmptyString(""))
                                .property("x", coordinate())
                                .property("y", coordinate())
                                .required("name", "x", "y").build()))
                        .property("clear", Schema.bool("Back to the definition's shape (a definition: the box)"))
                        .build(),
                operation("SetMemoryContents", "Writes hex words into the initial contents of a RAM or ROM from "
                        + "firstAddress; all or nothing; a linked memory is the user's")
                        .property("component", target("The RAM or ROM"))
                        .property("firstAddress", Schema.integerFrom("", 0))
                        .property("words", Schema.array("Hex, each within the width", Schema.string(""), 1, 4096))
                        .required("component", "firstAddress", "words").build(),
                operation("TidyWires", "«Ordenar cables» on the targets' wires (all wires when no targets)")
                        .property("targets", targets(""))
                        .build());
        return Schema.array(OPERATIONS_DESCRIPTION, Schema.oneOf("One operation, chosen by op", kinds), 1,
                MAXIMUM_OPERATIONS);
    }

    // --- parsing ---

    private static EditOperation operationOf(Arguments operation) {
        String kind = operation.string("op");
        return switch (kind) {
            case "PlacePart" -> new EditOperation.PlacePart(operation.optionalString("alias"),
                    operation.string("typeId"), operation.integer("x"), operation.integer("y"), rotationOf(operation),
                    operation.map("parameters"), operation.string("label", ""));
            case "SetParameters" -> new EditOperation.SetParameters(operation.string("target"),
                    operation.map("parameters"));
            case "SetLabel" -> new EditOperation.SetLabel(operation.string("target"),
                    operation.optionalString("text"), operation.optionalObject("style").map(EditTools::styleOf));
            case "Connect" -> new EditOperation.Connect(pinTargetOf(operation.object("from")),
                    pinTargetOf(operation.object("to")), operation.points("via"));
            case "AddWire" -> new EditOperation.AddWire(operation.points("points"));
            case "Move" -> new EditOperation.Move(operation.strings("targets"), operation.integer("deltaX"),
                    operation.integer("deltaY"));
            case "Delete" -> new EditOperation.Delete(operation.strings("targets"));
            case "MakeFrame" -> new EditOperation.MakeFrame(operation.optionalString("alias"),
                    operation.string("title"), operation.strings("targets"), operation.optionalBounds("bounds"),
                    operation.bool("locked", false));
            case "AddNote" -> new EditOperation.AddNote(operation.optionalString("alias"), operation.string("text"),
                    operation.integer("x"), operation.integer("y"), operation.optionalObject("style")
                    .map(style -> styleOf(style).applyTo(NoteStyle.PLAIN)).orElse(NoteStyle.PLAIN));
            case "AddStickyNote" -> new EditOperation.AddStickyNote(operation.optionalString("alias"),
                    operation.integer("x"), operation.integer("y"),
                    operation.has("width") ? OptionalInt.of(operation.integer("width")) : OptionalInt.empty(),
                    operation.has("height") ? OptionalInt.of(operation.integer("height")) : OptionalInt.empty(),
                    operation.has("color") ? Optional.of(operation.enumeration("color", StickyColor.class))
                            : Optional.empty(),
                    operation.optionalString("text"),
                    operation.has("items") ? Optional.of(operation.strings("items")) : Optional.empty(),
                    operation.bool("folded", false));
            case "SetStickyItem" -> new EditOperation.SetStickyItem(operation.string("target"),
                    operation.integer("index"), operation.bool("done", true));
            case "AddGuide" -> new EditOperation.AddGuide(
                    operation.enumeration("orientation", Guide.Orientation.class), operation.integer("position"),
                    operation.bool("layoutOnly", false));
            case "RemoveGuide" -> new EditOperation.RemoveGuide(
                    operation.enumeration("orientation", Guide.Orientation.class), operation.integer("position"));
            case "CreateComponent" -> new EditOperation.CreateComponent(operation.optionalString("alias"),
                    operation.string("name"), operation.strings("targets"), operation.stringMap("pinNames"),
                    operation.bool("replaceSelection", false));
            case "PlaceInstance" -> new EditOperation.PlaceInstance(operation.optionalString("alias"),
                    operation.string("definition"), operation.integer("x"), operation.integer("y"),
                    rotationOf(operation));
            case "SetComponentShape" -> new EditOperation.SetComponentShape(operation.optionalString("instance"),
                    operation.optionalString("definition"), operation.has("template")
                    ? Optional.of(operation.enumeration("template", SymbolTemplates.Template.class))
                    : Optional.empty(), operation.objects("pins").stream()
                    .map(pin -> new EditOperation.SetComponentShape.PinPosition(pin.string("name"),
                            pin.integer("x"), pin.integer("y"))).toList(), operation.bool("clear", false));
            case "SetMemoryContents" -> new EditOperation.SetMemoryContents(operation.string("component"),
                    operation.longValue("firstAddress"), operation.strings("words"));
            case "TidyWires" -> new EditOperation.TidyWires(operation.strings("targets"));
            default -> throw operation.invalid("op", "is not an operation VSim knows: " + kind);
        };
    }

    /** How «Presentar» took the call; a call that was not paced is APPLIED when it changed the circuit. */
    private static Optional<StepStatus> statusOf(PresentedEdit presented) {
        if (presented.status().isPresent()) {
            return presented.status();
        }
        return presented.receipt().applied() ? Optional.of(StepStatus.APPLIED) : Optional.empty();
    }

    /** The colours a {@code style} argument names; the ones it leaves out stay empty. */
    private static NoteStyleChange styleOf(Arguments style) {
        return new NoteStyleChange(color(style, "background"), color(style, "text"), color(style, "border"));
    }

    private static Optional<NoteColor> color(Arguments style, String name) {
        return style.has(name) ? Optional.of(style.enumeration(name, NoteColor.class)) : Optional.empty();
    }

    private static Rotation rotationOf(Arguments operation) {
        return operation.enumeration("rotation", Rotation.class, Rotation.R0);
    }

    private static PinTarget pinTargetOf(Arguments pin) {
        return new PinTarget(pin.string("target"), pin.optionalString("pinName"),
                pin.has("index") ? OptionalInt.of(pin.integer("index")) : OptionalInt.empty());
    }

    // --- the tool ---

    private static Tool edit(AutomationApi api) {
        return new Tool(EDIT, "Edit the circuit",
                "Typed operations carried out in order on a copy of the circuit and applied together as one undo "
                        + "step, or not at all (the first failure names its operation). STALE_DOCUMENT: the user "
                        + "changed the circuit since your version; read again and adapt. A retry with the same "
                        + "operationId and arguments returns the first receipt. The receipt gives the new version, "
                        + "the real ids of the aliases, each Connect's wires (joinedNets only when it joined more "
                        + "than its two pins) and warnings; empty and default fields are left out. validateOnly "
                        + "changes nothing. Leave the user's sticky notes alone unless asked. A guide only to "
                        + "align: AddGuide layoutOnly true. At most " + MAXIMUM_OPERATIONS + " operations; in a "
                        + "big circuit send fewer. In «Presentar» send one "
                        + "step per call with stage, step, text and reason: status APPLIED is in the circuit; "
                        + "PROPOSED is a ghost, nothing changed yet (the aliases already name the ids it will "
                        + "have): call vsim_await_step until APPLIED before the next step. No status: only "
                        + "validated.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("version", Common.version())
                        .property("operationId", Schema.nonEmptyString("Your own key for this call"))
                        .property("taskId", Schema.nonEmptyString("From vsim_begin_task"))
                        .property("stage", Schema.string("The stage's title"))
                        .property("step", Schema.string("The step it carries out"))
                        .property("text", Schema.string("One line: what it does («Conecto la realimentación»)"))
                        .property("reason", Schema.string("Optional, short: why («así el latch recuerda»)"))
                        .property("narration", PresentationTools.narration("before the step"))
                        .property("operations", operations())
                        .property("validateOnly", Schema.bool("Check everything, change nothing"))
                        .required("document", "version", "operationId", "taskId", "operations").build(),
                receiptSchema(),
                Tool.Hints.MAY_REMOVE_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "version", 3, "operationId", "latch-1", "taskId", "task-1",
                        "stage", "Latch SR", "step", "Coloco las compuertas", "text", "Coloco dos NAND",
                        "reason", "Dos NAND cruzadas forman el latch",
                        "operations", List.of(
                                Map.of("op", "PlacePart", "alias", "top", "typeId", "nand2", "x", 10, "y", 0),
                                Map.of("op", "PlacePart", "alias", "bottom", "typeId", "nand2", "x", 10, "y", 10),
                                Map.of("op", "PlacePart", "alias", "s", "typeId", "switch", "x", 0, "y", 0,
                                        "label", "S"),
                                Map.of("op", "Connect", "from", Map.of("target", "new:s", "pinName", "out"),
                                        "to", Map.of("target", "new:top", "pinName", "in0")))),
                        Map.of("document", "circuit-1", "version", 4, "operationId", "component-1",
                                "taskId", "task-1", "operations", List.of(
                                        Map.of("op", "CreateComponent", "alias", "latch", "name", "Latch SR",
                                                "targets", List.of("c1", "c2", "c3", "c4"),
                                                "replaceSelection", false),
                                        Map.of("op", "PlaceInstance", "definition", "new:latch", "x", 40, "y", 0)))),
                arguments -> {
                    List<EditOperation> operations = new ArrayList<>();
                    for (Arguments operation : arguments.objects("operations")) {
                        operations.add(operationOf(operation));
                    }
                    Optional<TaskStep> step = Optional.of(new TaskStep(arguments.string("taskId"),
                            arguments.string("stage", ""), arguments.string("step", ""),
                            arguments.string("text", ""),
                            arguments.optionalString("reason").filter(reason -> !reason.isBlank())));
                    PresentedEdit presented = api.editStep(new EditRequest(arguments.document(),
                            arguments.version("version"), arguments.string("operationId"), step, operations,
                            arguments.bool("validateOnly", false)),
                            arguments.optionalString("narration").filter(line -> !line.isBlank()));
                    Map<String, Object> structured = new LinkedHashMap<>();
                    structured.put("document", arguments.document().value());
                    statusOf(presented).ifPresent(status -> structured.put("status", status.name()));
                    structured.putAll(receipt(presented.receipt()));
                    return Tool.Output.of(structured);
                });
    }

    // --- the receipt ---

    private static Map<String, Object> receiptSchema() {
        return Common.versionedResult("The receipt")
                .property("status", Schema.enumeration("APPLIED: in the circuit; PROPOSED: a ghost in «Presentar», "
                        + "call vsim_await_step; absent for validateOnly",
                        List.of(StepStatus.APPLIED.name(), StepStatus.PROPOSED.name())))
                .property("aliases", Schema.result("alias → real id").build())
                .property("connections", Schema.array("Per Connect: its operation, from, to, wireIds; joinedNets "
                        + "when it joined more than its two pins; alreadyConnected when it added nothing",
                        Schema.anyObject()))
                .property("components", Schema.array("Components created: id, name and pins",
                        Schema.anyObject()))
                .property("changes", Schema.result("Element ids added, changed and removed (wires merge: an id "
                        + "may give way to another)").build())
                .property("warnings", Schema.array("Things to look at", Schema.string("")))
                .required("aliases", "changes").build();
    }

    /**
     * What the AI goes on with after an edit (fewer tokens, 2026-10-22): the version, the aliases, each Connect's
     * wires, the components, the changed ids and the warnings; empty lists, default values ({@code index} 0,
     * {@code alreadyConnected} false), {@code applied} (the status says it) and the undo step's i18n key are left
     * out, and a Connect's {@code joinedNets} only when a net held more than the pin it was joined by.
     */
    static Map<String, Object> receipt(EditReceipt receipt) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("version", receipt.version().value());
        answer.put("aliases", Json.toJson(receipt.aliases()));
        if (!receipt.connections().isEmpty()) {
            answer.put("connections", receipt.connections().stream().map(EditTools::connection).toList());
        }
        if (!receipt.components().isEmpty()) {
            answer.put("components", Json.toJson(receipt.components()));
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        putUnlessEmpty(changes, "added", receipt.changes().added());
        putUnlessEmpty(changes, "changed", receipt.changes().changed());
        putUnlessEmpty(changes, "removed", receipt.changes().removed());
        answer.put("changes", changes);
        putUnlessEmpty(answer, "warnings", receipt.warnings());
        return answer;
    }

    private static Map<String, Object> connection(EditReceipt.ConnectionReport report) {
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("operation", report.operation());
        connection.put("from", pin(report.from()));
        connection.put("to", pin(report.to()));
        putUnlessEmpty(connection, "wireIds", report.wireIds());
        if (!report.alreadyConnected() && report.joinedNets().stream().anyMatch(net -> net.size() > 1)) {
            connection.put("joinedNets", report.joinedNets().stream()
                    .map(net -> net.stream().map(EditTools::pin).toList()).toList());
        }
        if (report.alreadyConnected()) {
            connection.put("alreadyConnected", true);
        }
        return connection;
    }

    private static Map<String, Object> pin(PinReference pin) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("elementId", pin.elementId());
        answer.put("pinName", pin.pinName());
        if (pin.index() != 0) {
            answer.put("index", pin.index());
        }
        return answer;
    }

    private static void putUnlessEmpty(Map<String, Object> object, String key, List<?> values) {
        if (!values.isEmpty()) {
            object.put(key, Json.toJson(values));
        }
    }
}
