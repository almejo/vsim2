package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.read.ReadFilter;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.read.WindowPicture;

import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reading a circuit: its contents, its problems and pictures of it. */
final class CircuitTools {

    static final String READ_CIRCUIT = "vsim_read_circuit";
    static final String READ_PROBLEMS = "vsim_read_problems";
    static final String PICTURE = "vsim_picture";
    static final String WINDOW_PICTURE = "vsim_window_picture";

    private CircuitTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(readCircuit(api), readProblems(api), picture(api), windowPicture(api));
    }

    private static Tool readCircuit(AutomationApi api) {
        return new Tool(READ_CIRCUIT, "Read a circuit",
                "One page of a circuit, all at one version, positions in grid units (x right, y down). SUMMARY (the "
                        + "default) lists parts, frames and notes and counts wires and nets; FULL adds every part's "
                        + "pins (name, direction, width, position, net, connected), the wires and the nets as lists "
                        + "of pins. Narrow it by kinds, element ids or a region; a page holds at most 200 elements, "
                        + "read the next with nextCursor (only good for the same version). Net ids are only good for "
                        + "the version read: refer to pins by part id and pin name." + Common.DATA_NOTE,
                Schema.object("")
                        .property("document", Common.document())
                        .property("detail", Schema.enumeration("SUMMARY (default) or FULL",
                                List.of("SUMMARY", "FULL")))
                        .property("kinds", Schema.array("Only these kinds of element",
                                Schema.enumeration("", List.of("PART", "WIRE", "FRAME", "NOTE"))))
                        .property("elementIds", Schema.array("Only these elements", Schema.string("")))
                        .property("region", Schema.bounds("Only elements whose box touches this region"))
                        .property("pageSize", Schema.integer("Elements per page (at most 200)", 1,
                                ReadFilter.MAXIMUM_PAGE_SIZE))
                        .property("cursor", Schema.string("nextCursor of the previous page"))
                        .required("document").build(),
                Common.versionedResult("One page of the circuit")
                        .property("name", Schema.string("The circuit's name"))
                        .property("summary", Schema.result("The whole circuit in numbers: parts, wires, frames, "
                                + "notes, guides, nets, bounds, parts by type, user components").build())
                        .property("parts", Schema.array("Placed parts: id, typeId, label, position, rotation, "
                                + "parameters, bounds and (FULL) pins", Schema.anyObject()))
                        .property("wires", Schema.array("Wires: id, start, end, net", Schema.anyObject()))
                        .property("frames", Schema.array("Frames", Schema.anyObject()))
                        .property("notes", Schema.array("Free texts", Schema.anyObject()))
                        .property("guides", Schema.array("Alignment guides (first page only)",
                                Schema.anyObject()))
                        .property("nets", Schema.array("Nets as lists of pins and wires", Schema.anyObject()))
                        .property("matchingElements", Schema.integer("Elements the filter keeps, over all pages"))
                        .property("nextCursor", Schema.string("Read the next page with it; absent on the last"))
                        .required("summary", "parts", "matchingElements").build(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1"),
                        Map.of("document", "circuit-1", "detail", "FULL", "elementIds", List.of("c3", "c4"))),
                arguments -> Tool.Output.of(api.readCircuit(arguments.document(), filter(arguments),
                        arguments.optionalString("cursor"))));
    }

    private static ReadFilter filter(Arguments arguments) {
        ReadFilter.Detail detail = arguments.enumeration("detail", ReadFilter.Detail.class,
                ReadFilter.Detail.SUMMARY);
        Set<ReadFilter.ElementKind> kinds = EnumSet.noneOf(ReadFilter.ElementKind.class);
        for (String kind : arguments.strings("kinds")) {
            kinds.add(ReadFilter.ElementKind.valueOf(kind));
        }
        return new ReadFilter(detail, kinds, arguments.stringSet("elementIds"), arguments.optionalBounds("region"),
                arguments.integer("pageSize", ReadFilter.MAXIMUM_PAGE_SIZE));
    }

    private static Tool readProblems(AutomationApi api) {
        return new Tool(READ_PROBLEMS, "Read the circuit's problems",
                "The Problems panel of a circuit: short circuits, width mismatches, tunnel problems…, each with the "
                        + "parts and pins it is about. upToDate is false while the simulation has not caught up with "
                        + "the last edit: read again in a moment. Warnings may be on purpose while building.",
                Schema.object("").property("document", Common.document()).required("document").build(),
                Common.versionedResult("The problems")
                        .property("simulationTime", Schema.string("When the simulation found them"))
                        .property("upToDate", Schema.bool("Whether the simulation has caught up with the circuit"))
                        .property("problems", Schema.array("kind, warning, message, elementIds, pins, insidePaths",
                                Schema.anyObject()))
                        .required("upToDate", "problems").build(),
                Tool.Hints.READS, List.of(Map.of("document", "circuit-1")),
                arguments -> Tool.Output.of(api.readProblems(arguments.document())));
    }

    private static Tool picture(AutomationApi api) {
        return new Tool(PICTURE, "Look at a region of the circuit",
                "A PNG of a region of the circuit (grid units; none: the whole circuit) to check your own layout "
                        + "before saying it is done: as the PNG export paints it, or asOnScreen as the user sees the "
                        + "active pane (theme, grid, guides, selection). At most 1600 × 1000 pixels. Never shown to "
                        + "the user, never saved.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("region", Schema.bounds("The region to paint"))
                        .property("asOnScreen", Schema.bool("Paint the active pane as the user sees it"))
                        .required("document").build(),
                Common.versionedResult("What the picture shows (the image comes as image content)")
                        .property("region", Schema.result("The region painted, in grid units").build())
                        .property("asOnScreen", Schema.bool("As on screen or as the export"))
                        .property("width", Schema.integer("Pixels"))
                        .property("height", Schema.integer("Pixels"))
                        .required("region", "width", "height").build(),
                Tool.Hints.READS,
                List.of(Map.of("document", "circuit-1"), Map.of("document", "circuit-1", "region",
                        Map.of("x", 0, "y", 0, "width", 40, "height", 20), "asOnScreen", true)),
                arguments -> {
                    RegionPicture picture = api.pictureOf(arguments.document(), arguments.optionalBounds("region"),
                            arguments.bool("asOnScreen", false));
                    return new Tool.Output(Json.object(picture),
                            Optional.of(Base64.getEncoder().encodeToString(picture.png())));
                });
    }

    private static Tool windowPicture(AutomationApi api) {
        return new Tool(WINDOW_PICTURE, "Look at the VSim window",
                "A PNG of the whole VSim window that shows the circuit — menus, toolbar, palette, panes, inspector, "
                        + "status bar and its open dialogs — painted from VSim's own window (never a capture of the "
                        + "screen: no other program is ever in it), at most 1920 × 1200 pixels, so you see what the "
                        + "user sees. Never shown to the user, never saved.",
                Schema.object("").property("document", Common.document()).required("document").build(),
                Common.versionedResult("What the picture shows (the image comes as image content)")
                        .property("windowTitle", Schema.string("The window's title"))
                        .property("dialogs", Schema.array("Titles of the open dialogs painted on it",
                                Schema.string("")))
                        .property("width", Schema.integer("Pixels"))
                        .property("height", Schema.integer("Pixels"))
                        .required("width", "height").build(),
                Tool.Hints.READS, List.of(Map.of("document", "circuit-1")),
                arguments -> {
                    WindowPicture picture = api.pictureOfWindow(arguments.document());
                    Map<String, Object> structured = new LinkedHashMap<>(Json.object(picture));
                    return new Tool.Output(structured,
                            Optional.of(Base64.getEncoder().encodeToString(picture.png())));
                });
    }
}
