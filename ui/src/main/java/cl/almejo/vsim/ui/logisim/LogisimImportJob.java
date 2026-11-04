package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.logisim.ImportResult;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * One import of a Logisim file: the reading and converting run on {@code worker} (never the event thread), and the
 * progress dialog shows only when that takes more than {@link #PROGRESS_DELAY_MILLISECONDS}. Everything but the loading
 * itself runs on the event thread ({@code eventThread} is where the worker's answer is brought back to), so the state
 * here needs no locks.
 */
final class LogisimImportJob {

    /** A quick import must not flash a dialog: it shows after this long. */
    static final long PROGRESS_DELAY_MILLISECONDS = 2000;

    /** Reads and converts {@code file}; runs on the worker. */
    @FunctionalInterface
    interface Loader {
        ImportResult load(Path file) throws CircuitFormatException;
    }

    /** The progress dialog; {@code show} gets what the dialog's «Cancelar» runs. Both run on the event thread. */
    interface Progress {
        void show(Runnable cancel);

        void hide();
    }

    /** What the import ends in, on the event thread; nothing is told once the user cancelled. */
    interface Outcome {
        void imported(Path file, ImportResult result);

        /** {@code problem} is a {@link CircuitFormatException} for a file that is not what it should be. */
        void failed(Path file, Exception problem);
    }

    private final Executor worker;
    private final Executor eventThread;
    private final Delayer delayer;
    private final Loader loader;
    private final Progress progress;
    private final Outcome outcome;
    private boolean finished;
    private boolean progressShown;
    private Runnable stopTimer = () -> { };

    LogisimImportJob(Executor worker, Executor eventThread, Delayer delayer, Loader loader, Progress progress,
                     Outcome outcome) {
        this.worker = worker;
        this.eventThread = eventThread;
        this.delayer = delayer;
        this.loader = loader;
        this.progress = progress;
        this.outcome = outcome;
    }

    /** Starts the import of {@code file}; called on the event thread. */
    void start(Path file) {
        stopTimer = delayer.after(PROGRESS_DELAY_MILLISECONDS, () -> {
            if (!finished) {
                progressShown = true;
                progress.show(this::cancel);
            }
        });
        worker.execute(() -> {
            try {
                ImportResult result = loader.load(file);
                eventThread.execute(() -> end(() -> outcome.imported(file, result)));
            } catch (CircuitFormatException | RuntimeException problem) {
                eventThread.execute(() -> end(() -> outcome.failed(file, problem)));
            } catch (Error error) {
                // A stack overflow or an out-of-memory in a huge file must not leave the progress dialog up.
                IllegalStateException problem = new IllegalStateException(error.toString(), error);
                eventThread.execute(() -> end(() -> outcome.failed(file, problem)));
            }
        });
    }

    private void cancel() {
        finished = true;
        hideProgress();
    }

    private void end(Runnable report) {
        if (finished) { // the user cancelled: the result is dropped
            return;
        }
        finished = true;
        hideProgress();
        report.run();
    }

    private void hideProgress() {
        stopTimer.run();
        if (progressShown) {
            progressShown = false;
            progress.hide();
        }
    }
}
