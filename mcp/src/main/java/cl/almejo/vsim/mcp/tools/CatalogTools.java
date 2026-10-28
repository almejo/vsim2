package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;

import java.util.List;
import java.util.Map;

/** The component catalogue: which types can be placed, their parameters and the pins they get. */
final class CatalogTools {

    static final String DESCRIBE_COMPONENTS = "vsim_describe_components";
    static final String DESCRIBE_COMPONENT = "vsim_describe_component";

    private CatalogTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(describeComponents(api), describeComponent(api));
    }

    private static Map<String, Object> typeSchema() {
        return Schema.result("One component type")
                .property("typeId", Schema.string("What PlacePart takes"))
                .property("category", Schema.string("Palette group"))
                .property("spanishName", Schema.string("Its name in the Spanish palette"))
                .property("englishName", Schema.string("Its name in the English palette"))
                .property("userComponent", Schema.bool("A user component (place it with PlaceInstance)"))
                .property("definitionName", Schema.string("For a user component, the name PlaceInstance takes"))
                .property("origin", Schema.enumeration("BUILT_IN, CIRCUIT (already in this circuit) or LIBRARY "
                        + "(the user's «Mis componentes»; placing it brings it into the circuit)",
                        List.of("BUILT_IN", "CIRCUIT", "LIBRARY")))
                .property("parameters", Schema.array("Its parameters: name, kind, default, minimum/maximum or "
                        + "choices", Schema.anyObject()))
                .property("parameterValues", Schema.result("The values these pins are for").build())
                .property("width", Schema.integer("Body width in grid units, unrotated"))
                .property("height", Schema.integer("Body height in grid units, unrotated"))
                .property("pins", Schema.array("Its pins for these parameters: index (place among the part's pins), "
                        + "name, direction, width in bits (0: takes the width of what it touches) and position "
                        + "relative to the part's origin, unrotated", Schema.anyObject()))
                .required("typeId", "category", "userComponent", "origin", "pins")
                .build();
    }

    private static Tool describeComponents(AutomationApi api) {
        return new Tool(DESCRIBE_COMPONENTS, "Describe the component catalogue",
                "The types that can be placed in a circuit: VSim's own, then the circuit's user components, then "
                        + "the user's library («Mis componentes»), each with its parameters and the pins of a part "
                        + "with default parameters. Filter by part of an id or a name (Spanish or English) or by a "
                        + "category (gates, input output, interface…). Ask vsim_describe_component for the pins with "
                        + "other parameters (a register's pins follow its width, a gate's its «inputs»; NAND/NOR/"
                        + "XNOR = and/or/xor + negate, NOT = buffer + negate)." + Common.DATA_NOTE,
                Schema.object("")
                        .property("document", Common.document())
                        .property("filter", Schema.string("Part of an id or a name, or a category"))
                        .required("document").build(),
                Common.versionedResult("The catalogue")
                        .property("types", Schema.array("The types", typeSchema()))
                        .required("types").build(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1"), Map.of("document", "circuit-1", "filter", "flip")),
                arguments -> Tool.Output.of(api.describeComponents(arguments.document(),
                        arguments.optionalString("filter").filter(filter -> !filter.isBlank()))));
    }

    private static Tool describeComponent(AutomationApi api) {
        return new Tool(DESCRIBE_COMPONENT, "Describe one component type",
                "One type with the pins and size a part placed with these parameters would have (missing ones take "
                        + "their defaults). Use it before connecting: pins are named by these names.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("typeId", Schema.nonEmptyString("From vsim_describe_components"))
                        .property("parameters", Schema.mapOf("Parameter values by name",
                                Schema.anyValue("A number or a text, as the parameter's kind says")))
                        .required("document", "typeId").build(),
                typeSchema(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1", "typeId", "register", "parameters", Map.of("width", 8))),
                arguments -> Tool.Output.of(api.describeComponent(arguments.document(), arguments.string("typeId"),
                        arguments.map("parameters"))));
    }
}
