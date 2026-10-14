package cl.almejo.vsim.ui.export;

import java.nio.file.Path;
import java.util.Locale;

/** What the user chose in the export dialog: the file, its format, the PNG scale and the background. */
public record ExportChoice(Path path, Format format, int scale, boolean printBackground) {

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
        String fileName = typed.getFileName().toString().toLowerCase(Locale.ROOT);
        for (Format format : Format.values()) {
            if (fileName.endsWith(format.extension())) {
                return new ExportChoice(typed, format, scale, printBackground);
            }
        }
        return new ExportChoice(typed.resolveSibling(typed.getFileName() + filterFormat.extension()), filterFormat,
                scale, printBackground);
    }
}
