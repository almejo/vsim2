package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.StateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A {@link MachineTestSession} for the test table's tests: holds a machine, applies every test edit to it at once and
 * remembers the step names, as the draft would.
 */
final class FakeTestSession implements MachineTestSession {

    private StateMachine machine;
    private int chosenTest;
    private int chosenColumn;
    private final List<String> steps = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    FakeTestSession(StateMachine machine) {
        this.machine = machine;
    }

    @Override
    public StateMachine machine() {
        return machine;
    }

    @Override
    public void editTests(UnaryOperator<List<MachineTest>> edit, String stepName) {
        machine = machine.withTests(edit.apply(machine.tests()));
        steps.add(stepName);
        fire();
    }

    /** An edit of the diagram (not of the tests), as the canvas would make it in test mode. */
    void replaceMachine(StateMachine newMachine) {
        machine = newMachine;
        fire();
    }

    @Override
    public int chosenTest() {
        return chosenTest;
    }

    @Override
    public void chooseTest(int index) {
        chosenTest = index;
        fire();
    }

    @Override
    public int chosenColumn() {
        return chosenColumn;
    }

    @Override
    public void chooseColumn(int index) {
        chosenColumn = index;
        fire();
    }

    @Override
    public Runnable addListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    List<String> steps() {
        return steps;
    }

    int listenerCount() {
        return listeners.size();
    }

    private void fire() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
