package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CircuitCanvasTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<Optional<DocumentElement>> menus = new ArrayList<>();
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument,
            new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
            new CanvasHost() {
                @Override
                public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target, Point2D screenPosition) {
                    menus.add(target);
                }

                @Override
                public void configure(Component component) {
                }
            });

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private static final class RecordingTool implements Tool {
        final List<CanvasMouseEvent> pressed = new ArrayList<>();
        final List<Integer> keys = new ArrayList<>();

        @Override
        public void mousePressed(ToolContext context, CanvasMouseEvent event) {
            pressed.add(event);
        }

        @Override
        public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
            keys.add(keyCode);
            return keyCode == KeyEvent.VK_ESCAPE;
        }
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    @Test
    void pressReachesTheToolInGridCoordinates() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 17, 23, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        CanvasMouseEvent event = tool.pressed.get(0);
        assertThat(event.gridPosition().getX()).isCloseTo(17 / 8.0, within(1e-9));
        assertThat(event.snapped().x()).isEqualTo(2);
        assertThat(event.snapped().y()).isEqualTo(3);
        assertThat(event.button()).isEqualTo(CanvasMouseEvent.PRIMARY);
    }

    @Test
    void middleDragPansInsteadOfUsingTheTool() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2));
        canvas.getMouseMotionListeners()[0].mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 30, 15, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.NOBUTTON));
        canvas.getMouseListeners()[0].mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, 30, 15, 0, MouseEvent.BUTTON2));
        assertThat(tool.pressed).isEmpty();
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(new Point2D.Double(20, 5));
    }

    @Test
    void controlWheelZoomsAroundTheCursorAndNotifiesViewListeners() {
        List<Double> zooms = new ArrayList<>();
        canvas.addViewListener(() -> zooms.add(canvas.viewport().zoom()));
        Point2D before = canvas.viewport().screenToGrid(100, 50);
        canvas.getMouseWheelListeners()[0].mouseWheelMoved(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0,
                InputEvent.CTRL_DOWN_MASK, 100, 50, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
        assertThat(canvas.viewport().zoom()).isCloseTo(1.25, within(1e-9));
        assertThat(canvas.viewport().screenToGrid(100, 50).getX()).isCloseTo(before.getX(), within(1e-9));
        assertThat(zooms).containsExactly(1.25);
    }

    @Test
    void losingFocusForgetsSpaceAndAPanInProgress() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        canvas.getKeyListeners()[0].keyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_SPACE, ' '));
        canvas.getFocusListeners()[0].focusLost(new java.awt.event.FocusEvent(canvas, java.awt.event.FocusEvent.FOCUS_LOST));
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        assertThat(tool.pressed).hasSize(1);

        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2));
        canvas.getFocusListeners()[0].focusLost(new java.awt.event.FocusEvent(canvas, java.awt.event.FocusEvent.FOCUS_LOST));
        canvas.getMouseMotionListeners()[0].mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 30, 15, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.NOBUTTON));
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(new Point2D.Double(0, 0));
    }

    @Test
    void keysGoToTheToolFirst() {
        RecordingTool tool = new RecordingTool();
        canvas.setTool(tool);
        KeyEvent escape = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        canvas.getKeyListeners()[0].keyPressed(escape);
        assertThat(tool.keys).containsExactly(KeyEvent.VK_ESCAPE);
        assertThat(escape.isConsumed()).isTrue();
    }

    @Test
    void toolChangesAreAnnouncedAndUseDefaultToolRestoresSelection() {
        List<Tool> announced = new ArrayList<>();
        canvas.addToolListener(announced::add);
        PanTool pan = new PanTool();
        canvas.setTool(pan);
        canvas.useDefaultTool();
        assertThat(announced).containsExactly(pan, canvas.defaultTool());
        assertThat(canvas.tool()).isSameAs(canvas.defaultTool());
    }

    @Test
    void rightClickAsksTheHostForAContextMenu() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 16, 16, InputEvent.BUTTON3_DOWN_MASK, MouseEvent.BUTTON3));
        assertThat(menus).containsExactly(Optional.of(gate));
    }

    @Test
    void refreshSnapshotPublishesEachNewSnapshotOnce() {
        List<SimulationSnapshot> received = new ArrayList<>();
        canvas.addSnapshotListener(received::add);
        waitUntil(() -> openDocument.latestSnapshot() != SimulationSnapshot.EMPTY);
        openDocument.close(); // stops the simulation thread, so the latest snapshot no longer changes
        canvas.refreshSnapshot();
        canvas.refreshSnapshot();
        assertThat(received).containsExactly(openDocument.latestSnapshot());
        assertThat(canvas.displayedSnapshot()).isSameAs(openDocument.latestSnapshot());
    }

    @Test
    void deletingTheSelectedElementPrunesTheSelection() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        openDocument.history().undo();
        assertThat(canvas.selection().isEmpty()).isTrue();
    }

    @Test
    void droppingAPaletteEntryPlacesItAtTheDropPoint() {
        canvas.setTool(new PanTool());
        assertThat(canvas.dropComponent(PaletteTransfer.encode("led"), new Point(41, 23))).isTrue();
        assertThat(openDocument.document().components()).singleElement()
                .satisfies(led -> assertThat(led.position()).isEqualTo(new GridPoint(5, 3)));
        assertThat(canvas.tool()).isSameAs(canvas.defaultTool());
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void dropOfForeignTextIsRejected() {
        assertThat(canvas.dropComponent("hello", new Point(10, 10))).isFalse();
        assertThat(canvas.dropComponent(PaletteTransfer.encode("no-such-type"), new Point(10, 10))).isFalse();
        assertThat(openDocument.document().isEmpty()).isTrue();
    }

    @Test
    void removingASelectedElementDropsItFromTheSelection() {
        Component gate = place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        canvas.selection().select(gate.id());
        openDocument.history().undo();
        assertThat(canvas.selection().isEmpty()).isTrue();
    }

    @Test
    void switchingToolCommitsAFloatingFragment() {
        CircuitFragment fragment = new CircuitFragment(
                List.of(openDocument.registry().require(StandardComponentTypes.NOT)
                        .newComponent(new ElementId("c1"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "")),
                List.of(), List.of());
        canvas.setTool(new FloatingSelectionTool(fragment, new GridPoint(3, 3)));
        canvas.setTool(new FloatingSelectionTool(fragment, new GridPoint(20, 3)));
        assertThat(openDocument.document().components()).hasSize(1);
        canvas.setTool(new PanTool());
        assertThat(openDocument.document().components()).hasSize(2);
        assertThat(openDocument.history().undoDescription()).contains("command.paste");
    }
}
