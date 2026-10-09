package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.HitTester;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** ToolContext for tests: a real OpenDocument, recorded UI requests, no screen. */
public final class TestToolContext implements ToolContext, AutoCloseable {

    public final Selection selection = new Selection();
    public final Viewport viewport = new Viewport();
    public final List<DocumentElement> ghosts = new ArrayList<>();
    public final Tool defaultTool = new SelectTool();
    public Tool tool = defaultTool;
    public Optional<DocumentElement> contextMenuTarget;
    public Component configured;
    public int repaints;
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
        return ColorScheme.LIGHT;
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        ghosts.add(element);
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
    public void close() {
        openDocument.close();
    }
}
