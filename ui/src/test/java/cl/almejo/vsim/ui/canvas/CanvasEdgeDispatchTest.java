package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.edges.EdgeGesture;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** How the canvas hands presses, drags, keys and painting to its edge controller (canvas-edges spec §4). */
class CanvasEdgeDispatchTest {

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
    private final List<String> log = new ArrayList<>();
    private final RecordingTool tool = new RecordingTool();

    @BeforeEach
    void setUp() {
        canvas.setSize(200, 100);
        canvas.overrideOnScreen(() -> false);
        canvas.setTool(tool);
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private final class RecordingTool implements Tool {
        final List<String> events = new ArrayList<>();

        @Override
        public void mousePressed(ToolContext context, CanvasMouseEvent event) {
            events.add("press");
        }

        @Override
        public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
            events.add("drag");
        }

        @Override
        public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
            events.add("release");
        }

        @Override
        public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
            events.add("key " + keyCode);
            return true;
        }
    }

    /** A 10 px strip along the top that paints itself red and logs its gesture. */
    private final class TopStrip implements EdgeHandle {
        @Override public Shape hitArea(Dimension canvasSize) { return new Rectangle(0, 0, canvasSize.width, 10); }
        @Override public int priority() { return 1; }
        @Override public Cursor cursor() { return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR); }
        @Override public String tooltip() { return "Arrastra"; }

        @Override
        public void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered) {
            screenGraphics.setColor(Color.RED);
            screenGraphics.fillRect(0, 0, canvasSize.width, 10);
        }

        @Override
        public EdgeGesture press(CanvasMouseEvent event) {
            log.add("press");
            return new EdgeGesture() {
                @Override public void drag(CanvasMouseEvent drag) { log.add("drag"); }
                @Override public void release(CanvasMouseEvent release) { log.add("release"); }
                @Override public void cancel() { log.add("cancel"); }
            };
        }
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    private void press(int x, int y, int modifiers, int button) {
        canvas.getMouseListeners()[0].mousePressed(mouse(MouseEvent.MOUSE_PRESSED, x, y, modifiers, button));
    }

    private void drag(int x, int y, int modifiers) {
        canvas.getMouseMotionListeners()[0].mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, x, y, modifiers,
                MouseEvent.NOBUTTON));
    }

    private void release(int x, int y, int button) {
        canvas.getMouseListeners()[0].mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, x, y, 0, button));
    }

    private void key(int keyCode) {
        canvas.getKeyListeners()[0].keyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void pressesOnAHandleNeverReachTheToolNorTheInterceptors() {
        canvas.addEdgeHandle(new TopStrip());
        List<String> intercepted = new ArrayList<>();
        canvas.addPressInterceptor(event -> {
            intercepted.add("intercepted");
            return false;
        });

        press(50, 5, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);
        drag(50, 60, InputEvent.BUTTON1_DOWN_MASK);
        release(50, 60, MouseEvent.BUTTON1);
        press(50, 5, InputEvent.BUTTON3_DOWN_MASK, MouseEvent.BUTTON3);
        release(50, 5, MouseEvent.BUTTON3);

        assertThat(log).containsExactly("press", "drag", "release");
        assertThat(tool.events).isEmpty();
        assertThat(intercepted).isEmpty();
        press(50, 50, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);
        assertThat(tool.events).containsExactly("press");
    }

    @Test
    void theMiddleButtonAndSpacePanEvenOnAHandle() {
        canvas.addEdgeHandle(new TopStrip());
        Point2D before = canvas.viewport().gridToScreen(0, 0);

        press(50, 5, InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2);
        drag(70, 25, InputEvent.BUTTON2_DOWN_MASK);
        release(70, 25, MouseEvent.BUTTON2);

        assertThat(canvas.viewport().gridToScreen(0, 0).getX()).isEqualTo(before.getX() + 20);
        assertThat(log).isEmpty();
        assertThat(tool.events).isEmpty();
    }

    @Test
    void escapeGoesToTheEdgeGestureFirst() {
        canvas.addEdgeHandle(new TopStrip());
        press(50, 5, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);

        key(KeyEvent.VK_ESCAPE);
        assertThat(log).containsExactly("press", "cancel");
        assertThat(tool.events).isEmpty();
        assertThat(canvas.isEdgeGestureActive()).isFalse();

        key(KeyEvent.VK_ESCAPE);
        assertThat(tool.events).containsExactly("key " + KeyEvent.VK_ESCAPE);
    }

    @Test
    void escapeFromElsewhereInTheWindowAlsoCancelsTheEdgeGesture() {
        canvas.addEdgeHandle(new TopStrip());
        press(50, 5, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);

        assertThat(canvas.pressKeyOnTool(KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(log).containsExactly("press", "cancel");
        assertThat(tool.events).isEmpty();
    }

    @Test
    void aToolChangeCancelsTheEdgeGestureAndTheReleaseStaysAway() {
        canvas.addEdgeHandle(new TopStrip());
        press(50, 5, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);

        canvas.useDefaultTool();
        canvas.setTool(tool);
        release(50, 60, MouseEvent.BUTTON1);

        assertThat(log).containsExactly("press", "cancel");
        assertThat(tool.events).isEmpty();
    }

    @Test
    void disposingTheCanvasCancelsTheEdgeGesture() {
        canvas.addEdgeHandle(new TopStrip());
        press(50, 5, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);

        canvas.dispose();

        assertThat(log).containsExactly("press", "cancel");
    }

    @Test
    void theHandleGivesTheCursorAndTheTooltipWhileThePointerIsOverIt() {
        canvas.addEdgeHandle(new TopStrip());

        canvas.getMouseMotionListeners()[0].mouseMoved(mouse(MouseEvent.MOUSE_MOVED, 50, 5, 0, MouseEvent.NOBUTTON));
        assertThat(canvas.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        assertThat(canvas.getToolTipText(mouse(MouseEvent.MOUSE_MOVED, 50, 5, 0, MouseEvent.NOBUTTON)))
                .isEqualTo("Arrastra");

        canvas.getMouseMotionListeners()[0].mouseMoved(mouse(MouseEvent.MOUSE_MOVED, 50, 50, 0, MouseEvent.NOBUTTON));
        assertThat(canvas.getCursor()).isEqualTo(Cursor.getDefaultCursor());
        assertThat(canvas.getToolTipText(mouse(MouseEvent.MOUSE_MOVED, 50, 50, 0, MouseEvent.NOBUTTON))).isNull();
    }

    @Test
    void handlesArePaintedOnTopInScreenCoordinatesAndGoWithTheirRemoval() {
        Runnable remover = canvas.addEdgeHandle(new TopStrip());
        canvas.viewport().zoomAt(0, 0, 2);
        BufferedImage painted = CanvasDragSupport.paint(canvas);
        assertThat(painted.getRGB(150, 9)).isEqualTo(Color.RED.getRGB());
        assertThat(painted.getRGB(150, 10)).isNotEqualTo(Color.RED.getRGB());

        remover.run();
        assertThat(canvas.edgeHandles()).isEmpty();
        assertThat(CanvasDragSupport.paint(canvas).getRGB(150, 5)).isNotEqualTo(Color.RED.getRGB());
    }
}
