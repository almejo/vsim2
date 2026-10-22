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
import java.util.Set;

/**
 * Turns a {@link Tool} into the SDK's tool specification. Every call is first checked against the tool's input
 * schema here (the SDK's own check is turned off so that a bad input is answered like any other refusal); then the
 * tool runs. A refusal ({@link AutomationException}) or a bad input becomes an error <em>result</em> — never a
 * protocol error — with {@code {error, message, recovery}} (spec §10), so the AI can read what to do next.
 */
final class ToolRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRunner.class);
    /** How long each tool call took in VSim, by name only (never arguments): off unless {@code -Dvsim.ai.timing=INFO}. */
    private static final Logger TIMING = LoggerFactory.getLogger("cl.almejo.vsim.mcp.timing");

    /** The tools whose description shows its first example (fewer tokens: the others are plain enough). */
    static final Set<String> EXAMPLE_SHOWN_FOR = Set.of("vsim_edit", "vsim_begin_task",
            "vsim_check_stage", "vsim_export");

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
        long start = System.nanoTime();
        McpSchema.CallToolResult result = callTimed(tool, arguments);
        if (TIMING.isInfoEnabled()) {
            TIMING.info("{} {} ms{}", tool.name(), (System.nanoTime() - start) / 1_000_000,
                    Boolean.TRUE.equals(result.isError()) ? " (error)" : "");
        }
        return result;
    }

    private McpSchema.CallToolResult callTimed(Tool tool, Map<String, Object> arguments) {
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

    /**
     * The description, with the first example for the tools of {@link #EXAMPLE_SHOWN_FOR}; the other examples stay
     * as test data (every one is checked against the schema and run by the tests).
     */
    private String descriptionWithExamples(Tool tool) {
        if (tool.examples().isEmpty() || !EXAMPLE_SHOWN_FOR.contains(tool.name())) {
            return tool.description();
        }
        return tool.description() + "\n\nExample arguments: " + json(tool.examples().getFirst());
    }

    private String json(Object content) {
        try {
            return jsonMapper.writeValueAsString(content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
