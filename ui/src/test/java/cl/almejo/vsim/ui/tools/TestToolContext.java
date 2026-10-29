package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.HitTester;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.MemoryPainter;
import cl.almejo.vsim.ui.snap.Snapper;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** ToolContext for tests: a real OpenDocument, recorded UI requests, no screen. */
public final class TestToolContext implements ToolContext, AutoCloseable {

    public final Selection selection = new Selection();
    public final Viewport viewport = new Viewport();
    public final List<DocumentElement> ghosts = new ArrayList<>();
    /** The tunnel colours each ghost was given, in the order of {@link #ghosts} (empty when none were given). */
    public final List<Map<String, Integer>> ghostTunnelColors = new ArrayList<>();
    public final Tool defaultTool = new SelectTool();
    public Tool tool = defaultTool;
    public Optional<DocumentElement> contextMenuTarget;
    public Component configured;
    public DocumentElement editedLabel;
    public GridPoint textAddedAt;
    public Component valueEdited;
    /** The memory whose content editor a double click asked for ({@link #editMemory}). */
    public Component memoryEdited;
    public int repaints;
    public double selectionDashPhase;
    public final List<CanvasCursor> cursors = new ArrayList<>();
    /** The status-bar notices of moves (M6.7), in order. */
    public final List<String> notices = new ArrayList<>();
    public final AppPreferences preferences = AppPreferences.inMemory();
    public final SelectionMoves selectionMoves = new SelectionMoves(preferences, notices::add);
    /** What the tools snap with; none unless a test sets one (every gesture as before the magnet). */
    public Snapper snapper = Snapper.none();
    /** The board colours the tools read at paint time; a test may switch it mid-gesture. */
    public ColorScheme scheme = ColorScheme.LIGHT;
    /** The pane's size in pixels ({@link #viewSize()}); a test may change it. */
    public Dimension paneSize = new Dimension(800, 600);
    /** Every view a tool asked to zoom to ({@link #zoomTo}), in order; each is also shown on {@link #viewport}. */
    public final List<ViewState> zoomTargets = new ArrayList<>();
    private final OpenDocument openDocument;
    private final HitTester hitTester;

    public TestToolContext(OpenDocument openDocument) {
        this.openDocument = openDocument;
        this.hitTester = new HitTester(openDocument.registry());
    }

    public static TestToolContext withNewDocument() {
        return new TestToolContext(OpenDocument.createEmpty(StandardComponentTypes.registry()));
    }

    public static CanvasMouseEvent at(double gridX, double gridY) {
        return event(gridX, gridY, CanvasMouseEvent.PRIMARY, 1, false);
    }

    public static CanvasMouseEvent event(double gridX, double gridY, int button, int clickCount, boolean shiftDown) {
        return new CanvasMouseEvent(new Point2D.Double(gridX, gridY),
                new GridPoint((int) Math.round(gridX), (int) Math.round(gridY)),
                new Point2D.Double(gridX * GridPoint.PIXELS_PER_UNIT, gridY * GridPoint.PIXELS_PER_UNIT),
                button, shiftDown, false, clickCount);
    }

    public static Graphics2D scratchGraphics() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    public void click(Tool target, double gridX, double gridY) {
        target.mousePressed(this, at(gridX, gridY));
        target.mouseReleased(this, at(gridX, gridY));
    }

    public void drag(Tool target, double fromX, double fromY, double toX, double toY) {
        target.mousePressed(this, at(fromX, fromY));
        target.mouseDragged(this, at(toX, toY));
        target.mouseReleased(this, at(toX, toY));
    }

    public static CanvasMouseEvent withModifiers(double gridX, double gridY, boolean shiftDown, boolean controlDown) {
        return new CanvasMouseEvent(new Point2D.Double(gridX, gridY),
                new GridPoint((int) Math.round(gridX), (int) Math.round(gridY)),
                new Point2D.Double(gridX * GridPoint.PIXELS_PER_UNIT, gridY * GridPoint.PIXELS_PER_UNIT),
                CanvasMouseEvent.PRIMARY, shiftDown, controlDown, 1);
    }

