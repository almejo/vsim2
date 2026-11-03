package cl.almejo.vsim.ui.print;

import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link PrinterAccess} for tests: no dialog, no printer. The test sets whether printers exist, scripts what each
 * printer dialog answers, and finishes each print through its future.
 */
final class FakePrinterAccess implements PrinterAccess {

    /** One call to {@link #print}: what it was asked to print, and the future the test completes. */
    record PrintCall(Job job, PagePainter pages, PrintSetup setup, PdfExporter.ProgressListener progress,
                     CompletableFuture<Void> result) {
    }

    /** The job every {@link PrinterChoice.Chosen} of {@link #accepting} carries. */
    static final Job JOB = new Job() { };

    final List<CompletableFuture<Boolean>> lookups = new ArrayList<>();
    final List<PrintSetup> requested = new ArrayList<>();
    final List<PrintCall> prints = new ArrayList<>();
    private final Deque<PrinterChoice> answers = new ArrayDeque<>();
    /** Answer every lookup at once with this, or leave them waiting when null. */
    private Boolean printersInstalled = Boolean.TRUE;

    /** Lookups answer at once that printers exist ({@code true}), that none do ({@code false}). */
    FakePrinterAccess printersInstalled(boolean installed) {
        this.printersInstalled = installed;
        return this;
    }

    /** Lookups stay unanswered until the test completes them ({@link #lookups}). */
    FakePrinterAccess slowLookups() {
        this.printersInstalled = null;
        return this;
    }

    /** The next printer dialog answers {@code answer}. */
    FakePrinterAccess answer(PrinterChoice answer) {
        answers.add(answer);
        return this;
    }

    /** The next printer dialog accepts {@code effective}. */
    FakePrinterAccess accepting(PrintSetup effective) {
        return answer(new PrinterChoice.Chosen(effective, JOB));
    }

    @Override
    public CompletableFuture<Boolean> hasPrinters() {
        CompletableFuture<Boolean> lookup = printersInstalled == null ? new CompletableFuture<>()
                : CompletableFuture.completedFuture(printersInstalled);
        lookups.add(lookup);
        return lookup;
    }

    /** Without a scripted answer the dialog accepts what it was asked for. */
    @Override
    public PrinterChoice choose(Component parent, PrintSetup requested) {
        this.requested.add(requested);
        PrinterChoice answer = answers.poll();
        return answer == null ? new PrinterChoice.Chosen(requested, JOB) : answer;
    }

    @Override
    public CompletableFuture<Void> print(Job job, PagePainter pages, PrintSetup setup,
                                         PdfExporter.ProgressListener progress) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        prints.add(new PrintCall(job, pages, setup, progress, result));
        return result;
    }
}
