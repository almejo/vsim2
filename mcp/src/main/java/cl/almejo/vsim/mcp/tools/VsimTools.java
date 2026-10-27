package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.ArrayList;
import java.util.List;

/** Every tool VSim offers the AI (spec §10), in the order a client lists them. */
public final class VsimTools {

    private VsimTools() {
    }

    /** The SDK's specifications of every tool over {@code api}. */
    public static List<McpServerFeatures.SyncToolSpecification> specifications(AutomationApi api,
                                                                              McpJsonMapper jsonMapper) {
        ToolRunner runner = new ToolRunner(jsonMapper, McpJsonDefaults.getSchemaValidator());
        return tools(api).stream().map(runner::specification).toList();
    }

    static List<Tool> tools(AutomationApi api) {
        List<Tool> tools = new ArrayList<>();
        tools.addAll(DocumentTools.tools(api));
        tools.addAll(CatalogTools.tools(api));
        tools.addAll(CircuitTools.tools(api));
        tools.addAll(MemoryTools.tools(api));
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
