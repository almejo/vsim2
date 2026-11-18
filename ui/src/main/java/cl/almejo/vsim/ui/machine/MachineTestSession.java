package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.StateMachine;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A machine tab in test mode, as the test table sees it (hito 4): the draft being tested, its tests, and which test
 * and column are chosen. The test mode implements it; the table only uses it, so it can be tested with a fake.
 * Every method runs on the EDT.
 */
public interface MachineTestSession {

    /** The draft machine being tested now, with its tests. */
    StateMachine machine();

    /**
     * Replaces the draft's tests with {@code edit} applied to them, as one step of the draft's history called
     * {@code stepName} (shown in Deshacer / Rehacer). The tests run again and the listeners are told.
     */
    void editTests(UnaryOperator<List<MachineTest>> edit, String stepName);

    /** The index of the chosen test in {@code machine().tests()}. */
    int chosenTest();

    /** Chooses the test at {@code index} (no undo step). */
    void chooseTest(int index);

    /** The index of the chosen column of the chosen test (the column cursor). */
    int chosenColumn();

    /** Moves the column cursor to {@code index} (no undo step). */
    void chooseColumn(int index);

    /**
     * Calls {@code listener} after every change of the draft, its tests or the choices.
     *
     * @return what removes the listener again
     */
    Runnable addListener(Runnable listener);
}
