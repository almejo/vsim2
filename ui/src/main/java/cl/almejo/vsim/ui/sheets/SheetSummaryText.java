package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.print.PageLayout;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.PdfExport;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintedSheets;
import cl.almejo.vsim.ui.print.ScaleMode;

/**
 * The summary label's text (sheets spec §3.1 point 4): the paper, the orientation, the scale and how many sheets, the
 * only place the canvas names them. «A4 vertical · 100 % · 2 × 3 = 6 hojas», «A4 vertical · 1 hoja · 100 %», «A4
 * vertical · Ajustar a una hoja (63 %)», with « · demasiadas para imprimir (máx. 1000)» past what printing accepts
 * and « · se imprimen 4» when «Saltar hojas vacías» leaves some out.
 */
final class SheetSummaryText {

    private SheetSummaryText() {
    }

    static String of(PrintOptions options, PageLayout layout, PrintedSheets printed) {
        String paper = Messages.text(options.paper().shortMessageKey());
        String orientation = Messages.text(options.orientation() == PageOrientation.PORTRAIT
                ? "sheets.orientation.portrait" : "sheets.orientation.landscape");
        if (layout.problem().isPresent()) {
            return Messages.format("sheets.summary.noRoom", paper, orientation);
        }
        double percent = layout.scale() * 100;
        if (options.scaleMode() instanceof ScaleMode.FitToOnePage) {
            return Messages.format("sheets.summary.fit", paper, orientation, percent);
        }
        if (!layout.tiled()) {
            return Messages.format("sheets.summary.one", paper, orientation, percent);
        }
        String sheets = Messages.format("sheets.summary.many", paper, orientation, percent, layout.columns(),
                layout.rows(), layout.pageCountExact());
        if (printed.tooMany()) {
            return sheets + Messages.format("sheets.summary.tooMany", PdfExport.REFUSE_ABOVE_PAGES);
        }
        return printed.skippedCount() > 0 ? sheets + Messages.format("sheets.summary.printed", printed.count())
                : sheets;
    }
}
