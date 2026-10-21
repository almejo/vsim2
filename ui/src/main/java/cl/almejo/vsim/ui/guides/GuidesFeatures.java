package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.command.SetGuidesCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The guides of one editor view (spec §4.2–§4.5, canvas-edges spec §1–§4): the rulers along the top and left edges
 * of its canvas with their corner, the guide lines and their grabbing, Ver ▸ «Mostrar guías» / «Agregar guía
 * horizontal» / «Agregar guía vertical» / «Borrar guías» (shortcut ids {@code view.showGuides},
 * {@code view.addHorizontalGuide}, {@code view.addVerticalGuide}, {@code view.clearGuides}, no default keys) and the
 * canvas context menu items. Guides are document data, shared by every view of the circuit; «Mostrar guías» is an app
 * preference. Every edit is one {@link SetGuidesCommand}. {@link #dispose()} takes back the edge handles, the overlay,
 * the menu items, the shortcuts, the context-menu contributor and the listeners.
 */
public final class GuidesFeatures {

    /** The rulers' thickness, in logical pixels. */
    static final int RULER_SIZE = 10;
    /** How close to a guide line a press grabs it, in screen pixels. */
    static final int GRAB_TOLERANCE = 3;
    /** Length of a dash and of the gap after it, in logical pixels. */
    private static final int DASH = 4;
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
    private final CircuitCanvas canvas;
    private final AppPreferences preferences;
    private final MenuShower menuShower;
    private final Action showGuides;
    private final Action addHorizontal;
    private final Action addVertical;
    private final Action clear;
    private final JMenu viewMenu;
    private final List<Component> menuComponents = new ArrayList<>();
    private final List<Runnable> removers = new ArrayList<>();
    private final List<EdgeHandle> handles;
    private final GuideOverlay overlay;
    private final List<Runnable> chromeRemovers = new ArrayList<>();
    private final Runnable preferencesListener = this::refresh;
    private final Runnable guideListener = this::guidesChanged;
    private GuideDrag currentDrag;
    private boolean disposed;

    public GuidesFeatures(EditorView view) {
        this(view, (menu, invoker, x, y) -> menu.show(invoker, x, y));
    }

    GuidesFeatures(EditorView view, MenuShower menuShower) {
        this.view = view;
        this.openDocument = view.openDocument();
        this.canvas = view.canvas();
        this.preferences = view.services().preferences();
        this.menuShower = menuShower;
        this.showGuides = action("action.showGuides", () -> preferences.setShowGuides(!preferences.showGuides()));
        this.addHorizontal = action("action.addHorizontalGuide",
                () -> addAtPoint(Orientation.HORIZONTAL, pointerOrMiddle()));
        this.addVertical = action("action.addVerticalGuide",
                () -> addAtPoint(Orientation.VERTICAL, pointerOrMiddle()));
        this.clear = action("action.clearGuides",
                () -> SetGuidesCommand.clearing(document()).ifPresent(openDocument::execute));
        this.handles = List.of(new RulerCorner(this), new RulerHandle(this, RulerHandle.Side.TOP),
                new RulerHandle(this, RulerHandle.Side.LEFT), new GuideGrab(this));
        this.overlay = new GuideOverlay(this);
        this.viewMenu = view.menu(MenuId.VIEW);
        insertMenuItems();
        removers.add(view.registerShortcutAction(SHOW_SHORTCUT, showGuides));
        removers.add(view.registerShortcutAction(ADD_HORIZONTAL_SHORTCUT, addHorizontal));
        removers.add(view.registerShortcutAction(ADD_VERTICAL_SHORTCUT, addVertical));
        removers.add(view.registerShortcutAction(CLEAR_SHORTCUT, clear));
        removers.add(view.addContextMenuContributor(new GuideMenuContributor()));
        preferences.addListener(preferencesListener);
        document().addGuideListener(guideListener);
        refresh();
        guidesChanged();
    }

    private static Action action(String nameKey, Runnable run) {
        return new AbstractAction(Messages.text(nameKey)) {
            @Override
            public void actionPerformed(ActionEvent event) {
                run.run();
            }
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

    /** The menu of the rulers' corner: the guide items of Ver, the new guides in the middle of the pane. */
    void showCornerMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem(action("action.addHorizontalGuide",
                () -> addAtPoint(Orientation.HORIZONTAL, middleOfThePane()))));
        menu.add(new JMenuItem(action("action.addVerticalGuide",
                () -> addAtPoint(Orientation.VERTICAL, middleOfThePane()))));
        menu.addSeparator();
        menu.add(new JCheckBoxMenuItem(showGuides));
        menu.add(new JMenuItem(clear));
        menuShower.show(menu, canvas, 0, RULER_SIZE);
    }

    /** Empty canvas: «Agregar guía … aquí»; on a guide line: «Quitar guía». Nothing while the guides are hidden. */
    private final class GuideMenuContributor implements ContextMenuContributor {
        @Override
        public void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                               Point2D gridPosition) {
            if (target.isPresent() || !preferences.showGuides()) {
                return;
            }
            Point2D screen = canvas.viewport().gridToScreen(gridPosition.getX(), gridPosition.getY());
            Optional<Guide> near = guideNear(screen);
            if (near.isPresent()) {
                menu.add(new JMenuItem(action("action.removeGuide", () -> removeGuide(near.get()))));
                return;
            }
            GridPoint point = new GridPoint((int) Math.round(gridPosition.getX()),
                    (int) Math.round(gridPosition.getY()));
            menu.add(new JMenuItem(action("action.addHorizontalGuideHere",
                    () -> addAtPoint(Orientation.HORIZONTAL, point))));
            menu.add(new JMenuItem(action("action.addVerticalGuideHere",
                    () -> addAtPoint(Orientation.VERTICAL, point))));
        }
    }

    // --- edits ---------------------------------------------------------------------------------------------------

    private GridPoint pointerOrMiddle() {
        return canvas.mouseGridPoint().orElseGet(this::middleOfThePane);
    }

    private GridPoint middleOfThePane() {
        return canvas.viewport().snap(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
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

    GuideDrag startDrag(Orientation orientation, Optional<Guide> moving, CanvasMouseEvent press) {
        currentDrag = new GuideDrag(this, orientation, moving, press);
        canvas.repaint();
        return currentDrag;
    }

    /** The drag released or was cancelled: guide changes from now on are its own, or nobody's. */
    void dragEnded(GuideDrag drag) {
        if (currentDrag == drag) {
            currentDrag = null;
        }
        canvas.repaint();
    }

    boolean isHiddenByTheDrag(Guide guide) {
        return currentDrag != null && currentDrag.moving().filter(guide::equals).isPresent()
                && currentDrag.hidesTheMovedGuide();
    }

    /** The list changed (this view, another one, undo): a drag in progress was identified by value, so it ends. */
    private void guidesChanged() {
        clear.setEnabled(!document().guides().isEmpty());
        if (currentDrag != null) {
            canvas.cancelEdgeGesture();
        }
        canvas.repaint();
    }

    // --- showing -------------------------------------------------------------------------------------------------

    /** Follows «Mostrar guías» (also when another view changes it): the rulers and the lines come and go with it. */
    private void refresh() {
        boolean shown = preferences.showGuides();
        showGuides.putValue(Action.SELECTED_KEY, shown);
        if (shown && chromeRemovers.isEmpty()) {
            for (EdgeHandle handle : handles) {
                chromeRemovers.add(canvas.addEdgeHandle(handle));
            }
            canvas.addOverlay(overlay);
            chromeRemovers.add(() -> canvas.removeOverlay(overlay));
        } else if (!shown && !chromeRemovers.isEmpty()) {
            removeChrome();
        }
    }

    /** Takes the handles away (cancelling a drag in progress) and the overlay. */
    private void removeChrome() {
        chromeRemovers.forEach(Runnable::run);
        chromeRemovers.clear();
    }

    // --- geometry and painting -----------------------------------------------------------------------------------

    CircuitCanvas canvas() {
        return canvas;
    }

    CircuitDocument document() {
        return openDocument.document();
    }

    /** Where {@code guide} crosses the screen: its y for a horizontal guide, its x for a vertical one. */
    double screenCoordinate(Guide guide) {
        Viewport viewport = canvas.viewport();
        return guide.orientation() == Orientation.HORIZONTAL
                ? viewport.gridToScreen(0, guide.position()).getY()
                : viewport.gridToScreen(guide.position(), 0).getX();
    }

    /** The guide within {@link #GRAB_TOLERANCE} screen pixels of {@code screenPoint}, the nearest if several are. */
    Optional<Guide> guideNear(Point2D screenPoint) {
        Guide nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Guide guide : document().guides()) {
            double along = guide.orientation() == Orientation.HORIZONTAL ? screenPoint.getY() : screenPoint.getX();
            double distance = Math.abs(screenCoordinate(guide) - along);
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

    /**
     * Paints {@code guide} across the whole pane, dashed, 1 logical pixel wide, in the scheme's guide colour read now;
     * {@code screenGraphics} is in logical pixels.
     */
    void paintGuideLine(Graphics2D screenGraphics, Guide guide) {
        paintGuideLine(screenGraphics, guide, false);
    }

    /**
     * Like {@link #paintGuideLine(Graphics2D, Guide)}; a {@code highlighted} guide (the one the gesture under way
     * snapped to, spec §4.4) is drawn slightly stronger: solid and opaque instead of dashed.
     */
    void paintGuideLine(Graphics2D screenGraphics, Guide guide, boolean highlighted) {
        Color color = canvas.scheme().guide();
        screenGraphics.setColor(highlighted ? new Color(color.getRGB() | 0xFF000000) : color);
        int at = (int) Math.round(screenCoordinate(guide));
        boolean horizontal = guide.orientation() == Orientation.HORIZONTAL;
        int length = horizontal ? canvas.getWidth() : canvas.getHeight();
        int step = highlighted ? length : 2 * DASH;
        for (int start = 0; start < length; start += step) {
            int piece = highlighted ? length : Math.min(DASH, length - start);
            if (horizontal) {
                screenGraphics.fillRect(start, at, piece, 1);
            } else {
                screenGraphics.fillRect(at, start, 1, piece);
            }
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        removeChrome();
        currentDrag = null;
        preferences.removeListener(preferencesListener);
        document().removeGuideListener(guideListener);
        removers.forEach(Runnable::run);
        removers.clear();
        menuComponents.forEach(viewMenu::remove);
        menuComponents.clear();
    }
}
