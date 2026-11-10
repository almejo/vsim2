package cl.almejo.vsim.ui.shortcuts.sheet;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/**
 * A menu item that shows its accelerator but never runs by it: Ayuda ▸ «Atajos de teclado» shows «?», and the key
 * itself belongs to the sheet's key hook, which knows when «?» is typing (a text field) and when it is the shortcut.
 * A Swing binding of the typed «?» would also miss it on most keyboards (it comes with Mayús held).
 */
final class ShownAcceleratorItem extends JMenuItem {

    ShownAcceleratorItem(Action action, KeyStroke shown) {
        super(action);
        setAccelerator(shown);
    }

    @Override
    protected boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
        return false;
    }
}
