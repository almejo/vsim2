package cl.almejo.vsim.ui.print;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The real {@link PdfExporter}: paints every sheet of a {@link PagePainter} through {@link PdfPages} on the given
 * executor, so the EDT never waits for it.
 *
 * <p>The file is written next to the target as {@code <name>.tmp-<random>} and moved over the target only once every
 * page painted (atomically when the file system can, else with a plain replacing move). JFreePDF assembles the whole
 * document in memory, so the temporary file is only created when the finished bytes are written: a cancellation or a
 * failure while painting leaves no file at all, and a failure while writing or moving deletes it. Either way an
 * earlier file at the target is untouched.
 *
 * <p>«Cancelar» is checked between sheets and once the bytes are ready; JFreePDF's assembling of the bytes itself
 * cannot be interrupted, so a cancel during it waits for it (each sheet only carries its share of the circuit, which
 * keeps that step short).
 */
public final class PdfExport implements PdfExporter {

    /** Above this many sheets the dialog asks before exporting. */
    public static final int CONFIRM_ABOVE_PAGES = 50;
    /** Above this many sheets nothing is exported. */
    public static final int REFUSE_ABOVE_PAGES = 1000;

    /** Moves a finished file into place; {@link Files#move} outside tests. */
    @FunctionalInterface
    interface FileMover {
        void move(Path source, Path target, CopyOption... options) throws IOException;
    }

    private static final String TEMPORARY_INFIX = ".tmp-";

    private final Executor executor;
    private final FileMover mover;

    public PdfExport(Executor executor) {
        this(executor, Files::move);
    }

    PdfExport(Executor executor, FileMover mover) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    /**
     * Writes the sheets on the executor. The future also fails with an {@link IllegalArgumentException} when the
     * painter has no sheet or more than {@link #REFUSE_ABOVE_PAGES}.
     */
    @Override
    public CompletableFuture<Void> export(PagePainter pages, Path target, ProgressListener progress) {
        Objects.requireNonNull(pages, "pages");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(progress, "progress");
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    write(pages, target.toAbsolutePath(), progress);
                    result.complete(null);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException rejection) {
            result.completeExceptionally(rejection);
        }
        return result;
    }

    private void write(PagePainter painter, Path target, ProgressListener progress) throws IOException {
        List<PagePainter.Page> pages = painter.pages();
        int total = pages.size();
        if (total > REFUSE_ABOVE_PAGES) {
            throw new IllegalArgumentException("too many sheets to export: " + total);
        }
        List<PdfPages.PageSize> sizes = pages.stream().map(PagePainter.Page::size).toList();
        Path folder = target.getParent();
        String prefix = target.getFileName() + TEMPORARY_INFIX;
        try (LazyTemporaryFile temporary = new LazyTemporaryFile(folder, prefix)) {
            PdfPages.write(temporary, sizes, (pageIndex, graphics) -> {
                stopIfCancelled(progress);
                painter.paint(graphics, pages.get(pageIndex).layoutIndex());
                progress.pageDone(pageIndex + 1, total);
            });
            stopIfCancelled(progress);
            Path written = temporary.close(true).orElseThrow();
            moveIntoPlace(written, target);
            temporary.moved();
        }
    }

    private static void stopIfCancelled(ProgressListener progress) {
        if (progress.cancelled()) {
            throw new CancellationException("PDF export cancelled");
        }
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            mover.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException notSupported) {
            mover.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * An output stream that creates its temporary file on the first byte, and deletes it on close unless it was
     * {@link #moved()}.
     */
    private static final class LazyTemporaryFile extends OutputStream {

        private final Path folder;
        private final String prefix;
        private Path file;
        private OutputStream output;
        private boolean moved;

        LazyTemporaryFile(Path folder, String prefix) {
            this.folder = folder;
            this.prefix = prefix;
        }

        private OutputStream output() throws IOException {
            if (output == null) {
                file = Files.createTempFile(folder, prefix, "");
                output = Files.newOutputStream(file);
            }
            return output;
        }

        @Override
        public void write(int value) throws IOException {
            output().write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            output().write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            if (output != null) {
                output.flush();
            }
        }

        /** Closes the stream and gives the file written, if any; with {@code keep} false it also deletes it. */
        Optional<Path> close(boolean keep) throws IOException {
            if (output != null) {
                OutputStream closing = output;
                output = null;
                closing.close();
            }
            if (!keep) {
                deleteFile();
            }
            return Optional.ofNullable(file);
        }

        void moved() {
            moved = true;
            file = null;
        }

        private void deleteFile() throws IOException {
            if (file != null) {
                Path deleting = file;
                file = null;
                Files.deleteIfExists(deleting);
            }
        }

        @Override
        public void close() throws IOException {
            if (moved) {
                return;
            }
            try {
                close(false);
            } catch (IOException closing) {
                deleteFile();
                throw closing;
            }
        }
    }
}
