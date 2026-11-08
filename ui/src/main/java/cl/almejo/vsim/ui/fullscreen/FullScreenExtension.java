package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.util.Optional;

/**
 * Ver ▸ «Modo presentación (pantalla completa)» (F11, catalog id {@value #SHORTCUT_ID}) in every editor view: a
 * check item at the end of Ver, checked while the window is in the mode, and a toggle at the end of the toolbar,
 * pressed while it is on, whose tooltip says how to leave («Salir del modo presentación (F11 o Esc)»); both act on
 * the window's {@link FullScreenFeatures}, so every tab of a window shows the same. Outside a real window (tests) the
 * item and the toggle are there but disabled.
 */
public final class FullScreenExtension implements EditorExtension {

    static final String SHORTCUT_ID = "view.presentationMode";

    @Override
    public Runnable install(EditorView view) {
        Optional<FullScreenFeatures> features = view.windowCommands().fullScreen();
        AppPreferences preferences = view.services().preferences();
        Action toggle = Actions.of(Messages.text("action.presentationMode"),
                () -> features.ifPresent(FullScreenFeatures::toggle));
        Icons.install(toggle, FullScreenIcons.FULL_SCREEN);
        toggle.setEnabled(features.isPresent());

        JMenu viewMenu = view.menu(MenuId.VIEW);
        JPopupMenu.Separator menuSeparator = new JPopupMenu.Separator();
        JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(toggle);
        viewMenu.add(menuSeparator);
        viewMenu.add(menuItem);

        // not built on the action: its tooltip says how to leave, which the toolbar's shortcut tooltips would replace
        JToggleButton toolbarButton = new JToggleButton(Icons.get(FullScreenIcons.FULL_SCREEN, Icons.TOOLBAR_SIZE));
        toolbarButton.setFocusable(false);
        toolbarButton.setEnabled(features.isPresent());
        toolbarButton.addActionListener(event -> features.ifPresent(FullScreenFeatures::toggle));
        Runnable removeSeparator = view.addToolbarComponent(new JToolBar.Separator());
        Runnable removeButton = view.addToolbarComponent(toolbarButton);
        Runnable removeShortcut = view.registerShortcutAction(SHORTCUT_ID, toggle);

        Runnable follow = () -> {
            boolean active = features.map(FullScreenFeatures::isActive).orElse(false);
            toggle.putValue(Action.SELECTED_KEY, active);
            toolbarButton.setSelected(active);
            toolbarButton.setToolTipText(tooltip(preferences, active));
        };
        follow.run();
        Runnable stopFollowingTheMode = features.map(found -> found.addActiveListener(follow)).orElse(() -> {
        });
        preferences.addListener(follow); // another shortcut for the mode
        return () -> {
            preferences.removeListener(follow);
            stopFollowingTheMode.run();
            removeShortcut.run();
            removeButton.run();
            removeSeparator.run();
            viewMenu.remove(menuItem);
            viewMenu.remove(menuSeparator);
        };
    }

    /** «Modo presentación (F11)», or while on «Salir del modo presentación (F11 o Esc)», with the current shortcut. */
    static String tooltip(AppPreferences preferences, boolean active) {
        Optional<KeyStroke> stroke = new KeyBindings(preferences).strokeFor(SHORTCUT_ID);
        String key = active ? "fullScreen.tooltip.leave" : "fullScreen.tooltip.enter";
        return stroke.map(shortcut -> Messages.format(key, KeyBindings.describe(shortcut)))
                .orElseGet(() -> Messages.text(key + ".noShortcut"));
    }
}
