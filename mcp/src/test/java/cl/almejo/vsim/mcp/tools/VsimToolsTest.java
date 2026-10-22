package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The tools as a client sees them: ids, schemas, examples, hints. */
class VsimToolsTest {

    private static final JsonSchemaValidator VALIDATOR = McpJsonDefaults.getSchemaValidator();

    private final AutomationApi api = new AutomationApi(new ToolTestHost());

    @Test
    void everyToolOfTheSpecIsOfferedInOrder() {
        assertThat(VsimTools.tools(api)).extracting(Tool::name).containsExactly(
                "vsim_list_documents", "vsim_new_circuit",
                "vsim_describe_components", "vsim_describe_component",
                "vsim_read_circuit", "vsim_read_problems", "vsim_picture", "vsim_window_picture",
                "vsim_begin_task", "vsim_update_plan", "vsim_end_task", "vsim_undo_task",
                "vsim_edit",
                "vsim_get_presentation", "vsim_set_presentation", "vsim_begin_stage", "vsim_await_step",
                "vsim_say", "vsim_ask_user", "vsim_await_answer", "vsim_withdraw_question",
                "vsim_simulation", "vsim_set_inputs", "vsim_step", "vsim_read_signals", "vsim_truth_table",
                "vsim_check_stage",
                "vsim_export",
                "vsim_set_editor_setting", "vsim_propose_preference", "vsim_await_proposal");
    }

    @Test
    void everySchemaIsAValidDraft202012Schema() {
        for (Tool tool : VsimTools.tools(api)) {
            assertThat(VALIDATOR.validateSchema(tool.inputSchema()).valid()).as("input of %s", tool.name()).isTrue();
            assertThat(VALIDATOR.validateSchema(tool.outputSchema()).valid()).as("output of %s", tool.name())
                    .isTrue();
        }
    }

    @Test
    void everyToolHasExamplesThatItsInputSchemaAccepts() {
        for (Tool tool : VsimTools.tools(api)) {
            assertThat(tool.examples()).as("examples of %s", tool.name()).isNotEmpty();
            for (Map<String, Object> example : tool.examples()) {
                JsonSchemaValidator.ValidationResponse validation = VALIDATOR.validate(tool.inputSchema(), example);
                assertThat(validation.valid()).as("%s accepts %s: %s", tool.name(), example,
                        validation.errorMessage()).isTrue();
            }
        }
    }

    @Test
    void theInputSchemasRefuseMisspeltArguments() {
        for (Tool tool : VsimTools.tools(api)) {
            Map<String, Object> misspelt = new java.util.LinkedHashMap<>(tool.examples().getFirst());
            misspelt.put("documnet", "circuit-1");
            assertThat(VALIDATOR.validate(tool.inputSchema(), misspelt).valid()).as(tool.name()).isFalse();
        }
    }

    @Test
    void theClientSeesTheExamplesTheHintsAndTheOutputSchemaOfEachTool() {
        List<McpServerFeatures.SyncToolSpecification> specifications = VsimTools.specifications(api,
                new JacksonMcpJsonMapper(new ObjectMapper()));

        McpSchema.Tool edit = specifications.stream().map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(tool -> tool.name().equals("vsim_edit")).findFirst().orElseThrow();
        assertThat(edit.description()).contains("Example arguments: {").contains("\"op\":\"PlacePart\"");
        assertThat(edit.outputSchema()).containsKey("properties");
        assertThat(edit.annotations().readOnlyHint()).isFalse();
        assertThat(edit.annotations().destructiveHint()).isTrue();
        McpSchema.Tool read = specifications.stream().map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(tool -> tool.name().equals("vsim_read_circuit")).findFirst().orElseThrow();
        assertThat(read.annotations().readOnlyHint()).isTrue();
        assertThat(read.annotations().openWorldHint()).isFalse();
    }

    /** Fewer tokens (2026-10-22): one example, and only on the tools whose arguments it explains. */
    @Test
    void aDescriptionShowsAtMostOneExampleAndOnlyWhereItExplainsTheArguments() {
        for (McpServerFeatures.SyncToolSpecification specification : VsimTools.specifications(api,
                new JacksonMcpJsonMapper(new ObjectMapper()))) {
            McpSchema.Tool tool = specification.tool();
            int shown = tool.description().split("Example arguments:", -1).length - 1;
            assertThat(shown).as(tool.name()).isEqualTo(ToolRunner.EXAMPLE_SHOWN_FOR.contains(tool.name()) ? 1 : 0);
        }
        assertThat(ToolRunner.EXAMPLE_SHOWN_FOR).containsExactlyInAnyOrder("vsim_edit", "vsim_begin_task",
                "vsim_check_stage", "vsim_export");
    }

    @Test
    void noIntegerIsBoundedByTheLargestIntegerAndTheEditTargetsAreExplainedOnce() {
        for (Tool tool : VsimTools.tools(api)) {
            assertThat(tool.inputSchema().toString()).as(tool.name())
                    .doesNotContain("maximum=" + Integer.MAX_VALUE, "maximum=" + Long.MAX_VALUE);
        }
        String edit = VsimTools.tools(api).stream().filter(tool -> tool.name().equals("vsim_edit")).findFirst()
                .orElseThrow().inputSchema().toString();
        assertThat(edit.split("earlier operation of this call", -1)).hasSize(2);
        assertThat(edit.split("the receipt maps it to the real id", -1)).hasSize(2);
    }

    @Test
    void theToolIdsAndSchemasAreEnglishAndNeverMentionAPixel() {
        for (Tool tool : VsimTools.tools(api)) {
            assertThat(tool.name()).matches("vsim_[a-z_]+");
            assertThat(tool.inputSchema().toString()).doesNotContainIgnoringCase("pixel");
        }
    }

    /** IA.3: a note may hold several lines; the edit tool says how to write them. */
    @Test
    void addNoteAndSetLabelSayHowToWriteANoteOfSeveralLines() {
        Tool edit = VsimTools.tools(api).stream().filter(tool -> tool.name().equals("vsim_edit")).findFirst()
                .orElseThrow();
        String schema = edit.inputSchema().toString();
        assertThat(schema.split(java.util.regex.Pattern.quote("use \\n for a new line"), -1)).hasSize(3);
    }
}
