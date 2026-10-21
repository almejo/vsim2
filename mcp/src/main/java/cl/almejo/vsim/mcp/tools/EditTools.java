package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.edit.EditOperation;
import cl.almejo.vsim.automation.edit.EditReceipt;
import cl.almejo.vsim.automation.edit.EditRequest;
import cl.almejo.vsim.automation.edit.PinTarget;
import cl.almejo.vsim.automation.edit.TaskStep;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** {@code vsim_edit}: one call, a list of typed operations, all or nothing, one undo step (spec §10, A§6). */
final class EditTools {

    static final String EDIT = "vsim_edit";
    /** At most this many operations per call (as the planner allows). */
    static final int MAXIMUM_OPERATIONS = 500;

    private static final List<String> ROTATIONS = List.of("R0", "R90", "R180", "R270");
    private static final List<String> ORIENTATIONS = List.of("HORIZONTAL", "VERTICAL");

    private EditTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(edit(api));
    }

    // --- schema ---

    private static Map<String, Object> target(String description) {
        return Schema.nonEmptyString(description + ": a real id (c12, w3, f1, t2) or new:<alias> for something an "
                + "earlier operation of this call made");
    }

    private static Map<String, Object> targets(String description) {
        return Schema.array(description, target("An element"));
    }

    private static Map<String, Object> alias() {
        return Schema.nonEmptyString("A name for it in this call; later operations refer to it as new:<alias> and "
                + "the receipt maps it to the real id");
    }

    private static Map<String, Object> rotation() {
        return Schema.enumeration("R0 (default), R90, R180 or R270, clockwise on screen", ROTATIONS);
    }

    private static Map<String, Object> pinTarget(String description) {
        return Schema.object(description)
                .property("target", target("The part"))
                .property("pinName", Schema.nonEmptyString("The pin's name as vsim_describe_component lists it"))
                .property("index", Schema.integer("With pinName: which pin of that name (0 for the first; only "
                        + "names that repeat need it). Without pinName: the pin's place among the part's pins",
                        0, Integer.MAX_VALUE))
                .required("target").build();
    }

    private static Schema.ObjectSchema operation(String kind, String description) {
        return Schema.object(description).property("op", Schema.constant(kind)).required("op");
    }

    private static Map<String, Object> operations() {
        List<Map<String, Object>> kinds = List.of(
                operation("PlacePart", "Places a part of typeId with its origin at (x, y)")
                        .property("alias", alias())
                        .property("typeId", Schema.nonEmptyString("From vsim_describe_components"))
                        .property("x", Schema.integer("Grid units"))
                        .property("y", Schema.integer("Grid units"))
                        .property("rotation", rotation())
                        .property("parameters", Schema.mapOf("Parameter values by name",
                                Schema.anyValue("A number or a text")))
                        .property("label", Schema.string("Its label (inputs, outputs and blocks should have one)"))
                        .required("typeId", "x", "y").build(),
                operation("SetParameters", "Changes some parameters of a part; the others keep their value")
                        .property("target", target("The part"))
                        .property("parameters", Schema.mapOf("Parameter values by name",
                                Schema.anyValue("A number or a text")))
                        .required("target", "parameters").build(),
                operation("SetLabel", "The label of a part, the text of a note or the title of a frame")
                        .property("target", target("The part, note or frame"))
                        .property("text", Schema.string("The new text"))
                        .required("target", "text").build(),
                operation("Connect", "Joins two pins with an orthogonal route through the via points, if any; "
                        + "VSim checks what the route joins and refuses (ROUTE_REJECTED) one that would touch "
                        + "another net — then retry with via points around it")
                        .property("from", pinTarget("One pin"))
                        .property("to", pinTarget("The other pin"))
                        .property("via", Schema.array("Points the route passes through, in order (each leg goes "
                                + "across first, then down)", Schema.point("")))
                        .required("from", "to").build(),
                operation("AddWire", "A wire through points, each straight across or down from the one before")
                        .property("points", Schema.array("At least two", Schema.point(""), 2, 1000))
                        .required("points").build(),
                operation("Move", "Moves the targets; their wires follow as when the user drags them")
                        .property("targets", targets("What to move"))
                        .property("deltaX", Schema.integer("Grid units right"))
                        .property("deltaY", Schema.integer("Grid units down"))
                        .required("targets", "deltaX", "deltaY").build(),
                operation("Delete", "Deletes the targets (a locked frame with what it holds). Never delete a "
                        + "frame or a stage's parts the user did not ask to remove")
                        .property("targets", targets("What to delete"))
                        .required("targets").build(),
                operation("MakeFrame", "A titled frame around the targets, or exactly at bounds")
                        .property("alias", alias())
                        .property("title", Schema.string("Its title"))
                        .property("targets", targets("What it wraps"))
                        .property("bounds", Schema.bounds("Its rectangle instead of wrapping targets"))
                        .property("locked", Schema.bool("Locked frames move and copy with their contents"))
                        .required("title").build(),
                operation("AddNote", "A free text, such as the «Prueba: …» line of the bundled examples")
                        .property("alias", alias())
                        .property("text", Schema.nonEmptyString("The text"))
                        .property("x", Schema.integer("Top-left corner"))
                        .property("y", Schema.integer("Top-left corner"))
                        .required("text", "x", "y").build(),
                operation("AddGuide", "An alignment guide across the canvas (remove the guides you only used to "
                        + "align before the task ends)")
                        .property("orientation", Schema.enumeration("HORIZONTAL (y = position) or VERTICAL "
                                + "(x = position)", ORIENTATIONS))
                        .property("position", Schema.integer("Grid units"))
                        .required("orientation", "position").build(),
                operation("RemoveGuide", "Takes an alignment guide away")
                        .property("orientation", Schema.enumeration("HORIZONTAL or VERTICAL", ORIENTATIONS))
                        .property("position", Schema.integer("Grid units"))
                        .required("orientation", "position").build(),
                operation("CreateComponent", "«Crear componente desde selección» on the targets: their "
                        + "input-pin/output-pin parts and test parts (switches, LEDs…) become the component's pins. "
                        + "replaceSelection false keeps the parts as «cómo se hizo» (what a stage that keeps its "
                        + "steps wants); true replaces them by one instance. The component stays in the circuit; "
                        + "VSim never writes it to the user's library")
                        .property("alias", Schema.nonEmptyString("A name for the new component in this call: "
                                + "PlaceInstance takes it as new:<alias>"))
                        .property("name", Schema.nonEmptyString("Its name, new in the circuit"))
                        .property("targets", targets("The parts that make it; the wires that join only "
                                + "these parts come along (a wire to a part outside does not)"))
                        .property("pinNames", Schema.mapOf("Renamed pins: key an interface pin or a test part "
                                + "that gives one pin", Schema.nonEmptyString("The pin's name")))
                        .property("replaceSelection", Schema.bool("Replace the targets by one instance"))
                        .required("name", "targets", "replaceSelection").build(),
                operation("PlaceInstance", "Places an instance of a user component: the circuit's own or one of "
                        + "the user's library (its definition comes into the circuit, as from the palette)")
                        .property("alias", alias())
                        .property("definition", Schema.nonEmptyString("Its name, its id or new:<alias> of a "
                                + "CreateComponent of this call"))
                        .property("x", Schema.integer("Grid units"))
                        .property("y", Schema.integer("Grid units"))
                        .property("rotation", rotation())
                        .required("definition", "x", "y").build(),
                operation("TidyWires", "«Ordenar cables» on the targets' wires (all wires when no targets)")
                        .property("targets", targets("Whose wires to tidy"))
                        .build());
        return Schema.array("The operations, carried out in order on a copy and applied together or not at all",
                Schema.oneOf("One operation, chosen by op", kinds), 1, MAXIMUM_OPERATIONS);
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
            case "SetLabel" -> new EditOperation.SetLabel(operation.string("target"), operation.string("text"));
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
                    operation.integer("x"), operation.integer("y"));
            case "AddGuide" -> new EditOperation.AddGuide(
                    operation.enumeration("orientation", Guide.Orientation.class), operation.integer("position"));
            case "RemoveGuide" -> new EditOperation.RemoveGuide(
                    operation.enumeration("orientation", Guide.Orientation.class), operation.integer("position"));
            case "CreateComponent" -> new EditOperation.CreateComponent(operation.optionalString("alias"),
                    operation.string("name"), operation.strings("targets"), operation.stringMap("pinNames"),
                    operation.bool("replaceSelection", false));
            case "PlaceInstance" -> new EditOperation.PlaceInstance(operation.optionalString("alias"),
                    operation.string("definition"), operation.integer("x"), operation.integer("y"),
                    rotationOf(operation));
            case "TidyWires" -> new EditOperation.TidyWires(operation.strings("targets"));
            default -> throw operation.invalid("op", "is not an operation VSim knows: " + kind);
        };
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
                "One edit call: a list of typed operations carried out in order on a copy of the circuit and "
                        + "applied all together as one undo step, or not at all (the first failure names its "
                        + "operation and nothing changes). Send the version you read: if the user changed the "
                        + "circuit since, it is refused as STALE_DOCUMENT with the current version — read again and "
                        + "adapt. A retry with the same operationId and arguments returns the first receipt and adds "
                        + "nothing. Parts made in this call are named new:<alias>; the receipt maps aliases to real "
                        + "ids, says what each Connect joined (as pins) and lists warnings. validateOnly checks "
                        + "everything and changes nothing. Positions are grid units; lay parts out left to right "
                        + "(inputs left, outputs right) with room between them and for their labels.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("version", Common.version())
                        .property("operationId", Schema.nonEmptyString("Your own key for this call: a retry with "
                                + "the same key and arguments is answered with the first receipt"))
                        .property("taskId", Schema.nonEmptyString("The task this edit belongs to (vsim_begin_task)"))
                        .property("stage", Schema.string("The stage's title"))
                        .property("step", Schema.string("The step it carries out"))
                        .property("text", Schema.string("One line saying what it does («Conecto la "
                                + "realimentación»)"))
                        .property("operations", operations())
                        .property("validateOnly", Schema.bool("Check everything, change nothing"))
                        .required("document", "version", "operationId", "taskId", "operations").build(),
                Common.versionedResult("The receipt")
                        .property("applied", Schema.bool("False for validateOnly"))
                        .property("aliases", Schema.result("alias → real id").build())
                        .property("connections", Schema.array("Per Connect: from, to, the wires added and the nets "
                                + "joined (as pins)", Schema.result("").build()))
                        .property("components", Schema.array("Components created: their id, name and pins",
                                Schema.result("").build()))
                        .property("changes", Schema.result("Element ids added, changed and removed (wires merge: "
                                + "an id may give way to another)").build())
                        .property("warnings", Schema.array("Things to look at", Schema.string("")))
                        .property("undoDescription", Schema.string("The i18n key of its undo step"))
                        .required("applied", "aliases", "changes", "warnings").build(),
                Tool.Hints.MAY_REMOVE_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "version", 3, "operationId", "latch-1", "taskId", "task-1",
                        "stage", "Latch SR", "step", "Coloco las compuertas", "text", "Coloco dos NAND",
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
                            arguments.string("text", "")));
                    EditReceipt receipt = api.edit(new EditRequest(arguments.document(),
                            arguments.version("version"), arguments.string("operationId"), step, operations,
                            arguments.bool("validateOnly", false)));
                    Map<String, Object> structured = new java.util.LinkedHashMap<>();
                    structured.put("document", arguments.document().value());
                    structured.putAll(Json.object(receipt));
                    return Tool.Output.of(structured);
                });
    }
}
