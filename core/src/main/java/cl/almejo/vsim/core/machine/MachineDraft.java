package cl.almejo.vsim.core.machine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * The draft of «Modo prueba»: a copy of a machine the student may change (diagram and tests) without touching the
 * circuit, with its own undo history (decision 1 of hito 4: one Ctrl+Z for the diagram and the test table, so it undoes
 * whatever was done last here). {@code base} is the real machine when the mode started (or was last
 * {@link #rebased}); {@code current} is the draft. Lives in memory only: autosave and Guardar write the real document,
 * never this (spec assumption 7). Not thread-safe: the editor uses it on the EDT only.
 */
public final class MachineDraft {

    /** How many steps the draft's history keeps; the oldest goes first. */
    public static final int MAXIMUM_STEPS = 200;

    /** A machine of the history and the name of the step that leaves it (a message key, e.g. «mover estado»). */
    private record Step(StateMachine machine, String name) {
    }

    private final Deque<Step> undoSteps = new ArrayDeque<>();
    private final Deque<Step> redoSteps = new ArrayDeque<>();
    private StateMachine base;
    private StateMachine current;

    public MachineDraft(StateMachine base) {
        this.base = Objects.requireNonNull(base, "base");
        this.current = base;
    }

    /** The real machine the draft is compared with. */
    public StateMachine base() {
        return base;
    }

    /** The draft as it is now. */
    public StateMachine current() {
        return current;
    }

    /**
     * The draft becomes {@code machine}, one step called {@code stepName} (what Deshacer names); clears the redo. An
     * edit that changes nothing is no step.
     */
    public void edited(StateMachine machine, String stepName) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(stepName, "stepName");
        if (machine.equals(current)) {
            return;
        }
        undoSteps.push(new Step(current, stepName));
        if (undoSteps.size() > MAXIMUM_STEPS) {
            undoSteps.removeLast();
        }
        redoSteps.clear();
        current = machine;
    }

    public boolean canUndo() {
        return !undoSteps.isEmpty();
    }

    public boolean canRedo() {
        return !redoSteps.isEmpty();
    }

    /** The name of the step {@link #undo} undoes; empty when there is none. */
    public Optional<String> undoName() {
        return Optional.ofNullable(undoSteps.peek()).map(Step::name);
    }

    /** The name of the step {@link #redo} redoes; empty when there is none. */
    public Optional<String> redoName() {
        return Optional.ofNullable(redoSteps.peek()).map(Step::name);
    }

    /** Undoes the last step; nothing when there is none. */
    public void undo() {
        Step step = undoSteps.poll();
        if (step == null) {
            return;
        }
        redoSteps.push(new Step(current, step.name()));
        current = step.machine();
    }

    /** Redoes the last undone step; nothing when there is none. */
    public void redo() {
        Step step = redoSteps.poll();
        if (step == null) {
            return;
        }
        undoSteps.push(new Step(current, step.name()));
        current = step.machine();
    }

    /** Whether the draft's diagram (anything but the tests) differs from the base: then leaving asks. */
    public boolean diagramChanged() {
        return !current.withTests(base.tests()).equals(base);
    }

    /** Whether the draft's tests differ from the base's: then leaving writes them, without asking. */
    public boolean testsChanged() {
        return !current.tests().equals(base.tests());
    }

    /**
     * Whether the draft's diagram (anything but the tests) differs from {@code machine}'s: the circuit's machine as it
     * is now, whatever happened to it since the base (an undo in the circuit of what was applied).
     */
    public boolean diagramDiffersFrom(StateMachine machine) {
        Objects.requireNonNull(machine, "machine");
        return !current.withTests(machine.tests()).equals(machine);
    }

    /**
     * Whether the draft's tests, fitted to {@code machine}'s pins ({@link MachineTests#fittedTo}), differ from
     * {@code machine}'s tests: what writing the tests to the circuit's machine as it is now would change.
     */
    public boolean testsDifferFrom(StateMachine machine) {
        Objects.requireNonNull(machine, "machine");
        return !MachineTests.fittedTo(machine, current.tests()).equals(machine.tests());
    }

    /**
     * The real machine is now {@code newBase} (the tests were written to it on Guardar, or it was applied): the draft
     * and its history stay, and {@link #diagramChanged} and {@link #testsChanged} compare with it.
     */
    public void rebased(StateMachine newBase) {
        base = Objects.requireNonNull(newBase, "newBase");
    }
}
