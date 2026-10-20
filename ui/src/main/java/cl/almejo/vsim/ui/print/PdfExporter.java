package cl.almejo.vsim.ui.print;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Writes the sheets of a {@link PagePainter} to a PDF file, off the EDT. */
public interface PdfExporter {

    /**
     * Writes every page of {@code pages} to {@code target}; an earlier file there is replaced only on success. The future
     * completes exceptionally with the cause on failure, and with a {@link java.util.concurrent.CancellationException}
     * when {@code progress} asked to stop.
     */
    CompletableFuture<Void> export(PagePainter pages, Path target, ProgressListener progress);

    /** Told about each finished page, and asked between pages whether to stop. */
    interface ProgressListener {

        void pageDone(int done, int total);

        boolean cancelled();
    }
}
