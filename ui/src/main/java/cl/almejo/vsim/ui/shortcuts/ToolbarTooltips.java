package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Ends the tooltip of every toolbar button whose action the shortcut catalog names with its current shortcut, in
 * parentheses («Deshacer agregar (Ctrl+Z)», M6.1-E); no shortcut, no parentheses. The text is the action's
 * description when it has one, else its name, and follows both. {@link ShortcutsExtension} calls {@link #refresh()}
 * after every re-apply of the shortcuts.
 * <p>
 * The followers are added after the button's own and {@code ActionIcons}' listeners (both installed earlier), so on a
 * name or description change this one runs last and its text stays.
 */
final class ToolbarTooltips {

    private final Map<AbstractButton, String> idsByButton = new LinkedHashMap<>();
    private final Function<String, Optional<KeyStroke>> strokes;
    private final List<Runnable> removals = new ArrayList<>();

    /**
     * {@code actions} maps catalog ids to the view's actions; {@code strokes} gives the shortcut each id has right
     * now (what the extension applied).
     */
    ToolbarTooltips(JToolBar toolBar, Map<String, Action> actions, Function<String, Optional<KeyStroke>> strokes) {
        this.strokes = strokes;
        Map<Action, String> ids = new IdentityHashMap<>();
        actions.forEach((id, action) -> ids.putIfAbsent(action, id));
        for (Component child : toolBar.getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null
                    && ids.containsKey(button.getAction())) {
                Action action = button.getAction();
                idsByButton.put(button, ids.get(action));
                PropertyChangeListener follower = event -> {
                    if (Action.NAME.equals(event.getPropertyName())
                            || Action.SHORT_DESCRIPTION.equals(event.getPropertyName())) {
                        refresh(button);
                    }
                };
                action.addPropertyChangeListener(follower);
                removals.add(() -> action.removePropertyChangeListener(follower));
            }
        }
    }

    /** Rewrites every tooltip (after the shortcuts were applied again). */
    void refresh() {
        idsByButton.keySet().forEach(this::refresh);
    }

    /** Stops following the actions; the tooltips keep their last text (the buttons go away with the view). */
    void dispose() {
        removals.forEach(Runnable::run);
        removals.clear();
    }

    private void refresh(AbstractButton button) {
        Action action = button.getAction();
        Object description = action.getValue(Action.SHORT_DESCRIPTION);
        String text = String.valueOf(description != null ? description : action.getValue(Action.NAME));
        button.setToolTipText(strokes.apply(idsByButton.get(button))
                .map(stroke -> Messages.format("tooltip.withShortcut", text, KeyBindings.describe(stroke)))
                .orElse(text));
    }
}
