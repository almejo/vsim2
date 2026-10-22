package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;

import java.util.List;
import java.util.Map;

/** The open circuits themselves: listing them and opening a new one. */
final class DocumentTools {

    static final String LIST_DOCUMENTS = "vsim_list_documents";
    static final String NEW_CIRCUIT = "vsim_new_circuit";

    private DocumentTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(listDocuments(api), newCircuit(api));
    }

    private static Tool listDocuments(AutomationApi api) {
        Map<String, Object> document = Schema.result("One open circuit")
                .property("id", Schema.string("Name it in every other call"))
                .property("name", Schema.string("The name its window shows"))
                .property("version", Schema.integer("Send it with an edit or set_inputs"))
                .property("modified", Schema.bool("Whether it has unsaved changes"))
                .required("id", "name", "version", "modified")
                .build();
        return new Tool(LIST_DOCUMENTS, "List the open circuits",
                "Lists the circuits open in VSim right now, one per circuit however many windows show it: its id "
                        + "(name it in every other call), the name its window shows, its version (send it with an "
                        + "edit) and whether it has unsaved changes. Start here.",
                Schema.object("").build(),
                Schema.result("The open circuits")
                        .property("documents", Schema.array("In the order they were opened", document))
                        .required("documents").build(),
                Tool.Hints.READS, List.of(Map.of()),
                arguments -> Tool.Output.of(Map.of("documents",
                        Json.toJson(api.listDocuments().stream()
                                .map(summary -> Map.of("id", summary.id(), "name", summary.name(),
                                        "version", summary.version(), "modified", summary.modified()))
                                .toList()))));
    }

    private static Tool newCircuit(AutomationApi api) {
        return new Tool(NEW_CIRCUIT, "Open a new circuit",
                "Opens a new, empty, unsaved circuit in its own VSim window, as Archivo ▸ Nuevo, and returns its id. "
                        + "VSim never saves it: the user does. For examples like a stage of a bigger build, one "
                        + "circuit is usually better; open a new one only for something that stands on its own. It "
                        + "takes the user's «Presentar» choice: read vsim_get_presentation before its task.",
                Schema.object("")
                        .property("name", Schema.nonEmptyString("What its window shows until the user saves it"))
                        .required("name").build(),
                Schema.result("The new circuit")
                        .property("document", Schema.string("Its id"))
                        .property("name", Schema.string("Its name"))
                        .required("document", "name").build(),
                Tool.Hints.CHANGES, List.of(Map.of("name", "Sumador de 4 bits")),
                arguments -> {
                    String name = arguments.string("name");
                    DocumentId created = api.newCircuit(name);
                    return Tool.Output.of(Map.of("document", created.value(), "name", name));
                });
    }
}
