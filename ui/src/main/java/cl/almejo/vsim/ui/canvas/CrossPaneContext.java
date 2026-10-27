package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.snap.Snapper;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Optional;

/**
 * What a gesture's tool sees while the pointer is over another pane of the window, or what that pane paints the
 * gesture with (split views: a gesture crosses the panes). The {@code owner} pane was pressed and keeps the gesture: its
 * tool, the cursor, tool changes and dialogs. The {@code over} pane lends what depends on where the pointer is: the
 * viewport (zoom, pan), the snapper, the hit tester and the painting. Document and selection are the window's, shared.
 */
final class CrossPaneContext implements ToolContext {

    private final CircuitCanvas owner;
    private final CircuitCanvas over;

    CrossPaneContext(CircuitCanvas owner, CircuitCanvas over) {
        this.owner = owner;
        this.over = over;
    }

    CircuitCanvas over() {
        return over;
    }

    @Override
    public OpenDocument openDocument() {
        return over.openDocument();
    }

    @Override
    public Selection selection() {
        return over.selection();
    }

    @Override
    public HitTester hitTester() {
        return over.hitTester();
    }

    @Override
    public Viewport viewport() {
        return over.viewport();
    }

    /** The pressed pane's: a zoom belongs to the pane where the gesture started (view gestures do not cross panes). */
    @Override
    public Dimension viewSize() {
        return owner.viewSize();
    }

    /** Zooms the pressed pane, never the one the pointer is over. */
    @Override
    public void zoomTo(ViewState target) {
        owner.zoomTo(target);
    }

    /** Zooms the pressed pane, never the one the pointer is over. */
    @Override
    public void zoomAt(Point2D screenPoint, double zoom) {
        owner.zoomAt(screenPoint, zoom);
    }

    @Override
    public ColorScheme scheme() {
        return over.scheme();
    }

    @Override
    public SelectionMoves selectionMoves() {
        return owner.selectionMoves();
    }

    @Override
    public Snapper snapper() {
        return over.snapper();
    }

    @Override
    public double selectionDashPhase() {
        return owner.selectionDashPhase();
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element) {
        over.paintGhost(graphics, element);
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry) {
        over.paintGhost(graphics, element, registry);
    }

    @Override
    public void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry,
                           Map<String, Integer> tunnelColors) {
        over.paintGhost(graphics, element, registry, tunnelColors);
    }

    /** Both panes: the gesture's preview shows in each (and in the other panes, which the owner repaints too). */
    @Override
    public void repaint() {
        owner.repaint();
        over.repaint();
    }

    @Override
    public void setTool(Tool tool) {
        owner.setTool(tool);
    }

    @Override
    public void useDefaultTool() {
        owner.useDefaultTool();
    }

    @Override
    public void setCursor(CanvasCursor cursor) {
        owner.setCursor(cursor);
    }

    @Override
    public void showContextMenu(Optional<DocumentElement> target, Point2D screenPosition) {
        owner.showContextMenu(target, screenPosition);
    }

    @Override
    public void configure(Component component) {
        owner.configure(component);
    }

    @Override
    public void editLabel(DocumentElement element) {
        owner.editLabel(element);
    }

    @Override
    public void componentPlaced(ComponentType type) {
        owner.componentPlaced(type);
    }

    @Override
    public void editValue(Component component) {
        owner.editValue(component);
    }

    @Override
    public void editValue(Component component, Value startingValue) {
        owner.editValue(component, startingValue);
    }

    @Override
    public void editMemory(Component component) {
        owner.editMemory(component);
    }
}
