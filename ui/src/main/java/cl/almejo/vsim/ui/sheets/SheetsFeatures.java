package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.print.PageGeometry;
import cl.almejo.vsim.ui.print.PageLayout;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScopes;
import cl.almejo.vsim.ui.print.PrintedSheets;
import cl.almejo.vsim.ui.print.SheetGrid;
import cl.almejo.vsim.ui.print.SheetPlan;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The print sheets on the canvas of one editor view (sheets spec §0a, §3, §5): Ver ▸ «Mostrar hojas de impresión» (a
 * check box after «Borrar guías», shortcut id {@code view.showSheets}, no default key) and a toolbar toggle, both on
 * one {@code Action} whose selected state is this window's {@link AppPreferences#showSheets()} (layered, so the AI may
 * show them in one window only). While shown, every pane gets a {@link SheetOverlay} and a {@link SheetSummaryHandle}
 * (through {@link EditorView#forEachPane}), all reading one {@link SheetPicture} computed here once per change.
 * <p>
 * Showing is anchoring: the first view that lays a circuit's sheets out tiled takes the anchor in {@link SheetAnchors}
 * (the automatic layout's corner), so editing never reshuffles them; when this view stops showing them, the extension
 * clears the anchor unless another view of the circuit still shows them. Nothing here is a document edit: no command,
 * no undo step, the circuit is never marked modified. {@link #dispose()} takes back the item, the button, the shortcut,
 * the panes' overlays and handles and every listener.
 */
public final class SheetsFeatures {

    private static final String SHORTCUT = "view.showSheets";
    private static final String CLEAR_GUIDES_SHORTCUT = "view.clearGuides";

    /** Shows a popup menu (headless tests record it instead). */
    @FunctionalInterface
    interface MenuShower {
        void show(JPopupMenu menu, java.awt.Component invoker, int x, int y);
    }

    private final EditorView view;
    private final OpenDocument openDocument;
    private final AppPreferences preferences;
    private final SheetAnchors anchors;
    private final SheetsExtension extension;
    private final SheetsQuickOptions quickOptions;
    private final Action toggle;
    private final JMenu viewMenu;
    private final JCheckBoxMenuItem menuItem;
    private final List<Runnable> removers = new ArrayList<>();
    /** What each pane shows of the sheets. */
    private final Map<CircuitCanvas, PaneSheets> panes = new LinkedHashMap<>();
    private final Runnable preferencesListener = this::refresh;
    private final Runnable documentListener = this::layoutChanged;
    private final Consumer<CircuitDocument> anchorListener = this::anchorChanged;
    private Optional<SheetPicture> picture = Optional.empty();
    /** Whether {@link #picture} must be laid out again before it is read (spec §6: a dirty flag). */
    private boolean stale = true;
    private boolean disposed;

    SheetsFeatures(EditorView view, SheetAnchors anchors, SheetsExtension extension, MenuShower menuShower) {
        this.view = view;
        this.openDocument = view.openDocument();
        this.preferences = view.services().preferences();
        this.anchors = anchors;
        this.extension = extension;
        this.quickOptions = new SheetsQuickOptions(view, preferences, menuShower);
        this.toggle = new AbstractAction(Messages.text("action.showSheets")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                preferences.setShowSheets(!preferences.showSheets());
            }
        };
        toggle.putValue(Action.SHORT_DESCRIPTION, Messages.text("action.showSheets"));
        Icons.install(toggle, SheetsIcons.PRINT_SHEETS);
        this.viewMenu = view.menu(MenuId.VIEW);
        this.menuItem = new JCheckBoxMenuItem(toggle);
        viewMenu.insert(menuItem, indexInViewMenu());
        removers.add(view.addToolbarToggleAfterTools(new JToggleButton(toggle)));
        removers.add(view.registerShortcutAction(SHORTCUT, toggle));
        preferences.addListener(preferencesListener);
        openDocument.addChangeListener(documentListener);
        anchors.addListener(anchorListener);
        removers.add(view.forEachPane(this::installOn));
    }

    /** The toggle's action (menu item and toolbar button); its {@code SELECTED_KEY} is the preference. */
    public Action toggleAction() {
        return toggle;
    }

    CircuitDocument document() {
        return openDocument.document();
    }

    /** Whether this window shows the sheets now (its layered preference); never once disposed. */
    boolean isShowing() {
        return !disposed && preferences.showSheets();
    }

    /** On one pane: its overlay and summary label, there while the sheets are shown. */
    private Runnable installOn(CircuitCanvas pane) {
        PaneSheets paneSheets = new PaneSheets(this, pane);
        panes.put(pane, paneSheets);
        paneSheets.show(isShowing());
        return () -> {
            panes.remove(pane);
            paneSheets.dispose();
        };
    }

    /**
     * Follows the preferences (also when another window or the AI's layer changes them): the toggle, the panes, and
     * the anchor, released when this view stops showing the sheets and no other view of the circuit shows them. Any
     * preference may change the layout (the print options, the margin, the text font), so the picture is laid out
     * again.
     */
    void refresh() {
        if (disposed) {
            return;
        }
        boolean shown = isShowing();
        toggle.putValue(Action.SELECTED_KEY, shown);
        List.copyOf(panes.values()).forEach(paneSheets -> paneSheets.show(shown));
        if (!shown) {
            extension.releaseIfNoViewShows(document());
        }
        layoutChanged();
    }

    private void anchorChanged(CircuitDocument document) {
        if (document == document()) {
            layoutChanged();
        }
    }

    /** Something the sheets depend on changed: lay them out again on the next paint. */
    private void layoutChanged() {
        stale = true;
        panes.keySet().forEach(CircuitCanvas::repaint);
    }

    /**
     * The sheets as the panes draw them now; empty while hidden or when the circuit has nothing to lay out. Laid out
     * once per change and read by every pane. Laying them out tiled while none is held takes the anchor; laying them out
     * on one sheet drops it.
     */
    Optional<SheetPicture> picture() {
        if (stale) {
            Optional<SheetPicture> laidOut = layOut();
            stale = false; // after the anchor was taken, whose notice marked it stale again
            picture = laidOut;
        }
        return picture;
    }

    private Optional<SheetPicture> layOut() {
        if (!isShowing()) {
            return Optional.empty();
        }
        PrintOptions options = preferences.printOptions(openDocument.displayName());
        Optional<Rectangle2D> area = PrintScopes.wholeCircuitArea(document(), openDocument.registry(),
                options.includeStickyNotes());
        if (area.isEmpty()) {
            return Optional.empty();
        }
        PageGeometry geometry = PageGeometry.standard(options, preferences.printMarginPoints());
        PageLayout automatic = PageLayout.of(area.get(), geometry, options.scaleMode());
        if (!automatic.tiled()) {
            // one sheet or «Ajustar a una hoja»: nothing is anchored; the next tiled layout takes it again
            if (anchors.anchor(document()).isPresent()) {
                anchors.clear(document());
            }
        } else if (anchors.anchor(document()).isEmpty()) {
            anchors.take(document(), SheetPlan.anchorOf(automatic));
        }
        PageLayout layout = SheetPlan.layout(area.get(), geometry, options.scaleMode(), anchors.anchor(document()));
        SheetGrid grid = SheetGrid.of(layout);
        // the paper's rule, on the live document: the empty sheets «Saltar hojas vacías» leaves out
        PrintedSheets printed = PrintedSheets.of(grid, PrintScopes.wholeCircuitDrawnBounds(document(),
                openDocument.registry(), options.includeStickyNotes()), Optional.empty(), options.skipEmptySheets());
        return Optional.of(new SheetPicture(grid, printed, area.get(), cuts(grid),
                SheetSummaryText.of(options, layout, printed)));
    }

    /** The drawn bounds of the parts and frames a border cuts although they would fit one sheet (spec §3.4). */
    private List<Rectangle2D> cuts(SheetGrid grid) {
        if (!grid.tiled()) {
            return List.of();
        }
        List<Rectangle2D> cuts = new ArrayList<>();
        for (DocumentElement element : document().elements()) {
            if (element instanceof Component || element instanceof Frame) {
                Bounds bounds = DrawnBounds.of(element, openDocument.registry());
                Rectangle2D drawn = new Rectangle2D.Double(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                if (grid.isCut(drawn)) {
                    cuts.add(drawn);
                }
            }
        }
        return List.copyOf(cuts);
    }

    /** A click on {@code pane}'s summary label: the quick options popup under it. */
    void showQuickOptions(CircuitCanvas pane, int x, int y) {
        quickOptions.show(pane, x, y);
    }

    /**
     * Right after «Borrar guías»; without the guides at the end of the group of «Cuadrícula ▸»; else after «Centrar
     * circuito»; else at the end.
     */
    private int indexInViewMenu() {
        Action clearGuides = view.shortcutActions().get(CLEAR_GUIDES_SHORTCUT);
        for (int index = 0; clearGuides != null && index < viewMenu.getMenuComponentCount(); index++) {
            if (viewMenu.getMenuComponent(index) instanceof JMenuItem item && item.getAction() == clearGuides) {
                return index + 1;
            }
        }
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
        preferences.removeListener(preferencesListener);
        openDocument.removeChangeListener(documentListener);
        anchors.removeListener(anchorListener);
        removers.forEach(Runnable::run);
        removers.clear();
        viewMenu.remove(menuItem);
        extension.forget(this);
        extension.releaseIfNoViewShows(document());
    }
}
