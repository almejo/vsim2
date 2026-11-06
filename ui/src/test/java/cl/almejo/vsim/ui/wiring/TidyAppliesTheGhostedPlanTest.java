package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Ordenar» in the dialog applies the very plan its ghost showed (no second computation), and a change of the circuit
 * while the dialog is open takes the ghost away and computes it again, once per burst of changes.
 */
class TidyAppliesTheGhostedPlanTest {

    private static final CircuitTidyOptions OPTIONS = CircuitTidyOptions.ALL;

    private final Deque<Runnable> edtQueue = new ArrayDeque<>();
    private final List<Runnable> delayedTasks = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();
    private EditorFixture fixture;
    private OpenDocument openDocument;
    private EditorView view;
    private TidyWiresFeatures features;
    private Component gate;
    private int computations;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(AppPreferences.inMemory());
        openDocument = fixture.openDocument();
        view = fixture.view();
        gate = place(openDocument, StandardComponentTypes.AND, -15, -8, Map.of());
    }

    @AfterEach
    void close() {
        features.dispose();
        fixture.close();
    }

    /**
     * Every computation moves the gate 10 units further right than the one before, so a second computation would
     * leave the gate somewhere else than the ghost of the first one.
     */
    private Optional<CircuitTidyPlan> movesTheGateFurtherEachTime(CircuitDocument snapshot) {
        computations++;
        CircuitDocument after = snapshot.copy();
        after.replace(gate.withPosition(new GridPoint(10 * computations, -8)));
        return Optional.of(new CircuitTidyPlan(after, 1, 0, Set.of(), TidyPlan.EMPTY));
    }

    private TidyRunner runner() {
        return new TidyRunner(Runnable::run, edtQueue::add,
                (snapshot, registry, scope, options, cancelled, progress) -> movesTheGateFurtherEachTime(snapshot));
    }

    /** Opens the dialog, does {@code whileOpen} with a real panel, then presses «Ordenar». */
    private void openDialogAndApply(Consumer<TidyWiresPanel> whileOpen) {
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> {
            TidyWiresPanel panel = new TidyWiresPanel(false, OPTIONS, planner);
            whileOpen.accept(panel);
            TidyChoice choice = panel.choice();
            panel.dispose();
            return Optional.of(choice);
        }, runner(), runner(), (milliseconds, task) -> {
            if (milliseconds == TidyWiresFeatures.PROGRESS_DELAY_MILLISECONDS) {
                return () -> { };
            }
            delayedTasks.add(task);
            return () -> delayedTasks.remove(task);
        }, (parent, onCancel) -> null);
        features.tidyWithDialog();
        drainEdt();
    }

    private void drainEdt() {
        while (!edtQueue.isEmpty()) {
            edtQueue.poll().run();
        }
    }

    private void runDelayedTasks() {
        for (Runnable task : List.copyOf(delayedTasks)) {
            delayedTasks.remove(task);
            task.run();
        }
    }

    private void addAWire() {
        openDocument.execute(new AddElementsCommand(List.of(
                new Wire(openDocument.document().newWireId(), new GridPoint(40, 40), new GridPoint(48, 40)))));
    }

    private int gateX() {
        return openDocument.document().component(gate.id()).orElseThrow().position().x();
    }

    @Test
    void theAppliedPlanIsTheGhostedPlan() {
        openDialogAndApply(panel -> {
            drainEdt();
            observations.add("ghost " + features.ghost().isShowing());
        });

        assertThat(observations).containsExactly("ghost true");
        assertThat(computations).isEqualTo(1);
        assertThat(gateX()).isEqualTo(10);
        assertThat(openDocument.history().canUndo()).isTrue();
    }

    @Test
    void aChangeWhileTheDialogIsOpenRecomputesTheGhost() {
        openDialogAndApply(panel -> {
            drainEdt();
            addAWire();
            observations.add("after the change " + features.ghost().isShowing());
            runDelayedTasks();
            observations.add("summary " + panel.summaryText());
            drainEdt();
            observations.add("recomputed " + features.ghost().isShowing() + " " + computations);
        });

        assertThat(observations).containsExactly("after the change false", "summary Calculando…",
                "recomputed true 2");
        assertThat(computations).isEqualTo(2);
        assertThat(gateX()).isEqualTo(20);
    }

    @Test
    void aBurstOfChangesRecomputesOnce() {
        openDialogAndApply(panel -> {
            drainEdt();
            addAWire();
            addAWire();
            addAWire();
            observations.add("pending " + delayedTasks.size());
            runDelayedTasks();
            drainEdt();
        });

        assertThat(observations).containsExactly("pending 1");
        assertThat(computations).isEqualTo(2);
    }

    @Test
    void ordenarRightAfterAChangeComputesAgainOnTheChangedCircuit() {
        openDialogAndApply(panel -> {
            drainEdt();
            addAWire();
        });

        assertThat(computations).isEqualTo(2);
        assertThat(gateX()).isEqualTo(20);
    }

    @Test
    void theDocumentIsNoLongerWatchedOnceTheDialogCloses() {
        openDialogAndApply(panel -> drainEdt());
        int pending = delayedTasks.size();

        addAWire();

        assertThat(delayedTasks).hasSize(pending);
        assertThat(computations).isEqualTo(1);
    }
}
