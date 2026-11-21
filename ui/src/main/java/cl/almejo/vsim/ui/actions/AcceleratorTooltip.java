package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyNames;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.beans.PropertyChangeListener;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Keeps an action's tooltip ({@link Action#SHORT_DESCRIPTION}) in step with its key and its state: «text (Ctrl+E)»,
 * or just the text when the action has no {@link Action#ACCELERATOR_KEY} nor {@link Actions#SHOWN_ACCELERATOR} (a
 * canvas key: «Borrar la selección (Supr)»), and while it is disabled with a reason ({@link Actions#DISABLED_REASON})
 * «… — Selecciona algo primero». Rebinding the key in «Atajos de teclado» changes the accelerator or the shown key, and
 * the tooltip follows; so it does when the action turns on or off.
 */
public final class AcceleratorTooltip {

    /** The action property holding the refresh of the one follower that writes its tooltip. */
    private static final String FOLLOWER = "vsim.tooltipFollower";
    private static final Set<String> FOLLOWED = Set.of(Action.ACCELERATOR_KEY, Actions.SHOWN_ACCELERATOR, "enabled",
            Actions.DISABLED_REASON);

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
        return follow(action, () -> withKey(action, text.get()) + suffix.get());
    }

    /**
     * The reason of {@link Actions#disabledBecause} reaches the tooltip: through the follower {@link #install} gave the
     * action, or else one that keeps the tooltip it has now (none: only the reason, while disabled).
     */
    static void followReason(Action action) {
        if (action.getValue(FOLLOWER) instanceof Runnable refresh) {
            refresh.run();
            return;
        }
        Object description = action.getValue(Action.SHORT_DESCRIPTION);
        String text = description instanceof String own ? own : "";
        follow(action, () -> text);
    }

    private static Runnable follow(Action action, Supplier<String> text) {
        Runnable[] self = new Runnable[1];
        Runnable refresh = () -> {
            if (action.getValue(FOLLOWER) == self[0]) {
                action.putValue(Action.SHORT_DESCRIPTION, describe(action, text.get()));
            }
        };
        self[0] = refresh;
        action.putValue(FOLLOWER, refresh);
        PropertyChangeListener listener = event -> {
            if (FOLLOWED.contains(event.getPropertyName())) {
                refresh.run();
            }
        };
        action.addPropertyChangeListener(listener);
        refresh.run();
        return refresh;
    }

    /**
     * What a toolbar button of {@code action} shows as its tooltip: the description or the name, the action's key
     * and the button's {@code note} ({@link Actions#TOOLBAR_NOTE}), the same text {@code ToolbarTooltips} writes.
     */
    public static String toolbarText(Action action, String note) {
        Object description = action.getValue(Action.SHORT_DESCRIPTION);
        String text = String.valueOf(description != null ? description : action.getValue(Action.NAME));
        KeyStroke key = ShownAcceleratorItem.acceleratorOf(action);
        return Actions.withNote(key == null ? text : Messages.format("tooltip.withShortcut", text,
                KeyNames.describe(key)), note);
    }

    /** The name of the key of {@code action} («F8»), or the empty text when it has none. */
    public static String keyName(Action action) {
        KeyStroke key = ShownAcceleratorItem.acceleratorOf(action);
        return key == null ? "" : KeyNames.describe(key);
    }

    private static String withKey(Action action, String text) {
        KeyStroke key = ShownAcceleratorItem.acceleratorOf(action);
        return key == null ? text : Messages.format("tooltip.withKey", text, KeyNames.describe(key));
    }

    /** The text with the reason while disabled; null (no tooltip) when both are empty. */
    private static String describe(Action action, String text) {
        String described = Actions.disabledReason(action)
                .map(reason -> text.isEmpty() ? reason : Messages.format("tooltip.withReason", text, reason))
                .orElse(text);
        return described.isEmpty() ? null : described;
    }
}
