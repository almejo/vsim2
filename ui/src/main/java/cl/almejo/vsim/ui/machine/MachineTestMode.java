package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.machine.MachineDraft;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTestRun;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.DraftGuard;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A machine's tab in test mode (spec 2026-11-14 §1 «Test mode»; plan hito 4, task 4): the diagram and the test table
 * read and edit a {@link MachineDraft} of the machine, never the circuit, until the user leaves.
 * <ul>
 * <li>The diagram's source ({@link MachineSource}) is the draft, and the lit state is the chosen test's state at the
 * chosen column, with the arrow taken; Deshacer and Rehacer walk the draft's steps
 * ({@link EditorActions.StepSource}), diagram and table together; the bottom dock shows the test table instead of
 * the wave strip; the diagram gets the test mode's border and bar ({@link MachineTestBar}).</li>
 * <li>Leaving: «Salir de la prueba» (the diagram unchanged) and «Dejar como estaba» keep the circuit's diagram and
 * write the tests, when they changed, in one step «Guardar las pruebas»; «Aplicar» puts the whole draft in the
 * circuit in one step «Aplicar la prueba». Nothing changed, no step.</li>
 * <li>It is a {@link DraftGuard} of its view: before a tab switch (only with a changed diagram), a close and a save
 * it settles the draft, asking through {@link MachineTestQuestions}.</li>
 * <li>With no tests, it shows an empty «Prueba 1» that is written only once a cell changes.</li>
 * <li>When the tab moves to another window, the test goes on there with the same draft ({@link #traveller()}).</li>
 * <li>When the circuit's machine changes meanwhile (another tab, an undo in the circuit, the library), the bar says
 * so and «Aplicar» asks before replacing it. When its definition leaves the circuit the mode ends with it.</li>
 * </ul>
 * EDT only.
 */
final class MachineTestMode implements MachineTestSession, EditorActions.StepSource, DraftGuard {

    private final EditorView view;
    private final MachineCanvas canvas;
    private final MachineTestQuestions questions;
    private final MachineDraft draft;
    private final MachineTest firstTest;
    private final MachineTestBar bar;
    private final Listeners<Runnable> listeners = new Listeners<>();
    private final List<Runnable> removals = new ArrayList<>();
    private final Runnable ended;
    /** The draft as the table sees it: with the empty first test while it has none. */
    private StateMachine shown;
    private int chosenTest;
    private int chosenColumn;
    /** The lit state, worked out after each change (the diagram asks for it on every frame). */
    private Optional<ActiveMachineState> activeState = Optional.empty();
    private boolean over;

    /**
     * A test mode on its way to the tab's new view in another window (review finding 1): its draft with its history,
     * the chosen test and column, and who asks its questions.
     */
    record Carried(MachineDraft draft, int chosenTest, int chosenColumn, MachineTestQuestions questions) {
    }

    private MachineTestMode(EditorView view, MachineCanvas canvas, Carried carried, Runnable ended) {
        this.view = view;
        this.canvas = canvas;
        this.questions = carried.questions();
        this.ended = ended;
        this.draft = carried.draft();
        this.chosenTest = carried.chosenTest();
        this.chosenColumn = carried.chosenColumn();
        this.firstTest = MachineTest.empty(MachineTests.freeId(List.of()), Messages.text("machine.test.firstName"),
                MachineTests.DEFAULT_COLUMNS);
        this.bar = new MachineTestBar(() -> view.services().appearance().scheme(), this::exit, this::apply,
                this::keepAsItWas);
        recompute();
    }

    /**
     * Starts the test mode of {@code canvas}'s machine in {@code view}: the draft, the diagram's source, the steps of
     * Deshacer, the bar and border of {@code frame}, the test table ({@link MachineTestTable}) in the dock instead of
     * the wave strip, and the guard. Empty when the machine is gone. {@code ended} runs once when the mode ends,
     * whichever way.
     */
    static Optional<MachineTestMode> start(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame,
                                           MachineTestQuestions questions, Runnable ended) {
        return canvas.standardSource().machine().map(real -> started(view, canvas, frame,
                new Carried(new MachineDraft(real), 0, 0, questions), ended));
    }

    /**
     * Goes on with a test mode {@code carried} from another window's tab (review finding 1), as {@link #start} but on
     * its draft; empty when the machine is gone.
     */
    static Optional<MachineTestMode> resume(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame,
                                            Carried carried, Runnable ended) {
        return canvas.standardSource().machine().map(real -> started(view, canvas, frame, carried, ended));
    }

    private static MachineTestMode started(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame,
                                           Carried carried, Runnable ended) {
        MachineTestMode mode = new MachineTestMode(view, canvas, carried, ended);
        mode.install(frame);
        return mode;
    }

    private void install(MachineDiagramFrame frame) {
        // one test mode per machine (review finding 4): it is this one's until it ends
        removals.add(MachineTestsUnderWay.add(MachineTestsUnderWay.circuitOf(canvas.place()), canvas.definitionId(),
                this));
        removals.add(canvas.useSource(new DraftSource()));
        removals.add(view.actions().stepsInstead(this));
        removals.add(frame.showTestMode(bar));
        BottomDock dock = view.bottomDock();
        MachineTestTable table = new MachineTestTable(this, () -> view.services().appearance().scheme());
        removals.add(dock.contentInstead(table));
        removals.add(table::close); // runs first on leaving: the table stops following, then the strip comes back
        if (!dock.isDockShowing()) {
            dock.showDock();
            removals.add(dock::hideDock);
        }
        // the circuit's machine may change meanwhile (another tab, an undo there, the library): the bar says so
        removals.add(canvas.addChangeListener(this::refreshBar));
        OpenDocument place = canvas.place();
        Runnable definitionLeft = this::end;
        place.addBodyRemovedListener(definitionLeft);
        removals.add(() -> place.removeBodyRemovedListener(definitionLeft));
        removals.add(view.draftGuards().add(this));
        refreshBar();
    }

    /** The tab that tests. */
    EditorView view() {
        return view;
    }

    /** The bar over the diagram. */
    MachineTestBar bar() {
        return bar;
    }

    /**
     * Whether the draft's diagram differs from the circuit's machine as it is now (not as it was when the test started
     * or was last applied: an undo in the circuit of «aplicar la prueba» makes it differ again, review finding 2).
     */
    boolean diagramChanged() {
        return realMachine().map(draft::diagramDiffersFrom).orElseGet(draft::diagramChanged);
    }

    /** Whether the circuit's machine changed since the test started (or was last applied or saved). */
    boolean changedOutside() {
        return realMachine().filter(real -> !real.equals(draft.base())).isPresent();
    }

    // --- the session the table uses ---

    @Override
    public StateMachine machine() {
        return shown;
    }

    @Override
    public void editTests(UnaryOperator<List<MachineTest>> edit, String stepName) {
        Objects.requireNonNull(edit, "edit");
        Objects.requireNonNull(stepName, "stepName");
        List<MachineTest> before = shown.tests();
        List<MachineTest> after = List.copyOf(edit.apply(before));
        if (after.equals(before)) {
            return;
        }
        draft.edited(draft.current().withTests(after), stepName);
        changed();
    }

    @Override
    public int chosenTest() {
        return chosenTest;
    }

    @Override
    public void chooseTest(int index) {
        int chosen = Math.clamp(index, 0, shown.tests().size() - 1);
        if (chosen != chosenTest) {
            chosenTest = chosen;
            changed();
        }
    }

    @Override
    public int chosenColumn() {
        return chosenColumn;
    }

    @Override
    public void chooseColumn(int index) {
        int chosen = Math.clamp(index, 0, shown.tests().get(chosenTest).columns().size() - 1);
        if (chosen != chosenColumn) {
            chosenColumn = chosen;
            changed();
        }
    }

    @Override
    public Runnable addListener(Runnable listener) {
        return listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // --- Deshacer and Rehacer: the draft's steps ---

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
        return draft.undoName().map(name -> Messages.format("machine.test.undoNamed", name));
    }

    @Override
    public Optional<String> redoName() {
        return draft.redoName().map(name -> Messages.format("machine.test.redoNamed", name));
    }

    @Override
    public Optional<String> cannotUndoReason() {
        return Optional.of(Messages.text("machine.test.cannotUndo"));
    }

    @Override
    public Optional<String> cannotRedoReason() {
        return Optional.of(Messages.text("machine.test.cannotRedo"));
    }

    @Override
    public void undo() {
        if (draft.canUndo()) {
            draft.undo();
            changed();
        }
    }

    @Override
    public void redo() {
        if (draft.canRedo()) {
            draft.redo();
            changed();
        }
    }

    // --- the guard the window asks ---

    @Override
    public Answer settle(Reason reason) {
        if (over) {
            return Answer.GO_ON;
        }
        return switch (reason) {
            case SWITCH -> diagramChanged() ? leaveAsked("machine.test.option.keepTesting") : Answer.GO_ON;
            case CLOSE -> {
                if (diagramChanged()) {
                    yield leaveAsked("machine.test.option.cancel");
                }
                exit();
                yield Answer.GO_ON;
            }
            case SAVE -> settleForSaving();
        };
    }

    @Override
    public Optional<String> titleNote() {
        return Optional.of(Messages.text("machine.test.tabNote"));
    }

    /**
     * The tab moves to another window: the test goes on in its new view there, with the same draft, history and
     * choices; nothing is written and nothing is asked. Should that view not take it (it is no machine's tab), the
     * mode is left as «Salir de la prueba» leaves it, so the tests are written rather than lost.
     */
    @Override
    public Optional<Consumer<EditorView>> traveller() {
        return Optional.of(arrived -> {
            if (over) {
                return;
            }
            Optional<MachineMode> there = MachineMode.of(arrived);
            if (there.isEmpty()) {
                exit();
                return;
            }
            Carried carried = new Carried(draft, chosenTest, chosenColumn, questions);
            end(); // this tab is going: it lets the machine go, so the new tab may test it
            there.get().goOnTesting(carried);
        });
    }

    @Override
    public Optional<String> discardNotice() {
        return Optional.of(Messages.text("machine.test.discarded"));
    }

    /** «Aplicar / Dejar como estaba / {@code lastKey}»: GO_ON once the mode was left one of the two ways. */
    private Answer leaveAsked(String lastKey) {
        int choice = questions.ask(view, Messages.format("machine.test.question.leave", machineName()),
                List.of(Messages.text("machine.test.option.apply"), Messages.text("machine.test.option.keep"),
                        Messages.text(lastKey)));
        if (choice == 0) {
            return apply() ? Answer.GO_ON : Answer.CANCEL;
        }
        if (choice == 1) {
            keepAsItWas();
            return Answer.GO_ON;
        }
        return Answer.CANCEL;
    }

    /**
     * Before Guardar: the tests are written first, without a question; with a changed diagram it asks «Aplicar y
     * guardar / Guardar sin el borrador / Cancelar». The mode goes on after the save.
     */
    private Answer settleForSaving() {
        if (!diagramChanged()) {
            writeTestsStaying();
            return Answer.GO_ON;
        }
        int choice = questions.ask(view, Messages.format("machine.test.question.save", machineName()),
                List.of(Messages.text("machine.test.option.applyAndSave"),
                        Messages.text("machine.test.option.saveWithoutDraft"),
                        Messages.text("machine.test.option.cancel")));
        if (choice == 0) {
            return applyStaying() ? Answer.GO_ON : Answer.CANCEL;
        }
        if (choice == 1) {
            writeTestsStaying();
            return Answer.GO_ON;
        }
        return Answer.CANCEL;
    }

    // --- the ways out ---

    /** «Salir de la prueba»: the tests written when they changed, the draft's diagram (if any) left behind. */
    void exit() {
        if (over) {
            return;
        }
        end();
        writeTests();
    }

    /** «Dejar como estaba»: the circuit's diagram stays; the tests are written when they changed. */
    void keepAsItWas() {
        exit();
    }

    /**
     * «Aplicar»: the whole draft goes to the circuit in one step, and the mode ends; when the circuit's machine
     * changed meanwhile it asks first («Reemplazar con el borrador / Seguir probando»). False when the user kept
     * testing.
     */
    boolean apply() {
        if (over) {
            return true;
        }
        if (!replacingIsAgreed()) {
            return false;
        }
        StateMachine applied = fitted(draft.current());
        end();
        execute(applied, "command.applyTest");
        return true;
    }

    /** «Aplicar y guardar»: as {@link #apply()}, the mode going on from the applied machine. */
    private boolean applyStaying() {
        if (!replacingIsAgreed()) {
            return false;
        }
        execute(fitted(draft.current()), "command.applyTest");
        realMachine().ifPresent(draft::rebased);
        changed();
        return true;
    }

    private boolean replacingIsAgreed() {
        if (!changedOutside()) {
            return true;
        }
        int choice = questions.ask(view, Messages.format("machine.test.question.replace", machineName()),
                List.of(Messages.text("machine.test.option.replace"),
                        Messages.text("machine.test.option.keepTesting")));
        return choice == 0;
    }

    /**
     * The draft's tests written to the circuit's machine in one step «Guardar las pruebas», when they differ from the
     * circuit's machine as it is now: what the table shows is what the machine keeps, also after an undo in the circuit
     * of a step that wrote them (review finding 2).
     */
    private void writeTests() {
        realMachine().filter(draft::testsDifferFrom).ifPresent(real -> execute(
                real.withTests(MachineTests.fittedTo(real, draft.current().tests())), "command.saveTests"));
    }

    /** As {@link #writeTests()}, the mode going on: its base takes the written tests. */
    private void writeTestsStaying() {
        if (realMachine().filter(draft::testsDifferFrom).isEmpty()) {
            return;
        }
        writeTests();
        realMachine().ifPresent(real -> draft.rebased(draft.base().withTests(real.tests())));
        changed();
    }

    /** {@code machine} with its tests fitted to its own pins. */
    private static StateMachine fitted(StateMachine machine) {
        return machine.withTests(MachineTests.fittedTo(machine, machine.tests()));
    }

    /**
     * One step of the circuit through the tab's place (so it belongs to this tab), named {@code descriptionKey};
     * nothing when the circuit's machine is that already. The status line names the wires a removed pin loosened.
     */
    private void execute(StateMachine machine, String descriptionKey) {
        if (realMachine().isEmpty() || realMachine().filter(machine::equals).isPresent()) {
            return;
        }
        OpenDocument place = canvas.place();
        Optional<Command> before = place.history().undoCommand();
        place.execute(new EditMachineCommand(canvas.definitionId(), machine, descriptionKey));
        place.history().undoCommand().filter(step -> before.isEmpty() || before.get() != step)
                .flatMap(step -> MachinePinRows.looseWiresNotice(MachinePinRows.removedPinWires(step)))
                .ifPresent(view::showNotice);
    }

    /** Ends the mode without writing anything: everything it put in the view goes, the draft with it. Runs once. */
    void end() {
        if (over) {
            return;
        }
        over = true;
        for (int index = removals.size() - 1; index >= 0; index--) {
            removals.get(index).run();
        }
        removals.clear();
        ended.run();
    }

    /** Whether the mode ended. */
    boolean isOver() {
        return over;
    }

    // --- keeping up ---

    private Optional<StateMachine> realMachine() {
        return canvas.standardSource().machine();
    }

    private String machineName() {
        return MachineNaming.nameOf(canvas.place(), canvas.definitionId()).orElse(canvas.definitionId());
    }

    /** The draft, a choice or the table changed: recomputed, and told to the diagram, the table and Deshacer. */
    private void changed() {
        recompute();
        refreshBar();
        listeners.fire(Runnable::run);
    }

    /** The table's machine, the choices kept in range, and the state the chosen column lights. */
    private void recompute() {
        StateMachine current = draft.current();
        shown = current.tests().isEmpty() ? current.withTests(List.of(firstTest)) : current;
        chosenTest = Math.clamp(chosenTest, 0, shown.tests().size() - 1);
        MachineTest test = shown.tests().get(chosenTest);
        chosenColumn = Math.clamp(chosenColumn, 0, test.columns().size() - 1);
        MachineTestRun.TestResult result = MachineTestRun.run(shown, test);
        if (result.stop().isPresent() || result.columns().isEmpty()) {
            activeState = Optional.empty();
            return;
        }
        MachineTestRun.ColumnResult column = result.columns().get(Math.min(chosenColumn,
                result.columns().size() - 1));
        activeState = Optional.of(new ActiveMachineState(column.stateId(), Optional.of(column.nextStateId()),
                column.transitionId()));
    }

    private void refreshBar() {
        if (!over) {
            bar.show(diagramChanged(), changedOutside());
        }
    }

    /** The diagram's source while testing: the draft, each edit one draft step, the chosen column's state lit. */
    private final class DraftSource implements MachineSource {

        @Override
        public Optional<StateMachine> machine() {
            return Optional.of(draft.current());
        }

        @Override
        public boolean edit(UnaryOperator<StateMachine> change) {
            StateMachine current = draft.current();
            StateMachine changed = change.apply(current);
            if (changed.equals(current)) {
                return false;
            }
            draft.edited(changed, Messages.text("command.editMachine"));
            changed();
            return true;
        }

        @Override
        public Optional<ActiveMachineState> activeState() {
            return activeState;
        }

        @Override
        public Runnable addChangeListener(Runnable listener) {
            return listeners.add(Objects.requireNonNull(listener, "listener"));
        }
    }
}
