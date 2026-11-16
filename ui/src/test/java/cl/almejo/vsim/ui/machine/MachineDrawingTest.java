package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.logic.ExpressionParser;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.InspectorPanel;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.itemDeep;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Drawing and editing a machine's diagram in its tab (plan 2026-11-14 task 5, spec §1 «Drawing»): each gesture is one
 * edit of the machine, one undo step of the circuit that undoes in the machine's tab; a new name in a condition gives
 * the placed part a pin; a bad condition stays as written and shows its problem; Esc cancels an inline edit.
 */
class MachineDrawingTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private LibraryService service;
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;
    private MachineCanvas canvas;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, registry, Runnable::run);
        List<EditorExtension> extensions = new ArrayList<>(
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE).stream()
                        .filter(extension -> !(extension instanceof MachineModeExtension))
                        .toList());
        extensions.add(new MachineModeExtension((snapshot, instancePath) -> Optional.empty()));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, extensions);
        document = MachineFixtures.circuitWithGeneratedMachines(2);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(1)));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        mode = MachineMode.of(view).orElseThrow();
        canvas = mode.canvas();
        canvas.setBounds(0, 0, 900, 600);
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        place.close();
        document.close();
        service.close();
    }

    private StateMachine machine() {
        return canvas.machine().orElseThrow();
    }

    private ComponentDefinition definition() {
        return document.document().definition(MachineFixtures.DEFINITION_ID).orElseThrow();
    }

    private List<String> pinNames() {
        return definition().pinout().stream().map(pin -> pin.spec().name()).toList();
    }

    private Point2D at(double x, double y) {
        return canvas.toScreen(new DiagramPoint(x, y));
    }

    private void mouse(int id, Point2D point, int modifiers, int clicks) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, modifiers, (int) Math.round(point.getX()),
                (int) Math.round(point.getY()), clicks, false, MouseEvent.BUTTON1));
    }

    private void click(Point2D point, int modifiers, int clicks) {
        mouse(MouseEvent.MOUSE_PRESSED, point, modifiers | InputEvent.BUTTON1_DOWN_MASK, clicks);
        mouse(MouseEvent.MOUSE_RELEASED, point, modifiers, clicks);
        mouse(MouseEvent.MOUSE_CLICKED, point, modifiers, clicks);
    }

    private void click(Point2D point) {
        click(point, 0, 1);
    }

    private void doubleClick(Point2D point) {
        click(point, 0, 1);
        click(point, 0, 2);
    }

    private void drag(Point2D from, Point2D... through) {
        mouse(MouseEvent.MOUSE_MOVED, from, 0, 0);
        mouse(MouseEvent.MOUSE_PRESSED, from, InputEvent.BUTTON1_DOWN_MASK, 1);
        Point2D last = from;
        for (Point2D point : through) {
            mouse(MouseEvent.MOUSE_DRAGGED, point, InputEvent.BUTTON1_DOWN_MASK, 0);
            last = point;
        }
        mouse(MouseEvent.MOUSE_RELEASED, last, 0, 1);
    }

    private InlineTextEditor inline() {
        return mode.inlineEditor().editor();
    }

    @Test
    void theTypeChipsDoNotCatchClicks() {
        int before = machine().states().size();
        Point2D underTheChips = new Point2D.Double(20, 20); // inside the «Moore» pill, over an empty spot

        click(underTheChips, 0, 1);
        click(underTheChips, 0, 2);

        assertThat(machine().states()).hasSize(before + 1);
    }

    @Test
    void hoveringAChipExplainsIt() {
        MouseEvent hover = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, 20, 20, 0, false);

        assertThat(canvas.getToolTipText(hover)).contains("Moore: la salida depende solo del estado");
    }

    @Test
    void theCanvasPaintsTheChipsOnTopOfTheDiagram() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(900, 600,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();

        java.awt.geom.Rectangle2D moore = MachineKindChips.mooreBounds(MachineKindChips.defaultFont());
        java.awt.geom.Rectangle2D mealy = MachineKindChips.mealyBounds(MachineKindChips.defaultFont());
        assertThat(image.getRGB((int) moore.getMinX() + 5, (int) moore.getCenterY()))
                .as("the detector is Moore: its pill is lit, Mealy's is not")
                .isNotEqualTo(image.getRGB((int) mealy.getMinX() + 5, (int) mealy.getCenterY()));
    }

    @Test
    void aDoubleClickOnEmptySpaceCreatesTheNextStateInOneUndoStep() {
        doubleClick(at(160, 200));

        MachineState created = machine().states().get(3);
        assertThat(created.name()).isEqualTo("S3");
        assertThat(created.position().x()).isCloseTo(160, offset(1.0));
        assertThat(created.position().y()).isCloseTo(200, offset(1.0));
        assertThat(mode.selection().stateIds()).containsExactly(created.id());

        perform(view.actions().undo());
        assertThat(machine().states()).hasSize(3);
        perform(view.actions().redo());
        assertThat(machine().states()).hasSize(4);
    }

    @Test
    void aMachineStepBelongsToTheMachineTabSoItsUndoStaysThere() {
        doubleClick(at(160, 200));

        EditInsideCommand step = OpenDocument.insideEditOf(document.history().undoCommand().orElseThrow())
                .orElseThrow();
        assertThat(step.definitionId()).isEqualTo(MachineFixtures.DEFINITION_ID);
        assertThat(step.contextPath()).isEqualTo(place.contextPath());
        assertThat(String.valueOf(view.actions().undo().getValue(javax.swing.Action.NAME)))
                .contains(Messages.text("command.editMachine"));
    }

    @Test
    void aDragFromAStatesCentreMovesItInOneUndoStep() {
        drag(at(160, 0), at(160, 30), at(160, 70), at(160, 100));

        DiagramPoint moved = machine().state("s1").orElseThrow().position();
        assertThat(moved.x()).isCloseTo(160, offset(1.0));
        assertThat(moved.y()).isCloseTo(100, offset(1.0));

        perform(view.actions().undo());
        assertThat(machine().state("s1").orElseThrow().position()).isEqualTo(new DiagramPoint(160, 0));
    }

    /** S0 moved over S1 (S0 comes first in the model) and selected: it is drawn on top, so it is the one hit. */
    private void selectS0OverS1() {
        canvas.edit(current -> MachineEdits.withStatesMoved(current, java.util.Set.of("s0"), 110, 0));
        mode.selection().selectState("s0");
    }

    @Test
    void aDragInTheOverlapMovesTheStateDrawnOnTop() {
        selectS0OverS1();

        drag(at(130, 0), at(130, 40), at(130, 80), at(130, 120));

        assertThat(machine().state("s0").orElseThrow().position().y()).isCloseTo(120, offset(1.0));
        assertThat(machine().state("s1").orElseThrow().position()).isEqualTo(new DiagramPoint(160, 0));
        assertThat(mode.selection().stateIds()).containsExactly("s0");
    }

    @Test
    void aDoubleClickInTheOverlapRenamesTheStateDrawnOnTop() {
        selectS0OverS1();

        doubleClick(at(130, 0));

        assertThat(inline().textComponent().getText()).isEqualTo("S0");
    }

    @Test
    void theBorderOfTheStateDrawnOnTopLightsUpOverTheOneBelow() {
        selectS0OverS1();

        mouse(MouseEvent.MOUSE_MOVED, at(148, 0), 0, 0); // S0's border, inside S1's circle

        assertThat(mode.tool().litState()).contains("s0");
    }

    @Test
    void theBorderLightsUpNearItAndADragFromThereMakesAnArrowWhoseConditionOpensInline() {
        mouse(MouseEvent.MOUSE_MOVED, at(160, 38), 0, 0);
        assertThat(mode.tool().litState()).contains("s1");
        mouse(MouseEvent.MOUSE_MOVED, at(160, 0), 0, 0);
        assertThat(mode.tool().litState()).isEmpty();

        drag(at(160, 38), at(100, 30), at(20, 10), at(0, 0));

        MachineTransition arrow = machine().transitions().get(6);
        assertThat(arrow.fromId()).isEqualTo("s1");
        assertThat(arrow.toId()).isEqualTo("s0");
        assertThat(inline().isOpen()).isTrue();

        inline().textComponent().setText("y");
        inline().confirm();

        assertThat(machine().transition(arrow.id()).orElseThrow().condition()).isEqualTo("y");
        assertThat(pinNames()).contains("y");
    }

    /** The user's decision (hand test of hito 2): «/» stays the only way to write outputs, and it is shown. */
    @Test
    void theArrowsInlineEditorShowsHowALabelIsWritten() {
        mode.inlineEditor().editCondition("t1");

        assertThat(inline().hintText().isVisible()).isTrue();
        assertThat(inline().hintText().getText()).contains("condición / salidas").contains("x / Z=1");

        inline().textComponent().setText("x / Z=");
        assertThat(inline().hintText().isVisible()).as("a problem takes its place").isFalse();
        inline().cancel();

        mode.inlineEditor().editStateName("s1");
        assertThat(inline().hintText().isVisible()).as("a name has no notation to show").isFalse();
    }

    @Test
    void aSelectedArrowsPropertiesShowHowALabelIsWritten() {
        mode.selection().selectTransition("t1");

        List<String> hints = new ArrayList<>();
        for (java.awt.Component child : mode.inspectorRows().getComponents()) {
            if (child instanceof javax.swing.JTextArea hint) {
                hints.add(hint.getText());
            }
        }
        assertThat(hints).anySatisfy(hint -> assertThat(hint).contains("condición / salidas").contains("x / Z=1"));
    }

    @Test
    void aNewNameInAConditionGivesThePlacedPartsAPin() {
        Component copy = document.document().components().get(0);
        int pinsBefore = document.registry().typeOf(copy).pinCount();

        doubleClick(at(240, -14)); // the condition «x» of S1 → S2
        inline().textComponent().setText("x·w");
        inline().confirm();

        Component copyAfter = document.document().component(copy.id()).orElseThrow();
        assertThat(document.registry().typeOf(copyAfter).pinCount()).isEqualTo(pinsBefore + 1);
        assertThat(pinNames()).contains("w");
        perform(view.actions().undo());
        assertThat(pinNames()).doesNotContain("w");
    }

    @Test
    void aDragFromTheBorderBackIntoTheSameStateMakesASelfLoop() {
        drag(at(160, 38), at(160, 80), at(160, 120), at(160, 10));

        MachineTransition arrow = machine().transitions().get(6);
        assertThat(arrow.isSelfLoop()).isTrue();
        assertThat(arrow.fromId()).isEqualTo("s1");
    }

    @Test
    void aDoubleClickOnAConditionEditsItInlineAndEscCancels() {
        doubleClick(at(240, -14));

        assertThat(inline().isOpen()).isTrue();
        assertThat(inline().textComponent().getText()).isEqualTo("x");
        Object stepBefore = document.history().undoCommand().orElse(null);
        inline().textComponent().setText("z");
        dispatchEscape(inline().textComponent());

        assertThat(inline().isOpen()).isFalse();
        assertThat(machine().transition("t3").orElseThrow().condition()).isEqualTo("x");
        assertThat(document.history().undoCommand().orElse(null)).isSameAs(stepBefore);
    }

    private static void dispatchEscape(JComponent field) {
        Object name = field.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        perform(field.getActionMap().get(name));
    }

    @Test
    void aDoubleClickOnAStateRenamesItInline() {
        doubleClick(at(160, 0));

        assertThat(inline().textComponent().getText()).isEqualTo("S1");
        inline().textComponent().setText("S0");
        assertThat(inline().problemText().isVisible()).isTrue(); // taken
        inline().textComponent().setText("Espera");
        inline().confirm();

        assertThat(machine().state("s1").orElseThrow().name()).isEqualTo("Espera");
    }

    @Test
    void aBadConditionIsKeptAsWrittenAndThePanelShowsItsProblem() {
        doubleClick(at(240, -14));
        inline().textComponent().setText("x +");
        inline().confirm();

        assertThat(machine().transition("t3").orElseThrow().condition()).isEqualTo("x +");
        mode.selection().selectTransition("t3");
        String expected = MachineInspectorRows.describe(ExpressionParser.parseExpression("x +").problem()
                .orElseThrow());
        assertThat(mode.inspectorRows().problemText()).contains(expected);
    }

    @Test
    void aClickSelectsMayusClickAddsAndTheRubberBandSelectsSeveral() {
        click(at(160, 0));
        assertThat(mode.selection().stateIds()).containsExactly("s1");

        click(at(320, 0), InputEvent.SHIFT_DOWN_MASK, 1);
        assertThat(mode.selection().stateIds()).containsExactly("s1", "s2");

        click(at(240, -14)); // the condition of S1 → S2 (its line lies under S2 → S0, which passes behind S1)
        assertThat(mode.selection().transitionIds()).containsExactly("t3");
        assertThat(mode.selection().stateIds()).isEmpty();

        double loopTop = MachineDiagramGeometry.of(machine()).arrow("t6").path().getBounds2D().getMinY();
        click(at(0, loopTop + 1)); // the line of the loop over S0
        assertThat(mode.selection().transitionIds()).containsExactly("t6");

        click(at(160, 300));
        assertThat(mode.selection().isEmpty()).isTrue();

        drag(at(100, -200), at(250, -50), at(380, 60));
        assertThat(mode.selection().stateIds()).containsExactlyInAnyOrder("s1", "s2");
    }

    @Test
    void borrarDeletesAStateWithItsArrowsInOneUndoStepAndTheInitialStateCanGo() {
        assertThat(view.actions().delete().isEnabled()).isFalse();
        click(at(0, 0));
        assertThat(view.actions().delete().isEnabled()).isTrue();

        perform(view.actions().delete());

        assertThat(machine().states()).extracting(MachineState::id).containsExactly("s1", "s2");
        assertThat(machine().transitions()).extracting(MachineTransition::id).containsExactly("t3", "t4");
        assertThat(machine().initialStateId()).isEmpty();
        perform(view.actions().undo());
        assertThat(machine().states()).hasSize(3);
        assertThat(machine().initialStateId()).contains("s0");
    }

    @Test
    void suprOnTheDiagramRunsBorrar() {
        Object name = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));

        assertThat(canvas.getActionMap().get(name)).isSameAs(view.actions().delete());
    }

    @Test
    void theDiagramFollowsTheKeysTheUserGaveBorrar() {
        KeyStroke control = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK);
        KeyStroke supr = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
        KeyStroke retroceso = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0);

        new KeyBindings(preferences).assign("edit.delete", control);

        javax.swing.InputMap keys = canvas.getInputMap(JComponent.WHEN_FOCUSED);
        assertThat(canvas.getActionMap().get(keys.get(control))).isSameAs(view.actions().delete());
        assertThat(keys.get(supr)).isNull();
        assertThat(keys.get(retroceso)).isNotNull();
        click(at(0, 0));
        perform(canvas.getActionMap().get(keys.get(control)));
        assertThat(machine().states()).extracting(MachineState::id).containsExactly("s1", "s2");

        new KeyBindings(preferences).reset("edit.delete");

        assertThat(keys.get(control)).isNull();
        assertThat(canvas.getActionMap().get(keys.get(supr))).isSameAs(view.actions().delete());
    }

    @Test
    void droppingAPaletteEntryOnTheDiagramCreatesNothingAndSaysWhy() {
        int definitions = document.document().definitions().size();
        TransferHandler handler = canvas.getTransferHandler();
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(canvas,
                new StringSelection(PaletteTransfer.encode(MachineCreation.PALETTE_ID)));

        assertThat(handler).isNotNull();
        assertThat(handler.canImport(support)).isTrue();
        assertThat(handler.importData(support)).isFalse();

        assertThat(document.document().definitions()).hasSize(definitions);
        assertThat(texts(view.statusBar())).contains(Messages.text("machine.dropRefused"));
    }

    private static List<String> texts(java.awt.Container container) {
        List<String> found = new ArrayList<>();
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof javax.swing.JLabel label && label.getText() != null) {
                found.add(label.getText());
            }
            if (component instanceof java.awt.Container inner) {
                found.addAll(texts(inner));
            }
        }
        return found;
    }

    @Test
    void thePalettesStateToolPlacesAStateWithAClickAndTheTransitionToolJoinsFromTheCentre() {
        mode.palette().stateButton().setSelected(true);
        click(at(160, 250));
        assertThat(machine().states()).hasSize(4);
        assertThat(mode.palette().stateButton().isSelected()).isFalse();

        mode.palette().transitionButton().setSelected(true);
        drag(at(0, 0), at(150, 0), at(320, 0));
        MachineTransition arrow = machine().transitions().get(6);
        assertThat(arrow.fromId()).isEqualTo("s0");
        assertThat(arrow.toId()).isEqualTo("s2");
        assertThat(machine().state("s0").orElseThrow().position()).isEqualTo(new DiagramPoint(0, 0));
    }

    @Test
    void theMenuOffersANewStateAndAnArrowBetweenTheSelectedStates() {
        JMenuItem newState = itemDeep(view.menu(MenuId.EDIT), Messages.text("machine.action.newState"))
                .orElseThrow();
        JMenuItem newArrow = itemDeep(view.menu(MenuId.EDIT), Messages.text("machine.action.newTransition"))
                .orElseThrow();
        assertThat(newArrow.isEnabled()).isFalse();

        perform(newState.getAction());
        assertThat(machine().states()).hasSize(4);

        mode.selection().set(new java.util.LinkedHashSet<>(List.of("s2", "s1")), java.util.Set.of());
        assertThat(newArrow.isEnabled()).isTrue();
        perform(newArrow.getAction());
        MachineTransition arrow = machine().transitions().get(6);
        assertThat(arrow.fromId()).isEqualTo("s2");
        assertThat(arrow.toId()).isEqualTo("s1");
        assertThat(inline().isOpen()).isTrue();
    }

    @Test
    void thePropertiesShowTheMachineRowsInsteadOfTheBodysProperties() {
        assertThat(SwingUtilities.isDescendingFrom(mode.inspectorRows(), view.inspector())).isTrue();
        assertThat(view.inspector().title(InspectorPanel.PROPERTIES)).contains(Messages.text("inspector.properties"));
    }

    @Test
    void theStatesRowsRenameItSetItsMooreOutputsAndMakeItInitial() {
        mode.selection().selectState("s1");
        MachineInspectorRows rows = mode.inspectorRows();

        JTextField name = rows.nameField().orElseThrow();
        name.setText("Uno");
        name.postActionEvent();
        JTextField outputs = rows.outputsField().orElseThrow();
        assertThat(outputs.getText()).isEmpty();
        outputs.setText("Z=1, Y=0");
        outputs.postActionEvent();
        rows.initialBox().orElseThrow().doClick();

        MachineState state = machine().state("s1").orElseThrow();
        assertThat(state.name()).isEqualTo("Uno");
        assertThat(state.outputs()).containsExactly(new OutputAssignment("Z", true), new OutputAssignment("Y", false));
        assertThat(machine().initialStateId()).contains("s1");
        assertThat(pinNames()).contains("Y");
    }

    @Test
    void aBadMooreOutputsTextIsNotAppliedAndSaysWhy() {
        mode.selection().selectState("s1");
        MachineInspectorRows rows = mode.inspectorRows();
        Object stepBefore = document.history().undoCommand().orElse(null);

        JTextField outputs = rows.outputsField().orElseThrow();
        outputs.setText("Z=2");
        outputs.postActionEvent();

        assertThat(document.history().undoCommand().orElse(null)).isSameAs(stepBefore);
        assertThat(rows.problemText()).contains(Messages.format("machine.outputs.badValue", "2"));
    }

    @Test
    void theArrowsRowsShowWhereItGoesAndEditItsCondition() {
        mode.selection().selectTransition("t3");
        MachineInspectorRows rows = mode.inspectorRows();

        assertThat(rows.arrowText()).contains("S1 → S2");
        JTextField condition = rows.conditionField().orElseThrow();
        assertThat(condition.getText()).isEqualTo("x");
        condition.setText("x'");
        condition.postActionEvent();

        assertThat(machine().transition("t3").orElseThrow().condition()).isEqualTo("x'");
    }

    @Test
    void anInlineLabelWithOutputsSetsConditionAndMealyOutputsInOneStepAndUndoRestoresBoth() {
        String conditionBefore = machine().transition("t3").orElseThrow().condition();
        doubleClick(at(240, -14));
        assertThat(inline().textComponent().getText()).isEqualTo(conditionBefore);
        inline().textComponent().setText("x' / Z=1");
        inline().confirm();

        MachineTransition edited = machine().transition("t3").orElseThrow();
        assertThat(edited.condition()).isEqualTo("x'");
        assertThat(edited.outputs()).containsExactly(new OutputAssignment("Z", true));

        perform(view.actions().undo());
        MachineTransition restored = machine().transition("t3").orElseThrow();
        assertThat(restored.condition()).isEqualTo(conditionBefore);
        assertThat(restored.outputs()).isEmpty();
    }

    @Test
    void anInlineLabelWithABadOutputsPartIsRefusedWithItsProblem() {
        Object stepBefore = document.history().undoCommand().orElse(null);
        doubleClick(at(240, -14));
        inline().textComponent().setText("x / Z=");

        assertThat(inline().problemText().isVisible()).isTrue();
        inline().confirm();

        assertThat(inline().isOpen()).isTrue();
        assertThat(document.history().undoCommand().orElse(null)).isSameAs(stepBefore);
    }

    @Test
    void aNewOutputNameInAMealyPartAddsAPinToThePlacedCopy() {
        assertThat(pinNames()).doesNotContain("Listo");
        doubleClick(at(240, -14));
        inline().textComponent().setText("x / Listo=1");
        inline().confirm();

        assertThat(pinNames()).contains("Listo");
    }

    @Test
    void theArrowsRowsEditItsMealyOutputsAsOneUndoStep() {
        mode.selection().selectTransition("t3");
        MachineInspectorRows rows = mode.inspectorRows();

        JTextField outputs = rows.mealyOutputsField().orElseThrow();
        assertThat(outputs.getText()).isEmpty();
        outputs.setText("Z=1, Y=0");
        outputs.postActionEvent();

        assertThat(machine().transition("t3").orElseThrow().outputs())
                .containsExactly(new OutputAssignment("Z", true), new OutputAssignment("Y", false));
        perform(view.actions().undo());
        assertThat(machine().transition("t3").orElseThrow().outputs()).isEmpty();
    }

    @Test
    void aBadMealyOutputsTextInThePanelIsNotAppliedAndSaysWhy() {
        mode.selection().selectTransition("t3");
        MachineInspectorRows rows = mode.inspectorRows();
        JTextField outputs = rows.mealyOutputsField().orElseThrow();

        outputs.setText("Z=2");
        outputs.postActionEvent();

        assertThat(machine().transition("t3").orElseThrow().outputs()).isEmpty();
        assertThat(rows.problemText()).contains(Messages.format("machine.outputs.badValue", "2"));
    }

    @Test
    void typingInTheConditionSurvivesAMachineChangeMadeElsewhere() {
        mode.selection().selectTransition("t3");
        MachineInspectorRows rows = mode.inspectorRows();
        JTextField condition = rows.conditionField().orElseThrow();
        condition.setText("x + half typed");

        canvas.edit(current -> MachineEdits.withStateName(current, "s0", "Inicio"));

        assertThat(rows.conditionField().orElseThrow()).isSameAs(condition);
        assertThat(condition.getText()).isEqualTo("x + half typed");
    }

    @Test
    void aFieldWithoutTheFocusFollowsAChangeMadeElsewhere() {
        mode.selection().selectTransition("t3");
        MachineInspectorRows rows = mode.inspectorRows();
        JTextField condition = rows.conditionField().orElseThrow();

        canvas.edit(current -> MachineEdits.withCondition(current, "t3", "x'"));

        assertThat(rows.conditionField().orElseThrow().getText()).isEqualTo("x'");
    }

    @Test
    void theMachinesRowsShowItsKindFromWhereTheOutputsAreAssigned() {
        mode.selection().clear();
        MachineInspectorRows rows = mode.inspectorRows();
        assertThat(rows.kindText().orElseThrow()).isEqualTo(Messages.text("machine.properties.kind.moore"));

        canvas.edit(current -> MachineEdits.withMealyOutputs(current, "t3", List.of(new OutputAssignment("Z", true))));
        assertThat(rows.kindText().orElseThrow()).isEqualTo(Messages.text("machine.properties.kind.both"));

        canvas.edit(current -> current.withStates(current.states().stream()
                .map(state -> state.withOutputs(List.of())).toList()));
        assertThat(rows.kindText().orElseThrow()).isEqualTo(Messages.text("machine.properties.kind.mealy"));
    }
}
