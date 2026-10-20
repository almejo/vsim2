package cl.almejo.vsim.ui.export;

import java.util.List;

/** The icons of «Exportar imagen…» (M6.2) and «Exportar a PDF…», by file name under {@code icons/} (installed with {@code Icons.install}). */
public final class ExportIcons {

    public static final String EXPORT_IMAGE = "export-image";
    public static final String EXPORT_PDF = "export-pdf";

    private ExportIcons() {
    }

    /** Every export icon, once. */
    public static List<String> names() {
        return List.of(EXPORT_IMAGE, EXPORT_PDF);
    }
}
