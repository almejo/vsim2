package cl.almejo.vsim.ui.actions;

import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

/** Menu items that show a canvas key (Supr, R, F2…) without running by it: the canvas binding is the one that fires. */
class ShownAcceleratorItemTest {

    private static final KeyStroke DELETE = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
    private static final KeyStroke BACKSPACE = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0);

    private static KeyEvent pressed(JMenuItem item, KeyStroke stroke) {
        return new KeyEvent(item, KeyEvent.KEY_PRESSED, 0L, stroke.getModifiers(), stroke.getKeyCode(),
                KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void theItemShowsTheShownKeyOfItsActionAndFollowsIt() {
        Action delete = Actions.of("Borrar", () -> { });
        delete.putValue(Actions.SHOWN_ACCELERATOR, DELETE);

        ShownAcceleratorItem item = new ShownAcceleratorItem(delete);
        assertThat(item.getAccelerator()).isEqualTo(DELETE);

        delete.putValue(Actions.SHOWN_ACCELERATOR, BACKSPACE);
        assertThat(item.getAccelerator()).isEqualTo(BACKSPACE);
        delete.putValue(Actions.SHOWN_ACCELERATOR, null);
        assertThat(item.getAccelerator()).isNull();
    }

    @Test
    void theShownKeyNeverRunsTheItem() {
        int[] runs = {0};
        Action delete = Actions.of("Borrar", () -> runs[0]++);
        delete.putValue(Actions.SHOWN_ACCELERATOR, DELETE);
        ShownAcceleratorItem item = new ShownAcceleratorItem(delete);

        boolean fired = item.processKeyBinding(DELETE, pressed(item, DELETE), JMenuItem.WHEN_IN_FOCUSED_WINDOW, true);

        assertThat(fired).isFalse();
        assertThat(runs[0]).isZero();
    }

    @Test
    void aRealAcceleratorStaysTheItemsOwn() {
        KeyStroke controlZ = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
        Action tool = Actions.of("Zoom", controlZ, () -> { });
        tool.putValue(Actions.SHOWN_ACCELERATOR, KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0));

        ShownAcceleratorItem item = new ShownAcceleratorItem(tool);

        assertThat(item.getAccelerator()).as("a menu accelerator wins over a shown canvas key").isEqualTo(controlZ);
        assertThat(item.onlyShowsItsAccelerator()).isFalse();
    }

    @Test
    void theRadioItemOfAToolShowsItsCanvasKeyTooWithoutRunningByIt() {
        KeyStroke zed = KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0);
        Action tool = Actions.of("Zoom", () -> { });
        tool.putValue(Actions.SHOWN_ACCELERATOR, zed);

        ShownAcceleratorItem.Radio item = new ShownAcceleratorItem.Radio(tool);

        assertThat(item).isInstanceOf(JRadioButtonMenuItem.class);
        assertThat(item.getAccelerator()).isEqualTo(zed);
        assertThat(item.processKeyBinding(zed, pressed(item, zed), JMenuItem.WHEN_IN_FOCUSED_WINDOW, true)).isFalse();
    }
}
