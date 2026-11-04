package cl.almejo.vsim.ui.sheets;

import java.util.List;

/** The icon of the «Mostrar hojas de impresión» toggle, by file name under {@code icons/}. */
public final class SheetsIcons {

    public static final String PRINT_SHEETS = "print-sheets";

    private SheetsIcons() {
    }

    /** Every icon of the print sheets on the canvas, once. */
    public static List<String> names() {
        return List.of(PRINT_SHEETS);
    }
}
