package cl.almejo.vsim.ui.print;

import java.util.Objects;

/**
 * Everything the user chose in «Exportar a PDF…».
 *
 * @param sheetGuides     on a tiled layout, the cut marks, the position label and the neighbours' numbers (and the
 *                        strip they need)
 * @param showDate        the date in the footer, when there is a header and footer
 * @param showFileName    the file name in the header, when there is a header and footer
 * @param showSheetNumber «Hoja n de N» in the footer, when there is a header and footer
 */
public record PrintOptions(PrintScope scope, Paper paper, PageOrientation orientation, ScaleMode scaleMode,
                           boolean withValues, boolean blackAndWhite, boolean headerFooter, String title,
                           boolean includeStickyNotes, boolean sheetGuides, boolean showDate, boolean showFileName,
                           boolean showSheetNumber) {

    /** Options with the guides and every header and footer item on, as they always were. */
    public PrintOptions(PrintScope scope, Paper paper, PageOrientation orientation, ScaleMode scaleMode,
                        boolean withValues, boolean blackAndWhite, boolean headerFooter, String title,
                        boolean includeStickyNotes) {
        this(scope, paper, orientation, scaleMode, withValues, blackAndWhite, headerFooter, title,
                includeStickyNotes, true, true, true, true);
    }

    /** Options that leave the sticky notes out, as printing always did. */
    public PrintOptions(PrintScope scope, Paper paper, PageOrientation orientation, ScaleMode scaleMode,
                        boolean withValues, boolean blackAndWhite, boolean headerFooter, String title) {
        this(scope, paper, orientation, scaleMode, withValues, blackAndWhite, headerFooter, title, false);
    }

    public PrintOptions {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(scaleMode, "scaleMode");
        Objects.requireNonNull(title, "title");
    }

    /** The whole circuit on one A4 portrait sheet, with values, in colour, without header and footer and without sticky notes. */
    public static PrintOptions defaults(String title) {
        return new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.FitToOnePage(), true, false, false, title);
    }
}
