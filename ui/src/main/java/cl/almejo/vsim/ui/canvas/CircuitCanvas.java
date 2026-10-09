package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.panels.PaletteTransfer;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.ComponentPlacement;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/** The drawing surface of one window. Mouse input goes to the current {@link Tool}. */
public final class CircuitCanvas extends JComponent implements ToolContext {

    private static final int FRAME_MILLISECONDS = 16;
    private static final double WHEEL_SCROLL_PIXELS = 40;

    private final OpenDocument openDocument;
    private final CircuitRenderer renderer;
    private final CanvasHost host;
    private final HitTester hitTester;
    private final Selection selection = new Selection();
    private final Viewport viewport = new Viewport();
    private final Tool defaultTool = new SelectTool();
    private final Timer frameTimer;
    private final List<Consumer<Tool>> toolListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SimulationSnapshot>> snapshotListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> viewListeners = new CopyOnWriteArrayList<>();
    private final List<CanvasOverlay> overlays = new CopyOnWriteArrayList<>();
    private final DocumentListener documentListener;
    private Function<Point2D, Optional<String>> toolTipProvider = gridPoint -> Optional.empty();
    private Tool tool = defaultTool;
    private SimulationSnapshot displayedSnapshot = SimulationSnapshot.EMPTY;
    private boolean spaceDown;
    private Point panAnchor;

