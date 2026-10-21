package cl.almejo.vsim.mcp.tools;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.automation.export.ExportRequest.ImageOptions;
import cl.almejo.vsim.automation.export.ExportRequest.PdfOptions;

import java.util.List;
import java.util.Map;

/** {@code vsim_export}: the menus' exports, only into the remembered export folder, never overwriting (spec §7). */
final class ExportTools {

    static final String EXPORT = "vsim_export";

    private ExportTools() {
    }

    static List<Tool> tools(AutomationApi api) {
        return List.of(export(api));
    }

    private static Map<String, Object> scope() {
        return Schema.oneOf("What goes in the file (default: the whole circuit)", List.of(
                Schema.object("The whole circuit").property("kind", Schema.constant("whole")).required("kind")
                        .build(),
                Schema.object("One frame's rectangle").property("kind", Schema.constant("frame"))
                        .property("frameId", Schema.nonEmptyString("The frame's id (f1)"))
                        .required("kind", "frameId").build(),
                Schema.object("Some elements, as if the user selected them")
                        .property("kind", Schema.constant("elements"))
                        .property("elementIds", Schema.array("Their ids", Schema.nonEmptyString(""), 1, 10_000))
                        .required("kind", "elementIds").build()));
    }

    private static ExportRequest.Scope scopeOf(Arguments arguments) {
        return arguments.optionalObject("scope").<ExportRequest.Scope>map(scope -> switch (scope.string("kind")) {
            case "whole" -> new ExportRequest.Scope.WholeCircuit();
            case "frame" -> new ExportRequest.Scope.Frame(scope.string("frameId"));
            case "elements" -> new ExportRequest.Scope.Elements(scope.stringSet("elementIds"));
            default -> throw scope.invalid("kind", "is whole, frame or elements");
        }).orElseGet(ExportRequest.Scope.WholeCircuit::new);
    }

    private static Tool export(AutomationApi api) {
        return new Tool(EXPORT, "Export the circuit to a file",
                "Exports as Archivo ▸ «Exportar a PDF…» or «Exportar imagen…» (PNG or SVG) do, with their options, "
                        + "of the whole circuit, one frame or some elements. The file goes only into the export "
                        + "folder VSim remembers (the last one the user exported to; none yet: the VSim folder in "
                        + "the user's home), under a new name: the name given (or the circuit's), with -2, -3… "
                        + "when it is taken. VSim never overwrites a file and the AI never chooses the folder. The "
                        + "user sees «La IA guardó <archivo>». Export only when the user asked for it.",
                Schema.object("")
                        .property("document", Common.document())
                        .property("format", Schema.enumeration("PDF, PNG or SVG", List.of("PDF", "PNG", "SVG")))
                        .property("scope", scope())
                        .property("fileName", Schema.string("The name without folder or extension (default: the "
                                + "circuit's name)"))
                        .property("scale", Schema.integer("PNG: 1, 2 (default) or 4", 1, 4))
                        .property("printBackground", Schema.bool("PNG/SVG: «Fondo blanco para imprimir»"))
                        .property("paper", Schema.enumeration("PDF: the paper (default A4)", PdfOptions.PAPERS))
                        .property("orientation", Schema.enumeration("PDF: PORTRAIT (default) or LANDSCAPE",
                                PdfOptions.ORIENTATIONS))
                        .property("scalePercent", Schema.integer("PDF: 0 (default) fits one sheet; 10 to 400 is a "
                                + "fixed scale tiled over sheets", 0, PdfOptions.MAXIMUM_PERCENT))
                        .property("withValues", Schema.bool("PDF: «Con valores» (default true)"))
                        .property("blackAndWhite", Schema.bool("PDF: «Blanco y negro»"))
                        .property("headerFooter", Schema.bool("PDF: «Encabezado y pie»"))
                        .property("title", Schema.string("PDF: the header's title (default: the circuit's name)"))
                        .required("document", "format").build(),
                Common.versionedResult("What was written")
                        .property("format", Schema.string(""))
                        .property("fileName", Schema.string("The name it got"))
                        .property("folder", Schema.string("The export folder"))
                        .property("path", Schema.string("The full path"))
                        .property("width", Schema.integer("Pixels (images)"))
                        .property("height", Schema.integer("Pixels (images)"))
                        .property("pages", Schema.integer("Sheets (PDF)"))
                        .required("format", "fileName", "folder", "path").build(),
                Tool.Hints.CHANGES,
                List.of(Map.of("document", "circuit-1", "format", "PNG", "scale", 2),
                        Map.of("document", "circuit-1", "format", "PDF", "scope", Map.of("kind", "frame",
                                "frameId", "f1"), "fileName", "latch", "orientation", "LANDSCAPE")),
                arguments -> {
                    ImageOptions image = new ImageOptions(arguments.integer("scale", ImageOptions.DEFAULT.scale()),
                            arguments.bool("printBackground", ImageOptions.DEFAULT.printBackground()));
                    PdfOptions pdf = new PdfOptions(arguments.string("paper", PdfOptions.DEFAULT.paper()),
                            arguments.string("orientation", PdfOptions.DEFAULT.orientation()),
                            arguments.integer("scalePercent", 0),
                            arguments.bool("withValues", PdfOptions.DEFAULT.withValues()),
                            arguments.bool("blackAndWhite", false), arguments.bool("headerFooter", false),
                            arguments.string("title", ""));
                    return Tool.Output.of(api.export(new ExportRequest(arguments.document(),
                            arguments.enumeration("format", ExportRequest.Format.class), scopeOf(arguments),
                            arguments.optionalString("fileName").filter(name -> !name.isBlank()), image, pdf)));
                });
    }
}
