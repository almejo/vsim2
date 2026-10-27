package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.appearance.ShapeCanvas.HIT_RADIUS;
import static cl.almejo.vsim.ui.appearance.ShapeCanvas.MINIMUM_CLOSED_NODES;
import static cl.almejo.vsim.ui.appearance.ShapeCanvas.MINIMUM_OPEN_NODES;

/** A multiline or polygon being drawn on the shape canvas, a click per node, until it is finished or dropped. */
final class ShapePathDrawing {
    private final ShapeCanvas canvas;
    /** The nodes placed so far, in eighths; empty when nothing is being drawn. */
    final List<SymbolPoint> pathNodes = new ArrayList<>();

    ShapePathDrawing(ShapeCanvas canvas) {
        this.canvas = canvas;
    }

    /** Whether a multiline or a polygon is being drawn: Ctrl+Z takes back its last node instead of an edit. */
    boolean drawingPath() {
        return !pathNodes.isEmpty();
    }

    /** Ctrl+Z while drawing: the last placed node goes; with the first one, the drawing ends. */
    void removeLastNode() {
        if (!pathNodes.isEmpty()) {
            pathNodes.removeLast();
            canvas.changed.run();
            canvas.repaint();
        }
    }

    /** The nodes placed so far by Multilínea or Polígono, in eighths. */
    List<SymbolPoint> placedNodes() {
        return List.copyOf(pathNodes);
    }

    /**
     * A click places a node; on the first node (three or more placed) it closes the figure; the second click of a
     * double-click finishes it (its first click already placed the last node).
     */
    void clickPathTool(MouseEvent event) {
        if (closesOnFirstNode(event.getPoint())) {
            commitPath(true);
        } else if (event.getClickCount() > 1) {
            finishPath();
        } else {
            SymbolPoint at = canvas.point(event);
            if (pathNodes.size() >= SymbolPath.MAXIMUM_NODES) {
                canvas.refuse("shape.nodes.limit");
            } else if (pathNodes.isEmpty() || !pathNodes.getLast().equals(at)) {
                pathNodes.add(at);
                canvas.changed.run();
            }
        }
        canvas.repaint();
    }

    /** Whether the pointer is on the first placed node and there are enough nodes to close the figure there. */
    boolean closesOnFirstNode(Point at) {
        return pathNodes.size() >= MINIMUM_CLOSED_NODES
                && canvas.screen(pathNodes.getFirst().x(), pathNodes.getFirst().y()).distance(at) < HIT_RADIUS;
    }

    /** Finishes the figure being drawn if it has enough nodes; otherwise forgets it. */
    void finishOrDropPath() {
        if (!finishPath()) {
            pathNodes.clear();
            canvas.changed.run();
            canvas.repaint();
        }
    }

    /** A multiline needs two nodes and stays open; a polygon needs three and closes. False when there are too few. */
    private boolean finishPath() {
        boolean closed = canvas.tool == ShapeTool.POLYGON;
        if (pathNodes.size() < (closed ? MINIMUM_CLOSED_NODES : MINIMUM_OPEN_NODES)) {
            return false;
        }
        commitPath(closed);
        return true;
    }

    private void commitPath(boolean closed) {
        List<SymbolPoint> nodes = List.copyOf(pathNodes);
        pathNodes.clear();
        canvas.publish.accept(canvas.current.get().addFigure(SymbolTemplates.polygon(nodes, closed)));
        canvas.repaint();
    }
}
