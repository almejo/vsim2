package cl.almejo.vsim.ui.shortcuts;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeyCaptureTest {

    private final List<KeyStroke> captured = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();
    private final KeyCapture capture = new KeyCapture(captured::add, () -> cancelled.add("esc"));
    private final JPanel source = new JPanel();

    private KeyEvent press(int keyCode, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void theFirstRealKeyWithItsModifiersIsCapturedAndConsumed() {
        capture.start();
        KeyEvent controlAlone = press(KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK);
        capture.keyPressed(controlAlone);
        assertThat(controlAlone.isConsumed()).isTrue();
        assertThat(captured).isEmpty();
        assertThat(capture.isActive()).isTrue();

        KeyEvent controlG = press(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK);
        capture.keyPressed(controlG);
        assertThat(controlG.isConsumed()).isTrue();
        assertThat(captured).containsExactly(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
        assertThat(capture.isActive()).isFalse();
    }

    @Test
    void nothingIsTouchedWhileInactive() {
        KeyEvent letter = press(KeyEvent.VK_A, 0);
        capture.keyPressed(letter);
        assertThat(letter.isConsumed()).isFalse();
        assertThat(captured).isEmpty();
    }

    @Test
    void escapeCancels() {
        capture.start();
        capture.keyPressed(press(KeyEvent.VK_ESCAPE, 0));
        assertThat(cancelled).containsExactly("esc");
        assertThat(captured).isEmpty();
        assertThat(capture.isActive()).isFalse();
    }
}
