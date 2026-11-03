package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adds Archivo ▸ «Imprimir…» (spec 2026-10-19 §4) to every editor view. */
public final class PrintExtension implements EditorExtension {

    /**
     * Looks up the installed printers, which can take a while with CUPS; a daemon thread of its own, so a long print
     * never delays the answer and the thread never keeps the app alive.
     */
    private static final ExecutorService LOOKUP_THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vsim-printer-lookup");
        thread.setDaemon(true);
        return thread;
    });

    private final PdfExportDialog.Collaborators collaborators;
    private final PdfExportDialog.Presenter presenter;
    private final Clock clock;

    /** The system's printers; prints and PDFs go out one at a time on the PDF export's thread. */
    public PrintExtension() {
        this(new SystemPrinterAccess(ExportPdfExtension.EXPORT_THREAD, LOOKUP_THREAD));
    }

    /** The real dialogs over {@code printers} ({@link PrinterAccess#none()} where no printer may be reached). */
    public PrintExtension(PrinterAccess printers) {
        this(PdfExportDialog.Collaborators.swing(new PdfExport(ExportPdfExtension.EXPORT_THREAD), printers),
                PdfExportDialog.Presenter.modal(), Clock.systemDefaultZone());
    }

    /** {@code collaborators} write, print and ask, {@code presenter} shows the dialog, {@code clock} dates the sheets. */
    public PrintExtension(PdfExportDialog.Collaborators collaborators, PdfExportDialog.Presenter presenter,
                          Clock clock) {
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Runnable install(EditorView view) {
        PrintFeatures features = new PrintFeatures(view, collaborators, presenter, clock);
        return features::dispose;
    }
}
