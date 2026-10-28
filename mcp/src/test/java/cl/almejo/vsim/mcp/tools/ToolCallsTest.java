package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.ProposalAnswer;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.automation.simulate.SimulationReceipt;
import cl.almejo.vsim.automation.simulate.SimulationRequest;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.mcp.McpResults.structured;
import static cl.almejo.vsim.mcp.McpResults.map;
import static cl.almejo.vsim.mcp.McpResults.list;
import static org.assertj.core.api.Assertions.assertThat;

/** Each tool called as a client calls it, on a headless host with real circuits; every result matches its schema. */
class ToolCallsTest {

    private static final JsonSchemaValidator VALIDATOR = McpJsonDefaults.getSchemaValidator();

    private final ToolTestHost host = new ToolTestHost();
    private final AutomationApi api = new AutomationApi(host);
    private final ToolRunner runner = new ToolRunner(new JacksonMcpJsonMapper(new ObjectMapper()), VALIDATOR);
    private final List<Tool> tools = VsimTools.tools(api);
    private int nextOperation = 1;

    @BeforeEach
    void openACircuit() {
        host.open("circuit-1");
    }

    // --- errors are tool results, never protocol errors ---

    @Test
    void anInputTheSchemaRefusesIsAToolErrorThatSaysWhy() {
        McpSchema.CallToolResult result = runner.call(tool("vsim_read_circuit"), Map.of("detail", "FULL"));

        assertThat(result.isError()).isTrue();
        assertThat(error(result)).containsEntry("error", "INVALID_PARAMETER");
        assertThat((String) error(result).get("message")).contains("document");
        assertThat(result.content()).hasSize(1).first().isInstanceOf(McpSchema.TextContent.class);
    }

    @Test
    void anUnknownCircuitIsAnInvalidReference() {
        assertThat(refusal("vsim_read_problems", Map.of("document", "circuit-99")))
                .containsEntry("error", "INVALID_REFERENCE");
    }

    @Test
    void everyExampleRunsAndAnswersEitherAResultOfItsSchemaOrAToolError() {
        host.open("circuit-2");
        for (Tool tool : tools) {
            if (tool.name().equals("vsim_await_proposal")) {
                continue; // waits for the user
            }
            for (Map<String, Object> example : tool.examples()) {
                McpSchema.CallToolResult result = runner.call(tool, example);
                if (Boolean.TRUE.equals(result.isError())) {
                    assertThat(error(result)).as("%s %s", tool.name(), example).containsKeys("error", "message",
                            "recovery");
                } else {
                    assertMatchesOutputSchema(tool, result);
                }
            }
        }
    }

    // --- reading ---

