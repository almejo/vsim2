package cl.almejo.vsim.ui.print;

import java.awt.Component;
import java.util.concurrent.CompletableFuture;

/**
 * The one door to the system's printers ({@code java.awt.print} and {@code javax.print}): nothing else in VSim
 * touches {@code PrinterJob}, {@code PrintServiceLookup} or the print attributes, so tests use a fake and never open
 * a printer dialog nor reach a printer. {@link SystemPrinterAccess} is the real one.
 */
public interface PrinterAccess {

    /** Whether any printer is installed; answered off the EDT (the lookup can be slow with CUPS). */
    CompletableFuture<Boolean> hasPrinters();

    /**
     * Shows the printer's modal dialog pre-filled with {@code requested} and one copy, and says what the user chose.
     * EDT only.
     */
    PrinterChoice choose(Component parent, PrintSetup requested);

    /**
     * Prints every sheet of {@code pages} on {@code setup} through {@code job}, off the EDT. The future completes
     * exceptionally with the cause on failure, and with a {@link java.util.concurrent.CancellationException} when
     * {@code progress} asked to stop or the printer aborted the job.
     */
    CompletableFuture<Void> print(Job job, PagePainter pages, PrintSetup setup,
                                  PdfExporter.ProgressListener progress);

    /** A print job the user accepted in {@link #choose}, with what they chose there (copies, page range…). */
    interface Job {
    }
}
