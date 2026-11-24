package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The popup listener asks for the menu on every press and every release; only a popup trigger opens it. */
class PopupMouseListenerTest {

    private final JPanel source = new JPanel();
    private final List<MouseEvent> opened = new ArrayList<>();
    private final PopupMouseListener listener = new PopupMouseListener(event -> {
        if (event.isPopupTrigger()) {
            opened.add(event);
        }
    });

    private MouseEvent event(int id, boolean popupTrigger) {
        return new MouseEvent(source, id, 0L, 0, 3, 4, 1, popupTrigger);
    }

    @Test
    void aPressThatIsThePopupTriggerOpensThePopup() {
        MouseEvent press = event(MouseEvent.MOUSE_PRESSED, true);

        listener.mousePressed(press);

        assertThat(opened).containsExactly(press);
    }

    @Test
    void aReleaseThatIsThePopupTriggerOpensThePopup() {
        MouseEvent release = event(MouseEvent.MOUSE_RELEASED, true);

        listener.mouseReleased(release);

        assertThat(opened).containsExactly(release);
    }

    @Test
    void anythingThatIsNotThePopupTriggerOpensNothing() {
        listener.mousePressed(event(MouseEvent.MOUSE_PRESSED, false));
        listener.mouseReleased(event(MouseEvent.MOUSE_RELEASED, false));
        listener.mouseClicked(event(MouseEvent.MOUSE_CLICKED, true));
        listener.mouseEntered(event(MouseEvent.MOUSE_ENTERED, true));

        assertThat(opened).isEmpty();
    }
}
