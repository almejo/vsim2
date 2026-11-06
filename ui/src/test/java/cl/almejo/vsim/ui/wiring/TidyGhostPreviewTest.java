package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
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

/** The dialog's preview plans drawn as a ghost in the canvas: shown, replaced, cleared on every way out. */
class TidyGhostPreviewTest {

    private static final TidyPlanner.Results IGNORE = new TidyPlanner.Results() {
        @Override
        public void accept(CircuitTidyPlan plan) {
        }

        @Override
        public void failed(String message) {
        }
    };

    private final Deque<Runnable> edtQueue = new ArrayDeque<>();
    private final List<String> observations = new ArrayList<>();
    private EditorFixture fixture;
    private OpenDocument openDocument;
    private EditorView view;
    private TidyWiresFeatures features;
    private Component gate;
    private boolean planningFails;

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

    /** A plan that moves the gate to the right, or throws when {@link #planningFails}. */
    private Optional<CircuitTidyPlan> movesTheGateRight(CircuitDocument snapshot) {
        if (planningFails) {
            throw new IllegalStateException("boom");
        }
        CircuitDocument after = snapshot.copy();
        after.replace(gate.withPosition(new GridPoint(10, -8)));
        return Optional.of(new CircuitTidyPlan(after, 1, 0, Set.of(), TidyPlan.EMPTY));
    }

    private TidyRunner runner() {
        return new TidyRunner(Runnable::run, edtQueue::add,
                (snapshot, registry, scope, options, cancelled, progress) -> movesTheGateRight(snapshot));
    }

    private void openDialogDoing(Consumer<TidyPlanner> whileOpen) {
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> {
            whileOpen.accept(planner);
            return Optional.empty();
        }, runner(), runner(), (milliseconds, task) -> () -> { }, (parent, onCancel) -> null);
        features.tidyWithDialog();
    }

    private void drainEdt() {
        while (!edtQueue.isEmpty()) {
            edtQueue.poll().run();
        }
    }

    private void recompute(TidyPlanner planner) {
        planner.plan(CircuitTidyOptions.DEFAULTS, false, IGNORE);
    }

    @Test
    void aDeliveredPreviewPlanShowsItsGhostWhileTheDialogIsOpen() {
        openDialogDoing(planner -> {
            recompute(planner);
            observations.add("before delivery " + features.ghost().isShowing());
            drainEdt();
            observations.add("after delivery " + features.ghost().isShowing());
            observations.add("ghost " + features.ghost().ghostIds());
        });

        assertThat(observations).containsExactly("before delivery false", "after delivery true",
                "ghost [" + gate.id() + "]");
    }

    @Test
    void theGhostIsClearedWhenTheDialogCloses() {
        openDialogDoing(planner -> {
            recompute(planner);
            drainEdt();
            observations.add("open " + features.ghost().isShowing());
        });

        assertThat(observations).containsExactly("open true");
        assertThat(features.ghost().isShowing()).isFalse();
    }

    @Test
    void aNewRecomputationClearsTheOldGhostFirst() {
        openDialogDoing(planner -> {
            recompute(planner);
            drainEdt();
            observations.add("first " + features.ghost().isShowing());
            recompute(planner);
            observations.add("recomputing " + features.ghost().isShowing());
            drainEdt();
            observations.add("second " + features.ghost().isShowing());
        });

        assertThat(observations).containsExactly("first true", "recomputing false", "second true");
    }

    @Test
    void aFailedPlanningShowsNoGhost() {
        planningFails = true;
        openDialogDoing(planner -> {
            recompute(planner);
            drainEdt();
            observations.add("failed " + features.ghost().isShowing());
        });

        assertThat(observations).containsExactly("failed false");
    }

    @Test
    void theOverlayIsRemovedWhenTheViewIsDisposed() {
        features = new TidyWiresFeatures(view, (parent, hasSelection, initial, planner) -> Optional.empty());
        int withOverlay = view.canvas().overlayCount();

        features.dispose();

        assertThat(view.canvas().overlayCount()).isEqualTo(withOverlay - 1);
    }
}