    /** A primary event with every modifier spelled out (Alt included, M6.7). */
    public static CanvasMouseEvent withKeys(double gridX, double gridY, boolean shiftDown, boolean controlDown,
                                            boolean altDown) {
        return new CanvasMouseEvent(new Point2D.Double(gridX, gridY),
                new GridPoint((int) Math.round(gridX), (int) Math.round(gridY)),
                new Point2D.Double(gridX * GridPoint.PIXELS_PER_UNIT, gridY * GridPoint.PIXELS_PER_UNIT),
                CanvasMouseEvent.PRIMARY, shiftDown, controlDown, altDown, 1);
    }

    /** A plain press, then the drag and the release with Shift and Alt as given (held after the press). */
    public void dragHolding(Tool target, double fromX, double fromY, double toX, double toY, boolean shiftDown,
                            boolean altDown) {
        target.mousePressed(this, at(fromX, fromY));
        target.mouseDragged(this, withKeys(toX, toY, shiftDown, false, altDown));
        target.mouseReleased(this, withKeys(toX, toY, shiftDown, false, altDown));
    }

    public void clickWith(Tool target, double gridX, double gridY, boolean shiftDown, boolean controlDown) {
        target.mousePressed(this, withModifiers(gridX, gridY, shiftDown, controlDown));
        target.mouseReleased(this, withModifiers(gridX, gridY, shiftDown, controlDown));
    }

    public void dragWith(Tool target, double fromX, double fromY, double toX, double toY,
                         boolean shiftDown, boolean controlDown) {
        target.mousePressed(this, withModifiers(fromX, fromY, shiftDown, controlDown));
        target.mouseDragged(this, withModifiers(toX, toY, shiftDown, controlDown));
        target.mouseReleased(this, withModifiers(toX, toY, shiftDown, controlDown));
    }

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
        return scheme;
    }

    @Override
    public Snapper snapper() {
        return snapper;
    }

    @Override
    public double selectionDashPhase() {
        return selectionDashPhase;
    }

    @Override
    public SelectionMoves selectionMoves() {
        return selectionMoves;
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        ghosts.add(element);
        ghostTunnelColors.add(Map.of());
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry,
                           Map<String, Integer> tunnelColors) {
        ghosts.add(element);
        ghostTunnelColors.add(tunnelColors);
    }

    @Override
    public void repaint() {
        repaints++;
    }

    @Override
    public void setTool(Tool next) {
        tool = next;
    }

    @Override
    public void useDefaultTool() {
        tool = defaultTool;
    }

    @Override
    public void showContextMenu(Optional<DocumentElement> target, Point2D screenPosition) {
        contextMenuTarget = target;
    }

    @Override
    public void configure(Component component) {
        configured = component;
    }

    @Override
    public void editLabel(DocumentElement element) {
        editedLabel = element;
    }

    @Override
    public void addTextAt(GridPoint point) {
        textAddedAt = point;
    }

    @Override
    public void editValue(Component component) {
        valueEdited = component;
    }

    @Override
    public void editMemory(Component component) {
        memoryEdited = component;
    }

    /** As the editor answers: the square the memory painter draws. */
    @Override
    public boolean memoryFoldToggleAt(Component component, Point2D gridPosition) {
        return MemoryPainter.isOnFoldToggle(openDocument.registry().typeOf(component), component, gridPosition);
    }

    @Override
    public void setCursor(CanvasCursor cursor) {
        cursors.add(cursor);
    }

    /** The last cursor a tool asked for, or null when none did. */
    public CanvasCursor lastCursor() {
        return cursors.isEmpty() ? null : cursors.get(cursors.size() - 1);
    }

    @Override
    public Dimension viewSize() {
        return new Dimension(paneSize);
    }

    /** Records {@code target} and shows it at once, as the default does (no animation here). */
    @Override
    public void zoomTo(ViewState target) {
        zoomTargets.add(target);
        ToolContext.super.zoomTo(target);
    }

    /** The last view a tool asked to zoom to, or null when none did. */
    public ViewState lastZoomTarget() {
        return zoomTargets.isEmpty() ? null : zoomTargets.get(zoomTargets.size() - 1);
    }

    @Override
    public void close() {
        openDocument.close();
    }
}
