package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

import static cl.almejo.vsim.ui.CanvasClicks.click;
import static cl.almejo.vsim.ui.CanvasClicks.doubleClick;
import static cl.almejo.vsim.ui.CanvasClicks.move;
import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.pinAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * A continuous wire drawn from Selección in a real editor view (it starts at a pin at grid (5, 5)): changing the tool cancels it with «Cable cancelado», the window losing the
 * focus keeps it, and Deshacer (Ctrl+Z, the button, the menu: one action) drops its last corner while it lasts, never
 * an earlier step.
 */
class ContinuousWireInterruptionsTest {

    private EditorFixture fixture;
    private EditorView view;
    private CircuitCanvas canvas;

    @BeforeEach
    void open() {
        Messages.initialize(Messages.SPANISH);
        fixture = EditorFixture.create();
        view = fixture.view();
        canvas = view.canvas();
        canvas.setSize(400, 300);
        canvas.applyViewState(new ViewState(1, canvas.getWidth() / 16.0, canvas.getHeight() / 16.0)); // grid 0, 0 at the corner
        pinAt(fixture.openDocument(), 5, 5);
        view.actions().refresh();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private List<Wire> wires() {
        return fixture.openDocument().document().wires();
    }

    /** A path from (5, 5) with a corner at (15, 5) and the pointer at (15, 10). */
    private void drawAPathWithOneCorner() {
        click(canvas, 40, 40);
        click(canvas, 120, 40);
        move(canvas, 120, 80);
    }

    private Action undo() {
        return view.actions().undo();
    }

    @Test
    void choosingAnotherToolWhileDrawingCancelsThePathWithTheNotice() {
        drawAPathWithOneCorner();

        perform(view.actions().panTool());

        assertThat(view.statusBar().text()).contains("Cable cancelado");
        assertThat(wires()).isEmpty();
        assertThat(fixture.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void choosingAPartInThePaletteWhileDrawingCancelsThePathToo() {
        drawAPathWithOneCorner();

        view.palette().choose(StandardComponentTypes.LED);

        assertThat(view.statusBar().text()).contains("Cable cancelado");
    }

    @Test
    void choosingSelectionAgainWhileDrawingKeepsThePath() {
        drawAPathWithOneCorner();

        perform(view.actions().selectTool());

        assertThat(view.statusBar().text()).doesNotContain("Cable cancelado");
        assertThat(canvas.drawsBetweenClicks()).isTrue();
    }

    @Test
    void changingTheToolWithNothingDrawnSaysNothing() {
        perform(view.actions().panTool());

        assertThat(view.statusBar().text()).doesNotContain("Cable cancelado");
    }

    @Test
    void theCanvasLosingTheFocusKeepsThePath() {
        drawAPathWithOneCorner();

        for (FocusListener listener : canvas.getFocusListeners()) {
            listener.focusLost(new FocusEvent(canvas, FocusEvent.FOCUS_LOST, false, null));
        }

        assertThat(canvas.tool().holdsGesture()).isTrue();
        doubleClick(canvas, 120, 80);
        assertThat(wires()).isNotEmpty();
    }

    @Test
    void undoIsEnabledWhileDrawingWithAnEmptyHistoryAndDropsTheLastCorner() {
        assertThat(undo().isEnabled()).isFalse();
        drawAPathWithOneCorner();
        assertThat(undo().isEnabled()).isTrue();
        assertThat(undo().getValue(Action.NAME)).isEqualTo(Messages.text("action.undo"));

        perform(undo());
        assertThat(canvas.tool().holdsGesture()).isTrue(); // only the corner went
        doubleClick(canvas, 40, 120);

        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(5, 5), new GridPoint(5, 15)));
    }

    @Test
    void undoWithOnlyTheStartLeftCancelsThePathAndThenTheHistoryIsEmptyAgain() {
        click(canvas, 40, 40);
        assertThat(undo().isEnabled()).isTrue();

        perform(undo());

        assertThat(canvas.tool().holdsGesture()).isFalse();
        assertThat(canvas.tool()).isInstanceOf(SelectTool.class);
        assertThat(undo().isEnabled()).isFalse();
    }

    @Test
    void undoWhileDrawingNeverUndoesAnEarlierStep() {
        addWire(fixture.openDocument(), 0, 30, 10, 30);
        drawAPathWithOneCorner();

        perform(undo());
        perform(undo());

        assertThat(canvas.tool().holdsGesture()).isFalse();
        assertThat(wires()).hasSize(1);
        assertThat(fixture.openDocument().history().canUndo()).isTrue();
        assertThat(undo().isEnabled()).isTrue();
    }

    @Test
    void afterAPathEndsUndoAndRedoWorkOnItAsOneStep() {
        drawAPathWithOneCorner();
        doubleClick(canvas, 120, 80);
        int wiresDrawn = wires().size();
        assertThat(wiresDrawn).isEqualTo(2);

        perform(undo());
        assertThat(wires()).isEmpty();
        perform(view.actions().redo());
        assertThat(wires()).hasSize(wiresDrawn);
    }

    @Test
    void redoIsDisabledWhileDrawingAndComesBackWhenThePathEnds() {
        addWire(fixture.openDocument(), 0, 30, 10, 30);
        fixture.openDocument().history().undo();
        view.actions().refresh();
        assertThat(view.actions().redo().isEnabled()).isTrue();

        drawAPathWithOneCorner();
        assertThat(view.actions().redo().isEnabled()).isFalse();

        canvas.pressKeyOnTool(java.awt.event.KeyEvent.VK_ESCAPE, false);
        assertThat(view.actions().redo().isEnabled()).isTrue();
    }

    @Test
    void deleteAndRotateDoNothingWhileAPathIsInProgress() {
        cl.almejo.vsim.core.document.Component gate = cl.almejo.vsim.ui.UiTestSupport.place(fixture.openDocument(),
                StandardComponentTypes.BUFFER, 30, 30, java.util.Map.of(StandardComponentTypes.NEGATE, true));
        view.selection().select(gate.id());
        view.actions().refresh();
        assertThat(view.actions().delete().isEnabled()).isTrue();

        drawAPathWithOneCorner();

        assertThat(view.actions().delete().isEnabled()).isFalse();
        assertThat(view.actions().rotateClockwise().isEnabled()).isFalse();
        assertThat(view.actions().rotateCounterClockwise().isEnabled()).isFalse();
        assertThat(fixture.openDocument().document().components()).contains(gate);
    }

    @Test
    void closingThePaneThatHoldsThePathCancelsItWithTheNotice() {
        view.canvasPanes().split(CanvasPanes.Orientation.VERTICAL, 0.5);
        CircuitCanvas second = view.panes().get(1);
        second.setSize(200, 300);
        second.applyViewState(new ViewState(1, 12.5, 18.75));
        click(second, 40, 40);
        assertThat(second.tool().holdsGesture()).isTrue();

        view.canvasPanes().removeDivider(CanvasPanes.Orientation.VERTICAL, CanvasPanes.Side.AFTER);

        assertThat(view.statusBar().text()).contains("Cable cancelado");
        assertThat(view.actions().undo().isEnabled()).isFalse();
    }
}
