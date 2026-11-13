package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;

/**
 * Ver ▸ «Resaltar la red al pasar»: a check box (shortcut id {@code view.highlightNet}, no default key) that mirrors
 * {@link AppPreferences#highlightNetOnHover()}, so every pane and window shows the same state. It sits right after
 * «Valores de los buses». The highlight and the signal card themselves are installed by {@link NetCardFeatures}.
 */
public final class NetCardExtension implements EditorExtension {

    static final String SHORTCUT = "view.highlightNet";

    @Override
    public Runnable install(EditorView view) {
        AppPreferences preferences = view.services().preferences();
        Action toggle = new AbstractAction(Messages.text("action.highlightNet")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                preferences.setHighlightNetOnHover(!preferences.highlightNetOnHover());
            }
        };
        toggle.putValue(Action.SHORT_DESCRIPTION, Messages.text("action.highlightNet"));
        Runnable preferencesListener = () ->
                toggle.putValue(Action.SELECTED_KEY, preferences.highlightNetOnHover());
        toggle.putValue(Action.SELECTED_KEY, preferences.highlightNetOnHover());
        JMenu viewMenu = view.menu(MenuId.VIEW);
        JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(toggle);
        menuItem.putClientProperty(Actions.SEARCH_TERMS, Messages.text("highlightNet.searchTerms"));
        viewMenu.insert(menuItem, indexAfterBusValues(viewMenu));
        Runnable shortcutRemover = view.registerShortcutAction(SHORTCUT, toggle);
        preferences.addListener(preferencesListener);
        Runnable featuresRemover = NetCardFeatures.install(view, preferences);
        return () -> {
            featuresRemover.run();
            preferences.removeListener(preferencesListener);
            shortcutRemover.run();
            viewMenu.remove(menuItem);
        };
    }

    private static int indexAfterBusValues(JMenu viewMenu) {
        String busValuesText = Messages.text("action.busValues");
        for (int index = 0; index < viewMenu.getMenuComponentCount(); index++) {
            if (viewMenu.getMenuComponent(index) instanceof JMenuItem item && busValuesText.equals(item.getText())) {
                return index + 1;
            }
        }
        return viewMenu.getMenuComponentCount();
    }
}
