package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the tools cost the AI (the user's «fewer tokens», 2026-10-22). The part of each tool a model reads (name,
 * description with its example, input schema) and a typical {@code vsim_edit} answer stay within a budget: the sizes
 * measured after the IA.3 fix round plus about 10 %, so a change that bloats them shows here. ≈ tokens = characters /
 * 4. Before the fix round: 49,084 characters for the 31 tools, 14,782 for {@code vsim_edit}, 1,157 for the answer.
 */
class ToolBudgetTest {

    /**
     * 40,961 characters after the fix round; 45,535 with the 32nd tool (vsim_read_memory) and SetMemoryContents, so the
     * budget is 45,600; 45,671 with the gates (the NAND example places «and» with negate, the catalogue hint on
     * «inputs»), so 45,700; 45,764 with set_inputs saying a switch keeps its value, so 45,800 (still enough when it
     * says instead that the values are the simulation's only); about 45,850 with vsim_step's NEXT_CLOCK_CYCLE and
     * clockEdge, so 45,900.
     */
    private static final int ALL_TOOLS_BUDGET = 45_900;
    /** 10,371 characters after the fix round; 12,244 with SetComponentShape (the shape editor, about 1,000); 13,474 with the sticky notes (AddStickyNote, SetStickyItem and their rule, about 1,240); 13,425 with those descriptions shortened; the budget is 13,470; 13,959 with SetMemoryContents (about 530), so the budget is 14,000; 14,037 with the NAND example as «and» + negate, so 14,050. */
    private static final int EDIT_BUDGET = 14_050;
    /** The answer to four parts and three Connects: 590 characters after the fix round. */
    private static final int TYPICAL_EDIT_ANSWER_BUDGET = 650;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolTestHost host = new ToolTestHost();
    private final AutomationApi api = new AutomationApi(host);
    private final List<McpSchema.Tool> tools = VsimTools.specifications(api, new JacksonMcpJsonMapper(objectMapper))
            .stream().map(McpServerFeatures.SyncToolSpecification::tool).toList();

    @Test
    void theToolDefinitionsStayWithinTheirBudget() {
        int total = 0;
        for (McpSchema.Tool tool : tools) {
            total += modelVisible(tool);
        }

        assertThat(total).as("the 32 tools as a model reads them").isLessThanOrEqualTo(ALL_TOOLS_BUDGET);
        assertThat(modelVisible(tool("vsim_edit"))).as("vsim_edit").isLessThanOrEqualTo(EDIT_BUDGET);
    }

    @Test
    void aTypicalEditAnswerStaysWithinItsBudget() {
        host.open("circuit-1");
        ToolRunner runner = new ToolRunner(new JacksonMcpJsonMapper(objectMapper),
                McpJsonDefaults.getSchemaValidator());
        List<Tool> definitions = VsimTools.tools(api);
        Tool beginTask = definitions.stream().filter(tool -> tool.name().equals("vsim_begin_task")).findFirst()
                .orElseThrow();
        Tool edit = definitions.stream().filter(tool -> tool.name().equals("vsim_edit")).findFirst().orElseThrow();
        Map<?, ?> task = (Map<?, ?>) runner.call(beginTask, Map.of("document", "circuit-1", "title", "AND",
                "stages", List.of(Map.of("title", "Compuerta")))).structuredContent();
        String taskId = (String) task.get("taskId");

        McpSchema.CallToolResult answer = runner.call(edit, Map.of("document", "circuit-1", "version", 0,
                "operationId", "and-1", "taskId", taskId, "operations", List.of(
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

        assertThat(answer.isError()).as("%s", answer.content()).isFalse();
        String text = ((McpSchema.TextContent) answer.content().getFirst()).text();
        System.out.println("Typical vsim_edit answer: " + text.length() + " characters: " + text);
        assertThat(text.length()).isLessThanOrEqualTo(TYPICAL_EDIT_ANSWER_BUDGET);
    }

    private McpSchema.Tool tool(String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
    }

    private int modelVisible(McpSchema.Tool tool) {
        return tool.name().length() + tool.description().length() + json(tool.inputSchema()).length();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
