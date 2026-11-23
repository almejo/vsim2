package cl.almejo.vsim.ui.actions;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/**
 * A menu item that shows the key of its action's {@link Actions#SHOWN_ACCELERATOR} but never runs by it: the key
 * belongs to someone who knows when it is typing and when it is the shortcut. The canvas keys of the editor (Supr, R,
 * F2, F8, Mayús+1, Z…) are bindings of the focused canvas, so a «r» typed in a text field never rotates; their items in
 * Editar, Ver and Herramientas show the key the user gave them (I1) and the canvas binding stays the only one that
 * fires. Ayuda ▸ «Atajos de teclado» shows «?» the same way: the sheet's key hook owns it. An action with a real menu
 * accelerator ({@link Action#ACCELERATOR_KEY}) keeps it: the item shows and runs it like any other.
 */
public class ShownAcceleratorItem extends JMenuItem {

    public ShownAcceleratorItem(Action action) {
        super(action);
    }

    @Override
    protected void configurePropertiesFromAction(Action action) {
        super.configurePropertiesFromAction(action);
        setAccelerator(acceleratorOf(action));
    }

    @Override
    protected void actionPropertyChanged(Action action, String propertyName) {
        super.actionPropertyChanged(action, propertyName);
        followAccelerator(this, action, propertyName);
    }

    /** Public so the menus' tests can check that a shown key never runs the item; false then. */
    @Override
    public boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
        return !onlyShowsItsAccelerator() && super.processKeyBinding(stroke, event, condition, pressed);
    }

    /** Shows {@code action}'s key again on {@code item} when the property that changed is one of its keys. */
    static void followAccelerator(JMenuItem item, Action action, String propertyName) {
        if (Actions.SHOWN_ACCELERATOR.equals(propertyName) || Action.ACCELERATOR_KEY.equals(propertyName)) {
            item.setAccelerator(acceleratorOf(action));
        }
    }

    /** True while the key on the item is a shown one: its action has no menu accelerator of its own. */
    public boolean onlyShowsItsAccelerator() {
        return onlyShown(getAction());
    }

    /** The menu accelerator of {@code action}, else its shown key; null for none. */
    static KeyStroke acceleratorOf(Action action) {
        if (action == null) {
            return null;
        }
        if (action.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke accelerator) {
            return accelerator;
        }
        return action.getValue(Actions.SHOWN_ACCELERATOR) instanceof KeyStroke shown ? shown : null;
    }

    static boolean onlyShown(Action action) {
        return action != null && !(action.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke);
    }

    /** The same for a tool of Herramientas: one radio item of the tools' group (M4), Zoom showing its Z. */
    public static final class Radio extends JRadioButtonMenuItem {

        public Radio(Action action) {
            super(action);
        }

        @Override
        protected void configurePropertiesFromAction(Action action) {
            super.configurePropertiesFromAction(action);
            setAccelerator(acceleratorOf(action));
        }

        @Override
        protected void actionPropertyChanged(Action action, String propertyName) {
            super.actionPropertyChanged(action, propertyName);
            followAccelerator(this, action, propertyName);
        }

        /** Public so the menus' tests can check that a shown key never runs the item; false then. */
        @Override
        public boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
            return !onlyShown(getAction()) && super.processKeyBinding(stroke, event, condition, pressed);
        }
    }
}
