package cl.almejo.vsim.ui.shortcuts;

import javax.swing.KeyStroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Listens to the shortcuts table while «Cambiar…» waits for a combination (the cell reads «Presiona la
 * combinación…»): the first key that is not a lone modifier becomes the stroke, with the modifiers held; Esc cancels.
 * While active it consumes every key, so neither the table's keys (arrows, Enter, Tab) nor the dialog's Esc act.
 */
final class KeyCapture extends KeyAdapter {

    private static final Set<Integer> LONE_KEYS = Set.of(KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT,
            KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_META, KeyEvent.VK_WINDOWS, KeyEvent.VK_CAPS_LOCK, KeyEvent.VK_UNDEFINED);

    private final Consumer<KeyStroke> captured;
    private final Runnable cancelled;
    private boolean active;

    KeyCapture(Consumer<KeyStroke> captured, Runnable cancelled) {
        this.captured = captured;
        this.cancelled = cancelled;
    }

    void start() {
        active = true;
    }

    /** Deactivates without reporting: the owner abandoned the capture (focus lost, another row, a button). */
    void stop() {
        active = false;
    }

    boolean isActive() {
        return active;
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if (!active) {
            return;
        }
        event.consume();
        int keyCode = event.getKeyCode();
        if (LONE_KEYS.contains(keyCode)) {
            return;
        }
        active = false;
        if (keyCode == KeyEvent.VK_ESCAPE) {
            cancelled.run();
        } else {
            captured.accept(KeyStroke.getKeyStroke(keyCode, event.getModifiersEx()));
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        if (active) {
            event.consume();
        }
    }

    @Override
    public void keyTyped(KeyEvent event) {
        if (active) {
            event.consume();
        }
    }
}
