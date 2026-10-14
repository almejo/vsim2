package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.WireTidy;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Tidying off the EDT: the 2 s progress window, Cancelar, a document that changed meanwhile and the dialog summary. */
class TidyWiresBackgroundTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    /** Collects what should run on the EDT, so the test decides when. */
    private final Deque<Runnable> edtQueue = new ArrayDeque<>();
    private final List<Runnable> delayedTasks = new ArrayList<>();
    private final List<String> progressEvents = new ArrayList<>();
    private final List<Runnable> progressCancels = new ArrayList<>();
    private final AtomicBoolean progressClosed = new AtomicBoolean();
    private final CountDownLatch planStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<Thread> threads = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;
    private TidyWiresFeatures features;
    private ElementId stub;
    private boolean slowPlanner;
    private volatile boolean plannerSawCancellation;

    private TidyRunner.Planner planner() {
        return (snapshot, registry, scope, options, cancelled, progress) -> {
            if (slowPlanner) {
                planStarted.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                }
                progress.accept(7);
                plannerSawCancellation = cancelled.getAsBoolean();
                if (plannerSawCancellation) {
                    return Optional.empty();
                }
            }
            return WireTidy.plan(snapshot, registry, scope, options, cancelled, progress);
        };
    }

    private TidyRunner newRunner() {
        return new TidyRunner(task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }, edtQueue::add, planner());
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        ElementId left = addWire(new GridPoint(0, 0), new GridPoint(8, 0));
        addWire(new GridPoint(8, 0), new GridPoint(16, 0));
        stub = addWire(new GridPoint(8, 0), new GridPoint(8, 4));
        assertThat(left).isNotNull();
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> Optional.empty(),
                newRunner(), newRunner(), (milliseconds, task) -> {
                    assertThat(milliseconds).isEqualTo(2000);
                    delayedTasks.add(task);
                    return () -> delayedTasks.remove(task);
                }, (parent, onCancel) -> {
                    progressEvents.add("open");
                    progressCancels.add(onCancel);
                    return new TidyProgress() {
                        @Override
                        public void round(int round) {
                            progressEvents.add("round " + round);
                        }

                        @Override
                        public void close() {
                            progressClosed.set(true);
                        }
                    };
                });
    }

    @AfterEach
    void close() {
        release.countDown();
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private ElementId addWire(GridPoint start, GridPoint end) {
        ElementId id = openDocument.document().newWireId();
        openDocument.execute(new AddElementsCommand(List.of(new Wire(id, start, end))));
        return id;
    }

    /** Waits for the worker threads and runs what they queued for the EDT. */
    private void drainEdt() throws InterruptedException {
        for (Thread thread : List.copyOf(threads)) {
            thread.join(10_000);
        }
        while (!edtQueue.isEmpty()) {
            edtQueue.poll().run();
        }
    }

    private static void perform(javax.swing.Action action) {
        action.actionPerformed(new java.awt.event.ActionEvent(action, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void aFastTidyOpensNoWindowAndAppliesTheCommand() throws InterruptedException {
        long historyBefore = openDocument.changeCount();
        perform(features.tidyAction());
        assertThat(openDocument.document().wire(stub)).isPresent(); // not yet: computing in the background
        assertThat(features.isRunning()).isTrue();
        drainEdt();
        assertThat(progressEvents).isEmpty();
        assertThat(delayedTasks).isEmpty(); // the timer was stopped
        assertThat(openDocument.document().wire(stub)).isEmpty();
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore + 1);
        assertThat(features.isRunning()).isFalse();
        assertThat(view.statusBar().text()).contains("Se borraron 3 puntas sueltas");
    }

    @Test
    void aSlowTidyShowsTheWindowAfterTwoSecondsAndFinishesNormally() throws InterruptedException {
        slowPlanner = true;
        perform(features.tidyAction());
        assertThat(planStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(progressEvents).isEmpty();
        assertThat(delayedTasks).hasSize(1);
        delayedTasks.get(0).run(); // the two seconds pass
        assertThat(progressEvents).containsExactly("open");
        release.countDown();
        drainEdt();
        assertThat(progressEvents).startsWith("open", "round 7", "round 1");
        assertThat(progressClosed).isTrue();
        assertThat(openDocument.document().wire(stub)).isEmpty();
    }

    @Test
    void cancelarLeavesTheDocumentUntouchedAndSaysSo() throws InterruptedException {
        slowPlanner = true;
        long historyBefore = openDocument.changeCount();
        perform(features.tidyAction());
        assertThat(planStarted.await(10, TimeUnit.SECONDS)).isTrue();
        delayedTasks.get(0).run();
        progressCancels.get(0).run();
        assertThat(progressClosed).isTrue();
        assertThat(view.statusBar().text()).contains("Ordenar cables cancelado");
        release.countDown();
        drainEdt();
        assertThat(plannerSawCancellation).isTrue();
        assertThat(openDocument.document().wire(stub)).isPresent();
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore);
        assertThat(features.isRunning()).isFalse();
        assertThat(view.statusBar().text()).contains("Ordenar cables cancelado");
    }

    @Test
    void aDocumentThatChangedMeanwhileDiscardsThePlan() throws InterruptedException {
        slowPlanner = true;
        perform(features.tidyAction());
        assertThat(planStarted.await(10, TimeUnit.SECONDS)).isTrue();
        addWire(new GridPoint(30, 30), new GridPoint(34, 30));
        long historyBefore = openDocument.changeCount();
        release.countDown();
        drainEdt();
        assertThat(openDocument.document().wire(stub)).isPresent();
        assertThat(openDocument.changeCount()).isEqualTo(historyBefore);
        assertThat(view.statusBar().text()).contains("El circuito cambió; vuelve a ordenar");
    }

    @Test
    void disposeStopsTheRunningWorkAndDeliversNothing() throws InterruptedException {
        slowPlanner = true;
        perform(features.tidyAction());
        assertThat(planStarted.await(10, TimeUnit.SECONDS)).isTrue();
        features.dispose();
        assertThat(features.isRunning()).isFalse();
        assertThat(delayedTasks).isEmpty();
        release.countDown();
        drainEdt();
        assertThat(plannerSawCancellation).isTrue();
        assertThat(openDocument.document().wire(stub)).isPresent();
    }

    @Test
    void theDialogSummaryShowsCalculandoThenTheSummary() throws InterruptedException {
        TidyPlanner[] captured = new TidyPlanner[1];
        features.dispose();
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> {
            captured[0] = planner;
            return Optional.empty();
        }, newRunner(), newRunner(), (milliseconds, task) -> () -> { }, (parent, onCancel) -> null);
        perform(features.tidyWithOptionsAction());
        TidyWiresPanel panel = new TidyWiresPanel(false, TidyOptions.DEFAULTS, captured[0]);
        assertThat(panel.summaryText()).isEqualTo("Calculando…");
        assertThat(panel.canApply()).isFalse();
        drainEdt();
        assertThat(panel.summaryText()).startsWith("Se borrarán 3 puntas sueltas");
        assertThat(panel.canApply()).isTrue();
    }

    @Test
    void aCheckboxChangeCancelsThePreviousComputationAndTheLatestWins() throws InterruptedException {
        List<Runnable> cancels = new ArrayList<>();
        List<java.util.function.Consumer<TidyPlan>> results = new ArrayList<>();
        TidyPlanner manual = (options, selectionOnly, onResult) -> {
            results.add(onResult);
            Runnable cancel = () -> cancels.add(null);
            return cancel;
        };
        TidyWiresPanel panel = new TidyWiresPanel(false, TidyOptions.DEFAULTS, manual);
        panel.mergeSegmentsBox().doClick();
        assertThat(results).hasSize(2);
        assertThat(cancels).hasSize(1); // the first computation was cancelled
        results.get(0).accept(TidyPlan.EMPTY); // a late result of the first one is ignored
        assertThat(panel.isReady()).isFalse();
        assertThat(panel.summaryText()).isEqualTo("Calculando…");
        results.get(1).accept(TidyPlan.EMPTY);
        assertThat(panel.isReady()).isTrue();
        assertThat(panel.canApply()).isFalse(); // ready but nothing to do
        panel.dispose();
        assertThat(Set.of(panel.options())).isNotEmpty();
    }
}
