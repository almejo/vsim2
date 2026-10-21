package cl.almejo.vsim.ui.grid;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * The grid style of one editor view (spec §4.6): Ver ▸ «Cuadrícula ▸» with the radio items Puntos / Líneas / Nada and
 * «Cambiar el estilo de la cuadrícula» (shortcut id {@code view.cycleGrid}, no default key), all on
 * {@link AppPreferences#gridStyle()}, so every view shows the same grid and repaints when it changes.
 * {@link #dispose()} takes back the submenu and its separator, the shortcut and the preferences listener, and gives
 * the canvas its default dots back.
 */
public final class GridFeatures {

    static final String CYCLE_SHORTCUT = "view.cycleGrid";

    private final EditorView view;
    private final AppPreferences preferences;
    private final JMenu viewMenu;
    private final JMenu gridMenu;
    private final Component separator;
    private final Map<GridStyle, JRadioButtonMenuItem> items = new EnumMap<>(GridStyle.class);
    private final Action cycle;
    private final Runnable shortcutRemover;
    private final Runnable preferencesListener;
    private boolean disposed;

    public GridFeatures(EditorView view) {
        this.view = view;
        this.preferences = view.services().preferences();
        this.gridMenu = new JMenu(Messages.text("menu.view.grid"));
        ButtonGroup group = new ButtonGroup();
        for (GridStyle style : GridStyle.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(choose(style));
            group.add(item);
            gridMenu.add(item);
            items.put(style, item);
        }
        this.cycle = new AbstractAction(Messages.text("action.cycleGrid")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                preferences.setGridStyle(next(preferences.gridStyle()));
            }
        };
        gridMenu.addSeparator();
        gridMenu.add(new JMenuItem(cycle));
        this.viewMenu = view.menu(MenuId.VIEW);
        int index = indexAfterCenterView();
        viewMenu.insertSeparator(index);
        this.separator = viewMenu.getMenuComponent(index);
        viewMenu.insert(gridMenu, index + 1);
        this.shortcutRemover = view.registerShortcutAction(CYCLE_SHORTCUT, cycle);
        this.preferencesListener = this::refresh;
        preferences.addListener(preferencesListener);
        refresh();
    }

    /** «Cambiar el estilo de la cuadrícula»: Puntos, then Líneas, then Nada, then Puntos again. */
    public Action cycleAction() {
        return cycle;
    }

    static GridStyle next(GridStyle style) {
        GridStyle[] styles = GridStyle.values();
        return styles[(style.ordinal() + 1) % styles.length];
    }

    private Action choose(GridStyle style) {
        return new AbstractAction(Messages.text(nameKey(style))) {
            @Override
            public void actionPerformed(ActionEvent event) {
                preferences.setGridStyle(style);
            }
        };
    }

    private static String nameKey(GridStyle style) {
        return switch (style) {
            case DOTS -> "grid.style.dots";
            case LINES -> "grid.style.lines";
            case NONE -> "grid.style.none";
        };
    }

    /** Shows the stored style in the items and on the canvas (also when another view changes it). */
    private void refresh() {
        GridStyle style = preferences.gridStyle();
        items.get(style).setSelected(true);
        view.canvas().setGridStyle(style);
    }

    private int indexAfterCenterView() {
        Action centerView = view.actions().centerView();
        for (int index = 0; index < viewMenu.getMenuComponentCount(); index++) {
            if (viewMenu.getMenuComponent(index) instanceof JMenuItem item && item.getAction() == centerView) {
                return index + 1;
            }
        }
        return viewMenu.getMenuComponentCount();
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shortcutRemover.run();
        preferences.removeListener(preferencesListener);
        viewMenu.remove(gridMenu);
        viewMenu.remove(separator);
        view.canvas().setGridStyle(GridStyle.DOTS);
    }
}
