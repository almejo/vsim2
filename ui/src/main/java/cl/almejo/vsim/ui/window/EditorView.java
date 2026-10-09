package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.actions.ContextMenuFactory;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.PlaceComponentTool;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.WireTool;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Content of an editor window: toolbar, palette, canvas, inspector and status bar, wired to one {@link OpenDocument}.
 * Feature code adds menu items through {@link #menu(MenuId)}, inspector tabs through {@link #inspector()} and
 * context-menu items through {@link #addContextMenuContributor}.
 */
public final class EditorView extends JPanel implements CanvasHost {

    private static final int PALETTE_WIDTH = 230;

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final EditorActions actions;
    private final PalettePanel palette;
    private final StatusBar statusBar = new StatusBar();
    private final InspectorPanel inspector = new InspectorPanel();
    private final JMenuBar menuBar;
    private final Map<MenuId, JMenu> menus = new EnumMap<>(MenuId.class);
    private final List<ContextMenuContributor> contextMenuContributors = new CopyOnWriteArrayList<>();
    private final JToolBar toolBar = new JToolBar();
    private final ButtonGroup toolButtons = new ButtonGroup();
    private final JToggleButton selectButton;
    private final JToggleButton wireButton;
    private final JToggleButton panButton;

    public EditorView(OpenDocument openDocument, PainterRegistry painters, ColorScheme scheme, WindowCommands window) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        CircuitRenderer renderer = new CircuitRenderer(openDocument.registry(), painters, scheme);
        this.canvas = new CircuitCanvas(openDocument, renderer, this);
        this.actions = new EditorActions(openDocument, canvas, window);
        this.palette = new PalettePanel(openDocument.registry(), painters, scheme, type -> {
            canvas.setTool(new PlaceComponentTool(type));
            canvas.requestFocusInWindow();
        });
        this.menuBar = actions.createMenuBar();
        indexMenus();
        selectButton = new JToggleButton(actions.selectTool());
        wireButton = new JToggleButton(actions.wireTool());
        panButton = new JToggleButton(actions.panTool());
        buildToolBar();

        JSplitPane inspectorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, inspector);
        inspectorSplit.setContinuousLayout(true);
        inspectorSplit.setResizeWeight(1);
        inspectorSplit.setOneTouchExpandable(true);
        inspectorSplit.setDividerSize(0); // no divider while the inspector is hidden; InspectorPanel restores it
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palette, inspectorSplit);
        split.setDividerLocation(PALETTE_WIDTH);
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        add(toolBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        canvas.addToolListener(this::toolChanged);
        canvas.addSnapshotListener(statusBar::showSnapshot);
        canvas.addViewListener(() -> statusBar.showZoom(canvas.viewport().zoom()));
        statusBar.showSnapshot(SimulationSnapshot.EMPTY);
        statusBar.showZoom(canvas.viewport().zoom());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", actions.delete());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete", actions.delete());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotateClockwise", actions.rotateClockwise());
        canvas.bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK), "rotateCounterClockwise",
                actions.rotateCounterClockwise());
        bindZoomKeys();
        toolChanged(canvas.tool());
    }

    public OpenDocument openDocument() { return openDocument; }
    public CircuitCanvas canvas() { return canvas; }
    public EditorActions actions() { return actions; }
    public PalettePanel palette() { return palette; }
    public StatusBar statusBar() { return statusBar; }
    public InspectorPanel inspector() { return inspector; }
    public JMenuBar menuBar() { return menuBar; }
    public JToolBar toolBar() { return toolBar; }

    /** The menu {@code id} of this window's menu bar. */
    public JMenu menu(MenuId id) {
        JMenu menu = menus.get(id);
        if (menu == null) {
            throw new IllegalStateException("the menu bar has no " + id + " menu");
        }
        return menu;
    }

    public void addContextMenuContributor(ContextMenuContributor contributor) {
        contextMenuContributors.add(contributor);
    }

    /**
     * Builds the right-click menu for {@code target} (the element under the mouse) clicked at {@code gridPosition}
     * (grid units) without showing it.
     */
    public JPopupMenu contextMenu(Optional<DocumentElement> target, Point2D gridPosition) {
        return ContextMenuFactory.create(target, actions, canvas.selection(), gridPosition, contextMenuContributors);
    }

    public void dispose() {
        canvas.dispose();
    }

    @Override
    public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target, Point2D screenPosition) {
        Point2D gridPosition = canvas.viewport().screenToGrid(screenPosition.getX(), screenPosition.getY());
        contextMenu(target, gridPosition).show(source, (int) screenPosition.getX(), (int) screenPosition.getY());
    }

    @Override
    public void configure(Component component) {
        actions.configure(component);
    }

    private void indexMenus() {
        for (int index = 0; index < menuBar.getMenuCount(); index++) {
            JMenu menu = menuBar.getMenu(index);
            if (menu.getClientProperty(MenuId.class) instanceof MenuId id) {
                menus.put(id, menu);
            }
        }
    }

    /** Menu accelerators allow one keystroke each; these cover "+" on non-US layouts and the keypad. */
    private void bindZoomKeys() {
        int mask = actions.menuShortcutMaskForBindings();
        var keys = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, mask), "zoomIn");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, mask), "zoomIn");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, mask | InputEvent.SHIFT_DOWN_MASK), "zoomIn");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, mask), "zoomOut");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, mask), "zoomReset");
        getActionMap().put("zoomIn", actions.zoomIn());
        getActionMap().put("zoomOut", actions.zoomOut());
        getActionMap().put("zoomReset", actions.zoomReset());
    }

    private void buildToolBar() {
        toolBar.setFloatable(false);
        toolBar.add(actions.newDocument());
        toolBar.add(actions.open());
        toolBar.add(actions.save());
        toolBar.addSeparator();
        toolBar.add(actions.undo());
        toolBar.add(actions.redo());
        toolBar.addSeparator();
        for (JToggleButton button : new JToggleButton[]{selectButton, wireButton, panButton}) {
            toolButtons.add(button);
            toolBar.add(button);
        }
        toolBar.addSeparator();
        toolBar.add(actions.start());
        toolBar.add(actions.pause());
        toolBar.add(actions.step());
        toolBar.add(actions.createSpeedSelector());
        toolBar.addSeparator();
        toolBar.add(actions.zoomOut());
        toolBar.add(actions.zoomIn());
        toolBar.add(actions.centerView());
    }

    private void toolChanged(Tool tool) {
        switch (tool) {
            case SelectTool select -> selectButton.setSelected(true);
            case WireTool wire -> wireButton.setSelected(true);
            case PanTool pan -> panButton.setSelected(true);
            default -> toolButtons.clearSelection();
        }
        if (!(tool instanceof PlaceComponentTool)) {
            palette.clearSelection();
        }
    }
}
