package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.mcp.McpServerSettings;
import cl.almejo.vsim.mcp.RunningServer;
import cl.almejo.vsim.mcp.VsimMcpServer;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.StateMachine;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** One end-to-end pass over a real MCP connection: a circuit with a state machine is placed, read and refused. */
class MachineOverMcpTest {

    private static final String SECRET = "a-test-secret-of-enough-length";

    private final ToolTestHost host = new ToolTestHost();
    private RunningServer server;
    private McpSyncClient client;

    @BeforeEach
    void startAServerOverAHostWithAMachineInTheLibrary() throws Exception {
        host.open("circuit-1");
        ComponentDefinition machine = MachineDefinitions.edited(MachineDefinitions.create("semaforo", "Semáforo"),
                StateMachine.initial().withPinNames(List.of("go"), List.of("stop")));
        host.library = List.of(new DefinitionBundle(machine, List.of()));
        server = VsimMcpServer.start(new AutomationApi(host), new McpServerSettings(0, SECRET));
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + SECRET))
                .build()).requestTimeout(Duration.ofSeconds(10)).build();
        client.initialize();
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop();
    }

    @Test
    void aCircuitWithAMachineIsPlacedReadAndItsInsideIsRefusedOverMcp() {
        String task = (String) structured(call("vsim_begin_task", Map.of("document", "circuit-1",
                "title", "Prueba", "stages", List.of(Map.of("title", "Etapa"))))).get("taskId");
        McpSchema.CallToolResult placed = call("vsim_edit", Map.of("document", "circuit-1", "version", 0,
                "operationId", "operation-1", "taskId", task, "stage", "Etapa", "operations", List.of(
                        Map.of("op", "PlaceInstance", "alias", "m", "definition", "Semáforo", "x", 10, "y", 10))));
        assertThat(placed.isError()).isNotEqualTo(Boolean.TRUE);

        McpSchema.CallToolResult read = call("vsim_read_circuit", Map.of("document", "circuit-1", "detail", "FULL"));

        assertThat(read.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(read.structuredContent().toString()).contains("machine=", "name=Semáforo", "kind=NONE",
                "inputs=[go]", "outputs=[stop]", "states=[S0]");
        assertThat(call("vsim_read_problems", Map.of("document", "circuit-1")).isError())
                .isNotEqualTo(Boolean.TRUE);

        long version = ((Number) structured(read).get("version")).longValue();
        McpSchema.CallToolResult refused = call("vsim_edit", Map.of("document", "circuit-1", "version", version,
                "operationId", "operation-2", "taskId", task, "stage", "Etapa", "operations", List.of(
                        Map.of("op", "SetComponentShape", "definition", "Semáforo", "template", "TRIANGLE"))));
        assertThat(refused.isError()).isTrue();
        assertThat(refused.structuredContent().toString()).contains("NOT_ALLOWED", "cannot edit state machines yet");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(McpSchema.CallToolResult result) {
        return (Map<String, Object>) result.structuredContent();
    }

    private McpSchema.CallToolResult call(String name, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(name).arguments(arguments).build());
    }
}
