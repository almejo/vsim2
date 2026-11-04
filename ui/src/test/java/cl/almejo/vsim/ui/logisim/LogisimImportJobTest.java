package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.logisim.ImportResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class LogisimImportJobTest {

    /** A delayer the test fires by hand: nothing waits for real time. */
    private static final class FakeDelayer implements Delayer {
        final List<Long> requestedMilliseconds = new ArrayList<>();
        final List<Runnable> pending = new ArrayList<>();

        @Override
        public Runnable after(long milliseconds, Runnable action) {
            requestedMilliseconds.add(milliseconds);
            pending.add(action);
            return () -> pending.remove(action);
        }

        void elapse() {
            new ArrayList<>(pending).forEach(Runnable::run);
            pending.clear();
        }
    }

    /** Keeps the work until the test runs it, as a background thread would finish later. */
    private static final class ManualExecutor implements Executor {
        final List<Runnable> queued = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runAll() {
            List<Runnable> now = new ArrayList<>(queued);
            queued.clear();
            now.forEach(Runnable::run);
        }
    }

    private final List<String> events = new ArrayList<>();
    private final FakeDelayer delayer = new FakeDelayer();
    private final ManualExecutor worker = new ManualExecutor();
    private final ManualExecutor eventThread = new ManualExecutor();
    private Runnable cancelFromDialog;

    private LogisimImportJob job(LogisimImportJob.Loader loader) {
        return new LogisimImportJob(worker, eventThread, delayer, loader, new LogisimImportJob.Progress() {
            @Override
            public void show(Runnable cancel) {
                cancelFromDialog = cancel;
                events.add("show");
            }

            @Override
            public void hide() {
                events.add("hide");
            }
        }, new LogisimImportJob.Outcome() {
            @Override
            public void imported(Path file, ImportResult result) {
                events.add("imported " + file.getFileName());
            }

            @Override
            public void failed(Path file, Exception problem) {
                events.add("failed " + problem.getClass().getSimpleName());
            }
        });
    }

    private static ImportResult empty() {
        return new ImportResult(new CircuitDocument(), List.of());
    }

    @Test
    void anErrorOnTheWorkerIsReportedAsAFailureAndTheProgressDialogStillCloses() {
        job(file -> {
            throw new StackOverflowError("a circuit that nests too deep");
        }).start(Path.of("a.circ"));
        delayer.elapse();
        worker.runAll();
        eventThread.runAll();

        assertThat(events).containsExactly("show", "hide", "failed IllegalStateException");
    }

    @Test
    void theProgressDialogIsAskedForAfterTwoSecondsNotBefore() {
        job(file -> empty()).start(Path.of("a.circ"));

        assertThat(delayer.requestedMilliseconds).containsExactly(2000L);
        assertThat(events).isEmpty();
    }

    @Test
    void aFastImportNeverShowsTheProgressDialog() {
        job(file -> empty()).start(Path.of("a.circ"));
        worker.runAll();
        eventThread.runAll();
        delayer.elapse();

        assertThat(events).containsExactly("imported a.circ");
        assertThat(delayer.pending).isEmpty();
    }

    @Test
    void aSlowImportShowsTheProgressDialogAndHidesItWhenItEnds() {
        job(file -> empty()).start(Path.of("a.circ"));
        delayer.elapse();
        assertThat(events).containsExactly("show");

        worker.runAll();
        eventThread.runAll();

        assertThat(events).containsExactly("show", "hide", "imported a.circ");
    }

    @Test
    void theLoadingRunsOffTheEventThread() {
        List<String> ran = new ArrayList<>();
        job(file -> {
            ran.add("load");
            return empty();
        }).start(Path.of("a.circ"));

        assertThat(ran).isEmpty();
        worker.runAll();
        assertThat(ran).containsExactly("load");
        assertThat(events).isEmpty();
    }

    @Test
    void aFormatProblemIsReportedAndTheDialogIsHidden() {
        job(file -> {
            throw new CircuitFormatException(CircuitFormatException.Reason.NOT_XML, "not XML: x");
        }).start(Path.of("a.circ"));
        delayer.elapse();
        worker.runAll();
        eventThread.runAll();

        assertThat(events).containsExactly("show", "hide", "failed CircuitFormatException");
    }

    @Test
    void anUnexpectedFailureIsAProblemToo() {
        job(file -> {
            throw new IllegalStateException("boom");
        }).start(Path.of("a.circ"));
        worker.runAll();
        eventThread.runAll();

        assertThat(events).containsExactly("failed IllegalStateException");
    }

    @Test
    void cancellingFromTheDialogDropsTheResult() {
        job(file -> empty()).start(Path.of("a.circ"));
        delayer.elapse();
        cancelFromDialog.run();
        worker.runAll();
        eventThread.runAll();

        assertThat(events).containsExactly("show", "hide");
    }
}
