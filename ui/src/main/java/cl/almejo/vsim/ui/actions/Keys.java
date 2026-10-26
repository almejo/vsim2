package cl.almejo.vsim.ui.actions;

import java.util.function.BooleanSupplier;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Key bindings: the InputMap entry and its ActionMap action in one call. */
public final class Keys {

    private Keys() {
    }

    /** {@link #bind} for the common case: the key works while {@code component} has the focus. */
    public static void bindWhenFocused(JComponent component, KeyStroke keyStroke, String name, Runnable behavior) {
        bind(component, JComponent.WHEN_FOCUSED, keyStroke, name, behavior);
    }

    /** Binds {@code keyStroke} to an unnamed action running {@code behavior}, under {@code name} in both maps. */
    public static void bind(JComponent component, int condition, KeyStroke keyStroke, String name, Runnable behavior) {
        bind(component, condition, keyStroke, name, Actions.unnamed(behavior));
    }

    /** As above, and the action is enabled only while {@code enabled} says so. */
    public static void bind(JComponent component, int condition, KeyStroke keyStroke, String name,
                            BooleanSupplier enabled, Runnable behavior) {
        bind(component, condition, keyStroke, name, Actions.unnamed(enabled, behavior));
    }

    /** Binds {@code keyStroke} to {@code action} under {@code name} (several strokes may share one action). */
    public static void bind(JComponent component, int condition, KeyStroke keyStroke, String name, Action action) {
        component.getInputMap(condition).put(keyStroke, name);
        component.getActionMap().put(name, action);
    }
}
