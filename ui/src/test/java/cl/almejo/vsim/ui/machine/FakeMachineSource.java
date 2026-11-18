package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineDraft;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.Listeners;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * A machine source for tests, kept in memory as the test mode keeps its draft: edits go to a {@link MachineDraft} (one
 * step each), never to the circuit, and it doubles as the step source Deshacer and Rehacer follow meanwhile.
 */
final class FakeMachineSource implements MachineSource, EditorActions.StepSource {

    private final MachineDraft draft;
    private final Listeners<Runnable> listeners = new Listeners<>();
    private Optional<ActiveMachineState> activeState = Optional.empty();
    private int edits;

    FakeMachineSource(StateMachine base) {
        draft = new MachineDraft(base);
    }

    MachineDraft draft() {
        return draft;
    }

    /** How many edits changed the machine. */
    int edits() {
        return edits;
    }

    /** How many listeners follow it now. */
    int listenerCount() {
        return listeners.size();
    }

    void showState(Optional<ActiveMachineState> shown) {
        activeState = shown;
        changed();
    }

    @Override
    public Optional<StateMachine> machine() {
        return Optional.of(draft.current());
    }

    @Override
    public boolean edit(UnaryOperator<StateMachine> change) {
        StateMachine changed = change.apply(draft.current());
        if (changed.equals(draft.current())) {
            return false;
        }
        draft.edited(changed, "probar");
        edits++;
        changed();
        return true;
    }

    @Override
    public Optional<ActiveMachineState> activeState() {
        return activeState;
    }

    @Override
    public Runnable addChangeListener(Runnable listener) {
        return listeners.add(listener);
    }

    @Override
    public boolean canUndo() {
        return draft.canUndo();
    }

    @Override
    public boolean canRedo() {
        return draft.canRedo();
    }

    @Override
    public Optional<String> undoName() {
        return draft.undoName().map(name -> "Deshacer (prueba): " + name);
    }

    @Override
    public Optional<String> redoName() {
        return draft.redoName().map(name -> "Rehacer (prueba): " + name);
    }

    @Override
    public Optional<String> cannotUndoReason() {
        return Optional.of("En la prueba, Deshacer deshace solo lo que hiciste aquí");
    }

    @Override
    public void undo() {
        draft.undo();
        changed();
    }

    @Override
    public void redo() {
        draft.redo();
        changed();
    }

    @Override
    public Runnable addListener(Runnable listener) {
        return listeners.add(listener);
    }

    private void changed() {
        listeners.fire(Runnable::run);
    }
}
