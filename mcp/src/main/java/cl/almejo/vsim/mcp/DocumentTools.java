package cl.almejo.vsim.mcp;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentSummary;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The tools about the open circuits themselves: for now {@code vsim_list_documents}. */
final class DocumentTools {

    static final String LIST_DOCUMENTS = "vsim_list_documents";

    private static final Map<String, Object> NO_INPUT = Map.of("type", "object", "properties", Map.of(),
            "additionalProperties", false);
    private static final Map<String, Object> DOCUMENT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "id", Map.of("type", "string"),
                    "name", Map.of("type", "string"),
                    "version", Map.of("type", "integer"),
                    "modified", Map.of("type", "boolean")),
            "required", List.of("id", "name", "version", "modified"));
    private static final Map<String, Object> LIST_OUTPUT = Map.of(
            "type", "object",
            "properties", Map.of("documents", Map.of("type", "array", "items", DOCUMENT_SCHEMA)),
            "required", List.of("documents"));

    private final AutomationApi api;
    private final ToolResults results;

    DocumentTools(AutomationApi api, ToolResults results) {
        this.api = api;
        this.results = results;
    }

    List<McpServerFeatures.SyncToolSpecification> tools() {
        return List.of(listDocuments());
    }

    private McpServerFeatures.SyncToolSpecification listDocuments() {
        McpSchema.Tool tool = McpSchema.Tool.builder(LIST_DOCUMENTS, NO_INPUT)
                .title("List the open circuits")
                .description("Lists the circuits open in VSim right now, one per circuit however many windows show "
                        + "it: its id (name it in every other call), the name its window shows, its version (send it "
                        + "with an edit) and whether it has unsaved changes.")
                .outputSchema(LIST_OUTPUT)
                .annotations(new McpSchema.ToolAnnotations(null, true, false, true, false, null))
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> results.run(
                        () -> Map.of("documents", api.listDocuments().stream().map(DocumentTools::document).toList())))
                .build();
    }

    private static Map<String, Object> document(DocumentSummary summary) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", summary.id().value());
        document.put("name", summary.name());
        document.put("version", summary.version().value());
        document.put("modified", summary.modified());
        return document;
    }
}
