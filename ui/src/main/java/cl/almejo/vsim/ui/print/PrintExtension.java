package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.app.DaemonThreads;
import cl.almejo.vsim.ui.sheets.SheetAnchors;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Adds Archivo ▸ «Imprimir…» (spec 2026-10-19 §4) to every editor view. */
public final class PrintExtension implements EditorExtension {

    /**
     * Looks up the installed printers, which can take a while with CUPS; a daemon thread of its own, so a long print
     * never delays the answer and the thread never keeps the app alive.
     */
    private static final ExecutorService LOOKUP_THREAD = DaemonThreads.single("vsim-printer-lookup");

    private final PdfExportDialog.Collaborators collaborators;
    private final PdfExportDialog.Presenter presenter;
    private final Clock clock;
    private final SheetAnchors sheetAnchors;

    /** The system's printers; prints and PDFs go out one at a time on the PDF export's thread. */
    public PrintExtension(SheetAnchors sheetAnchors) {
        this(new SystemPrinterAccess(ExportPdfExtension.EXPORT_THREAD, LOOKUP_THREAD), sheetAnchors);
    }

    /** The real dialogs over {@code printers} ({@link PrinterAccess#none()} where no printer may be reached). */
    public PrintExtension(PrinterAccess printers, SheetAnchors sheetAnchors) {
        this(PdfExportDialog.Collaborators.swing(new PdfExport(ExportPdfExtension.EXPORT_THREAD), printers),
                PdfExportDialog.Presenter.modal(), Clock.systemDefaultZone(), sheetAnchors);
    }

    /**
     * {@code collaborators} write, print and ask, {@code presenter} shows the dialog, {@code clock} dates the sheets,
     * {@code sheetAnchors} where the sheets shown on the canvas are anchored (the dialog lays «Todo» on them).
     */
    public PrintExtension(PdfExportDialog.Collaborators collaborators, PdfExportDialog.Presenter presenter,
                          Clock clock, SheetAnchors sheetAnchors) {
        this.sheetAnchors = Objects.requireNonNull(sheetAnchors, "sheetAnchors");
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Runnable install(EditorView view) {
        PrintFeatures features = new PrintFeatures(view, collaborators, presenter, clock, sheetAnchors);
        return features::dispose;
    }
}
