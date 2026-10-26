package cl.almejo.vsim.mcp;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/** Reads of a tool's structured answer, for the tests that call the MCP tools (here and in the editor's). */
public final class McpResults {

    private McpResults() {
    }

    /** The structured content of {@code result}, as the JSON object it is. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> structured(McpSchema.CallToolResult result) {
        return (Map<String, Object>) result.structuredContent();
    }

    /** The JSON object under {@code key} of {@code object}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> object, String key) {
        return (Map<String, Object>) object.get(key);
    }

    /** The JSON array of objects under {@code key} of {@code object}. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(Map<String, Object> object, String key) {
        return (List<Map<String, Object>>) object.get(key);
    }
}
