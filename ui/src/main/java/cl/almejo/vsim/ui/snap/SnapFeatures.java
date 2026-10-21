package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * «Ajustar» in one editor view (alignment-guides spec §2): the magnet toggle in the toolbar right after the tool
 * group and in Ver, and Ver ▸ «Ajustar a ▸» with Piezas / Guías / Líneas de la cuadrícula, all on the
 * {@link AppPreferences} snap booleans (so every view shows the same state). The view's canvas snaps with a
 * {@link DefaultSnapper}; when a snap preference, the viewport, the guides or the document (another view's edit)
 * change, the canvas's tool redoes its snapped preview ({@code refreshSnap}). {@link #dispose()} takes back the
 * button, the items, the shortcut registrations, the snapper and every listener.
 */
public final class SnapFeatures {

    static final String SNAP_SHORTCUT = "view.snap";
    static final String PARTS_SHORTCUT = "view.snapToParts";
    static final String GUIDES_SHORTCUT = "view.snapToGuides";
    static final String GRID_LINES_SHORTCUT = "view.snapToGridLines";

    private final EditorView view;
    private final AppPreferences preferences;
    private final DefaultSnapper snapper;
    private final Action toggle;
    private final Action parts;
    private final Action guides;
    private final Action gridLines;
    private final JMenu viewMenu;
    private final JCheckBoxMenuItem toggleItem;
    private final JMenu snapToMenu;
    private final List<Runnable> removals = new ArrayList<>();
    private SnapState lastState;
    private boolean disposed;

    public SnapFeatures(EditorView view) {
        this.view = view;
        this.preferences = view.services().preferences();
        CircuitCanvas canvas = view.canvas();
        this.snapper = new DefaultSnapper(preferences, canvas::getSize);
        removals.add(canvas.useSnapper(snapper));
        this.toggle = choice("action.snap", preferences::snapToAlignments, preferences::setSnapToAlignments);
        Icons.install(toggle, SnapIcons.SNAP);
        this.parts = choice("action.snapToParts", preferences::snapToParts, preferences::setSnapToParts);
        this.guides = choice("action.snapToGuides", preferences::snapToGuides, preferences::setSnapToGuides);
        this.gridLines = choice("action.snapToGridLines", preferences::snapToGridLines,
                preferences::setSnapToGridLines);
        refresh();
        this.lastState = SnapState.of(preferences);

        removals.add(view.addToolbarToggleAfterTools(new JToggleButton(toggle)));
        this.viewMenu = view.menu(MenuId.VIEW);
        this.toggleItem = new JCheckBoxMenuItem(toggle);
        this.snapToMenu = new JMenu(Messages.text("menu.view.snapTo"));
        snapToMenu.add(new JCheckBoxMenuItem(parts));
        snapToMenu.add(new JCheckBoxMenuItem(guides));
        snapToMenu.add(new JCheckBoxMenuItem(gridLines));
        int index = indexAfterGrid();
        viewMenu.insert(toggleItem, index);
        viewMenu.insert(snapToMenu, index + 1);
        removals.add(() -> {
            viewMenu.remove(snapToMenu);
            viewMenu.remove(toggleItem);
        });
        removals.add(view.registerShortcutAction(SNAP_SHORTCUT, toggle));
        removals.add(view.registerShortcutAction(PARTS_SHORTCUT, parts));
        removals.add(view.registerShortcutAction(GUIDES_SHORTCUT, guides));
        removals.add(view.registerShortcutAction(GRID_LINES_SHORTCUT, gridLines));

        Runnable preferencesListener = this::preferencesChanged;
        preferences.addListener(preferencesListener);
        removals.add(() -> preferences.removeListener(preferencesListener));
        removals.add(canvas.addViewportListener(this::refreshSnap));
        CircuitDocument document = view.openDocument().document();
        Runnable guideListener = this::refreshSnap;
        document.addGuideListener(guideListener);
        removals.add(() -> document.removeGuideListener(guideListener));
        // later, not inside the change: a tool committing its own drag must finish its release first
        DocumentListener documentListener = (changed, change) -> SwingUtilities.invokeLater(this::refreshSnap);
        document.addListener(documentListener);
        removals.add(() -> document.removeListener(documentListener));
    }

    /** «Ajustar»: the toolbar button, the Ver item and the shortcut {@code view.snap}. */
    public Action toggleAction() {
        return toggle;
    }

    /** «Ajustar a ▸ Piezas». */
    public Action partsAction() {
        return parts;
    }

    /** «Ajustar a ▸ Guías». */
    public Action guidesAction() {
        return guides;
    }

    /** «Ajustar a ▸ Líneas de la cuadrícula». */
    public Action gridLinesAction() {
        return gridLines;
    }

    /** The snapper the view's canvas uses. */
    public DefaultSnapper snapper() {
        return snapper;
    }

    /** What the toggle's tooltip says for these choices: what is snapped to, or that nothing is chosen. */
    static String tooltip(boolean toParts, boolean toGuides, boolean toGridLines) {
        List<String> chosen = new ArrayList<>();
        if (toParts) {
            chosen.add(Messages.text("snap.target.parts"));
        }
        if (toGuides) {
            chosen.add(Messages.text("snap.target.guides"));
        }
        if (toGridLines) {
            chosen.add(Messages.text("snap.target.gridLines"));
        }
        return switch (chosen.size()) {
            case 0 -> Messages.text("snap.tooltip.nothing");
            case 1 -> Messages.format("snap.tooltip", chosen.get(0));
            case 2 -> Messages.format("snap.tooltip", Messages.format("snap.list.two", chosen.get(0), chosen.get(1)));
            default -> Messages.format("snap.tooltip", Messages.format("snap.list.three", chosen.get(0),
                    chosen.get(1), chosen.get(2)));
        };
    }

    private Action choice(String nameKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        Action action = new AbstractAction(Messages.text(nameKey)) {
            @Override
            public void actionPerformed(ActionEvent event) {
                setter.accept(!getter.getAsBoolean());
                refresh();
            }
        };
        action.putValue(Action.SELECTED_KEY, getter.getAsBoolean());
        return action;
    }

    /** Takes the selected states and the tooltip from the preferences (also when another view changes them). */
    private void refresh() {
        toggle.putValue(Action.SELECTED_KEY, preferences.snapToAlignments());
        parts.putValue(Action.SELECTED_KEY, preferences.snapToParts());
        guides.putValue(Action.SELECTED_KEY, preferences.snapToGuides());
        gridLines.putValue(Action.SELECTED_KEY, preferences.snapToGridLines());
        toggle.putValue(Action.SHORT_DESCRIPTION, tooltip(preferences.snapToParts(), preferences.snapToGuides(),
                preferences.snapToGridLines()));
    }

    /** Any preference changed: the items follow, and a change of what snaps redoes the tool's snapped preview. */
    private void preferencesChanged() {
        if (disposed) {
            return;
        }
        refresh();
        SnapState state = SnapState.of(preferences);
        if (!state.equals(lastState)) {
            lastState = state;
            refreshSnap();
        }
    }

    private void refreshSnap() {
        if (!disposed) {
            view.canvas().tool().refreshSnap(view.canvas());
        }
    }

    /** Right after Ver ▸ «Cuadrícula ▸» (the grid extension's submenu), else at the end of Ver. */
    private int indexAfterGrid() {
        String gridText = Messages.text("menu.view.grid");
        for (int index = 0; index < viewMenu.getMenuComponentCount(); index++) {
            Component child = viewMenu.getMenuComponent(index);
            if (child instanceof JMenu menu && gridText.equals(menu.getText())) {
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
        for (int index = removals.size() - 1; index >= 0; index--) {
            removals.get(index).run();
        }
        removals.clear();
    }

    /** The preferences the magnet reads; a change of any of them redoes the snapped preview. */
    private record SnapState(boolean enabled, boolean toParts, boolean toGuides, boolean toGridLines,
                             boolean showGuides, int gridLineSpacing) {

        static SnapState of(AppPreferences preferences) {
            return new SnapState(preferences.snapToAlignments(), preferences.snapToParts(),
                    preferences.snapToGuides(), preferences.snapToGridLines(), preferences.showGuides(),
                    preferences.gridLineSpacing());
        }
    }
}