    public CircuitCanvas(OpenDocument openDocument, CircuitRenderer renderer, CanvasHost host) {
        this.openDocument = openDocument;
        this.renderer = renderer;
        this.host = host;
        this.hitTester = new HitTester(openDocument.registry());
        this.documentListener = this::documentChanged;
        setFocusable(true);
        setOpaque(true);
        setPreferredSize(new Dimension(900, 650));
        openDocument.document().addListener(documentListener);
        selection.addListener(this::repaint);
        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
        addKeyListener(new KeyHandler());
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                spaceDown = false;
                panAnchor = null;
            }
        });
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    String text = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    Point point = support.isDrop() ? support.getDropLocation().getDropPoint()
                            : new Point(getWidth() / 2, getHeight() / 2);
                    return dropComponent(text, point);
                } catch (UnsupportedFlavorException | IOException exception) {
                    return false;
                }
            }
        });
        frameTimer = new Timer(FRAME_MILLISECONDS, event -> refreshSnapshot());
    }

    /** Places the palette entry encoded in {@code transferText} at {@code screenPoint}; false when it is not one. */
    public boolean dropComponent(String transferText, Point screenPoint) {
        Optional<ComponentType> type = PaletteTransfer.decode(transferText)
                .flatMap(typeId -> openDocument.registry().find(typeId));
        if (type.isEmpty()) {
            return false;
        }
        ComponentPlacement.place(this, type.get(), viewport.snap(screenPoint.x, screenPoint.y), Rotation.R0);
        useDefaultTool();
        requestFocusInWindow();
        return true;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        frameTimer.start();
    }

    @Override
    public void removeNotify() {
        frameTimer.stop();
        super.removeNotify();
    }

    public void dispose() {
        frameTimer.stop();
        openDocument.document().removeListener(documentListener);
    }

    public Tool tool() {
        return tool;
    }

    public Tool defaultTool() {
        return defaultTool;
    }

    public void bindKey(KeyStroke stroke, String name, Action action) {
        getInputMap(WHEN_FOCUSED).put(stroke, name);
        getActionMap().put(name, action);
    }

    public void addToolListener(Consumer<Tool> listener) {
        toolListeners.add(listener);
    }

    public void addSnapshotListener(Consumer<SimulationSnapshot> listener) {
        snapshotListeners.add(listener);
    }

    public void addViewListener(Runnable listener) {
        viewListeners.add(listener);
    }

    public void zoomIn() {
        viewport.zoomIn(getWidth() / 2.0, getHeight() / 2.0);
        viewChanged();
    }

    public void zoomOut() {
        viewport.zoomOut(getWidth() / 2.0, getHeight() / 2.0);
        viewChanged();
    }

    public void resetZoom() {
        viewport.resetZoom(getWidth() / 2.0, getHeight() / 2.0);
        viewChanged();
    }

    public void centerCircuit() {
        CircuitBounds.of(openDocument.document(), openDocument.registry())
                .ifPresent(bounds -> viewport.center(bounds, getWidth(), getHeight()));
        viewChanged();
    }

    /** Centers the view on {@code bounds} (grid units) without changing the zoom. */
    public void centerOn(Bounds bounds) {
        viewport.center(bounds, getWidth(), getHeight());
        viewChanged();
    }

    public void addOverlay(CanvasOverlay overlay) {
        overlays.add(overlay);
        repaint();
    }

    /** {@code provider} receives the mouse position in grid units; empty means no tooltip. */
    public void setToolTipProvider(Function<Point2D, Optional<String>> provider) {
        toolTipProvider = provider;
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return toolTipProvider.apply(viewport.screenToGrid(event.getX(), event.getY())).orElse(null);
    }

    // ToolContext

    @Override
    public OpenDocument openDocument() {
        return openDocument;
    }

    @Override
    public Selection selection() {
        return selection;
    }

    @Override
    public HitTester hitTester() {
        return hitTester;
    }

    @Override
    public Viewport viewport() {
        return viewport;
    }

    @Override
    public ColorScheme scheme() {
        return renderer.scheme();
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        renderer.paintGhost(graphics, element);
    }

    @Override
    public void setTool(Tool next) {
        tool.deactivated(this);
        tool = next;
        next.activated(this);
        setCursor(next.cursor());
        toolListeners.forEach(listener -> listener.accept(next));
        repaint();
    }

    @Override
    public void useDefaultTool() {
        setTool(defaultTool);
    }

    @Override
    public void showContextMenu(Optional<DocumentElement> target, Point2D screenPosition) {
        host.showContextMenu(this, target, screenPosition);
    }

    @Override
    public void configure(Component component) {
        host.configure(component);
    }

    // Painting and snapshots

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            renderer.paintBackground(local, viewport, getWidth(), getHeight());
            local.transform(viewport.documentTransform());
            RenderState state = new RenderState(openDocument.document(), openDocument.netlist(),
                    openDocument.netlistToken(), displayedSnapshot, selection.ids());
            renderer.paint(local, state);
            for (CanvasOverlay overlay : overlays) {
                overlay.paint(local, state);
            }
            tool.paintOverlay(local, this);
        } finally {
            local.dispose();
        }
    }

    SimulationSnapshot displayedSnapshot() {
        return displayedSnapshot;
    }

    /** Called every frame: publishes a new snapshot and repaints only if something visible changed. */
    void refreshSnapshot() {
        SimulationSnapshot latest = openDocument.latestSnapshot();
        if (latest == displayedSnapshot) {
            return;
        }
        boolean visibleChange = latest.token() != displayedSnapshot.token()
                || !latest.netValues().equals(displayedSnapshot.netValues())
                || !latest.componentStates().equals(displayedSnapshot.componentStates())
                || !latest.problems().equals(displayedSnapshot.problems());
        displayedSnapshot = latest;
        if (visibleChange) {
            repaint();
        }
        snapshotListeners.forEach(listener -> listener.accept(latest));
    }

    private void documentChanged(CircuitDocument document, DocumentChange change) {
        selection.retain(change);
        repaint();
    }

    private void viewChanged() {
        viewListeners.forEach(Runnable::run);
        repaint();
    }

    private CanvasMouseEvent convert(MouseEvent event) {
        int button = SwingUtilities.isRightMouseButton(event) ? CanvasMouseEvent.SECONDARY
                : SwingUtilities.isMiddleMouseButton(event) ? CanvasMouseEvent.MIDDLE
                : CanvasMouseEvent.PRIMARY;
        return new CanvasMouseEvent(viewport.screenToGrid(event.getX(), event.getY()),
                viewport.snap(event.getX(), event.getY()), new Point2D.Double(event.getX(), event.getY()),
                button, event.isShiftDown(), event.isControlDown(), event.getClickCount());
    }

    private final class MouseHandler extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent event) {
            requestFocusInWindow();
            if (SwingUtilities.isMiddleMouseButton(event) || (spaceDown && SwingUtilities.isLeftMouseButton(event))) {
                panAnchor = event.getPoint();
                return;
            }
            tool.mousePressed(CircuitCanvas.this, convert(event));
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (panAnchor != null) {
                viewport.panBy(event.getX() - panAnchor.x, event.getY() - panAnchor.y);
                panAnchor = event.getPoint();
                repaint();
                return;
            }
            tool.mouseDragged(CircuitCanvas.this, convert(event));
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (panAnchor != null) {
                panAnchor = null;
                return;
            }
            tool.mouseReleased(CircuitCanvas.this, convert(event));
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            tool.mouseMoved(CircuitCanvas.this, convert(event));
        }

        @Override
        public void mouseExited(MouseEvent event) {
            tool.mouseExited(CircuitCanvas.this);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent event) {
            double rotation = event.getPreciseWheelRotation();
            if (event.isControlDown()) {
                viewport.zoomAt(event.getX(), event.getY(), viewport.zoom() * Math.pow(Viewport.ZOOM_STEP, -rotation));
                viewChanged();
            } else if (event.isShiftDown()) {
                viewport.panBy(-rotation * WHEEL_SCROLL_PIXELS, 0);
                repaint();
            } else {
                viewport.panBy(0, -rotation * WHEEL_SCROLL_PIXELS);
                repaint();
            }
        }
    }

    private final class KeyHandler extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                spaceDown = true;
                event.consume();
                return;
            }
            if (tool.keyPressed(CircuitCanvas.this, event.getKeyCode(), event.isShiftDown())) {
                event.consume();
            }
        }

        @Override
        public void keyReleased(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                spaceDown = false;
            }
        }
    }
}