    @Test
    void theCatalogueListsTheLibrarysComponentsWithTheirOrigin() {
        host.library = List.of(new DefinitionBundle(ExampleDefinitions.byteBuffer(), List.of()));

        Map<String, Object> catalogue = call("vsim_describe_components", Map.of("document", "circuit-1",
                "filter", "Búfer"));

        assertThat(list(catalogue, "types")).singleElement().satisfies(type -> assertThat(type)
                .containsEntry("origin", "LIBRARY")
                .containsEntry("typeId", SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER))
                .containsEntry("definitionName", "Búfer de 8 bits"));
    }

    @Test
    void anEightBitRegisterIsDescribedWithItsEightBitPins() {
        Map<String, Object> register = call("vsim_describe_component", Map.of("document", "circuit-1",
                "typeId", "register", "parameters", Map.of("width", 8)));

        assertThat(list(register, "pins")).anySatisfy(pin -> assertThat(pin).containsEntry("width", 8));
    }

    @Test
    void aPictureComesAsAPngImageWithItsDescription() {
        McpSchema.CallToolResult result = runner.call(tool("vsim_picture"), Map.of("document", "circuit-1",
                "region", Map.of("x", 0, "y", 0, "width", 20, "height", 10)));

        assertMatchesOutputSchema(tool("vsim_picture"), result);
        McpSchema.ImageContent image = (McpSchema.ImageContent) result.content().getFirst();
        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(Base64.getDecoder().decode(image.data())).isEqualTo(ToolTestHost.PNG);
        assertThat(structured(result)).doesNotContainKey("png");
        assertThat(runner.call(tool("vsim_window_picture"), Map.of("document", "circuit-1")).content())
                .hasAtLeastOneElementOfType(McpSchema.ImageContent.class);
    }

    // --- tasks and edits ---

    @Test
    void anEditPlacesAndConnectsPartsAndMapsTheAliases() {
        String task = beginTask();

        Map<String, Object> receipt = call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "PlacePart", "alias", "s", "typeId", "switch", "x", 0, "y", 0, "label", "A"),
                Map.of("op", "PlacePart", "alias", "lamp", "typeId", "led", "x", 12, "y", 0, "label", "Y"),
                Map.of("op", "Connect", "from", Map.of("target", "new:s", "pinName", "out"),
                        "to", Map.of("target", "new:lamp", "index", 0)))));

        assertThat(receipt).containsEntry("status", "APPLIED").containsEntry("version", 1L);
        assertThat(map(receipt, "aliases")).containsOnlyKeys("s", "lamp");
        assertThat(list(receipt, "connections")).singleElement()
                .satisfies(connection -> assertThat(connection).containsKey("wireIds"));
    }

    /**
     * Fewer tokens (2026-10-22): a receipt leaves out what the AI never needs (defaults, empty lists, the undo key,
     * the two pins a plain Connect joined) and keeps what it goes on with: version, status, aliases and wire ids.
     */
    @Test
    void anEditReceiptCarriesOnlyWhatTheAiGoesOnWith() {
        String task = beginTask();

        Map<String, Object> receipt = call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "PlacePart", "alias", "s", "typeId", "switch", "x", 0, "y", 0, "label", "A"),
                Map.of("op", "PlacePart", "alias", "lamp", "typeId", "led", "x", 12, "y", 0, "label", "Y"),
                Map.of("op", "Connect", "from", Map.of("target", "new:s", "pinName", "out"),
                        "to", Map.of("target", "new:lamp", "index", 0)))));

        assertThat(receipt).containsEntry("status", "APPLIED").containsEntry("version", 1L)
                .containsKeys("document", "aliases", "connections", "changes")
                .doesNotContainKeys("applied", "undoDescription", "warnings", "components");
        assertThat(map(receipt, "changes")).containsKey("added").doesNotContainKeys("changed", "removed");
        Map<String, Object> connection = list(receipt, "connections").getFirst();
        assertThat(connection).containsKeys("operation", "from", "to", "wireIds")
                .doesNotContainKeys("joinedNets", "alreadyConnected");
        assertThat(map(connection, "from")).containsOnlyKeys("elementId", "pinName");
    }

    /** Pulido T5: AddNote and SetLabel take a note's colours; the reading shows them only for a styled note. */
    @Test
    void aNoteGetsItsColoursAndTheReadingShowsOnlyThoseOfAStyledNote() {
        String task = beginTask();
        Map<String, Object> receipt = call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "AddNote", "alias", "plain", "text", "simple", "x", 0, "y", 0),
                Map.of("op", "AddNote", "alias", "warning", "text", "Ojo", "x", 0, "y", 4,
                        "style", Map.of("background", "YELLOW", "border", "RED")))));

        call("vsim_edit", edit(task, 1, List.of(Map.of("op", "SetLabel",
                "target", map(receipt, "aliases").get("warning"), "style", Map.of("text", "BLUE")))));

        List<Map<String, Object>> notes = list(call("vsim_read_circuit", Map.of("document", "circuit-1")),
                "notes");
        assertThat(notes).hasSize(2);
        assertThat(notes.get(0)).doesNotContainKey("style");
        assertThat(map(notes.get(1), "style")).containsExactlyInAnyOrderEntriesOf(
                Map.of("background", "YELLOW", "text", "BLUE", "border", "RED"));
    }

    @Test
    void aStickyNoteIsAddedTickedEditedAndReadBack() {
        String task = beginTask();
        Map<String, Object> receipt = call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "AddStickyNote", "alias", "memo", "x", 2, "y", 3, "color", "PINK",
                        "text", "Falta: probar con Cin = 1"),
                Map.of("op", "AddStickyNote", "alias", "todo", "x", 20, "y", 3, "width", 10, "height", 7,
                        "items", List.of("uno", "dos"), "folded", true))));
        Map<String, Object> aliases = map(receipt, "aliases");

        call("vsim_edit", edit(task, 1, List.of(
                Map.of("op", "SetStickyItem", "target", aliases.get("todo"), "index", 1, "done", true),
                Map.of("op", "SetLabel", "target", aliases.get("memo"), "text", "Ya probado"))));

        List<Map<String, Object>> stickyNotes = list(call("vsim_read_circuit", Map.of("document", "circuit-1")),
                "stickyNotes");
        assertThat(stickyNotes).hasSize(2);
        assertThat(stickyNotes.get(0)).containsEntry("kind", "TEXT").containsEntry("color", "PINK")
                .containsEntry("text", "Ya probado").containsEntry("folded", false)
                .doesNotContainKey("items");
        assertThat(stickyNotes.get(1)).containsEntry("kind", "CHECKLIST").containsEntry("width", 10)
                .containsEntry("folded", true).doesNotContainKey("text");
        assertThat(list(stickyNotes.get(1), "items")).containsExactly(Map.of("text", "uno", "done", false),
                Map.of("text", "dos", "done", true));
    }

    @Test
    void aStickyNoteWithBothTextAndItemsIsRefused() {
        assertThat(refusal("vsim_edit", edit(beginTask(), 0, List.of(Map.of("op", "AddStickyNote", "x", 0, "y", 0,
                "text", "a", "items", List.of("b")))))).containsEntry("error", "INVALID_PARAMETER");
    }

    @Test
    void aStickyNoteColourVsimDoesNotKnowIsRefusedByTheSchema() {
        assertThat(refusal("vsim_edit", edit(beginTask(), 0, List.of(Map.of("op", "AddStickyNote", "x", 0, "y", 0,
                "text", "a", "color", "RED"))))).containsEntry("error", "INVALID_PARAMETER");
    }

    @Test
    void anExportLeavesStickyNotesOutUnlessTheFlagAsksForThem() {
        call("vsim_export", Map.of("document", "circuit-1", "format", "PNG"));
        call("vsim_export", Map.of("document", "circuit-1", "format", "SVG", "includeStickyNotes", true));

        assertThat(host.exports).extracting(ExportRequest::includeStickyNotes).containsExactly(false, true);
    }

    @Test
    void aNoteColourVsimDoesNotKnowIsRefusedByTheSchema() {
        assertThat(refusal("vsim_edit", edit(beginTask(), 0, List.of(Map.of("op", "AddNote", "text", "x", "x", 0,
                "y", 0, "style", Map.of("background", "PINK")))))).containsEntry("error", "INVALID_PARAMETER");
    }

    @Test
    void aReceiptStillSaysWhenAConnectJoinedBiggerNetsOrNothingNew() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "PlacePart", "alias", "s", "typeId", "switch", "x", 0, "y", 0, "label", "A"),
                Map.of("op", "PlacePart", "alias", "one", "typeId", "led", "x", 12, "y", 0, "label", "Y"),
                Map.of("op", "PlacePart", "alias", "two", "typeId", "led", "x", 12, "y", 8, "label", "Z"),
                Map.of("op", "Connect", "from", Map.of("target", "new:s", "pinName", "out"),
                        "to", Map.of("target", "new:one", "index", 0)))));

        Map<String, Object> receipt = call("vsim_edit", edit(task, 1, List.of(
                Map.of("op", "Connect", "from", Map.of("target", "c1", "pinName", "out"),
                        "to", Map.of("target", "c3", "index", 0)),
                Map.of("op", "Connect", "from", Map.of("target", "c1", "pinName", "out"),
                        "to", Map.of("target", "c2", "index", 0)))));

        List<Map<String, Object>> connections = list(receipt, "connections");
        assertThat(connections.get(0)).containsKey("joinedNets");
        assertThat(connections.get(1)).containsEntry("alreadyConnected", true);
    }

    @Test
    void anEditPreparedAgainstAnOldVersionIsStaleWithTheCurrentVersion() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(place("one", 0, 0))));

        Map<String, Object> refusal = refusal("vsim_edit", edit(task, 0, List.of(place("two", 0, 20))));

        assertThat(refusal).containsEntry("error", "STALE_DOCUMENT");
        assertThat(map(refusal, "recovery")).containsEntry("currentVersion", 1L);
    }

    @Test
    void anAiGivesAnInstanceItsOwnShapeAndReadsItBack() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "PlacePart", "alias", "in", "typeId", "input-pin", "x", 0, "y", 1, "label", "A"),
                Map.of("op", "PlacePart", "alias", "gate", "typeId", "buffer", "x", 8, "y", 0,
                        "parameters", Map.of("negate", true)),
                Map.of("op", "PlacePart", "alias", "out", "typeId", "output-pin", "x", 18, "y", 1, "label", "Y"),
                Map.of("op", "Connect", "from", Map.of("target", "new:in", "pinName", "pin"),
                        "to", Map.of("target", "new:gate", "pinName", "in")),
                Map.of("op", "Connect", "from", Map.of("target", "new:gate", "pinName", "out"),
                        "to", Map.of("target", "new:out", "pinName", "pin")),
                Map.of("op", "CreateComponent", "alias", "inverter", "name", "Inversor", "replaceSelection", true,
                        "targets", List.of("new:in", "new:gate", "new:out")),
                Map.of("op", "PlaceInstance", "alias", "piece", "definition", "new:inverter", "x", 40, "y", 0))));
        Map<String, Object> circuit = call("vsim_read_circuit", Map.of("document", "circuit-1"));
        Map<String, Object> instance = list(circuit, "parts").stream()
                .filter(part -> part.containsKey("ownShape")).findFirst().orElseThrow();
        assertThat(instance).containsEntry("ownShape", false);

        call("vsim_edit", edit(task, 1, List.of(Map.of("op", "SetComponentShape", "instance",
                (String) instance.get("id"), "template", "TRIANGLE"))));

        Map<String, Object> after = call("vsim_read_circuit", Map.of("document", "circuit-1"));
        assertThat(list(after, "parts")).filteredOn(part -> part.containsKey("ownShape"))
                .extracting(part -> part.get("ownShape")).contains(true);
    }

    @Test
    void memoryWordsAreWrittenAndReadBackAndTheReadingOfTheCircuitStaysSmall() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(Map.of("op", "PlacePart", "alias", "memory", "typeId", "ram",
                "x", 0, "y", 0, "parameters", Map.of("words", "16777216", "width", 64)))));
        String memory = (String) list(call("vsim_read_circuit", Map.of("document", "circuit-1")), "parts")
                .getFirst().get("id");

        call("vsim_edit", edit(task, 1, List.of(Map.of("op", "SetMemoryContents", "component", memory,
                "firstAddress", 4, "words", List.of("DEADBEEF", "0x1")))));

        Map<String, Object> read = call("vsim_read_memory", Map.of("document", "circuit-1", "component", memory,
                "firstAddress", 3, "count", 4));
        assertThat((List<Object>) read.get("words")).isEqualTo(List.of("0000000000000000", "00000000DEADBEEF",
                "0000000000000001", "0000000000000000"));
        assertThat(read).containsEntry("totalWords", 16777216L).containsEntry("width", 64)
                .containsEntry("linked", false).containsEntry("nonZeroWords", 2L);
        assertThat(call("vsim_read_circuit", Map.of("document", "circuit-1")).toString().length()).isLessThan(2000);
        assertThat(refusal("vsim_read_memory", Map.of("document", "circuit-1", "component", memory,
                "source", "current"))).containsEntry("error", "NOT_ALLOWED");
    }

    @Test
    void aMemoryWordWiderThanTheWidthIsRefusedAndNothingIsChanged() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(Map.of("op", "PlacePart", "alias", "memory", "typeId", "rom",
                "x", 0, "y", 0, "parameters", Map.of("words", "16", "width", 4)))));
        String memory = (String) list(call("vsim_read_circuit", Map.of("document", "circuit-1")), "parts")
                .getFirst().get("id");

        assertThat(refusal("vsim_edit", edit(task, 1, List.of(Map.of("op", "SetMemoryContents", "component", memory,
                "firstAddress", 0, "words", List.of("1", "10")))))).containsEntry("error", "INVALID_PARAMETER");
        assertThat((List<Object>) call("vsim_read_memory", Map.of("document", "circuit-1", "component", memory)).get("words"))
                .containsOnly("0");
    }

    @Test
    void aShapeTemplateThatIsNotOneOfTheFormasIsRefusedAsAnInvalidParameter() {
        assertThat(refusal("vsim_edit", edit(beginTask(), 0, List.of(Map.of("op", "SetComponentShape",
                "definition", "Inversor", "template", "HEXAGON"))))).containsEntry("error", "INVALID_PARAMETER");
    }

    @Test
    void anEditNeedsItsTaskAndAnOperationOfAKnownKind() {
        Map<String, Object> withoutTask = new LinkedHashMap<>(edit("task-1", 0, List.of(place("one", 0, 0))));
        withoutTask.remove("taskId");
        assertThat(refusal("vsim_edit", withoutTask)).containsEntry("error", "INVALID_PARAMETER");

        assertThat(refusal("vsim_edit", edit(beginTask(), 0, List.of(Map.of("op", "Teleport", "target", "c1")))))
                .containsEntry("error", "INVALID_PARAMETER");
    }

    @Test
    void aComponentIsMadeWithoutReplacingAndItsInstancePlacedInTheSameCall() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "PlacePart", "alias", "a", "typeId", "input-pin", "x", 0, "y", 1, "label", "A"),
                Map.of("op", "PlacePart", "alias", "not", "typeId", "buffer", "x", 8, "y", 0,
                        "parameters", Map.of("negate", true)),
                Map.of("op", "PlacePart", "alias", "y", "typeId", "output-pin", "x", 18, "y", 1, "label", "Y"),
                Map.of("op", "Connect", "from", Map.of("target", "new:a", "pinName", "pin"),
                        "to", Map.of("target", "new:not", "pinName", "in")),
                Map.of("op", "Connect", "from", Map.of("target", "new:not", "pinName", "out"),
                        "to", Map.of("target", "new:y", "pinName", "pin")))));

        Map<String, Object> receipt = call("vsim_edit", edit(task, 1, List.of(
                Map.of("op", "CreateComponent", "alias", "inverter", "name", "Inversor",
                        "targets", List.of("c1", "c2", "c3"), "replaceSelection", false),
                Map.of("op", "PlaceInstance", "alias", "copy", "definition", "new:inverter", "x", 40, "y", 0))));

        assertThat(list(receipt, "components")).singleElement()
                .satisfies(component -> assertThat(component).containsEntry("name", "Inversor"));
        assertThat(map(receipt, "aliases")).containsKeys("inverter", "copy");
    }

    @Test
    void undoingTheTaskTakesItsEditsBack() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(place("one", 0, 0))));
        call("vsim_edit", edit(task, 1, List.of(place("two", 0, 20))));

        assertThat(call("vsim_undo_task", Map.of("taskId", task)))
                .containsEntry("undone", 2).containsEntry("stoppedAtUserEdit", false);
        assertThat(call("vsim_end_task", Map.of("taskId", task))).containsEntry("taskId", task);
    }

    @Test
    void guidesAddedOnlyToAlignGoAwayAtTheEndUnlessTheTaskKeepsThem() {
        assertThat(tool("vsim_edit").description()).contains("layoutOnly");
        assertThat(tool("vsim_end_task").description()).contains("keepLayoutGuides");
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "AddGuide", "orientation", "VERTICAL", "position", 12, "layoutOnly", true),
                Map.of("op", "AddGuide", "orientation", "HORIZONTAL", "position", 4))));

        Map<String, Object> ended = call("vsim_end_task", Map.of("taskId", task));

        assertThat(ended).containsEntry("taskId", task).containsEntry("layoutGuidesRemoved", 1);
        assertThat(list(call("vsim_read_circuit", Map.of("document", "circuit-1", "detail", "FULL")), "guides"))
                .singleElement().satisfies(guide -> assertThat(guide).containsEntry("orientation", "HORIZONTAL"));

        String kept = beginTask();
        call("vsim_edit", edit(kept, 2, List.of(
                Map.of("op", "AddGuide", "orientation", "VERTICAL", "position", 20, "layoutOnly", true))));
        assertThat(call("vsim_end_task", Map.of("taskId", kept, "keepLayoutGuides", true)))
                .containsEntry("layoutGuidesRemoved", 0);
        assertThat(list(call("vsim_read_circuit", Map.of("document", "circuit-1", "detail", "FULL")), "guides"))
                .hasSize(2);
    }

    // --- simulation ---

    @Test
    void aStepWhoseBudgetRanOutIsBudgetExceededWithItsReceipt() {
        host.simulation = request -> host.receipt(request.document(), SimulationReceipt.Outcome.BUDGET_EXCEEDED);

        Map<String, Object> refusal = refusal("vsim_step", Map.of("document", "circuit-1", "kind", "DURATION",
                "amount", 1000, "eventBudget", 100));

        assertThat(refusal).containsEntry("error", "BUDGET_EXCEEDED");
        assertThat(map(map(refusal, "recovery"), "receipt")).containsEntry("time", "40")
                .containsEntry("outcome", "BUDGET_EXCEEDED");
        assertThat(host.simulationRequests).singleElement().isEqualTo(new SimulationRequest.Step(
                new cl.almejo.vsim.automation.DocumentId("circuit-1"),
                cl.almejo.vsim.automation.simulate.StepKind.DURATION, 1000, 100));
    }

    @Test
    void inputsAreSetInOrderWithTheirVersionAndOperationId() {
        call("vsim_set_inputs", Map.of("document", "circuit-1", "version", 0, "operationId", "inputs-1",
                "values", List.of(Map.of("elementId", "c1", "value", "1"),
                        Map.of("elementId", "c2", "value", "0b0"))));

        assertThat(host.simulationRequests).singleElement().isInstanceOfSatisfying(SimulationRequest.SetInputs.class,
                request -> assertThat(request.values()).extracting(value -> value.elementId() + "=" + value.value())
                        .containsExactly("c1=1", "c2=0b0"));
    }

    @Test
    void theTruthTableOfAnAndGateHasItsFourRows() {
        buildAndGate();

        Map<String, Object> table = call("vsim_truth_table", Map.of("document", "circuit-1"));

        assertThat(list(table, "rows")).hasSize(4);
        assertThat(table).containsEntry("totalRows", 4).containsEntry("truncated", false);
    }

    // --- stage checks ---

    @Test
    void aStageWhoseTableMatchesPassesAndOneThatDoesNotSaysWhatVsimMeasured() {
        buildAndGate();

        Map<String, Object> passed = call("vsim_check_stage", Map.of("document", "circuit-1", "checks", List.of(
                Map.of("kind", "truthTable", "expected", andRows("1")), Map.of("kind", "layout"))));
        Map<String, Object> failed = call("vsim_check_stage", Map.of("document", "circuit-1", "checks", List.of(
                Map.of("kind", "truthTable", "expected", andRows("0")))));

        assertThat(passed).containsEntry("passed", true);
        assertThat(failed).containsEntry("passed", false);
        assertThat(list(failed, "checks")).singleElement().satisfies(check -> assertThat(check)
                .containsEntry("status", "FAILED"));
    }

    /** IA.3 Speed: the answer says whether «De corrido» stops at this stage's end, so the AI fixes it first. */
    @Test
    void aStageCheckSaysWhetherDeCorridoStopsAtItsEnd() {
        buildAndGate();
        String task = beginTask();

        Map<String, Object> failed = call("vsim_check_stage", Map.of("document", "circuit-1", "taskId", task,
                "checks", List.of(Map.of("kind", "truthTable", "expected", andRows("0")))));
        Map<String, Object> passed = call("vsim_check_stage", Map.of("document", "circuit-1", "taskId", task,
                "checks", List.of(Map.of("kind", "truthTable", "expected", andRows("1")))));

        assertThat(failed).containsEntry("continuousStops", true);
        assertThat((String) failed.get("continuousNote")).contains("«De corrido»", "«Siguiente»");
        assertThat(passed).containsEntry("continuousStops", false);
        assertThat((String) passed.get("continuousNote")).contains("«De corrido»");
        Map<String, Object> schema = tool("vsim_check_stage").outputSchema();
        assertThat(schema.toString()).contains("continuousStops", "continuousNote");
    }

    @Test
    void aCheckVsimCouldNotFinishIsNeverPassed() {
        String task = beginTask();
        call("vsim_edit", edit(task, 0, List.of(
                Map.of("op", "PlacePart", "alias", "flip", "typeId", "flip-flop-data", "x", 10, "y", 0))));

        Map<String, Object> result = call("vsim_check_stage", Map.of("document", "circuit-1", "checks", List.of(
                Map.of("kind", "truthTable", "expected", andRows("1")))));

        assertThat(result).containsEntry("passed", false);
        assertThat(list(result, "checks")).singleElement().satisfies(check -> assertThat(check)
                .containsEntry("status", "NOT_FINISHED"));
    }

    // --- exports and settings ---

    @Test
    void anExportCarriesTheMenusOptions() {
        Map<String, Object> receipt = call("vsim_export", Map.of("document", "circuit-1", "format", "PDF",
                "scope", Map.of("kind", "frame", "frameId", "f1"), "fileName", "latch", "orientation", "LANDSCAPE",
                "scalePercent", 150, "headerFooter", true));

        assertThat(receipt).containsEntry("fileName", "latch.pdf");
        ExportRequest request = host.exports.getFirst();
        assertThat(request.scope()).isEqualTo(new ExportRequest.Scope.Frame("f1"));
        assertThat(request.pdf()).isEqualTo(new ExportRequest.PdfOptions("A4", "LANDSCAPE", 150, true, false, true,
                ""));
        assertThat(refusal("vsim_export", Map.of("document", "circuit-1", "format", "PDF", "scalePercent", 5)))
                .containsEntry("error", "INVALID_PARAMETER");
    }

    @Test
    void settingsGoToTheWindowAndPreferencesOnlyThroughAProposal() {
        assertThat(refusal("vsim_set_editor_setting", Map.of("document", "circuit-1", "setting", "GRID_STYLE",
                "value", "LINES"))).containsEntry("error", "NOT_ALLOWED");
        beginTask();
        call("vsim_set_editor_setting", Map.of("document", "circuit-1", "setting", "GRID_STYLE", "value", "LINES"));
        assertThat(refusal("vsim_set_editor_setting", Map.of("document", "circuit-1", "setting", "GRID_SPACING",
                "value", "7"))).containsEntry("error", "INVALID_PARAMETER");

        assertThat(call("vsim_propose_preference", Map.of("preference", "THEME", "value", "DARK",
                "reason", "¿Paso al tema oscuro?"))).containsEntry("proposalId", "proposal-1");
        assertThat(call("vsim_await_proposal", Map.of("proposalId", "proposal-1", "waitSeconds", 0)))
                .containsEntry("answer", "WAITING");
        host.answer.complete(ProposalAnswer.DECLINED);
        assertThat(call("vsim_await_proposal", Map.of("proposalId", "proposal-1", "waitSeconds", 1)))
                .containsEntry("answer", "DECLINED");
        assertThat(host.settings).containsExactly("circuit-1 GRID_STYLE=LINES", "propose THEME=DARK");
    }

    @Test
    void aNewCircuitIsOpenedAndListed() {
        Map<String, Object> created = call("vsim_new_circuit", Map.of("name", "Sumador"));

        assertThat(list(call("vsim_list_documents", Map.of()), "documents"))
                .extracting(document -> document.get("id")).contains(created.get("document"));
    }

    // --- helpers ---

    private String beginTask() {
        return (String) call("vsim_begin_task", Map.of("document", "circuit-1", "title", "Prueba",
                "stages", List.of(Map.of("title", "Etapa")))).get("taskId");
    }

    private Map<String, Object> edit(String task, long version, List<Map<String, Object>> operations) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("document", "circuit-1");
        arguments.put("version", version);
        arguments.put("operationId", "operation-" + nextOperation++);
        arguments.put("taskId", task);
        arguments.put("stage", "Etapa");
        arguments.put("operations", operations);
        return arguments;
    }

    private static Map<String, Object> place(String alias, int x, int y) {
        return Map.of("op", "PlacePart", "alias", alias, "typeId", "and", "x", x, "y", y);
    }

    /** Switches A and B into an AND gate into a LED Y. */
    private void buildAndGate() {
        call("vsim_edit", edit(beginTask(), 0, List.of(
                Map.of("op", "PlacePart", "alias", "a", "typeId", "switch", "x", 0, "y", 0, "label", "A"),
                Map.of("op", "PlacePart", "alias", "b", "typeId", "switch", "x", 0, "y", 8, "label", "B"),
                Map.of("op", "PlacePart", "alias", "and", "typeId", "and", "x", 12, "y", 2),
                Map.of("op", "PlacePart", "alias", "y", "typeId", "led", "x", 24, "y", 3, "label", "Y"),
                Map.of("op", "Connect", "from", Map.of("target", "new:a", "pinName", "out"),
                        "to", Map.of("target", "new:and", "pinName", "in0")),
                Map.of("op", "Connect", "from", Map.of("target", "new:b", "pinName", "out"),
                        "to", Map.of("target", "new:and", "pinName", "in1")),
                Map.of("op", "Connect", "from", Map.of("target", "new:and", "pinName", "out"),
                        "to", Map.of("target", "new:y", "pinName", "in")))));
    }

    /** The AND rows, the last one's Y as given. */
    private static List<Map<String, Object>> andRows(String lastY) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("inputs", Map.of("A", "0", "B", "0"), "outputs", Map.of("Y", "0")));
        rows.add(Map.of("inputs", Map.of("A", "0", "B", "1"), "outputs", Map.of("Y", "0")));
        rows.add(Map.of("inputs", Map.of("A", "1", "B", "0"), "outputs", Map.of("Y", "0")));
        rows.add(Map.of("inputs", Map.of("A", "1", "B", "1"), "outputs", Map.of("Y", lastY)));
        return rows;
    }

    private Tool tool(String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
    }

    private Map<String, Object> call(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = runner.call(tool(name), arguments);
        assertThat(result.isError()).as("%s answered %s", name, result.content()).isFalse();
        assertMatchesOutputSchema(tool(name), result);
        return structured(result);
    }

    private Map<String, Object> refusal(String name, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = runner.call(tool(name), arguments);
        assertThat(result.isError()).as("%s answered %s", name, result.content()).isTrue();
        return error(result);
    }

    private static void assertMatchesOutputSchema(Tool tool, McpSchema.CallToolResult result) {
        JsonSchemaValidator.ValidationResponse validation = VALIDATOR.validate(tool.outputSchema(),
                result.structuredContent());
        assertThat(validation.valid()).as("%s output: %s", tool.name(), validation.errorMessage()).isTrue();
    }

    private static Map<String, Object> error(McpSchema.CallToolResult result) {
        return structured(result);
    }
}
