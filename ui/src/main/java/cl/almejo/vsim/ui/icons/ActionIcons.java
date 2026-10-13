package cl.almejo.vsim.ui.icons;

import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.AbstractButton;
import javax.swing.Action;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts the SVG icons on the actions of an editor view (menus, toolbar, context menu) and turns the toolbar into
 * icon-only buttons whose tooltip names the action.
 */
public final class ActionIcons implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        iconNames(view).forEach(Icons::install);
        List<Runnable> removals = new ArrayList<>();
        for (Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() != null
                    && button.getAction().getValue(Action.LARGE_ICON_KEY) != null) {
                removals.add(showOnlyTheIcon(button));
            }
        }
        // Icons and hideActionText stay on purpose: the actions and buttons are per view and go away with it.
        return () -> removals.forEach(Runnable::run);
    }

    /** The icon of each action of {@code view}, by file name under {@code icons/}. */
    static Map<Action, String> iconNames(EditorView view) {
        EditorActions actions = view.actions();
        SelectionActions selection = view.selectionActions();
        Map<Action, String> names = new LinkedHashMap<>();
        names.put(actions.newDocument(), "new");
        names.put(actions.open(), "open");
        names.put(actions.save(), "save");
        names.put(actions.saveAs(), "save-as");
        names.put(actions.undo(), "undo");
        names.put(actions.redo(), "redo");
        names.put(actions.delete(), "delete");
        names.put(actions.rotateClockwise(), "rotate-clockwise");
        names.put(actions.rotateCounterClockwise(), "rotate-counter-clockwise");
        names.put(actions.configure(), "configure");
        names.put(actions.selectTool(), "select");
        names.put(actions.wireTool(), "wire");
        names.put(actions.panTool(), "pan");
        names.put(actions.zoomIn(), "zoom-in");
        names.put(actions.zoomOut(), "zoom-out");
        names.put(actions.zoomReset(), "zoom-reset");
        names.put(actions.centerView(), "center");
        names.put(actions.start(), "start");
        names.put(actions.pause(), "pause");
        names.put(actions.restart(), "restart");
        names.put(actions.step(), "step");
        names.put(selection.cut(), "cut");
        names.put(selection.copy(), "copy");
        names.put(selection.paste(), "paste");
        names.put(selection.duplicate(), "duplicate");
        return names;
    }

    /**
     * Hides the text of a toolbar button. When its action has no description, the tooltip shows the action name and
     * follows it (Undo becomes "Deshacer agregar"). Returns what removes the listener again.
     */
    private static Runnable showOnlyTheIcon(AbstractButton button) {
        button.setHideActionText(true);
        Action action = button.getAction();
        if (action.getValue(Action.SHORT_DESCRIPTION) != null) {
            return () -> {
            };
        }
        button.setToolTipText((String) action.getValue(Action.NAME));
        PropertyChangeListener nameFollower = event -> {
            if (Action.NAME.equals(event.getPropertyName())) {
                button.setToolTipText((String) event.getNewValue());
            }
        };
        action.addPropertyChangeListener(nameFollower);
        return () -> action.removePropertyChangeListener(nameFollower);
    }
}
