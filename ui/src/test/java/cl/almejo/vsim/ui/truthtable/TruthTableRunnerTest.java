package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruthTableRunnerTest {

    private static final Executor DIRECT = Runnable::run;

    /** Records what a computation reported, in order. */
    private static class Recorder implements TruthTableRunner.Callbacks {
        final List<String> events = new ArrayList<>();
        TruthTable table;

        @Override
        public void progress(int percent) {
            events.add("progress " + percent);
        }

        @Override
        public void finished(TruthTable finishedTable) {
            table = finishedTable;
            events.add("finished");
        }

        @Override
        public void cancelled() {
            events.add("cancelled");
        }

        @Override
        public void failed(Throwable throwable) {
            events.add("failed " + throwable.getClass().getSimpleName());
        }
    }

    /** Runs its tasks only when asked. */
    private static final class ManualExecutor implements Executor {
        final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.poll().run();
            }
        }
    }

    /** A switch touching an LED: Y = A, two rows. */
    private static Preparation.Ready identity() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument document = new CircuitDocument();
        document.add(component(registry, document, StandardComponentTypes.SWITCH, 0, 0, "A"));
        document.add(component(registry, document, StandardComponentTypes.LED, 3, -2, "Y"));
        return (Preparation.Ready) TruthTableScope.prepare(document, Set.of(), registry);
    }

    private static Component component(ComponentTypeRegistry registry, CircuitDocument document, String typeId,
                                       int x, int y, String label) {
        return registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y), Rotation.R0,
                Map.of(), label);
    }

    @Test
    void aDirectRunDeliversProgressAndTheTable() {
        TruthTableRunner runner = new TruthTableRunner(DIRECT, DIRECT);
        Recorder recorder = new Recorder();

        runner.start(identity(), recorder);

        assertThat(recorder.events).containsExactly("progress 50", "progress 100", "finished");
        assertThat(recorder.table.rows()).hasSize(2);
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void aNewRequestCancelsThePreviousOneWhichDeliversNothingMore() {
        ManualExecutor worker = new ManualExecutor();
        TruthTableRunner runner = new TruthTableRunner(worker, DIRECT);
        Recorder first = new Recorder();
        Recorder second = new Recorder();

        runner.start(identity(), first);
        assertThat(runner.isRunning()).isTrue();
        runner.start(identity(), second);
        assertThat(first.events).containsExactly("cancelled");

        worker.runAll();

        assertThat(first.events).containsExactly("cancelled");
        assertThat(second.events).containsExactly("progress 50", "progress 100", "finished");
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void cancelTellsTheCallbacksOnceAndDropsTheResult() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor edt = new ManualExecutor();
        TruthTableRunner runner = new TruthTableRunner(worker, edt);
        Recorder recorder = new Recorder();

        runner.start(identity(), recorder);
        worker.runAll();
        runner.cancel();
        edt.runAll();
        runner.cancel();

        assertThat(recorder.events).containsExactly("cancelled");
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void aCircuitTheEngineRejectsIsReportedAsAFailure() {
        Preparation.Ready broken = new Preparation.Ready(new CircuitDocument(), List.of(new Column("A", "s", 1)),
                List.of(new Column("Y", "led", 1)),
                new FlatCircuit(List.of(), List.of(new FlatNet("n", List.of(new FlatPinRef("ghost", 0)), 1, false))),
                Map.of("led", "n"), false);
        TruthTableRunner runner = new TruthTableRunner(DIRECT, DIRECT);
        Recorder recorder = new Recorder();

        runner.start(broken, recorder);

        assertThat(recorder.events).containsExactly("failed IllegalArgumentException");
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void afterDisposeNothingIsDeliveredAndNothingStarts() {
        ManualExecutor worker = new ManualExecutor();
        TruthTableRunner runner = new TruthTableRunner(worker, DIRECT);
        Recorder recorder = new Recorder();

        runner.start(identity(), recorder);
        runner.dispose();
        worker.runAll();

        assertThat(recorder.events).isEmpty();
        assertThatThrownBy(() -> runner.start(identity(), recorder)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theApplicationRunnerEvaluatesOnItsOwnThread() throws Exception {
        TruthTableRunner runner = TruthTableRunner.create();
        Recorder recorder = new Recorder();
        try {
            SwingUtilities.invokeAndWait(() -> runner.start(identity(), recorder));
            UiTestSupport.waitUntil(() -> {
                List<String> seen = new ArrayList<>();
                try {
                    SwingUtilities.invokeAndWait(() -> seen.addAll(recorder.events));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                return seen.contains("finished");
            });
        } finally {
            SwingUtilities.invokeAndWait(runner::dispose);
        }
        assertThat(recorder.table.rows()).hasSize(2);
    }

    @Test
    void anErrorInTheComputationIsReportedAndRethrown() {
        TruthTableRunner runner = new TruthTableRunner(DIRECT, DIRECT);
        Recorder recorder = new Recorder() {
            @Override
            public void progress(int percent) {
                throw new StackOverflowError();
            }
        };

        assertThatThrownBy(() -> runner.start(identity(), recorder)).isInstanceOf(StackOverflowError.class);

        assertThat(recorder.events).containsExactly("failed StackOverflowError");
        assertThat(runner.isRunning()).isFalse();
    }
}
