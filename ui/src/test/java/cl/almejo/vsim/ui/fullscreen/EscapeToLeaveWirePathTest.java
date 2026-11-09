package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.WireMode;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

import static cl.almejo.vsim.ui.CanvasClicks.click;
import static cl.almejo.vsim.ui.CanvasClicks.move;
import static org.assertj.core.api.Assertions.assertThat;

class EscapeToLeaveWirePathTest {

    private FullScreenFixture fixture;
    private EditorView view;
    private CircuitCanvas left;
    private CircuitCanvas right;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new FullScreenFixture(List.of(new FullScreenExtension()));
        view = fixture.addTab();
        fixture.features.enter();
        fixture.layOut();
        CanvasPanes panes = view.canvasPanes();
        panes.setSize(800, 300);
        panes.split(CanvasPanes.Orientation.VERTICAL, 0.5);
        panes.doLayout();
        left = view.panes().get(0);
        right = view.panes().get(1);
        left.applyViewState(new ViewState(1, left.getWidth() / 16.0, left.getHeight() / 16.0));
        right.applyViewState(new ViewState(1, 100, 20));
        view.services().preferences().setWireMode(WireMode.CONTINUOUS);
        UiTestSupport.pinAt(view.openDocument(), 5, 5);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private void dispatch(KeyEvent event) {
        EscapeToLeave escape = fixture.features.escape();
        if (escape.dispatchKeyEvent(event)) {
            return;
        }
        if (event.getComponent() instanceof CircuitCanvas canvas) {
            for (KeyListener listener : canvas.getKeyListeners()) {
                listener.keyPressed(event);
            }
        }
        if (!event.isConsumed()) {
            SwingUtilities.processKeyBindings(event);
        }
        escape.postProcessKeyEvent(event);
    }

    private static KeyEvent escapeOn(java.awt.Component source) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void escapeInTheDrawingPaneCancelsThePathAndStays() {
        click(left, 40, 40);
        assertThat(left.drawsBetweenClicks()).isTrue();
        dispatch(escapeOn(left));
        assertThat(left.drawsBetweenClicks()).isFalse();
        assertThat(fixture.features.isActive()).isTrue();
    }

    @Test
    void escapeInTheOtherPaneCancelsThePathAndStays() {
        click(left, 40, 40);
        move(right, 100, 96);
        click(right, 100, 96); // a corner in the other pane, which takes the focus
        assertThat(left.drawsBetweenClicks()).isTrue();
        dispatch(escapeOn(right));
        assertThat(fixture.features.isActive()).as("still in presentation mode").isTrue();
        assertThat(left.drawsBetweenClicks()).as("path cancelled").isFalse();
    }
}
