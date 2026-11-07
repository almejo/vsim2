package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PlaceRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The places of a circuit (spec §12): the main circuit and the inside of each component, with where it is used. */
final class PlaceTools {

    static final String LIST_PLACES = "vsim_list_places";
    static final String OPEN_PLACE = "vsim_open_place";

    private PlaceTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(listPlaces(api), openPlace(api));
    }

    private static Tool listPlaces(AutomationApi api) {
        Map<String, Object> use = Schema.result("A use")
                .property("path", Schema.string("The place's context"))
                .property("references", Schema.array("Pieces it goes through", Schema.string("")))
                .required("path").build();
        Map<String, Object> place = Schema.result("The main circuit or a component")
                .property("definition", Schema.string("main, or the component's id"))
                .property("name", Schema.string("Its name"))
                .property("uses", Schema.array("Where it is used", use))
                .required("definition", "name", "uses").build();
        return new Tool(LIST_PLACES, "List the places of a circuit",
                "The main circuit and each component with its uses. Give a place to vsim_read_circuit, vsim_edit, "
                        + "vsim_read_signals, vsim_set_inputs, vsim_read_memory or vsim_picture to work inside a "
                        + "component; none: the main circuit.",
                Schema.object("").property("document", Common.document()).required("document").build(),
                Schema.result("The places").property("places", Schema.array("Main first", place))
                        .required("places").build(),
                Tool.Hints.READS, List.of(Map.of("document", "circuit-1")),
                arguments -> Tool.Output.of(Map.of("places", Json.toJson(api.listPlaces(arguments.document())))));
    }

    private static Tool openPlace(AutomationApi api) {
        return new Tool(OPEN_PLACE, "Show a place to the user",
                "Brings the place's tab to the front in the window you work in (the user's «Entrar»); the only call "
                        + "that changes what the user sees. Definition main: the circuit's own tab.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("place", Common.place())
                        .required("document", "place").build(),
                Schema.result("The place shown")
                        .property("document", Schema.string("The circuit"))
                        .property("definition", Schema.string("The place"))
                        .property("context", Schema.string("The piece it shows"))
                        .required("document", "definition").build(),
                Tool.Hints.CHANGES_IDEMPOTENT,
                List.of(Map.of("document", "circuit-1", "place", Map.of("definition", "alu", "context", "c7"))),
                arguments -> {
                    DocumentId document = arguments.document().base();
                    PlaceRef place = arguments.place();
                    api.openPlace(document, place);
                    Map<String, Object> shown = new LinkedHashMap<>();
                    shown.put("document", document.value());
                    shown.put("definition", place.definition());
                    place.context().ifPresent(context -> shown.put("context", context));
                    return Tool.Output.of(shown);
                });
    }
}
