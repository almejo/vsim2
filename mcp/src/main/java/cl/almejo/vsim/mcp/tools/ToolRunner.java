package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a {@link Tool} into the SDK's tool specification. Every call is first checked against the tool's input
 * schema here (the SDK's own check is turned off so that a bad input is answered like any other refusal); then the
 * tool runs. A refusal ({@link AutomationException}) or a bad input becomes an error <em>result</em> — never a
 * protocol error — with {@code {error, message, recovery}} (spec §10), so the AI can read what to do next.
 */
final class ToolRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRunner.class);

    private final McpJsonMapper jsonMapper;
    private final JsonSchemaValidator validator;

    ToolRunner(McpJsonMapper jsonMapper, JsonSchemaValidator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    McpServerFeatures.SyncToolSpecification specification(Tool tool) {
        McpSchema.Tool described = McpSchema.Tool.builder(tool.name(), tool.inputSchema())
                .title(tool.title())
                .description(descriptionWithExamples(tool))
                .outputSchema(tool.outputSchema())
                .annotations(new McpSchema.ToolAnnotations(tool.title(), tool.hints().readOnly(),
                        tool.hints().destructive(), tool.hints().idempotent(), false, null))
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(described)
                .callHandler((exchange, request) -> call(tool, request.arguments()))
                .build();
    }

    /** Runs {@code tool} on {@code arguments} as a call from a client would. */
    McpSchema.CallToolResult call(Tool tool, Map<String, Object> arguments) {
        Map<String, Object> given = arguments == null ? Map.of() : arguments;
        JsonSchemaValidator.ValidationResponse validation = validator.validate(tool.inputSchema(), given);
        if (!validation.valid()) {
            return error(new AutomationException(AutomationError.INVALID_PARAMETER, "The arguments do not match the "
                    + "input schema of " + tool.name() + ": " + validation.errorMessage(),
                    Map.of("tool", tool.name())));
        }
        try {
            Tool.Output output = tool.handler().call(new Arguments(given));
            return result(output);
        } catch (AutomationException refusal) {
            return error(refusal);
        } catch (IllegalArgumentException invalid) {
            return error(new AutomationException(AutomationError.INVALID_PARAMETER,
                    invalid.getMessage() == null ? "An argument is not valid" : invalid.getMessage()));
        } catch (RuntimeException failure) {
            // The class only: the message could quote circuit contents.
            LOGGER.warn("The tool {} failed with {}", tool.name(), failure.getClass().getName());
            return error(new AutomationException(AutomationError.OUTCOME_UNKNOWN, "VSim failed while doing this ("
                    + failure.getClass().getSimpleName() + "); read the circuit before trying again"));
        }
    }

    private McpSchema.CallToolResult result(Tool.Output output) {
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .structuredContent(output.structured())
                .isError(false);
        output.pngBase64().ifPresent(png -> builder.addContent(McpSchema.ImageContent.builder(png, "image/png")
                .build()));
        return builder.addTextContent(json(output.structured())).build();
    }

    private McpSchema.CallToolResult error(AutomationException refusal) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", refusal.error().name());
        error.put("message", refusal.getMessage());
        error.put("recovery", Json.toJson(refusal.recovery()));
        return McpSchema.CallToolResult.builder()
                .structuredContent(error)
                .addTextContent(json(error))
                .isError(true)
                .build();
    }

    private String descriptionWithExamples(Tool tool) {
        if (tool.examples().isEmpty()) {
            return tool.description();
        }
        StringBuilder description = new StringBuilder(tool.description());
        for (Map<String, Object> example : tool.examples()) {
            description.append("\n\nExample arguments: ").append(json(example));
        }
        return description.toString();
    }

    private String json(Object content) {
        try {
            return jsonMapper.writeValueAsString(content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
