package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.machine.MachineDraft;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTestRun;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.waveform.WaveformFeatures;
import cl.almejo.vsim.ui.whatsnew.NewsTargets;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.DraftGuard;
import cl.almejo.vsim.ui.window.DraftGuards;
import cl.almejo.vsim.ui.window.EditorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
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
 * <li>The test is the machine's, not the tab's ({@link MachineTestUnderWay}): every view of the machine that tests
 * shows and edits the same draft; a switch between them asks nothing, and closing one of them while another tests
 * asks nothing either (the guard's {@link #draft()} and {@link #keptOutside}). Leaving the test in one view leaves
 * it in all.</li>
 * <li>When the tab moves to another window, the test goes on there with the same draft ({@link #traveller()}).</li>
 * <li>When the circuit's machine changes meanwhile (another tab, an undo in the circuit, the library), the bar says
 * so and «Aplicar» asks before replacing it. When its definition leaves the circuit the mode ends with it.</li>
 * </ul>
 * EDT only.
 */
final class MachineTestMode implements MachineTestSession, EditorActions.StepSource, DraftGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineTestMode.class);

    private final EditorView view;
    private final MachineCanvas canvas;
    private final MachineTestQuestions questions;
    private final MachineTestUnderWay test;
    private final MachineTestBar bar;
    private final Listeners<Runnable> listeners = new Listeners<>();
    private final List<Runnable> removals = new ArrayList<>();
    private final Runnable ended;
    /**
     * The draft as the table shows it: with the empty first test while it has none (the table's own
     * {@link MachineTestTableModel#shown}, so both see the same «Prueba 1»).
     */
    private StateMachine shown;
    private int chosenTest;
    private int chosenColumn;
    /** The lit state, worked out after each change (the diagram asks for it on every frame). */
    private Optional<ActiveMachineState> activeState = Optional.empty();
    /** Whether this view stopped testing (the test may go on in others). */
    private boolean left;

    /**
     * A test a view joins (another view of the machine tests it, or the tab moved to another window, review finding
     * 1): the test, with its draft and history, and the chosen test and column the view starts on.
     */
    record Carried(MachineTestUnderWay test, int chosenTest, int chosenColumn) {
    }

    private MachineTestMode(EditorView view, MachineCanvas canvas, Carried carried, MachineTestQuestions questions,
                            Runnable ended) {
        this.view = view;
        this.canvas = canvas;
        this.questions = questions;
        this.ended = ended;
        this.test = carried.test();
        this.chosenTest = carried.chosenTest();
        this.chosenColumn = carried.chosenColumn();
        this.bar = new MachineTestBar(() -> view.services().appearance().scheme(), this::exit, this::apply,
                this::keepAsItWas);
        recompute();
    }

    /**
     * Starts the test mode of {@code canvas}'s machine in {@code view}: the draft, the diagram's source, the steps of
     * Deshacer, the bar and border of {@code frame}, the test table ({@link MachineTestTable}) in the dock instead of
     * the wave strip, and the guard. The machine's test begins with it (one per machine: call it only when there is
     * none, else {@link #join}). Empty when the machine is gone. {@code ended} runs once when this view stops testing,
     * whichever way.
     */
    static Optional<MachineTestMode> start(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame,
                                           MachineTestQuestions questions, Runnable ended) {
        return canvas.standardSource().machine().map(real -> started(view, canvas, frame,
                new Carried(MachineTestUnderWay.begin(MachineTestsUnderWay.circuitOf(canvas.place()),
                        canvas.definitionId(), new MachineDraft(real)), 0, 0), questions, ended));
    }

    /**
     * As {@link #start}, on a draft {@code recovered} from a recovery copy (fix round 2 of hito 4): it is the draft's
     * one step, «recuperar el borrador», over the circuit's machine; no step when they are the same.
     */
    static Optional<MachineTestMode> recovered(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame,
                                               StateMachine recovered, MachineTestQuestions questions,
                                               Runnable ended) {
        return canvas.standardSource().machine().map(real -> {
            MachineDraft draft = new MachineDraft(real);
            if (!recovered.equals(real)) {
                draft.edited(recovered, Messages.text("machine.test.recoveredStep"));
            }
            return started(view, canvas, frame, new Carried(MachineTestUnderWay.begin(
                    MachineTestsUnderWay.circuitOf(canvas.place()), canvas.definitionId(), draft), 0, 0),
                    questions, ended);
        });
    }

    /**
     * Joins the machine's test {@code carried} (another view of it tests, or the tab moved to another window), as
     * {@link #start} but on its draft; empty when the machine or the test is gone.
     */
    static Optional<MachineTestMode> join(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame,
                                          Carried carried, MachineTestQuestions questions, Runnable ended) {
        if (carried.test().isOver()) {
            return Optional.empty();
        }
        return canvas.standardSource().machine().map(real -> started(view, canvas, frame, carried, questions,
                ended));
    }

    private static MachineTestMode started(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame,
                                           Carried carried, MachineTestQuestions questions, Runnable ended) {
        MachineTestMode mode = new MachineTestMode(view, canvas, carried, questions, ended);
        mode.install(frame);
        return mode;
    }

    private void install(MachineDiagramFrame frame) {
        test.joined(this);
        removals.add(canvas.useSource(new DraftSource()));
        removals.add(view.actions().stepsInstead(this));
        removals.add(frame.showTestMode(bar));
        BottomDock dock = view.bottomDock();
        MachineTestTable table = new MachineTestTable(this, () -> view.services().appearance().scheme());
        NewsTargets.register(table, MachineTestHelp.TARGET_ID, NewsTargets.Side.ABOVE); // the help bubble points at it
        removals.add(dock.contentInstead(table));
        removals.add(table::close); // runs first on leaving: the table stops following, then the strip comes back
        // in presentation mode the table has the strip's pin, and the bottom edge brings it up as it brings the strip
        WaveformFeatures.of(view).ifPresent(features -> removals.add(features.addPin(table.pinButton())));
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

    /** The machine's test this view shows (shared with its other views that test). */
    MachineTestUnderWay test() {
        return test;
    }

    /** What a view joining this test starts on: this view's chosen test and column. */
    Carried carried() {
        return new Carried(test, chosenTest, chosenColumn);
    }

    /** Shows the chosen test and column of {@code carried} (a view that joined: the one it came from). */
    void choose(Carried carried) {
        chosenTest = carried.chosenTest();
        chosenColumn = carried.chosenColumn();
        changed();
    }

    private MachineDraft theDraft() {
        return test.draft();
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
        return realMachine().map(theDraft()::diagramDiffersFrom).orElseGet(theDraft()::diagramChanged);
    }

    /** Whether the circuit's machine changed since the test started (or was last applied or saved). */
    boolean changedOutside() {
        return realMachine().filter(real -> !real.equals(theDraft().base())).isPresent();
    }

    // --- the session the table uses ---

    /**
     * The draft with the tests it really has: none while «Prueba 1» is untouched, so the table knows that test is
     * unwritten (review finding 5) and shows it itself.
     */
    @Override
    public StateMachine machine() {
        return theDraft().current();
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
        theDraft().edited(theDraft().current().withTests(after), stepName);
        test.changed();
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
        return theDraft().canUndo();
    }

    @Override
    public boolean canRedo() {
        return theDraft().canRedo();
    }

    @Override
    public Optional<String> undoName() {
        return theDraft().undoName().map(name -> Messages.format("machine.test.undoNamed", name));
    }

    @Override
    public Optional<String> redoName() {
        return theDraft().redoName().map(name -> Messages.format("machine.test.redoNamed", name));
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
        if (theDraft().canUndo()) {
            theDraft().undo();
            test.changed();
        }
    }

    @Override
    public void redo() {
        if (theDraft().canRedo()) {
            theDraft().redo();
            test.changed();
        }
    }

    // --- the guard the window asks ---

    @Override
    public Answer settle(Reason reason) {
        if (isOver() || test.isClosing()) {
            return Answer.GO_ON; // a close already decided asks nothing more (the save question may switch tabs)
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

    /** The machine's test: every view that tests it keeps the same draft, asked about once. */
    @Override
    public Object draft() {
        return test;
    }

    /** Whether a view that is not closing tests this machine too: the test goes on there, nothing to ask. */
    @Override
    public boolean keptOutside(Collection<DraftGuards> leaving) {
        return test.views().stream().anyMatch(other -> leaving.stream()
                .noneMatch(guards -> guards == other.view.draftGuards()));
    }

    @Override
    public Optional<String> titleNote() {
        return Optional.of(Messages.text("machine.test.tabNote"));
    }

    /**
     * A close of the window, the group or VSim (review finding 3): the question about a changed diagram is asked now,
     * as a close asks it, but nothing is written until the close sequence says so; a Cancelar at «¿Guardar cambios?»
     * then takes the written step back, and the test goes on as it was.
     */
    @Override
    public Optional<Closing> decideClosing() {
        if (isOver()) {
            return Optional.of(Closing.NOTHING);
        }
        if (!diagramChanged()) {
            return Optional.of(new DecidedClosing(this::writeTests));
        }
        int choice = questions.ask(view, Messages.format("machine.test.question.leave", machineName()),
                List.of(Messages.text("machine.test.option.apply"), Messages.text("machine.test.option.keep"),
                        Messages.text("machine.test.option.cancel")));
        if (choice == 0 && replacingIsAgreed()) {
            return Optional.of(new DecidedClosing(() -> execute(fitted(theDraft().current()), "command.applyTest")));
        }
        if (choice == 1) {
            return Optional.of(new DecidedClosing(this::writeTests));
        }
        return Optional.empty();
    }

    /** A close decided: {@code writing} puts it in the circuit; the circuit's history can be rolled back after. */
    private final class DecidedClosing implements Closing {
        private final Runnable writing;
        private Optional<History.Checkpoint> checkpoint = Optional.empty();

        private DecidedClosing(Runnable writing) {
            this.writing = writing;
        }

        @Override
        public void write() {
            if (isOver()) {
                return;
            }
            test.setClosing(true);
            checkpoint = Optional.of(canvas.place().history().checkpoint());
            writing.run();
            refreshBar();
        }

        @Override
        public void finish() {
            test.setClosing(false);
            end();
        }

        @Override
        public void takeBack() {
            test.setClosing(false);
            checkpoint.ifPresent(taken -> {
                if (!canvas.place().history().rollBackTo(taken)) {
                    // the history went elsewhere meanwhile: the written step stays, Deshacer takes it back
                    LOGGER.warn("Could not take back the test's step in {}", machineName());
                }
            });
            checkpoint = Optional.empty();
            if (!isOver()) {
                test.changed();
            }
        }
    }

    /**
     * The tab moves to another window: its new view there joins the test, with the same draft, history and choices,
     * and this view leaves it; nothing is written and nothing is asked. Should that view not take it (it is no
     * machine's tab) and no other view test, the test is left as «Salir de la prueba» leaves it, so the tests are
     * written rather than lost.
     */
    @Override
    public Optional<Consumer<EditorView>> traveller() {
        return Optional.of(arrived -> {
            if (isOver()) {
                return;
            }
            Optional<MachineMode> there = MachineMode.of(arrived);
            if (there.isEmpty()) {
                if (test.views().size() == 1) {
                    exit();
                } else {
                    leave();
                }
                return;
            }
            there.get().goOnTesting(carried()); // first: the test never has no view meanwhile
            leave();
        });
    }

    /** Said only when the draft holds something the circuit does not (review finding 11). */
    @Override
    public Optional<String> discardNotice() {
        boolean unwritten = realMachine()
                .map(real -> theDraft().diagramDiffersFrom(real) || theDraft().testsDifferFrom(real))
                .orElseGet(() -> theDraft().diagramChanged() || theDraft().testsChanged());
        return unwritten ? Optional.of(Messages.text("machine.test.discarded")) : Optional.empty();
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
        if (isOver()) {
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
        if (isOver()) {
            return true;
        }
        if (!replacingIsAgreed()) {
            return false;
        }
        StateMachine applied = fitted(theDraft().current());
        end();
        execute(applied, "command.applyTest");
        return true;
    }

    /** «Aplicar y guardar»: as {@link #apply()}, the mode going on from the applied machine. */
    private boolean applyStaying() {
        if (!replacingIsAgreed()) {
            return false;
        }
        execute(fitted(theDraft().current()), "command.applyTest");
        realMachine().ifPresent(theDraft()::rebased);
        test.changed();
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
        realMachine().filter(theDraft()::testsDifferFrom).ifPresent(real -> execute(
                real.withTests(MachineTests.fittedTo(real, theDraft().current().tests())), "command.saveTests"));
    }

    /** As {@link #writeTests()}, the mode going on: its base takes the written tests. */
    private void writeTestsStaying() {
        if (realMachine().filter(theDraft()::testsDifferFrom).isEmpty()) {
            return;
        }
        writeTests();
        realMachine().ifPresent(real -> theDraft().rebased(theDraft().base().withTests(real.tests())));
        test.changed();
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

    /** Ends the machine's test in every view that tests it, without writing anything: the draft goes. Runs once. */
    void end() {
        test.end();
    }

    /**
     * This view stops testing without writing anything (its tab goes or moves): the test goes on in the machine's
     * other views that test, and ends with the last.
     */
    void leave() {
        if (left) {
            return;
        }
        takeOut();
        test.left(this);
    }

    /** Everything this view's test mode put in the view goes; runs once. */
    void takeOut() {
        if (left) {
            return;
        }
        left = true;
        for (int index = removals.size() - 1; index >= 0; index--) {
            removals.get(index).run();
        }
        removals.clear();
        ended.run();
    }

    /** Whether this view stopped testing (the test ended, or the view left it). */
    boolean isOver() {
        return left || test.isOver();
    }

    // --- keeping up ---

    private Optional<StateMachine> realMachine() {
        return canvas.standardSource().machine();
    }

    private String machineName() {
        return MachineNaming.nameOf(canvas.place(), canvas.definitionId()).orElse(canvas.definitionId());
    }

    /** The draft changed, here or in another view of the test: this view follows. */
    void draftChanged() {
        if (!isOver()) {
            changed();
        }
    }

    /** The draft, a choice or the table changed: recomputed, and told to the diagram, the table and Deshacer. */
    private void changed() {
        recompute();
        refreshBar();
        listeners.fire(Runnable::run);
    }

    /** The table's machine, the choices kept in range, and the state the chosen column lights. */
    private void recompute() {
        StateMachine current = theDraft().current();
        shown = current.withTests(MachineTestTableModel.shown(current.tests()));
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
        if (!isOver()) {
            bar.show(diagramChanged(), changedOutside());
        }
    }

    /** The diagram's source while testing: the draft, each edit one draft step, the chosen column's state lit. */
    private final class DraftSource implements MachineSource {

        @Override
        public Optional<StateMachine> machine() {
            return Optional.of(theDraft().current());
        }

        @Override
        public boolean edit(UnaryOperator<StateMachine> change) {
            StateMachine current = theDraft().current();
            StateMachine changed = change.apply(current);
            if (changed.equals(current)) {
                return false;
            }
            theDraft().edited(changed, Messages.text("command.editMachine"));
            test.changed();
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
