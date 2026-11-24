package cl.almejo.vsim.ui.panels;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class MouseClicksTest {

    private final JPanel source = new JPanel();

    private MouseEvent click(int button) {
        return new MouseEvent(source, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false, button);
    }

    @Test
    void onClickRunsOnAnyButton() {
        int[] runs = {0};
        MouseListener listener = MouseClicks.onClick(() -> runs[0]++);

        listener.mousePressed(click(MouseEvent.BUTTON1));
        listener.mouseReleased(click(MouseEvent.BUTTON1));
        assertThat(runs[0]).isZero();
        listener.mouseClicked(click(MouseEvent.BUTTON1));
        listener.mouseClicked(click(MouseEvent.BUTTON3));
        assertThat(runs[0]).isEqualTo(2);
    }

    @Test
    void onLeftClickIgnoresTheOtherButtons() {
        int[] runs = {0};
        MouseListener listener = MouseClicks.onLeftClick(() -> runs[0]++);

        listener.mouseClicked(click(MouseEvent.BUTTON3));
        listener.mouseClicked(click(MouseEvent.BUTTON2));
        assertThat(runs[0]).isZero();
        listener.mouseClicked(click(MouseEvent.BUTTON1));
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test
    void swallowingListenerDoesNothingButExists() {
        source.addMouseListener(MouseClicks.swallowing());
        assertThat(source.getMouseListeners()).hasSize(1);
        source.dispatchEvent(click(MouseEvent.BUTTON1));
    }

    @Test
    void popupListenerAsksOnPressAndReleaseOnly() {
        int[] asked = {0};
        MouseListener listener = new PopupMouseListener(event -> asked[0]++);

        listener.mouseClicked(click(MouseEvent.BUTTON1));
        listener.mouseEntered(click(MouseEvent.BUTTON1));
        assertThat(asked[0]).isZero();
        listener.mousePressed(click(MouseEvent.BUTTON3));
        listener.mouseReleased(click(MouseEvent.BUTTON3));
        assertThat(asked[0]).isEqualTo(2);
    }
}
