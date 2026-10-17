package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.frames.FramesExtension;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.preferences.PreferencesFeature;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The arrow keys move the selection one grid unit (five with Mayús), as one undo step per run. */
class ArrowKeyMoveTest {

    private static final WindowCommands WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private OpenDocument openDocument;
    private EditorServices services;
    private EditorView view;
    private CircuitCanvas canvas;
    private Component gate;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new PreferencesFeature(), new FramesExtension(), new ShortcutsExtension()));
        view = new EditorView(openDocument, services, WINDOW);
        canvas = view.canvas();
        canvas.setSize(400, 300);
        gate = place(openDocument, StandardComponentTypes.NOT, 5, 5, Map.of());
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private GridPoint positionOf(Component component) {
        return openDocument.document().component(component.id()).orElseThrow().position();
    }

    /** Types {@code keyCode} the way the canvas would: through its input map. */
    private boolean press(int keyCode, int modifiers) {
        Object name = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyCode, modifiers));
        if (name == null) {
            return false;
        }
        Action action = canvas.getActionMap().get(name);
        if (action == null || !action.isEnabled()) {
            return false;
        }
        action.actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, ""));
        return true;
    }

    @Test
    void theFourArrowsMoveTheSelectionOneGridUnitAndShiftFive() {
        canvas.selection().select(gate.id());

        assertThat(press(KeyEvent.VK_RIGHT, 0)).isTrue();
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(6, 5));
        press(KeyEvent.VK_DOWN, 0);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(6, 6));
        press(KeyEvent.VK_LEFT, 0);
        press(KeyEvent.VK_UP, 0);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 5));

        press(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(10, 5));
        press(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);
        press(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
        press(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 5));
        assertThat(canvas.selection().ids()).containsExactly(gate.id());
    }

    @Test
    void aWireNudgedIntoLineWithAnEarlierWireLeavesTheSurvivorSelected() {
        addWire(openDocument, 40, 20, 44, 20);
        Wire earlier = openDocument.document().wires().get(0);
        addWire(openDocument, 44, 22, 49, 22);
        Wire moved = openDocument.document().wires().get(1);
        canvas.selection().select(moved.id());

        press(KeyEvent.VK_UP, 0);
        assertThat(canvas.selection().ids()).containsExactly(moved.id());
        press(KeyEvent.VK_UP, 0);

        assertThat(openDocument.document().wire(moved.id())).isEmpty();
        assertThat(openDocument.document().wire(earlier.id())).contains(
                new Wire(earlier.id(), new GridPoint(40, 20), new GridPoint(49, 20)));
        assertThat(canvas.selection().ids()).containsExactly(earlier.id());
    }

    @Test
    void aRunOfPressesIsOneUndoStep() {
        canvas.selection().select(gate.id());
        for (int press = 0; press < 10; press++) {
            this.press(KeyEvent.VK_RIGHT, 0);
        }
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(15, 5));
        assertThat(openDocument.history().undoDescription()).contains("command.move");

        openDocument.history().undo();
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 5));
        assertThat(openDocument.history().undoDescription()).contains("command.add");
        openDocument.history().redo();
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(15, 5));
    }

    private static void addWire(OpenDocument target, int startX, int startY, int endX, int endY) {
        target.execute(new AddWireCommand(new Wire(target.document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY))));
    }

    /** The gate's output (9, 7) goes to a T at (20, 7): moving the gate down inserts an L. */
    private static void teeWires(OpenDocument target) {
        addWire(target, 9, 7, 20, 7);
        addWire(target, 20, 0, 20, 7);
        addWire(target, 20, 7, 20, 12);
    }

    @Test
    void fiveNudgesWithTheWiresFollowingEqualOneMoveOfFiveInOneUndoStep() {
        teeWires(openDocument);
        Set<DocumentElement> before = Set.copyOf(openDocument.document().elements());
        canvas.selection().select(gate.id());
        for (int press = 0; press < 5; press++) {
            this.press(KeyEvent.VK_DOWN, 0);
        }

        OpenDocument reference = OpenDocument.createEmpty(StandardComponentTypes.registry());
        try {
            Component referenceGate = place(reference, StandardComponentTypes.NOT, 5, 5, Map.of());
            teeWires(reference);
            CircuitDocument referenceDocument = reference.document();
            reference.execute(MoveWithWiresCommand.of(referenceDocument, reference.registry(),
                    Set.of(referenceGate.id()), 0, 5));
            assertThat(Set.copyOf(openDocument.document().elements()))
                    .isEqualTo(Set.copyOf(referenceDocument.elements()));
            assertThat(openDocument.document().wires()).hasSize(4); // one L corner, not five
        } finally {
            reference.close();
        }

        openDocument.history().undo();
        assertThat(Set.copyOf(openDocument.document().elements())).isEqualTo(before);
    }

    @Test
    void withTheModeOffTheArrowsLeaveTheWiresBehind() {
        services.preferences().setWiresFollow(false);
        teeWires(openDocument);
        List<Wire> wires = openDocument.document().wires();
        canvas.selection().select(gate.id());
        press(KeyEvent.VK_DOWN, 0);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 6));
        assertThat(openDocument.document().wires()).isEqualTo(wires);
    }

    @Test
    void aDifferentSelectionStartsANewUndoStep() {
        Component other = place(openDocument, StandardComponentTypes.NOT, 20, 20, Map.of());
        canvas.selection().select(gate.id());
        press(KeyEvent.VK_RIGHT, 0);
        canvas.selection().select(other.id());
        press(KeyEvent.VK_RIGHT, 0);
        openDocument.history().undo();
        assertThat(positionOf(other)).isEqualTo(new GridPoint(20, 20));
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(6, 5));
    }

    @Test
    void anEmptySelectionDoesNothingAndDoesNotConsumeTheKey() {
        assertThat(press(KeyEvent.VK_RIGHT, 0)).isFalse();
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 5));
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void aSelectedLockedFrameMovesItsBlock() {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 10, "Bloque",
                FrameTint.NONE, true);
        openDocument.execute(new CreateFrameCommand(frame));
        canvas.selection().select(frame.id());

        press(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK);

        assertThat(openDocument.document().frame(frame.id()).orElseThrow().topLeft()).isEqualTo(new GridPoint(0, 5));
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 10));
    }

    @Test
    void theArrowsAreRebindable() {
        canvas.selection().select(gate.id());
        KeyBindings bindings = new KeyBindings(services.preferences());
        bindings.assign("edit.moveRight", KeyStroke.getKeyStroke(KeyEvent.VK_L, 0));
        assertThat(press(KeyEvent.VK_L, 0)).isTrue();
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(6, 5));
        assertThat(press(KeyEvent.VK_RIGHT, 0)).isFalse();
    }

    @Test
    void nothingMovesWhileADragIsInProgress() {
        canvas.setSize(400, 300);
        canvas.getMouseListeners()[0].mousePressed(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                InputEvent.BUTTON1_DOWN_MASK, 56, 56, 1, false, MouseEvent.BUTTON1));
        canvas.getMouseMotionListeners()[0].mouseDragged(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0,
                InputEvent.BUTTON1_DOWN_MASK, 96, 56, 1, false, MouseEvent.NOBUTTON));
        assertThat(canvas.selection().ids()).containsExactly(gate.id());

        press(KeyEvent.VK_RIGHT, 0);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 5));
        assertThat(openDocument.history().undoDescription()).contains("command.add");

        canvas.getMouseListeners()[0].mouseReleased(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0,
                96, 56, 1, false, MouseEvent.BUTTON1));
        assertThat(positionOf(gate)).isNotEqualTo(new GridPoint(5, 5));
        assertThat(openDocument.history().undoDescription()).contains("command.move");
        GridPoint afterDrag = positionOf(gate);

        press(KeyEvent.VK_RIGHT, 0);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(afterDrag.x() + 1, afterDrag.y()));
        openDocument.history().undo();
        assertThat(positionOf(gate)).isEqualTo(afterDrag);
    }

    @Test
    void nothingMovesWhileAnotherToolIsActive() {
        canvas.selection().select(gate.id());
        canvas.setTool(new WireTool());
        press(KeyEvent.VK_RIGHT, 0);
        assertThat(positionOf(gate)).isEqualTo(new GridPoint(5, 5));
    }
}
