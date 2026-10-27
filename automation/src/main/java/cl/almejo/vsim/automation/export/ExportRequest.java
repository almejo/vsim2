package cl.almejo.vsim.automation.export;

import cl.almejo.vsim.automation.DocumentId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An export the AI asks for (spec §7, Exports row): the same exports and options as Archivo ▸ «Exportar imagen…» and
 * «Exportar a PDF…», of the whole circuit, one frame or some elements, written only into the export folder VSim
 * remembers (none yet: the user's {@code VSim} folder) under a new name, never over an existing file. {@code fileName}
 * is the name without folder or extension (the circuit's name when empty); VSim adds {@code -2}, {@code -3}… when it is
 * taken. {@code includeStickyNotes}: the user's sticky notes go in the file (left out by default, as in the menus).
 */
public record ExportRequest(DocumentId document, Format format, Scope scope, Optional<String> fileName,
                            ImageOptions image, PdfOptions pdf, boolean includeStickyNotes) {

    /** A request that leaves the sticky notes out. */
    public ExportRequest(DocumentId document, Format format, Scope scope, Optional<String> fileName,
                         ImageOptions image, PdfOptions pdf) {
        this(document, format, scope, fileName, image, pdf, false);
    }

    /** The file formats of the menus. */
    public enum Format {
        PDF("pdf"), PNG("png"), SVG("svg");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    /** What goes in the file. */
    public sealed interface Scope {
        /** Everything in the circuit. */
        record WholeCircuit() implements Scope {
        }

        /** One frame's rectangle (a visual crop, as the PDF export's «Un marco»). */
        record Frame(String frameId) implements Scope {
            public Frame {
                Objects.requireNonNull(frameId, "frameId");
            }
        }

        /** These elements only, as when the user selected them (a locked frame brings its contents). */
        record Elements(Set<String> elementIds) implements Scope {
            public Elements {
                elementIds = Set.copyOf(elementIds);
                if (elementIds.isEmpty()) {
                    throw new IllegalArgumentException("an export of elements names at least one");
                }
            }
        }
    }

    /** «Exportar imagen…»: the PNG scale (1, 2 or 4; SVG is always 1) and «Fondo blanco para imprimir». */
    public record ImageOptions(int scale, boolean printBackground) {
        public static final List<Integer> SCALES = List.of(1, 2, 4);
        public static final ImageOptions DEFAULT = new ImageOptions(2, false);

        public ImageOptions {
            if (!SCALES.contains(scale)) {
                throw new IllegalArgumentException("the scale is 1, 2 or 4: " + scale);
            }
        }
    }

    /**
     * «Exportar a PDF…»: paper ({@link #PAPERS}), orientation ({@code PORTRAIT} or {@code LANDSCAPE}), the scale as a
     * percentage ({@code 0}: «Ajustar a una hoja»; otherwise 10 to 400, tiled over several sheets), «Con valores»,
     * «Blanco y negro», «Encabezado y pie» and its title (the circuit's name when empty).
     */
    public record PdfOptions(String paper, String orientation, int scalePercent, boolean withValues,
                             boolean blackAndWhite, boolean headerFooter, String title) {
        public static final List<String> PAPERS = List.of("A4", "LETTER", "OFICIO", "A3");
        public static final List<String> ORIENTATIONS = List.of("PORTRAIT", "LANDSCAPE");
        public static final int MINIMUM_PERCENT = 10;
        public static final int MAXIMUM_PERCENT = 400;
        public static final PdfOptions DEFAULT = new PdfOptions("A4", "PORTRAIT", 0, true, false, false, "");

        public PdfOptions {
            if (!PAPERS.contains(paper)) {
                throw new IllegalArgumentException("the paper is one of " + PAPERS + ": " + paper);
            }
            if (!ORIENTATIONS.contains(orientation)) {
                throw new IllegalArgumentException("the orientation is PORTRAIT or LANDSCAPE: " + orientation);
            }
            if (scalePercent != 0 && (scalePercent < MINIMUM_PERCENT || scalePercent > MAXIMUM_PERCENT)) {
                throw new IllegalArgumentException("the scale is 0 (fit to one sheet) or " + MINIMUM_PERCENT + " to "
                        + MAXIMUM_PERCENT + " percent: " + scalePercent);
            }
            Objects.requireNonNull(title, "title");
        }
    }

    public ExportRequest {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(pdf, "pdf");
    }
}
