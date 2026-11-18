package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deshacer and Rehacer following another history while a step source is installed (plan 2026-11-14, hito 4 task 3:
 * the test mode's draft): their names, whether they are enabled and what they run come from it, and everything is the
 * circuit's again once it is removed.
 */
class EditorActionsStepsInsteadTest {

    private static final String REASON = "En la prueba, Deshacer deshace solo lo que hiciste aquí";

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;
    private EditorActions actions;
    private final FakeSteps steps = new FakeSteps();

    /** A history of names only: undo moves the last name to the redo list. */
    private static final class FakeSteps implements EditorActions.StepSource {
        final List<String> done = new ArrayList<>();
        final List<String> undone = new ArrayList<>();
        final Listeners<Runnable> listeners = new Listeners<>();
        Optional<String> reason = Optional.of(REASON);

        void make(String name) {
            done.add(name);
            undone.clear();
            listeners.fire(Runnable::run);
        }

        @Override
        public boolean canUndo() {
            return !done.isEmpty();
        }

        @Override
        public boolean canRedo() {
            return !undone.isEmpty();
        }

        @Override
        public Optional<String> undoName() {
            return done.isEmpty() ? Optional.empty() : Optional.of("Deshacer (prueba): " + done.getLast());
        }

        @Override
        public Optional<String> redoName() {
            return undone.isEmpty() ? Optional.empty() : Optional.of("Rehacer (prueba): " + undone.getLast());
        }

        @Override
        public Optional<String> cannotUndoReason() {
            return reason;
        }

        @Override
        public void undo() {
            undone.add(done.removeLast());
            listeners.fire(Runnable::run);
        }

        @Override
        public void redo() {
            done.add(undone.removeLast());
            listeners.fire(Runnable::run);
        }

        @Override
        public Runnable addListener(Runnable listener) {
            return listeners.add(listener);
        }
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        view = new EditorView(circuit, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        actions = view.actions();
        addABuffer("c1");
    }

    @AfterEach
    void close() {
        view.dispose();
        circuit.close();
    }

    private void addABuffer(String id) {
        circuit.execute(new AddElementsCommand(List.of(StandardComponentTypes.registry()
                .require(StandardComponentTypes.BUFFER).newComponent(new ElementId(id), new GridPoint(0, 0),
                        Rotation.R0, Map.of(), ""))));
    }

    private static String nameOf(Action action) {
        return String.valueOf(action.getValue(Action.NAME));
    }

    @Test
    void whileInstalledDeshacerAndRehacerNameRunAndFollowTheSourcesSteps() {
        actions.stepsInstead(steps);
        steps.make("mover estado");

        assertThat(actions.undo().isEnabled()).isTrue();
        assertThat(nameOf(actions.undo())).isEqualTo("Deshacer (prueba): mover estado");
        assertThat(actions.redo().isEnabled()).isFalse();
        assertThat(nameOf(actions.redo())).isEqualTo(Messages.text("action.redo"));

        perform(actions.undo());

        assertThat(steps.done).isEmpty();
        assertThat(circuit.document().component(new ElementId("c1"))).as("the circuit's step stays").isPresent();
        assertThat(actions.redo().isEnabled()).isTrue();
        assertThat(nameOf(actions.redo())).isEqualTo("Rehacer (prueba): mover estado");
        perform(actions.redo());
        assertThat(steps.done).containsExactly("mover estado");
    }

    @Test
    void deshacerIsDisabledWithItsReasonWhenTheSourceCannotUndo() {
        actions.stepsInstead(steps);

        assertThat(circuit.history().canUndo()).as("the circuit could").isTrue();
        assertThat(actions.undo().isEnabled()).isFalse();
        assertThat(nameOf(actions.undo())).isEqualTo(Messages.text("action.undo"));
        assertThat(actions.undo().getValue(Action.SHORT_DESCRIPTION)).isEqualTo(REASON);

        steps.make("mover estado");

        assertThat(actions.undo().isEnabled()).isTrue();
        assertThat(actions.undo().getValue(Action.SHORT_DESCRIPTION)).as("enabled: its name says it all").isNull();
    }

    @Test
    void aStepOfTheCircuitMeanwhileDoesNotChangeTheirNames() {
        actions.stepsInstead(steps);
        steps.make("mover estado");

        addABuffer("c2");

        assertThat(nameOf(actions.undo())).isEqualTo("Deshacer (prueba): mover estado");
    }

    @Test
    void removingItGivesTheCircuitsHistoryBack() {
        Runnable remove = actions.stepsInstead(steps);
        steps.make("mover estado");

        remove.run();

        assertThat(steps.listeners.size()).as("no longer followed").isZero();
        assertThat(nameOf(actions.undo())).isEqualTo("Deshacer agregar");
        assertThat(actions.undo().isEnabled()).isTrue();
        assertThat(actions.undo().getValue(Action.SHORT_DESCRIPTION)).isNull();
        perform(actions.undo());
        assertThat(circuit.document().component(new ElementId("c1"))).isEmpty();
        assertThat(steps.done).containsExactly("mover estado");
    }

    @Test
    void anOlderSourcesRemovalLeavesTheNewerOneInstalled() {
        FakeSteps newer = new FakeSteps();
        Runnable removeOlder = actions.stepsInstead(steps);
        actions.stepsInstead(newer);
        newer.make("borrar");

        removeOlder.run();

        assertThat(nameOf(actions.undo())).isEqualTo("Deshacer (prueba): borrar");
        assertThat(steps.listeners.size()).isZero();
    }

    @Test
    void withoutAReasonDisabledDeshacerKeepsItsUsualTooltip() {
        steps.reason = Optional.empty();
        actions.stepsInstead(steps);

        assertThat(actions.undo().isEnabled()).isFalse();
        assertThat(actions.undo().getValue(Action.SHORT_DESCRIPTION)).isNull();
    }
}
