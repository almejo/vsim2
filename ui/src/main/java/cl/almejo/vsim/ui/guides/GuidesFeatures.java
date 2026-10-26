package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.command.SetGuidesCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The guides of one editor view (spec §4.2–§4.5, canvas-edges spec §1–§4): on every pane ({@link PaneGuides}) the
 * rulers along the top and left edges with their corner, the guide lines and their grabbing; once for the view Ver ▸
 * «Mostrar guías» / «Agregar guía horizontal» / «Agregar guía vertical» / «Borrar guías» (shortcut ids
 * {@code view.showGuides}, {@code view.addHorizontalGuide}, {@code view.addVerticalGuide}, {@code view.clearGuides},
 * no default keys) and the canvas context menu items. Guides are document data, shared by every view of the circuit;
 * «Mostrar guías» is an app preference. Every edit is one {@link SetGuidesCommand}. {@link #dispose()} takes back the
 * edge handles and the overlay of every pane, the menu items, the shortcuts, the context-menu contributor and the
 * listeners.
 */
public final class GuidesFeatures {

    /** The rulers' thickness, in logical pixels. */
    static final int RULER_SIZE = 10;
    /** How close to a guide line a press grabs it, in screen pixels. */
    static final int GRAB_TOLERANCE = 3;
    private static final String SHOW_SHORTCUT = "view.showGuides";
    private static final String ADD_HORIZONTAL_SHORTCUT = "view.addHorizontalGuide";
    private static final String ADD_VERTICAL_SHORTCUT = "view.addVerticalGuide";
    private static final String CLEAR_SHORTCUT = "view.clearGuides";

    /** Shows a popup menu (headless tests record it instead). */
    @FunctionalInterface
    interface MenuShower {
        void show(JPopupMenu menu, Component invoker, int x, int y);
    }

    private final EditorView view;
    private final OpenDocument openDocument;
    private final AppPreferences preferences;
    private final MenuShower menuShower;
    private final Action showGuides;
    private final Action addHorizontal;
    private final Action addVertical;
    private final Action clear;
    private final JMenu viewMenu;
    private final List<Component> menuComponents = new ArrayList<>();
    private final List<Runnable> removers = new ArrayList<>();
    /** The rulers, lines and drags of each pane of the view. */
    private final Map<CircuitCanvas, PaneGuides> panes = new LinkedHashMap<>();
    private final Runnable preferencesListener = this::refresh;
    private final Runnable guideListener = this::guidesChanged;
    private boolean disposed;

    public GuidesFeatures(EditorView view) {
        this(view, (menu, invoker, x, y) -> menu.show(invoker, x, y));
    }

    GuidesFeatures(EditorView view, MenuShower menuShower) {
        this.view = view;
        this.openDocument = view.openDocument();
        this.preferences = view.services().preferences();
        this.menuShower = menuShower;
        this.showGuides = Actions.of(Messages.text("action.showGuides"), () -> preferences.setShowGuides(!preferences.showGuides()));
        this.addHorizontal = Actions.of(Messages.text("action.addHorizontalGuide"),
                () -> addAtPoint(Orientation.HORIZONTAL, pointerOrMiddle()));
        this.addVertical = Actions.of(Messages.text("action.addVerticalGuide"),
                () -> addAtPoint(Orientation.VERTICAL, pointerOrMiddle()));
        this.clear = Actions.of(Messages.text("action.clearGuides"),
                () -> SetGuidesCommand.clearing(document()).ifPresent(openDocument::execute));
        this.viewMenu = view.menu(MenuId.VIEW);
        insertMenuItems();
        removers.add(view.registerShortcutAction(SHOW_SHORTCUT, showGuides));
        removers.add(view.registerShortcutAction(ADD_HORIZONTAL_SHORTCUT, addHorizontal));
        removers.add(view.registerShortcutAction(ADD_VERTICAL_SHORTCUT, addVertical));
        removers.add(view.registerShortcutAction(CLEAR_SHORTCUT, clear));
        removers.add(view.addContextMenuContributor(new GuideMenuContributor()));
        preferences.addListener(preferencesListener);
        document().addGuideListener(guideListener);
        removers.add(view.forEachPane(this::installOn));
        refresh();
        guidesChanged();
    }

    /** On one pane: its rulers and lines, shown while «Mostrar guías» is on. */
    private Runnable installOn(CircuitCanvas pane) {
        PaneGuides guides = new PaneGuides(this, pane);
        panes.put(pane, guides);
        guides.show(preferences.showGuides());
        return () -> {
            panes.remove(pane);
            guides.dispose();
        };
    }

    /** Ver ▸ «Mostrar guías»; its {@code SELECTED_KEY} is the preference. */
    public Action showGuidesAction() {
        return showGuides;
    }

    /** Ver ▸ «Agregar guía horizontal»: at the pointer when it is over the canvas, else in the middle of the pane. */
    public Action addHorizontalAction() {
        return addHorizontal;
    }

    public Action addVerticalAction() {
        return addVertical;
    }

    /** Ver ▸ «Borrar guías»: one step; disabled while the circuit has no guides. */
    public Action clearAction() {
        return clear;
    }

    // --- menus ---------------------------------------------------------------------------------------------------

    /**
     * Puts the items at the end of the group of «Cuadrícula ▸» (so the snapping items, installed before, stay next to
     * the grid); without the grid submenu, in their own group after «Centrar circuito».
     */
    private void insertMenuItems() {
        int index = endOfGridGroup();
        if (index < 0) {
            index = indexAfterCenterView();
            viewMenu.insertSeparator(index);
            menuComponents.add(viewMenu.getMenuComponent(index));
            index++;
        }
        List<JMenuItem> items = List.of(new JCheckBoxMenuItem(showGuides), new JMenuItem(addHorizontal),
                new JMenuItem(addVertical), new JMenuItem(clear));
        for (JMenuItem item : items) {
            viewMenu.insert(item, index++);
            menuComponents.add(item);
        }
    }

    private int endOfGridGroup() {
        String gridText = Messages.text("menu.view.grid");
        for (int index = 0; index < viewMenu.getMenuComponentCount(); index++) {
            if (viewMenu.getMenuComponent(index) instanceof JMenu submenu && gridText.equals(submenu.getText())) {
                int end = index + 1;
                while (end < viewMenu.getMenuComponentCount()
                        && !(viewMenu.getMenuComponent(end) instanceof JSeparator)) {
                    end++;
                }
                return end;
            }
        }
        return -1;
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

    /** The menu of the rulers' corner of {@code pane}: the guide items of Ver, the new guides in its middle. */
    void showCornerMenu(CircuitCanvas pane) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem(Actions.of(Messages.text("action.addHorizontalGuide"),
                () -> addAtPoint(Orientation.HORIZONTAL, middleOf(pane)))));
        menu.add(new JMenuItem(Actions.of(Messages.text("action.addVerticalGuide"),
                () -> addAtPoint(Orientation.VERTICAL, middleOf(pane)))));
        menu.addSeparator();
        menu.add(new JCheckBoxMenuItem(showGuides));
        menu.add(new JMenuItem(clear));
        menuShower.show(menu, pane, 0, RULER_SIZE);
    }

    /**
     * Empty canvas: «Agregar guía … aquí»; on a guide line: «Quitar guía». Nothing while the guides are hidden. The menu
     * opens on the pane that was right-clicked, which the press made the active one.
     */
    private final class GuideMenuContributor implements ContextMenuContributor {
        @Override
        public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                               Point2D gridPosition) {
            if (target.isPresent() || !preferences.showGuides()) {
                return;
            }
            Viewport viewport = view.canvas().viewport();
            Point2D screen = viewport.gridToScreen(gridPosition.getX(), gridPosition.getY());
            Optional<Guide> near = guideNear(document(), viewport, screen);
            if (near.isPresent()) {
                menu.add(new JMenuItem(Actions.of(Messages.text("action.removeGuide"), () -> removeGuide(near.get()))));
                return;
            }
            GridPoint point = new GridPoint((int) Math.round(gridPosition.getX()),
                    (int) Math.round(gridPosition.getY()));
            menu.add(new JMenuItem(Actions.of(Messages.text("action.addHorizontalGuideHere"),
                    () -> addAtPoint(Orientation.HORIZONTAL, point))));
            menu.add(new JMenuItem(Actions.of(Messages.text("action.addVerticalGuideHere"),
                    () -> addAtPoint(Orientation.VERTICAL, point))));
        }
    }

    // --- edits ---------------------------------------------------------------------------------------------------

    /** Where Ver ▸ «Agregar guía …» puts it: at the pointer over the active pane, else in that pane's middle. */
    private GridPoint pointerOrMiddle() {
        CircuitCanvas pane = view.canvas();
        return pane.mouseGridPoint().orElseGet(() -> middleOf(pane));
    }

    private static GridPoint middleOf(CircuitCanvas pane) {
        return pane.viewport().snap(pane.getWidth() / 2.0, pane.getHeight() / 2.0);
    }

    /** Adds the guide of {@code orientation} through {@code point}; a hidden set of guides is shown first. */
    private void addAtPoint(Orientation orientation, GridPoint point) {
        if (!preferences.showGuides()) {
            preferences.setShowGuides(true); // asked for a guide: show it
        }
        addGuide(new Guide(orientation, orientation == Orientation.HORIZONTAL ? point.y() : point.x()));
    }

    void addGuide(Guide guide) {
        SetGuidesCommand.adding(document(), guide).ifPresent(command -> {
            openDocument.execute(command);
            view.statusBar().showNotice(Messages.text("guides.added"));
        });
    }

    void moveGuide(Guide from, Guide to) {
        SetGuidesCommand.moving(document(), from, to).ifPresent(openDocument::execute);
    }

    void removeGuide(Guide guide) {
        SetGuidesCommand.removing(document(), guide).ifPresent(command -> {
            openDocument.execute(command);
            view.statusBar().showNotice(Messages.text("guides.removed"));
        });
    }

    // --- gestures ------------------------------------------------------------------------------------------------

    /** The list changed (this view, another one, undo): a drag in progress was identified by value, so it ends. */
    private void guidesChanged() {
        clear.setEnabled(!document().guides().isEmpty());
        List.copyOf(panes.values()).forEach(PaneGuides::guidesChanged);
    }

    // --- showing -------------------------------------------------------------------------------------------------

    /** Follows «Mostrar guías» (also when another view changes it): the rulers and the lines come and go with it. */
    private void refresh() {
        boolean shown = preferences.showGuides();
        showGuides.putValue(Action.SELECTED_KEY, shown);
        List.copyOf(panes.values()).forEach(guides -> guides.show(shown));
    }

    // --- geometry and painting -----------------------------------------------------------------------------------

    CircuitDocument document() {
        return openDocument.document();
    }

    /** Where {@code guide} crosses a pane shown by {@code viewport}: its y when horizontal, its x when vertical. */
    static double screenCoordinate(Viewport viewport, Guide guide) {
        return guide.orientation() == Orientation.HORIZONTAL
                ? viewport.gridToScreen(0, guide.position()).getY()
                : viewport.gridToScreen(guide.position(), 0).getX();
    }

    /** The guide within {@link #GRAB_TOLERANCE} screen pixels of {@code screenPoint}, the nearest if several are. */
    static Optional<Guide> guideNear(CircuitDocument document, Viewport viewport, Point2D screenPoint) {
        Guide nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Guide guide : document.guides()) {
            double along = guide.orientation() == Orientation.HORIZONTAL ? screenPoint.getY() : screenPoint.getX();
            double distance = Math.abs(screenCoordinate(viewport, guide) - along);
            if (distance <= GRAB_TOLERANCE && distance < nearestDistance) {
                nearest = guide;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** The cursor over a guide that can be grabbed: up-down for a horizontal guide, left-right for a vertical one. */
    static Cursor moveCursor(Guide guide) {
        return Cursor.getPredefinedCursor(guide.orientation() == Orientation.HORIZONTAL
                ? Cursor.N_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR);
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        preferences.removeListener(preferencesListener);
        document().removeGuideListener(guideListener);
        removers.forEach(Runnable::run);
        removers.clear();
        menuComponents.forEach(viewMenu::remove);
        menuComponents.clear();
    }
}
