package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.ui.shortcuts.KeyBindings;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.util.function.Supplier;

/**
 * Keeps an action's tooltip ({@link Action#SHORT_DESCRIPTION}) in step with its key: «text (Ctrl+E)», or just the
 * text when the action has no {@link Action#ACCELERATOR_KEY}. Rebinding the key in «Atajos de teclado» changes the
 * action's accelerator, and the tooltip follows.
 */
public final class AcceleratorTooltip {

    private AcceleratorTooltip() {
    }

    /** Installs the tooltip «text (key)» and refreshes it when the key changes; returns a refresh for text changes. */
    public static Runnable install(Action action, Supplier<String> text) {
        return install(action, text, () -> "");
    }

    /**
     * As {@link #install(Action, Supplier)}, with {@code suffix} (already including its separator, empty for none)
     * after the key: «text (key)suffix».
     */
    public static Runnable install(Action action, Supplier<String> text, Supplier<String> suffix) {
        Runnable refresh = () -> action.putValue(Action.SHORT_DESCRIPTION, describe(action, text.get(), suffix.get()));
        action.addPropertyChangeListener(event -> {
            if (Action.ACCELERATOR_KEY.equals(event.getPropertyName())) {
                refresh.run();
            }
        });
        refresh.run();
        return refresh;
    }

    private static String describe(Action action, String text, String suffix) {
        Object key = action.getValue(Action.ACCELERATOR_KEY);
        String keyText = key instanceof KeyStroke stroke ? " (" + KeyBindings.describe(stroke) + ")" : "";
        return text + keyText + suffix;
    }
}
