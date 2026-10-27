package cl.almejo.vsim.ui.export;

import java.nio.file.Path;
import java.util.Locale;

/** What the user chose in the export dialog: the file, its format, the PNG scale, the background and whether the sticky notes go in. */
public record ExportChoice(Path path, Format format, int scale, boolean printBackground,
                           boolean includeStickyNotes) {

    /** A choice that leaves the sticky notes out, as the export always did. */
    public ExportChoice(Path path, Format format, int scale, boolean printBackground) {
        this(path, format, scale, printBackground, false);
    }

    public enum Format {
        PNG(".png"), SVG(".svg");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    /** A typed {@code .png} or {@code .svg} wins over the chosen filter; otherwise the filter's extension is added. */
    public static ExportChoice of(Path typed, Format filterFormat, int scale, boolean printBackground) {
        return of(typed, filterFormat, scale, printBackground, false);
    }

    /** Like the four-argument overload, with the sticky notes included or not. */
    public static ExportChoice of(Path typed, Format filterFormat, int scale, boolean printBackground,
                                  boolean includeStickyNotes) {
        String fileName = typed.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Format format : Format.values()) {
            if (fileName.endsWith(format.extension())) {
                return new ExportChoice(typed, format, scale, printBackground, includeStickyNotes);
            }
        }
        return new ExportChoice(typed.resolveSibling(typed.getFileName() + filterFormat.extension()), filterFormat,
                scale, printBackground, includeStickyNotes);
    }
}
