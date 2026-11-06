package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.PlaceRef;
import cl.almejo.vsim.automation.PlaceSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The places of a circuit for the AI (spec §12): two tools and an optional {@code place} on six more. */
class PlaceToolsTest {

    private static final JsonSchemaValidator VALIDATOR = McpJsonDefaults.getSchemaValidator();
    private static final List<String> TOOLS_WITH_A_PLACE = List.of("vsim_read_circuit", "vsim_edit",
            "vsim_read_signals", "vsim_set_inputs", "vsim_read_memory", "vsim_picture");

    private final ToolTestHost host = new ToolTestHost();
    private final AutomationApi api = new AutomationApi(host);
    private final ToolRunner runner = new ToolRunner(new JacksonMcpJsonMapper(new ObjectMapper()), VALIDATOR);
    private final List<Tool> tools = VsimTools.tools(api);

    @BeforeEach
    void openACircuit() {
        host.open("circuit-1");
    }

    @Test
    void listPlacesSchema() {
        Tool listPlaces = tool("vsim_list_places");

        assertThat(listPlaces.inputSchema()).extractingByKey("properties").asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP).containsOnlyKeys("document");
        assertThat(VALIDATOR.validate(listPlaces.inputSchema(), Map.of("document", "circuit-1")).valid()).isTrue();
        assertThat(listPlaces.hints()).isEqualTo(Tool.Hints.READS);
    }

    @Test
    void openPlaceSchema() {
        Tool openPlace = tool("vsim_open_place");

        assertThat(VALIDATOR.validate(openPlace.inputSchema(), Map.of("document", "circuit-1",
                "place", Map.of("definition", "bloque", "context", "c5"))).valid()).isTrue();
        assertThat(VALIDATOR.validate(openPlace.inputSchema(), Map.of("document", "circuit-1")).valid())
                .as("the place is required").isFalse();
        assertThat(VALIDATOR.validate(openPlace.inputSchema(), Map.of("document", "circuit-1",
                "place", Map.of("context", "c5"))).valid()).as("a place names a definition").isFalse();
    }

    @Test
    void placeIsOptionalOnTheSixTools() {
        for (String name : TOOLS_WITH_A_PLACE) {
            Tool tool = tool(name);
            Map<String, Object> withoutPlace = tool.examples().getFirst();
            Map<String, Object> withPlace = new java.util.LinkedHashMap<>(withoutPlace);
            withPlace.put("place", Map.of("definition", "bloque", "context", "c5"));

            assertThat(VALIDATOR.validate(tool.inputSchema(), withoutPlace).valid()).as("%s without", name).isTrue();
            assertThat(VALIDATOR.validate(tool.inputSchema(), withPlace).valid()).as("%s with", name).isTrue();
            assertThat(properties(tool)).as("%s offers place", name).containsKey("place");
        }
        assertThat(tools).filteredOn(tool -> !TOOLS_WITH_A_PLACE.contains(tool.name())
                && !tool.name().equals("vsim_open_place"))
                .allSatisfy(tool -> assertThat(properties(tool)).as(tool.name()).doesNotContainKey("place"));
    }

    @Test
    void listPlacesAnswersWhatTheHostGives() {
        host.places = List.of(new PlaceSummary("main", "Circuito", List.of()),
                new PlaceSummary("bloque", "Bloque", List.of(
                        new PlaceSummary.PlaceUse("c5", List.of("c5")),
                        new PlaceSummary.PlaceUse("c9/c2", List.of("c9", "c2")))));

        McpSchema.CallToolResult result = runner.call(tool("vsim_list_places"), Map.of("document", "circuit-1"));

        assertThat(result.isError()).isFalse();
        assertThat(VALIDATOR.validate(tool("vsim_list_places").outputSchema(), result.structuredContent()).valid())
                .isTrue();
        assertThat(result.structuredContent().toString()).contains("bloque", "c9/c2");
    }

    @Test
    void openPlaceAsksTheHostToShowItAndReadCircuitAddressesThePlace() {
        McpSchema.CallToolResult opened = runner.call(tool("vsim_open_place"), Map.of("document", "circuit-1",
                "place", Map.of("definition", "bloque", "context", "c5")));
        McpSchema.CallToolResult read = runner.call(tool("vsim_read_circuit"), Map.of("document", "circuit-1",
                "place", Map.of("definition", "bloque", "context", "c5")));

        assertThat(opened.isError()).isFalse();
        assertThat(host.openedPlaces).containsExactly("circuit-1 bloque@c5");
        assertThat(read.isError()).isFalse();
        assertThat(read.structuredContent().toString()).contains("document=circuit-1@bloque@c5");
        assertThat(PlaceRef.main().isMain()).isTrue();
    }

    private Tool tool(String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Tool tool) {
        return (Map<String, Object>) tool.inputSchema().get("properties");
    }
}
