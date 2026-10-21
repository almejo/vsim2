package cl.almejo.vsim.mcp;

import cl.almejo.vsim.automation.AutomationException;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Turns what the automation API returns into tool results: the structured content, plus the same JSON as text for
 * clients that read only text; a refusal becomes an error result with its code, message and recovery (spec §10).
 */
final class ToolResults {

    private final McpJsonMapper jsonMapper;

    ToolResults(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Runs {@code work}; its map is the result, an {@link AutomationException} the error result. */
    McpSchema.CallToolResult run(Supplier<Map<String, Object>> work) {
        try {
            return result(work.get(), false);
        } catch (AutomationException exception) {
            return result(error(exception), true);
        }
    }

    private McpSchema.CallToolResult result(Map<String, Object> content, boolean isError) {
        return McpSchema.CallToolResult.builder()
                .structuredContent(content)
                .addTextContent(json(content))
                .isError(isError)
                .build();
    }

    private static Map<String, Object> error(AutomationException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", exception.error().name());
        error.put("message", exception.getMessage());
        error.put("recovery", exception.recovery());
        return error;
    }

    private String json(Map<String, Object> content) {
        try {
            return jsonMapper.writeValueAsString(content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
