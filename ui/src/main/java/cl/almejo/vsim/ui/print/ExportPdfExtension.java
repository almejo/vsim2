package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adds Archivo ▸ «Exportar a PDF…» (spec 2026-10-19 §3) to every editor view. */
public final class ExportPdfExtension implements EditorExtension {

    /** Writes the PDFs, one at a time, off the EDT; a daemon thread, so it never keeps the app alive. */
    private static final ExecutorService EXPORT_THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vsim-pdf-export");
        thread.setDaemon(true);
        return thread;
    });

    private final PdfExportDialog.Collaborators collaborators;
    private final PdfExportDialog.Presenter presenter;
    private final Clock clock;

    public ExportPdfExtension() {
        this(PdfExportDialog.Collaborators.swing(realExporter(EXPORT_THREAD)), PdfExportDialog.Presenter.modal(),
                Clock.systemDefaultZone());
    }

    /** {@code collaborators} write and ask, {@code presenter} shows the dialog, {@code clock} dates the sheets. */
    public ExportPdfExtension(PdfExportDialog.Collaborators collaborators, PdfExportDialog.Presenter presenter,
                              Clock clock) {
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static PdfExporter realExporter(Executor executor) {
        return new PdfExport(executor);
    }

    @Override
    public Runnable install(EditorView view) {
        ExportPdfFeatures features = new ExportPdfFeatures(view, collaborators, presenter, clock);
        return features::dispose;
    }
}
