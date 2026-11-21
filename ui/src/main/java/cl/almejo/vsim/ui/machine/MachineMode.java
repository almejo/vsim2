package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.PlaceBar;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The machine mode of an editor view (spec 2026-11-14 §2.4): the tab of a «Máquina de estados» is an ordinary component
 * tab of the machine's definition (tabs, window, toolbar, menus, history, «Valores de» and presentation mode work as
 * everywhere) that shows the machine's diagram ({@link MachineCanvas}) where the circuit panes go and its own palette
 * ({@link MachinePalette}) instead of the parts. The generated pins-only body never reaches the user: its panes leave
 * the view and every circuit action is disabled with its reason ({@link MachineActionPolicy}). Any place of a machine's
 * body, this tab's or one opened without it (the AI), refuses every circuit edit ({@link EditInsideCommand}). The
 * machine's own edits ({@link MachineEditing}) are each one {@link EditMachineCommand} of its definition, the only
 * edit a machine's place lets through; made through this tab's place, it is one undo step of the circuit that
 * belongs to this tab.
 */
public final class MachineMode {

    private final EditorView view;
    private final MachineCanvas canvas;
    private final MachineDiagramFrame frame;
    private final MachinePalette palette;
    private final MachineEditing editing;
    /** Whether «Animaciones» is on now: a revealed problem then blinks. */
    private final BooleanSupplier animations;
    private final Listeners<Runnable> testModeListeners = new Listeners<>();
    private MachineTestQuestions questions = MachineTestQuestions.DIALOG;
    /** Asks through whatever {@link #questions} is when the question comes (set after the test may have started). */
    private final MachineTestQuestions asking = (parent, message, options) -> questions.ask(parent, message, options);
    private Optional<MachineTestMode> testMode = Optional.empty();
    private Runnable stopLockingTheName = () -> { };
    private Runnable stopHoldingTheLibraryButton = () -> { };

    private MachineMode(EditorView view, MachineCanvas canvas, MachineDiagramFrame frame, MachinePalette palette,
                        MachineEditing editing, BooleanSupplier animations) {
        this.view = view;
        this.canvas = canvas;
        this.frame = frame;
        this.palette = palette;
        this.editing = editing;
        this.animations = animations;
    }


    /**
     * Puts {@code view} in machine mode when it shows the body of a machine; returns what puts it back (nothing to do
     * for any other view).
     */
    public static Runnable install(EditorView view) {
        return install(view, MachineStateSource.standard());
    }

    /** As above, the active state read through {@code stateSource} (tests). */
    static Runnable install(EditorView view, MachineStateSource stateSource) {
        Objects.requireNonNull(view, "view");
        Optional<String> machineId = machineDefinitionOf(view.openDocument());
        if (machineId.isEmpty()) {
            return () -> { };
        }
        OpenDocument place = view.openDocument();
        MachineSelection selection = new MachineSelection();
        MachineCanvas canvas = new MachineCanvas(place, machineId.get(), selection, stateSource,
                () -> view.services().appearance().scheme());
        MachinePalette palette = new MachinePalette();
        canvas.setTransferHandler(new MachineDropRefusal(view::showNotice));
        // the frame stays while the tab is a machine's: the test mode adds its border and bar to it
        MachineDiagramFrame frame = new MachineDiagramFrame(canvas, () -> view.services().appearance().scheme());
        Runnable showDiagram = view.showInsteadOfCanvas(frame, canvas, canvas);
        Runnable showPalette = view.leftColumn().showInsteadOfPalette(palette);
        Runnable enableCircuitActions = MachineActionPolicy.disableCircuitActions(view);
        // the hidden panes' snapshot listeners (status bar, run buttons, Problemas) follow the diagram's frames
        Runnable stopFeedingThePanes = canvas.addFrameListener(
                () -> view.panes().forEach(CircuitCanvas::refreshSnapshot));
        MachineEditing editing = MachineEditing.install(view, canvas, palette);
        Runnable showProbes = MachineWaveStrip.install(view, place, machineId.get(), canvas);
        MachineMode mode = new MachineMode(view, canvas, frame, palette, editing,
                () -> Animations.enabled(view.services().preferences()));
        palette.testButton().addActionListener(event -> mode.startTest());
        // the «Probar…» of Propiedades is the same entry, not a second path
        Consumer<Boolean> followTesting = editing.rows().enterTestWith(mode::startTest);
        mode.addTestModeListener(() -> followTesting.accept(mode.isTesting()));
        view.putClientProperty(MachineMode.class, mode);
        return () -> {
            // the tab goes (or leaves machine mode): it leaves the test, which goes on in the machine's other views
            // that test, else its draft goes, unwritten
            mode.testMode.ifPresent(MachineTestMode::leave);
            view.putClientProperty(MachineMode.class, null);
            for (Runnable undo : List.of(showProbes, editing::uninstall, stopFeedingThePanes, enableCircuitActions,
                    showPalette, showDiagram, canvas::dispose)) {
                undo.run();
            }
        };
    }

    /** The machine mode of {@code view}; empty for a circuit's tab or a user component's. */
    public static Optional<MachineMode> of(EditorView view) {
        return view.getClientProperty(MachineMode.class) instanceof MachineMode mode
                ? Optional.of(mode) : Optional.empty();
    }

