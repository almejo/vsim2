package cl.almejo.vsim.ui.print;

import java.util.Objects;

/** Everything the user chose in «Exportar a PDF…». */
public record PrintOptions(PrintScope scope, Paper paper, PageOrientation orientation, ScaleMode scaleMode,
                           boolean withValues, boolean blackAndWhite, boolean headerFooter, String title) {

    public PrintOptions {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(scaleMode, "scaleMode");
        Objects.requireNonNull(title, "title");
    }

    /** The whole circuit on one A4 portrait sheet, with values, in colour, without header and footer. */
    public static PrintOptions defaults(String title) {
        return new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.FitToOnePage(), true, false, false, title);
    }
}
