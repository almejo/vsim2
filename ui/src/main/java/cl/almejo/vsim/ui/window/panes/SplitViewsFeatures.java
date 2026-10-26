package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Dividir la vista for one editor view (split views spec §2, canvas-edges §1–§3): the two split grips on every pane
 * ({@link SplitGripHandle}), Ventana ▸ «Dividir ▸» («Arriba y abajo», «A los lados», «Quitar división») and «Siguiente
 * panel» after «Nueva vista» (shortcut ids {@code window.splitHorizontal}, {@code window.splitVertical},
 * {@code window.removeSplit}, {@code window.nextPane}, no default keys). The menu puts a divider in the middle;
 * «Quitar división» removes both (the active pane stays); «Siguiente panel» makes the next pane in reading order the
 * active one and gives it the focus. None of it touches the document. {@link #dispose()} takes back the grips of every
 * pane, the menu items, the shortcut actions and the listener.
 */
public final class SplitViewsFeatures {

    private static final String SPLIT_HORIZONTAL_SHORTCUT = "window.splitHorizontal";
    private static final String SPLIT_VERTICAL_SHORTCUT = "window.splitVertical";
    private static final String REMOVE_SPLIT_SHORTCUT = "window.removeSplit";
    private static final String NEXT_PANE_SHORTCUT = "window.nextPane";
    private static final double MIDDLE = 0.5;

    private final EditorView view;
    private final CanvasPanes panes;
    private final Action splitHorizontal;
    private final Action splitVertical;
    private final Action removeSplit;
    private final Action nextPane;
    private final JMenu windowMenu;
    private final List<Component> menuComponents = new ArrayList<>();
    private final List<Runnable> removers = new ArrayList<>();
    private boolean disposed;

    public SplitViewsFeatures(EditorView view) {
        this.view = view;
        this.panes = view.canvasPanes();
        this.splitHorizontal = Actions.of(Messages.text("action.splitHorizontal"), () -> panes.split(Orientation.HORIZONTAL, MIDDLE));
        this.splitVertical = Actions.of(Messages.text("action.splitVertical"), () -> panes.split(Orientation.VERTICAL, MIDDLE));
        this.removeSplit = Actions.of(Messages.text("action.removeSplit"), panes::removeAll);
        this.nextPane = Actions.of(Messages.text("action.nextPane"), this::activateNextPane);
        this.windowMenu = view.menu(MenuId.WINDOW);
        insertMenuItems();
        removers.add(view.registerShortcutAction(SPLIT_HORIZONTAL_SHORTCUT, splitHorizontal));
        removers.add(view.registerShortcutAction(SPLIT_VERTICAL_SHORTCUT, splitVertical));
        removers.add(view.registerShortcutAction(REMOVE_SPLIT_SHORTCUT, removeSplit));
        removers.add(view.registerShortcutAction(NEXT_PANE_SHORTCUT, nextPane));
        removers.add(panes.addLayoutListener(this::refresh));
        removers.add(view.forEachPane(this::installOn));
        refresh();
    }

    /** On one pane: its two grips. */
    private Runnable installOn(CircuitCanvas pane) {
        Runnable horizontal = pane.addEdgeHandle(new SplitGripHandle(panes, pane, Orientation.HORIZONTAL));
        Runnable vertical = pane.addEdgeHandle(new SplitGripHandle(panes, pane, Orientation.VERTICAL));
        return () -> {
            horizontal.run();
            vertical.run();
        };
    }

    /** «Dividir ▸» and «Siguiente panel» right after «Nueva vista» (the list of open windows stays at the end). */
    private void insertMenuItems() {
        JMenu split = new JMenu(Messages.text("menu.window.split"));
        split.add(new JMenuItem(splitHorizontal));
        split.add(new JMenuItem(splitVertical));
        split.addSeparator();
        split.add(new JMenuItem(removeSplit));
        int index = indexAfterNewView();
        windowMenu.insert(split, index);
        menuComponents.add(split);
        JMenuItem next = new JMenuItem(nextPane);
        windowMenu.insert(next, index + 1);
        menuComponents.add(next);
    }

    private int indexAfterNewView() {
        Action newView = view.actions().newView();
        for (int index = 0; index < windowMenu.getMenuComponentCount(); index++) {
            if (windowMenu.getMenuComponent(index) instanceof JMenuItem item && item.getAction() == newView) {
                return index + 1;
            }
        }
        return windowMenu.getMenuComponentCount();
    }

    /** «Quitar división» and «Siguiente panel» only mean something while the window is split. */
    private void refresh() {
        boolean split = panes.panes().size() > 1;
        removeSplit.setEnabled(split);
        nextPane.setEnabled(split);
    }

    private void activateNextPane() {
        List<CircuitCanvas> all = panes.panes();
        if (all.size() < 2) {
            return;
        }
        CircuitCanvas next = all.get((all.indexOf(panes.active()) + 1) % all.size());
        panes.activate(next);
        next.requestFocusInWindow();
    }

    public Action splitHorizontalAction() {
        return splitHorizontal;
    }

    public Action splitVerticalAction() {
        return splitVertical;
    }

    public Action removeSplitAction() {
        return removeSplit;
    }

    public Action nextPaneAction() {
        return nextPane;
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        panes.hideSplitPreview();
        removers.forEach(Runnable::run);
        removers.clear();
        menuComponents.forEach(windowMenu::remove);
        menuComponents.clear();
    }
}
