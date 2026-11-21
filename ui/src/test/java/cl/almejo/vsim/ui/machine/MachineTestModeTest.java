package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.machine.MachinePinEdits;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.DraftGuard;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine's test mode (plan 2026-11-14, hito 4 task 4): a draft that never touches the circuit while it lasts, left
 * with «Salir de la prueba», «Aplicar» (one circuit step) or «Dejar como estaba» (the tests kept in one step), and
 * asked once before a save, a close or a tab switch.
 */
class MachineTestModeTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;
    private MachineCanvas canvas;
    private StateMachine real;
    private Optional<Command> lastCircuitStep;
    private final ScriptedQuestions questions = new ScriptedQuestions();

    /** Answers the questions with the scripted option texts, and records what was asked. */
    private static final class ScriptedQuestions implements MachineTestQuestions {
        private final Deque<String> answers = new ArrayDeque<>();
        private final List<String> asked = new ArrayList<>();
        private final List<List<String>> options = new ArrayList<>();

        @Override
        public int ask(java.awt.Component parent, String message, List<String> offered) {
            asked.add(message);
            options.add(offered);
            String answer = answers.poll();
            return answer == null ? -1 : offered.indexOf(answer);
        }
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(), appearance,
                preferences, new ComponentLibrary(), List.of(new MachineModeExtension()));
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        mode = MachineMode.of(view).orElseThrow();
        mode.askWith(questions);
        canvas = mode.canvas();
        canvas.setBounds(0, 0, 900, 600);
        real = circuitsMachine();
        lastCircuitStep = document.history().undoCommand();
    }

    @AfterEach
    void close() {
        view.dispose();
        place.close();
        document.close();
    }

    private StateMachine circuitsMachine() {
        return document.document().definition(MachineFixtures.DEFINITION_ID)
                .flatMap(ComponentDefinition::machine).orElseThrow();
    }

    private MachineTestMode start() {
        mode.startTest();
        return mode.testMode().orElseThrow();
    }

    private void assertTheCircuitIsUntouched() {
        assertThat(circuitsMachine()).isEqualTo(real);
        assertThat(document.history().undoCommand()).isEqualTo(lastCircuitStep);
    }

    private static StateMachine withS1Initial(StateMachine machine) {
        return machine.withInitialStateId(Optional.of("s1"));
    }

    private String inputId() {
        return real.pins(MachinePinEdits.Side.INPUT).getFirst().id();
    }

    private String outputId() {
        return real.pins(MachinePinEdits.Side.OUTPUT).getFirst().id();
    }

    /** Sets x = 1 in the first column of the chosen test: one draft step. */
    private void setAnInput(MachineTestSession session) {
        session.editTests(tests -> {
            List<MachineTest> changed = new ArrayList<>(tests);
            changed.set(0, MachineTests.withInput(tests.getFirst(), 0, inputId(), true));
            return changed;
        }, "cambiar x");
    }

    private static String nameOf(Action action) {
        return String.valueOf(action.getValue(Action.NAME));
    }

    @Test
    void anEditedDiagramLeavesTheCircuitsMachineAsItWas() {
        MachineTestMode test = start();

        canvas.edit(MachineTestModeTest::withS1Initial);

        assertThat(canvas.machine().orElseThrow().initialStateId()).contains("s1");
        assertTheCircuitIsUntouched();
        assertThat(test.bar().message()).contains("cambiaste el diagrama");
        assertThat(test.bar().applyButton().isShowing() || test.bar().applyButton().isVisible()).isTrue();
        assertThat(test.bar().exitButton().isVisible()).isFalse();
    }

    @Test
    void aplicarIsOneCircuitStepAndItsUndoBringsEverythingBack() {
        MachineTestMode test = start();
        canvas.edit(MachineTestModeTest::withS1Initial);
        setAnInput(test);

        test.bar().applyButton().doClick(0);

        assertThat(mode.testMode()).isEmpty();
        assertThat(circuitsMachine().initialStateId()).contains("s1");
        assertThat(circuitsMachine().tests()).hasSize(1);
        assertThat(nameOf(view.actions().undo())).startsWith("Deshacer aplicar la prueba");
        document.history().undo();
        assertThat(circuitsMachine()).isEqualTo(real);
        assertThat(document.history().undoCommand()).isEqualTo(lastCircuitStep);
    }

    @Test
    void dejarComoEstabaKeepsTheDiagramAndSavesTheTestsInOneStep() {
        MachineTestMode test = start();
        canvas.edit(MachineTestModeTest::withS1Initial);
        setAnInput(test);

        test.bar().keepButton().doClick(0);

        assertThat(mode.testMode()).isEmpty();
        assertThat(circuitsMachine().withTests(List.of())).isEqualTo(real);
        assertThat(circuitsMachine().tests()).hasSize(1);
        assertThat(circuitsMachine().tests().getFirst().columns().getFirst().input(inputId())).isTrue();
        assertThat(nameOf(view.actions().undo())).startsWith("Deshacer guardar las pruebas");
        document.history().undo();
        assertThat(document.history().undoCommand()).isEqualTo(lastCircuitStep);
    }

    @Test
    void leavingWithNothingChangedAddsNoStepAndPutsTheCircuitBack() {
        MachineTestMode test = start();

        test.bar().exitButton().doClick(0);

        assertThat(mode.testMode()).isEmpty();
        assertTheCircuitIsUntouched();
        canvas.edit(MachineTestModeTest::withS1Initial);
        assertThat(circuitsMachine().initialStateId()).contains("s1"); // the circuit's source is back
    }

    @Test
    void leavingWithOnlyChangedTestsSavesThemWithoutAsking() {
        MachineTestMode test = start();
        setAnInput(test);

        assertThat(test.bar().exitButton().isVisible()).isTrue();
        test.bar().exitButton().doClick(0);

        assertThat(questions.asked).isEmpty();
        assertThat(circuitsMachine().tests()).hasSize(1);
        assertThat(nameOf(view.actions().undo())).startsWith("Deshacer guardar las pruebas");
    }

    @Test
    void theFirstEmptyTestIsShownButNeverWritten() {
        MachineTestMode test = start();

        assertThat(test.machine().tests()).isEmpty(); // unwritten: the table shows it
        MachineTestTableModel table = MachineTestTableModel.of(test.machine(), test.chosenTest(),
                test.chosenColumn());
        assertThat(table.unwritten()).isTrue();
        MachineTest first = table.tests().getFirst();
        assertThat(first.name()).isEqualTo("Prueba 1");
        assertThat(first.columns()).hasSize(MachineTests.DEFAULT_COLUMNS);
        assertThat(canvas.machine().orElseThrow().tests()).isEmpty();

        test.bar().exitButton().doClick(0);

        assertThat(circuitsMachine().tests()).isEmpty();
        assertTheCircuitIsUntouched();
    }

    @Test
    void deshacerUndoesTheDraftWithItsOwnName() {
        MachineTestMode test = start();
        assertThat(view.actions().undo().isEnabled()).isFalse();
        assertThat(view.actions().undo().getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo("En la prueba, Deshacer deshace solo lo que hiciste aquí");

        setAnInput(test);
        canvas.edit(MachineTestModeTest::withS1Initial);
        assertThat(nameOf(view.actions().undo())).isEqualTo("Deshacer (prueba): editar la máquina");

        perform(view.actions().undo());
        assertThat(canvas.machine().orElseThrow().initialStateId()).contains("s0");
        assertThat(nameOf(view.actions().undo())).isEqualTo("Deshacer (prueba): cambiar x");
        perform(view.actions().undo());
        assertThat(test.machine().tests()).isEmpty(); // back to the unwritten «Prueba 1»
        assertTheCircuitIsUntouched();
    }

    @Test
    void choosingAColumnLightsItsStateAndTheArrowTaken() {
        MachineTestMode test = start();
        setAnInput(test); // x = 1 in column 0: S0 takes t1 to S1

        test.chooseColumn(0);
        assertThat(canvas.activeState()).contains(new ActiveMachineState("s0", Optional.of("s1"),
                Optional.of("t1")));
        test.chooseColumn(1);
        assertThat(canvas.activeState().orElseThrow().stateId()).isEqualTo("s1");
        assertThat(test.chosenColumn()).isEqualTo(1);
    }

    @Test
    void switchingTabsAsksOnlyWhenTheDiagramChangedAndSeguirProbandoStays() {
        MachineTestMode test = start();
        setAnInput(test);

        assertThat(view.draftGuards().settle(DraftGuard.Reason.SWITCH)).isEqualTo(DraftGuard.Answer.GO_ON);
        assertThat(questions.asked).isEmpty();
        assertThat(mode.testMode()).isPresent();

        canvas.edit(MachineTestModeTest::withS1Initial);
        questions.answers.add("Seguir probando");
        assertThat(view.draftGuards().settle(DraftGuard.Reason.SWITCH)).isEqualTo(DraftGuard.Answer.CANCEL);
        assertThat(questions.options.getLast()).containsExactly("Aplicar", "Dejar como estaba", "Seguir probando");
        assertThat(mode.testMode()).contains(test);
        assertTheCircuitIsUntouched();
    }

    @Test
    void closingAsksOnceAndCancelarKeepsTheMode() {
        MachineTestMode test = start();
        canvas.edit(MachineTestModeTest::withS1Initial);

        questions.answers.add("Cancelar");
        assertThat(view.draftGuards().settle(DraftGuard.Reason.CLOSE)).isEqualTo(DraftGuard.Answer.CANCEL);
        assertThat(questions.asked).hasSize(1);
        assertThat(mode.testMode()).contains(test);

        questions.answers.add("Aplicar");
        assertThat(view.draftGuards().settle(DraftGuard.Reason.CLOSE)).isEqualTo(DraftGuard.Answer.GO_ON);
        assertThat(questions.asked).hasSize(2);
        assertThat(mode.testMode()).isEmpty();
        assertThat(circuitsMachine().initialStateId()).contains("s1");
        assertThat(view.draftGuards().isEmpty()).isTrue();
    }

    @Test
    void savingWritesTheTestsFirstWithoutAskingAndStaysInTheMode() {
        MachineTestMode test = start();
        setAnInput(test);

        assertThat(view.draftGuards().settle(DraftGuard.Reason.SAVE)).isEqualTo(DraftGuard.Answer.GO_ON);

        assertThat(questions.asked).isEmpty();
        assertThat(circuitsMachine().tests()).hasSize(1);
        assertThat(mode.testMode()).contains(test);
        assertThat(test.bar().message()).doesNotContain("cambió afuera");
    }

    @Test
    void savingWithAChangedDiagramAsksAndGuardarSinElBorradorKeepsIt() {
        MachineTestMode test = start();
        canvas.edit(MachineTestModeTest::withS1Initial);

        questions.answers.add("Guardar sin el borrador");
        assertThat(view.draftGuards().settle(DraftGuard.Reason.SAVE)).isEqualTo(DraftGuard.Answer.GO_ON);

        assertThat(questions.options.getLast())
                .containsExactly("Aplicar y guardar", "Guardar sin el borrador", "Cancelar");
        assertThat(circuitsMachine().initialStateId()).contains("s0");
        assertThat(canvas.machine().orElseThrow().initialStateId()).contains("s1");
        assertThat(mode.testMode()).contains(test);
    }

    @Test
    void theMachineChangedElsewhereShowsTheNoticeAndAplicarAsks() {
        MachineTestMode test = start();
        canvas.edit(MachineTestModeTest::withS1Initial);
        // the same machine edited in another tab
        document.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID,
                real.withInitialStateId(Optional.of("s2"))));

        assertThat(test.bar().message()).contains("La máquina cambió afuera");
        questions.answers.add("Seguir probando");
        test.bar().applyButton().doClick(0);
        assertThat(questions.options.getLast()).containsExactly("Reemplazar con el borrador", "Seguir probando");
        assertThat(mode.testMode()).contains(test);
        assertThat(circuitsMachine().initialStateId()).contains("s2");

        questions.answers.add("Reemplazar con el borrador");
        test.bar().applyButton().doClick(0);
        assertThat(mode.testMode()).isEmpty();
        assertThat(circuitsMachine().initialStateId()).contains("s1");
    }

    @Test
    void theDefinitionLeavingEndsTheModeAndItsTabNoteSaysTheDraftWent() {
        MachineTestMode test = start();
        assertThat(view.draftGuards().titleNote()).contains(" · prueba");
        // review finding 11: nothing changed, nothing is said to be discarded
        assertThat(view.draftGuards().discardNotice()).isEmpty();
        setAnInput(test);
        assertThat(view.draftGuards().discardNotice()).contains("Se descartó el borrador de la prueba");

        document.history().undo(); // the copy
        document.history().undo(); // the definition

        assertThat(mode.testMode()).isEmpty();
        assertThat(view.draftGuards().isEmpty()).isTrue();
    }

    @Test
    void whileTestingTheEntriesSayYouAreAlreadyTestingAndTheNameCannotChange() {
        Action test = view.shortcutActions().get("tools.testMachine");
        assertThat(test.isEnabled()).isTrue();
        assertThat(mode.palette().testButton().isEnabled()).isTrue();

        mode.palette().testButton().doClick(0);

        assertThat(mode.testMode()).isPresent();
        assertThat(test.isEnabled()).isFalse();
        assertThat(test.getValue(Action.SHORT_DESCRIPTION)).isEqualTo("Ya estás probando");
        assertThat(mode.palette().testButton().isEnabled()).isFalse();
        assertThat(mode.palette().testButton().getToolTipText()).isEqualTo("Ya estás probando");
        assertThat(mode.inspectorRows().machineNameField().orElseThrow().isEnabled()).isFalse();
        assertThat(mode.inspectorRows().machineNameField().orElseThrow().getToolTipText())
                .isEqualTo("Sal de la prueba para cambiarle el nombre");

        mode.testMode().orElseThrow().bar().exitButton().doClick(0);
        assertThat(test.isEnabled()).isTrue();
        assertThat(mode.inspectorRows().machineNameField().orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void theDockShowsTheTestTableInsteadOfTheWaveStripWhileTesting() {
        Optional<JComponent> before = view.bottomDock().content();
        MachineTestMode test = start();

        assertThat(view.bottomDock().content()).isPresent().isNotEqualTo(before);
        assertThat(view.bottomDock().content().orElseThrow()).isInstanceOf(MachineTestTable.class);
        assertThat(view.bottomDock().isDockShowing()).isTrue();

        test.bar().exitButton().doClick(0);
        assertThat(view.bottomDock().content()).isEqualTo(before);
    }

    @Test
    void theBarAndTheBorderTakeTheirColoursWhenPainted() {
        MachineTestMode test = start();
        MachineTestBar bar = test.bar();
        bar.setSize(600, 40);

        Color light = pixel(bar);
        appearance.setScheme(ColorSchemes.DARK);
        Color dark = pixel(bar);

        assertThat(light).isEqualTo(MachineColors.testModeBar(ColorScheme.LIGHT));
        assertThat(dark).isEqualTo(MachineColors.testModeBar(ColorSchemes.DARK));
        assertThat(ColorScheme.LIGHT.testMode()).isEqualTo(new Color(0x6A1B9A));
        assertThat(ColorSchemes.DARK.testMode()).isEqualTo(new Color(0xCE93D8));
    }

    private static Color pixel(JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return new Color(image.getRGB(component.getWidth() - 2, component.getHeight() - 2));
    }

    @Test
    void inTheCircuitsTabProbarSaysToEnterAMachineAndTheMachinesPieceOffersIt() {
        EditorView circuitView = new EditorView(document, new EditorServices(PainterRegistry.standard(), appearance,
                preferences, new ComponentLibrary(), List.of(new MachineModeExtension())), NO_WINDOW);
        try {
            Action test = circuitView.shortcutActions().get("tools.testMachine");
            assertThat(test.isEnabled()).isFalse();
            assertThat(test.getValue(Action.SHORT_DESCRIPTION))
                    .isEqualTo("Entra a una máquina de estados para probarla");
            cl.almejo.vsim.core.document.Component piece = document.document().components().stream()
                    .filter(component -> component.id().value().equals(MachineFixtures.copies(document).getFirst()))
                    .findFirst().orElseThrow();
            circuitView.selection().select(piece.id());
            JPopupMenu menu = circuitView.contextMenu(Optional.of(piece), new Point2D.Double());
            assertThat(java.util.Arrays.stream(menu.getComponents())
                    .filter(child -> child instanceof JMenuItem item && "Probar la máquina…".equals(item.getText())))
                    .hasSize(1);
        } finally {
            circuitView.dispose();
        }
    }

    /** Review finding 2, scenario A: Guardar, then Ctrl+Z of «guardar las pruebas» in the circuit, then Salir. */
    @Test
    void afterGuardarAnUndoInTheCircuitStillLeavesTheTestsTheTableShows() {
        MachineTestMode test = start();
        setAnInput(test);
        assertThat(view.draftGuards().settle(DraftGuard.Reason.SAVE)).isEqualTo(DraftGuard.Answer.GO_ON);
        assertThat(circuitsMachine().tests()).hasSize(1);

        document.history().undo(); // «guardar las pruebas», from the circuit's tab
        assertThat(circuitsMachine().tests()).isEmpty();
        assertThat(test.bar().message()).contains("La máquina cambió afuera");

        test.bar().exitButton().doClick(0);

        assertThat(circuitsMachine().tests()).hasSize(1);
        assertThat(circuitsMachine().tests().getFirst().columns().getFirst().input(inputId())).isTrue();
    }

    /** Review finding 2, scenario B: «Aplicar y guardar», then Ctrl+Z of «aplicar la prueba» in the circuit. */
    @Test
    void afterAplicarYGuardarAnUndoInTheCircuitOffersAplicarAgain() {
        MachineTestMode test = start();
        canvas.edit(MachineTestModeTest::withS1Initial);
        questions.answers.add("Aplicar y guardar");
        assertThat(view.draftGuards().settle(DraftGuard.Reason.SAVE)).isEqualTo(DraftGuard.Answer.GO_ON);
        assertThat(circuitsMachine().initialStateId()).contains("s1");
        assertThat(test.bar().exitButton().isVisible()).isTrue();

        document.history().undo(); // «aplicar la prueba», from the circuit's tab

        assertThat(test.diagramChanged()).isTrue();
        assertThat(test.bar().applyButton().isVisible()).isTrue();
        assertThat(test.bar().exitButton().isVisible()).isFalse();
        questions.answers.add("Seguir probando");
        assertThat(view.draftGuards().settle(DraftGuard.Reason.SWITCH)).isEqualTo(DraftGuard.Answer.CANCEL);
    }

    /**
     * Review finding 4, as fix round 2 of hito 4 changed it: one test per machine at a time, shared by its views. A
     * view of a machine being tested (another copy, another tab) tests the same draft, so two drafts never overwrite
     * each other's tests; one view leaving the test leaves it in all, and a view that goes only leaves.
     */
    @Test
    void anotherViewOfAMachineBeingTestedTestsTheSameDraft() {
        MachineTestMode test = start();
        setAnInput(test);
        OpenDocument otherPlace = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()));
        EditorView other = new EditorView(otherPlace, view.services(), NO_WINDOW);
        OpenDocument thirdPlace = document.openBody(MachineFixtures.DEFINITION_ID, Optional.empty());
        EditorView third = new EditorView(thirdPlace, view.services(), NO_WINDOW);
        try {
            MachineMode otherMode = MachineMode.of(other).orElseThrow();
            otherMode.askWith(questions);

            assertThat(otherMode.isTesting()).isTrue();
            assertThat(otherMode.testMode().orElseThrow().machine()).isEqualTo(test.machine());
            otherMode.startTest(); // «Probar…» there: it tests already, nothing more
            assertThat(otherMode.testMode().orElseThrow().test()).isSameAs(test.test());

            third.dispose(); // a view that goes only leaves the test
            assertThat(mode.isTesting()).isTrue();
            assertThat(otherMode.isTesting()).isTrue();

            test.bar().exitButton().doClick(0); // leaving in one view leaves in all, the tests written once
            assertThat(otherMode.isTesting()).isFalse();
            assertThat(circuitsMachine().tests()).hasSize(1);
            assertThat(questions.asked).isEmpty();

            otherMode.startTest(); // a new test, of this tab
            assertThat(otherMode.isTesting()).isTrue();
            assertThat(mode.isTesting()).isFalse();
            otherMode.testMode().orElseThrow().end();
        } finally {
            other.dispose();
            otherPlace.close();
            third.dispose();
            thirdPlace.close();
        }
    }

    /** Review finding 5: with the real session, the untouched «Prueba 1» is unwritten, so «Borrar» is off. */
    @Test
    void theTableKnowsTheUntouchedFirstTestIsUnwritten() {
        MachineTestMode test = start();
        MachineTestTable table = (MachineTestTable) view.bottomDock().content().orElseThrow();

        JMenuItem delete = (JMenuItem) table.tabMenu(0).getComponent(2);

        assertThat(delete.getText()).isEqualTo("Borrar");
        assertThat(delete.isEnabled()).isFalse();
        setAnInput(test);
        assertThat(((JMenuItem) table.tabMenu(0).getComponent(2)).isEnabled()).isTrue();
    }

    /** Review finding 6: a test tab being renamed while Deshacer takes that test away: the rename is dropped. */
    @Test
    void renamingATestThatAnUndoTookAwayChangesNothing() {
        MachineTestMode test = start();
        setAnInput(test);
        MachineTestTable table = (MachineTestTable) view.bottomDock().content().orElseThrow();
        table.addTest();
        javax.swing.JTextField field = table.startRenaming(1);
        field.setText("Mía");

        perform(view.actions().undo()); // «+ Prueba nueva» goes, and its tab with it
        field.postActionEvent(); // the field loses the focus, or Intro: it commits

        assertThat(test.machine().tests()).hasSize(1);
        assertThat(test.machine().tests().getFirst().name()).isEqualTo("Prueba 1");
        assertThat(view.actions().redo().isEnabled()).isTrue(); // the rename made no step
    }

    /** Review finding 6: the rename follows its test, also when an undo moved it to another place. */
    @Test
    void renamingFollowsTheTestNotItsPlace() {
        MachineTestMode test = start();
        setAnInput(test);
        MachineTestTable table = (MachineTestTable) view.bottomDock().content().orElseThrow();
        table.addTest();
        table.addTest();
        String thirdId = test.machine().tests().get(2).id();
        table.deleteTest(1);
        javax.swing.JTextField field = table.startRenaming(1); // the third test, now second

        perform(view.actions().undo()); // the second test is back: the third is third again
        field.setText("Mía");
        field.postActionEvent();

        assertThat(test.machine().tests()).hasSize(3);
        assertThat(test.machine().tests().get(2).id()).isEqualTo(thirdId);
        assertThat(test.machine().tests().get(2).name()).isEqualTo("Mía");
        assertThat(test.machine().tests().get(1).name()).isNotEqualTo("Mía");
    }

    @Test
    void anExpectedValueChangesTheResultTheDiagramLights() {
        MachineTestMode test = start();
        test.editTests(tests -> List.of(MachineTests.withExpected(tests.getFirst(), 0, outputId(),
                LogicValue.ONE)), "esperar Z");

        assertThat(test.machine().tests().getFirst().columns().getFirst().expected(outputId()))
                .isEqualTo(LogicValue.ONE);
        assertThat(test.chosenTest()).isZero();
    }
}
