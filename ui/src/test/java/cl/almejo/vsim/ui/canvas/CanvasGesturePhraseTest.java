package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.wiring.SelectionMoves;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas asks its tool for the gesture phrase after every input event and when the tool changes, and tells its
 * window only when it changed; a canvas that lost the keyboard focus takes its phrase back until it regains it.
 */
class CanvasGesturePhraseTest {

    private final List<String> published = new ArrayList<>();
    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final SelectionMoves selectionMoves = SelectionMoves.detached();
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument,
            new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
            new CanvasHost() {
                @Override
                public SelectionMoves selectionMoves() {
                    return selectionMoves;
                }

                @Override
                public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                            Point2D screenPosition) {
                }

                @Override
                public void configure(Component component) {
                }

                @Override
                public void gesturePhraseChanged(CircuitCanvas source, String phrase) {
                    published.add(phrase);
                }
            });

    /** A tool whose phrase is on from the press to the release. */
    private static final class PressedTool implements Tool {
        private boolean pressed;

        @Override
        public void mousePressed(ToolContext context, CanvasMouseEvent event) {
            pressed = true;
        }

        @Override
        public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
            pressed = false;
        }

        @Override
        public String gesturePhrase(ToolContext context) {
            return pressed ? "Arrastrando" : "";
        }
    }

    private final PressedTool tool = new PressedTool();

    @BeforeEach
    void useTheTool() {
        canvas.overrideOnScreen(() -> true);
        canvas.useManualAutoscrollClock();
        canvas.setSize(400, 300);
        canvas.setTool(tool);
        published.clear();
    }

    @AfterEach
    void close() {
        canvas.dispose();
        openDocument.close();
    }

    private void press() {
        canvas.getMouseListeners()[0].mousePressed(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                InputEvent.BUTTON1_DOWN_MASK, 10, 10, 1, false, MouseEvent.BUTTON1));
    }

    private void release() {
        canvas.getMouseListeners()[0].mouseReleased(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, 10, 10, 1,
                false, MouseEvent.BUTTON1));
    }

    private void fireFocus(int id) {
        for (FocusListener listener : canvas.getFocusListeners()) {
            if (id == FocusEvent.FOCUS_LOST) {
                listener.focusLost(new FocusEvent(canvas, id));
            } else {
                listener.focusGained(new FocusEvent(canvas, id));
            }
        }
    }

    @Test
    void thePhraseGoesToTheHostWhenTheGestureStartsAndEnds() {
        press();
        release();

        assertThat(published).containsExactly("Arrastrando", "");
    }

    @Test
    void theHostIsNotToldAgainWhileThePhraseStaysTheSame() {
        press();
        canvas.getKeyListeners()[0].keyPressed(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a'));

        assertThat(published).containsExactly("Arrastrando");
    }

    @Test
    void changingTheToolClearsThePhrase() {
        press();
        canvas.setTool(new Tool() { });

        assertThat(published).containsExactly("Arrastrando", "");
        assertThat(canvas.gesturePhrase()).isEmpty();
    }

    @Test
    void losingTheKeyboardFocusClearsThePhraseAndRegainingItBringsItBack() {
        press();
        published.clear();

        fireFocus(FocusEvent.FOCUS_LOST);
        assertThat(published).containsExactly("");
        fireFocus(FocusEvent.FOCUS_GAINED);
        assertThat(published).containsExactly("", "Arrastrando");
    }

    /** A tool whose phrase names the invert key, as a move does. */
    private static final class InvertKeyTool implements Tool {
        @Override
        public String gesturePhrase(ToolContext context) {
            return "Invierte: " + context.selectionMoves().preferences().invertModifier();
        }
    }

    @Test
    void thePhraseIsToldAgainWhenAPreferenceItNamesChangesMidGesture() {
        canvas.setTool(new InvertKeyTool());
        published.clear();

        selectionMoves.preferences().setInvertModifier(AppPreferences.InvertModifier.ALT);

        assertThat(published).containsExactly("Invierte: ALT");
    }

    @Test
    void aDisposedCanvasNoLongerListensToThePreferences() {
        canvas.setTool(new InvertKeyTool());
        canvas.dispose();
        published.clear();

        selectionMoves.preferences().setInvertModifier(AppPreferences.InvertModifier.ALT);

        assertThat(published).isEmpty();
    }
}
