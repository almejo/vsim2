package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decided point 2 of 2026-11-08: Espacio held is still «hold to pan», and a tap of Espacio (pressed and released with
 * no pan in between) reaches the tool, which flips the elbow of the wire it draws.
 */
class CanvasSpaceTapTest {

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
    private final FlipCountingTool tool = new FlipCountingTool();

    /** A tool that counts the Space taps it is given and the keys that reach it. */
    private static final class FlipCountingTool implements Tool {
        int flips;
        int keys;

        @Override
        public boolean flipElbow(ToolContext context) {
            flips++;
            return true;
        }

        @Override
        public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
            keys++;
            return false;
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

    private KeyEvent space(int id) {
        KeyEvent event = new KeyEvent(canvas, id, 0, 0, KeyEvent.VK_SPACE, ' ');
        if (id == KeyEvent.KEY_PRESSED) {
            canvas.getKeyListeners()[0].keyPressed(event);
        } else {
            canvas.getKeyListeners()[0].keyReleased(event);
        }
        return event;
    }

    private MouseEvent mouse(int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    @Test
    void aTapOfSpaceFlipsTheElbowOnceAtTheRelease() {
        assertThat(space(KeyEvent.KEY_PRESSED).isConsumed()).isTrue();
        space(KeyEvent.KEY_PRESSED); // the key repeat of a held Space
        assertThat(tool.flips).isZero();
        assertThat(space(KeyEvent.KEY_RELEASED).isConsumed()).isTrue();
        assertThat(tool.flips).isEqualTo(1);
        assertThat(tool.keys).isZero(); // Space never reaches the tool's keys (nor the bindings)
    }

    @Test
    void spaceHeldWhileTheViewIsPannedDoesNotFlip() {
        space(KeyEvent.KEY_PRESSED);
        canvas.getMouseListeners()[0].mousePressed(
                mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        canvas.getMouseMotionListeners()[0].mouseDragged(
                mouse(MouseEvent.MOUSE_DRAGGED, 40, 30, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
        canvas.getMouseListeners()[0].mouseReleased(
                mouse(MouseEvent.MOUSE_RELEASED, 40, 30, 0, MouseEvent.BUTTON1));
        space(KeyEvent.KEY_RELEASED);

        assertThat(canvas.viewport().gridToScreen(0, 0)).isEqualTo(new Point2D.Double(30, 20));
        assertThat(tool.flips).isZero();
    }

    @Test
    void theNextTapAfterAPanFlipsAgain() {
        space(KeyEvent.KEY_PRESSED);
        canvas.getMouseListeners()[0].mousePressed(
                mouse(MouseEvent.MOUSE_PRESSED, 10, 10, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
        canvas.getMouseListeners()[0].mouseReleased(
                mouse(MouseEvent.MOUSE_RELEASED, 10, 10, 0, MouseEvent.BUTTON1));
        space(KeyEvent.KEY_RELEASED);
        space(KeyEvent.KEY_PRESSED);
        space(KeyEvent.KEY_RELEASED);

        assertThat(tool.flips).isEqualTo(1);
    }

    @Test
    void aSpaceWhosePressWasForgottenWithTheFocusDoesNotFlip() {
        space(KeyEvent.KEY_PRESSED);
        canvas.getFocusListeners()[0].focusLost(
                new java.awt.event.FocusEvent(canvas, java.awt.event.FocusEvent.FOCUS_LOST));
        space(KeyEvent.KEY_RELEASED);

        assertThat(tool.flips).isZero();
    }
}