    /**
     * Shows {@code problem} on the diagram: selects the states and arrows it is about, brings them into view and, with
     * «Animaciones» on, makes them blink ({@link MachineProblemNavigation}).
     */
    public void reveal(MachineProblem problem) {
        canvas.reveal(problem.finding(), animations.getAsBoolean());
    }

    /**
     * Starts the test mode of this tab's machine (hito 4): the diagram and a test table in the dock work on a draft
     * until the user leaves ({@link MachineTestMode}). Nothing when it is on already or the machine is gone. One test
     * per machine (review finding 4): when another view tests this machine, this one joins its test, on the same
     * draft (fix round 2).
     */
    public void startTest() {
        if (testMode.isPresent()) {
            return;
        }
        Optional<MachineTestUnderWay> underWay = testUnderWay();
        if (underWay.isPresent()) {
            joinTheTest(underWay.get());
            return;
        }
        testMode = MachineTestMode.start(view, canvas, frame, asking, this::testEnded);
        followTheTestMode();
    }

    /**
     * Joins the test of this tab's machine when another view tests it (a new view of a machine being tested tests
     * from the start, fix round 2 of hito 4); nothing otherwise.
     */
    void joinTheTestUnderWay() {
        if (testMode.isEmpty()) {
            testUnderWay().ifPresent(this::joinTheTest);
        }
    }

    private Optional<MachineTestUnderWay> testUnderWay() {
        return MachineTestsUnderWay.of(MachineTestsUnderWay.circuitOf(canvas.place()), canvas.definitionId())
                .filter(test -> !test.isOver());
    }

    /** Joins {@code test}, starting on the chosen test and column of the first view that tests it. */
    private void joinTheTest(MachineTestUnderWay test) {
        goOnTesting(test.views().stream().findFirst().map(MachineTestMode::carried)
                .orElseGet(() -> new MachineTestMode.Carried(test, 0, 0)));
    }

    /**
     * Joins the test {@code carried} (another view of the machine tests it, or this tab's old view in another window
     * carried it when the group moved, review finding 1): its draft, history and choices. When this tab tests it
     * already, it only takes the choices. Nothing when this tab tests another one or the machine is gone.
     */
    void goOnTesting(MachineTestMode.Carried carried) {
        if (testMode.isPresent()) {
            testMode.filter(mode -> mode.test() == carried.test()).ifPresent(mode -> mode.choose(carried));
            return;
        }
        testMode = MachineTestMode.join(view, canvas, frame, carried, asking, this::testEnded);
        followTheTestMode();
    }

    private void followTheTestMode() {
        testMode.ifPresent(started -> {
            palette.testButton().setEnabled(false);
            palette.testButton().setToolTipText(Messages.text("machine.test.alreadyTesting"));
            stopLockingTheName = editing.rows().lockMachineName(Messages.text("machine.test.renameLater"));
            stopHoldingTheLibraryButton = PlaceBar.of(view)
                    .map(bar -> bar.holdLibraryButton(Messages.text("machine.test.libraryLater")))
                    .orElse(() -> { });
            testModeListeners.fire(Runnable::run);
        });
    }

    private void testEnded() {
        testMode = Optional.empty();
        palette.testButton().setEnabled(true);
        palette.testButton().setToolTipText(Messages.text("machine.palette.testHint"));
        stopLockingTheName.run();
        stopHoldingTheLibraryButton.run();
        testModeListeners.fire(Runnable::run);
    }

    /** The test mode while it is on. */
    Optional<MachineTestMode> testMode() {
        return testMode;
    }

    /** Whether the tab is in test mode. */
    public boolean isTesting() {
        return testMode.isPresent();
    }

    /** Runs {@code listener} each time the test mode starts or ends; returns its removal. */
    public Runnable addTestModeListener(Runnable listener) {
        return testModeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Asks the test mode's questions through {@code replacement} (tests answer with a script). */
    void askWith(MachineTestQuestions replacement) {
        questions = Objects.requireNonNull(replacement, "replacement");
    }

    /** The diagram shown in the centre. */
    public MachineCanvas canvas() {
        return canvas;
    }

    /** The palette shown on the left. */
    public MachinePalette palette() {
        return palette;
    }

    /** The diagram's gestures. */
    MachineTool tool() {
        return editing.tool();
    }

    /** The inline editor of names and conditions. */
    MachineInlineEditor inlineEditor() {
        return editing.inlineEditor();
    }

    /** The rows the right panel's «Propiedades» shows. */
    MachineInspectorRows inspectorRows() {
        return editing.rows();
    }

    /** What is selected in the diagram (the canvas's). */
    public MachineSelection selection() {
        return canvas.selection();
    }

    /** The machine definition {@code place} shows the body of, when it is a machine's body place. */
    private static Optional<String> machineDefinitionOf(OpenDocument place) {
        Optional<OpenDocument> circuit = place.parent();
        Optional<String> definitionId = place.definitionId();
        if (circuit.isEmpty() || definitionId.isEmpty()) {
            return Optional.empty();
        }
        return circuit.get().document().definition(definitionId.get())
                .filter(ComponentDefinition::isMachine)
                .map(ComponentDefinition::id);
    }
}
