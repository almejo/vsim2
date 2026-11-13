package cl.almejo.vsim.ui.busvalues;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuSlot;
import cl.almejo.vsim.ui.window.MenuSlots;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JToggleButton;
import java.awt.event.ActionEvent;

/**
 * The «Valores de los buses» toggle of one editor view: Ver ▸ «Valores de los buses» (a check box, shortcut id
 * {@code view.busValues}, no default key) and a toolbar toggle after the tool group, both on one {@code Action}
 * whose selected state mirrors {@link AppPreferences#showBusValues()}, so every pane and window shows the same
 * state. Only the labels on the canvas (and in the exports) hide; the tooltip, the tree and the waveforms keep the
 * value. {@link #dispose()} takes back the button, the item, the shortcut, the listener and gives the panes their
 * default back.
 */
public final class BusValuesFeatures {

    private static final String SHORTCUT = "view.busValues";

    private final EditorView view;
    private final AppPreferences preferences;
    private final Action toggle;
    private final Runnable menuRemover;
    private final JCheckBoxMenuItem menuItem;
    private final Runnable toolButtonRemover;
    private final Runnable shortcutRemover;
    private final Runnable paneInstallation;
    private final Runnable preferencesListener = this::refresh;
    private boolean disposed;

    public BusValuesFeatures(EditorView view) {
        this.view = view;
        this.preferences = view.services().preferences();
        this.toggle = new AbstractAction(Messages.text("action.busValues")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                preferences.setShowBusValues(!preferences.showBusValues());
            }
        };
        toggle.putValue(Action.SHORT_DESCRIPTION, Messages.text("action.busValues"));
        Icons.install(toggle, BusValuesIcons.BUS_VALUES);
        this.menuItem = new JCheckBoxMenuItem(toggle);
        this.menuRemover = MenuSlots.add(view, MenuSlot.BUS_VALUES, menuItem);
        this.toolButtonRemover = view.addToolbarToggleAfterTools(new JToggleButton(toggle));
        this.shortcutRemover = view.registerShortcutAction(SHORTCUT, toggle);
        preferences.addListener(preferencesListener);
        this.paneInstallation = view.forEachPane(this::installOn);
        refresh();
    }

    /** The toggle's action (menu item and toolbar button); its {@code SELECTED_KEY} is the preference. */
    public Action toggleAction() {
        return toggle;
    }

    /** A pane paints the bus values as the preference says until it goes, then as it always did. */
    private Runnable installOn(CircuitCanvas pane) {
        pane.setPaintBusValues(preferences.showBusValues());
        return () -> pane.setPaintBusValues(true);
    }

    /** Follows the preference (also when another window changes it): the toggle and every pane. */
    private void refresh() {
        boolean shown = preferences.showBusValues();
        toggle.putValue(Action.SELECTED_KEY, shown);
        for (CircuitCanvas pane : view.panes()) {
            pane.setPaintBusValues(shown);
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        preferences.removeListener(preferencesListener);
        paneInstallation.run();
        shortcutRemover.run();
        toolButtonRemover.run();
        menuRemover.run();
    }
}
