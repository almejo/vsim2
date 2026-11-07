package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Every tool VSim offers the AI (spec §10), in the order a client lists them. */
public final class VsimTools {

    /**
     * The longest a tool call may take before it is answered OUTCOME_UNKNOWN (fix round D): well over any call's own
     * waits (the editor 10 s, the simulation 30 s, an export 60 s, a few of them in a row), so it only ends a call
     * that would never finish, which would otherwise hold one of the server's request slots for ever.
     */
    public static final Duration CALL_TIME_LIMIT = Duration.ofMinutes(3);

    private VsimTools() {
    }

    /** The SDK's specifications of every tool over {@code api}. */
    public static List<McpServerFeatures.SyncToolSpecification> specifications(AutomationApi api,
                                                                              McpJsonMapper jsonMapper) {
        return specifications(api, jsonMapper, CALL_TIME_LIMIT);
    }

    /** Like {@link #specifications(AutomationApi, McpJsonMapper)}, each call answered within {@code callTimeLimit}. */
    public static List<McpServerFeatures.SyncToolSpecification> specifications(AutomationApi api,
                                                                              McpJsonMapper jsonMapper,
                                                                              Duration callTimeLimit) {
        ToolRunner runner = new ToolRunner(jsonMapper, McpJsonDefaults.getSchemaValidator(), callTimeLimit);
        return tools(api).stream().map(runner::specification).toList();
    }

    static List<Tool> tools(AutomationApi api) {
        List<Tool> tools = new ArrayList<>();
        tools.addAll(DocumentTools.tools(api));
        tools.addAll(PlaceTools.tools(api));
        tools.addAll(CatalogTools.tools(api));
        tools.addAll(CircuitTools.tools(api));
        tools.addAll(MemoryTools.tools(api));
        tools.addAll(TerminalTools.tools(api));
        tools.addAll(TaskTools.tools(api));
        tools.addAll(EditTools.tools(api));
        tools.addAll(PresentationTools.tools(api));
        tools.addAll(AskTools.tools(api));
        tools.addAll(SimulationTools.tools(api));
        tools.addAll(CheckTools.tools(api));
        tools.addAll(ExportTools.tools(api));
        tools.addAll(SettingsTools.tools(api));
        return List.copyOf(tools);
    }
}
