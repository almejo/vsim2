package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.CanvasClicks;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tool that draws between clicks (a continuous wire's path) on the canvas: wheel, zoom and the middle button move the
 * view without losing it and its preview follows the pointer; near the edge the view scrolls by itself while it lasts;
 * Deshacer's drop and the interruptions reach it through the canvas.
 */
class DrawingBetweenClicksTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument,
            new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
            new CanvasHost() {
                @Override
                public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                            Point2D screenPosition) {
                }

                @Override
                public void configure(Component component) {
                }
            });
    private final RecordingTool tool = new RecordingTool();

    /** A tool that draws between clicks while {@code drawing}, and records where it is told the pointer moved. */
    private static final class RecordingTool implements Tool {
        boolean drawing = true;
        final List<Point2D> moves = new ArrayList<>();
        int presses;

        @Override
        public boolean drawsBetweenClicks() {
            return drawing;
        }

        @Override
        public boolean holdsGesture() {
            return drawing;
        }

        @Override
        public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
            moves.add(event.gridPosition());
        }

        @Override
        public void mousePressed(ToolContext context, CanvasMouseEvent event) {
            presses++;
        }

        Point2D lastMove() {
            return moves.getLast();
        }
    }

    @BeforeEach
    void useTheTool() {
        canvas.overrideOnScreen(() -> true);
        canvas.useManualAutoscrollClock();
        canvas.setSize(400, 300);
        canvas.setTool(tool);
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    private void moveTo(int x, int y) {
        canvas.getMouseMotionListeners()[0].mouseMoved(mouse(MouseEvent.MOUSE_MOVED, x, y, 0, MouseEvent.NOBUTTON));
    }

    private void wheel(int x, int y, int modifiers, int rotation) {
        MouseWheelEvent event = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0, modifiers, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
        canvas.getMouseWheelListeners()[0].mouseWheelMoved(event);
    }

    private Point2D gridUnder(int x, int y) {
        return canvas.viewport().screenToGrid(x, y);
    }

    @Test
    void theWheelScrollsTheViewAndThePreviewFollowsThePointer() {
        moveTo(200, 150);
        Point2D before = tool.lastMove();

        wheel(200, 150, 0, 2);

        assertThat(tool.lastMove()).isNotEqualTo(before).isEqualTo(gridUnder(200, 150));
    }

    @Test
    void zoomingWithControlAndTheWheelKeepsTheGestureUnderThePointer() {
        moveTo(120, 80);
        wheel(120, 80, InputEvent.CTRL_DOWN_MASK, -1);
        moveTo(300, 200);
        int movesBefore = tool.moves.size();

        canvas.zoomIn();

        assertThat(tool.moves).hasSizeGreaterThan(movesBefore);
        assertThat(tool.lastMove()).isEqualTo(gridUnder(300, 200));
    }

    @Test
    void theMiddleButtonPansWithoutAPressReachingTheTool() {
        // a middle-button pan carries the circuit with the pointer: the grid point under it stays the preview's
        moveTo(100, 100);
        canvas.getMouseListeners()[0].mousePressed(
                mouse(MouseEvent.MOUSE_PRESSED, 100, 100, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2));
        canvas.getMouseMotionListeners()[0].mouseDragged(
                mouse(MouseEvent.MOUSE_DRAGGED, 160, 130, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.NOBUTTON));
        canvas.getMouseListeners()[0].mouseReleased(
                mouse(MouseEvent.MOUSE_RELEASED, 160, 130, 0, MouseEvent.BUTTON2));

        assertThat(tool.presses).isZero();
        assertThat(tool.lastMove()).isEqualTo(gridUnder(160, 130));
    }

    @Test
    void aViewMoveWithNothingDrawnTellsTheToolNothing() {
        tool.drawing = false;
        moveTo(200, 150);
        int movesBefore = tool.moves.size();

        wheel(200, 150, 0, 2);

        assertThat(tool.moves).hasSize(movesBefore);
    }

    @Test
    void hoveringNearTheEdgeScrollsTheViewWhileTheToolDraws() {
        moveTo(395, 150);
        assertThat(canvas.isAutoscrolling()).isTrue();
        Point2D origin = canvas.viewport().gridToScreen(0, 0);

        canvas.autoscrollTick();

        assertThat(canvas.viewport().gridToScreen(0, 0).getX()).isLessThan(origin.getX());
        assertThat(tool.lastMove()).isEqualTo(gridUnder(395, 150)); // the preview went on with the view

        moveTo(200, 150);
        assertThat(canvas.isAutoscrolling()).isFalse();
    }

    @Test
    void hoveringNearTheEdgeDoesNotScrollWithNothingDrawn() {
        tool.drawing = false;

        moveTo(395, 150);

        assertThat(canvas.isAutoscrolling()).isFalse();
    }

    @Test
    void theHoverAutoscrollStopsWhenThePointerLeavesOrTheToolStopsDrawing() {
        moveTo(395, 150);
        canvas.getMouseListeners()[0].mouseExited(mouse(MouseEvent.MOUSE_EXITED, 405, 150, 0, MouseEvent.NOBUTTON));
        assertThat(canvas.isAutoscrolling()).isFalse();

        moveTo(395, 150);
        tool.drawing = false;
        Point2D origin = canvas.viewport().gridToScreen(0, 0);
        canvas.autoscrollTick();
        assertThat(canvas.isAutoscrolling()).isFalse();
        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(origin);
    }

    @Test
    void theCanvasDropsTheLastStepAndInterruptsAContinuousWire() {
        cl.almejo.vsim.core.geometry.GridPoint start = canvas.viewport().snap(80, 80);
        UiTestSupport.pinAt(openDocument, start.x(), start.y()); // Selección starts a wire at a pin
        canvas.useDefaultTool();
        Tool selection = canvas.tool();
        assertThat(canvas.dropLastToolStep()).isFalse(); // nothing drawn: the history undoes as usual
        assertThat(canvas.interruptGesture()).isEmpty();

        click(80, 80);
        click(160, 80);
        assertThat(canvas.dropLastToolStep()).isTrue();
        assertThat(selection.drawsBetweenClicks()).isTrue();

        assertThat(canvas.interruptGesture()).isPresent();
        assertThat(selection.drawsBetweenClicks()).isFalse();
        assertThat(openDocument.document().wires()).isEmpty();
    }

    private void click(int x, int y) {
        CanvasClicks.click(canvas, x, y);
    }
}
