package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine tab whose diagram reads and edits a draft (plan 2026-11-14, hito 4 task 3): the gestures, the inline
 * editor and Deshacer / Rehacer work on the draft while its source and step source are installed, and the circuit
 * neither changes nor gains a step.
 */
class MachineDraftSourceTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;
    private MachineCanvas canvas;
    private FakeMachineSource draft;
    private StateMachine real;
    private Optional<Command> lastCircuitStep;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(new MachineModeExtension()));
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        mode = MachineMode.of(view).orElseThrow();
        canvas = mode.canvas();
        canvas.setBounds(0, 0, 900, 600);
        real = circuitsMachine();
        lastCircuitStep = document.history().undoCommand();
        draft = new FakeMachineSource(real);
        canvas.useSource(draft);
        view.actions().stepsInstead(draft);
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

    private void assertTheCircuitIsUntouched() {
        assertThat(circuitsMachine()).isEqualTo(real);
        assertThat(document.history().undoCommand()).isEqualTo(lastCircuitStep);
    }

    private Point2D at(double x, double y) {
        return canvas.toScreen(new DiagramPoint(x, y));
    }

    private void click(Point2D point, int clicks) {
        int x = (int) Math.round(point.getX());
        int y = (int) Math.round(point.getY());
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, x, y,
                clicks, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, clicks, false,
                MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, clicks, false,
                MouseEvent.BUTTON1));
    }

    private void doubleClick(Point2D point) {
        click(point, 1);
        click(point, 2);
    }

    private static String nameOf(Action action) {
        return String.valueOf(action.getValue(Action.NAME));
    }

    @Test
    void aGestureEditsTheDraftAndDeshacerUndoesItThereWithItsOwnName() {
        doubleClick(at(160, 200));

        assertThat(canvas.machine().orElseThrow().states()).hasSize(4);
        assertTheCircuitIsUntouched();
        assertThat(view.actions().undo().isEnabled()).isTrue();
        assertThat(nameOf(view.actions().undo())).isEqualTo("Deshacer (prueba): probar");

        perform(view.actions().undo());

        assertThat(canvas.machine().orElseThrow().states()).hasSize(3);
        assertThat(nameOf(view.actions().redo())).isEqualTo("Rehacer (prueba): probar");
        perform(view.actions().redo());
        assertThat(canvas.machine().orElseThrow().states()).hasSize(4);
        assertTheCircuitIsUntouched();
    }

    @Test
    void theInlineEditorCommitsIntoTheDraft() {
        doubleClick(at(160, 0));
        mode.inlineEditor().editor().textComponent().setText("Uno");
        mode.inlineEditor().editor().confirm();

        assertThat(canvas.machine().orElseThrow().state("s1").orElseThrow().name()).isEqualTo("Uno");
        assertTheCircuitIsUntouched();
    }

    @Test
    void withNothingDoneInTheDraftDeshacerIsDisabledWithItsReason() {
        assertThat(view.actions().undo().isEnabled()).isFalse();
        assertThat(view.actions().undo().getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo("En la prueba, Deshacer deshace solo lo que hiciste aquí");
        assertThat(nameOf(view.actions().undo())).isEqualTo(Messages.text("action.undo"));
    }

    @Test
    void theInspectorFollowsTheDraft() {
        canvas.edit(machine -> MachineEdits.withStateName(machine, "s1", "Uno"));
        mode.selection().selectState("s1");

        assertThat(mode.inspectorRows().nameField().orElseThrow().getText()).isEqualTo("Uno");
    }
}
