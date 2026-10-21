package cl.almejo.vsim.mcp.tools;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One MCP tool of VSim: its id (English, {@code vsim_…}), title and description for the AI, input and output schemas,
 * hints for the client, example arguments (shown in the description and checked against the input schema by the
 * tests) and what it does.
 */
record Tool(String name, String title, String description, Map<String, Object> inputSchema,
            Map<String, Object> outputSchema, Hints hints, List<Map<String, Object>> examples, Handler handler) {

    Tool {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputSchema, "inputSchema");
        Objects.requireNonNull(outputSchema, "outputSchema");
        Objects.requireNonNull(hints, "hints");
        examples = List.copyOf(examples);
        Objects.requireNonNull(handler, "handler");
    }

    /** What the client may assume: reading only, possibly removing things, safe to repeat. */
    record Hints(boolean readOnly, boolean destructive, boolean idempotent) {
        static final Hints READS = new Hints(true, false, true);
        static final Hints CHANGES = new Hints(false, false, false);
        static final Hints CHANGES_IDEMPOTENT = new Hints(false, false, true);
        static final Hints MAY_REMOVE = new Hints(false, true, false);
        static final Hints MAY_REMOVE_IDEMPOTENT = new Hints(false, true, true);
    }

    /** Carries out a call with arguments already checked against the input schema. */
    @FunctionalInterface
    interface Handler {
        Output call(Arguments arguments);
    }

    /** A result: the structured content (also sent as JSON text) and, for pictures, a PNG image. */
    record Output(Map<String, Object> structured, Optional<String> pngBase64) {
        Output {
            Objects.requireNonNull(structured, "structured");
            Objects.requireNonNull(pngBase64, "pngBase64");
        }

        static Output of(Object result) {
            return new Output(Json.object(result), Optional.empty());
        }

        static Output of(Map<String, Object> structured) {
            return new Output(structured, Optional.empty());
        }
    }
}
