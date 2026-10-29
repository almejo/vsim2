package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.actions.AcceleratorTooltip;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.preferences.PreferencesForm;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JToggleButton;
import java.awt.event.ActionEvent;

/**
 * The follow-mode toggle of one editor view (M6.7): a toolbar toggle button right after the tool group and a checkbox
 * item in Editar, both on one {@code Action} whose selected state mirrors {@link AppPreferences#wiresFollow()} (so
 * every window shows the same state). {@link #dispose()} takes back the button, the item, the shortcut registration
 * and the preferences listener.
 */
public final class WiresFollowFeatures {

    private final AppPreferences preferences;
    private final Action toggle;
    private final JMenu editMenu;
    private final JCheckBoxMenuItem menuItem;
    private final Runnable toolButtonRemover;
    private final Runnable shortcutRemover;
    private final Runnable preferencesListener;
    private final Runnable tooltipRefresh;
    private boolean disposed;

    public WiresFollowFeatures(EditorView view) {
        this.preferences = view.services().preferences();
        this.toggle = new AbstractAction(Messages.text("action.wiresFollow")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                preferences.setWiresFollow(!preferences.wiresFollow());
                view.statusBar().showNotice(Messages.format("wiresFollow.state", Messages.text("action.wiresFollow"),
                        Messages.text(preferences.wiresFollow() ? "wiresFollow.on" : "wiresFollow.off")));
            }
        };
        Icons.install(toggle, WiresFollowIcons.WIRES_FOLLOW);
        this.tooltipRefresh = AcceleratorTooltip.install(toggle, () -> Messages.text("action.wiresFollow"),
                this::invertSuffix);
        refresh();
        this.preferencesListener = this::refresh;
        preferences.addListener(preferencesListener);
        this.toolButtonRemover = view.addToolbarToggleAfterTools(new JToggleButton(toggle));
        this.editMenu = view.menu(MenuId.EDIT);
        this.menuItem = new JCheckBoxMenuItem(toggle);
        editMenu.add(menuItem);
        this.shortcutRemover = view.registerShortcutAction("edit.wiresFollow", toggle);
    }

    /** The toggle's action (toolbar button and Editar item); its {@code SELECTED_KEY} is the preference. */
    public Action toggleAction() {
        return toggle;
    }

    /** Takes the selected state and the tooltip from the preferences (also when another window changes them). */
    private void refresh() {
        toggle.putValue(Action.SELECTED_KEY, preferences.wiresFollow());
        tooltipRefresh.run();
    }

    /** « — Mayús al arrastrar invierte», empty when no key inverts. */
    private String invertSuffix() {
        AppPreferences.InvertModifier modifier = preferences.invertModifier();
        return modifier == AppPreferences.InvertModifier.NONE ? ""
                : Messages.format("wiresFollow.invertSuffix", PreferencesForm.invertModifierName(modifier));
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemover.run();
        editMenu.remove(menuItem);
        toolButtonRemover.run();
        preferences.removeListener(preferencesListener);
    }
}
