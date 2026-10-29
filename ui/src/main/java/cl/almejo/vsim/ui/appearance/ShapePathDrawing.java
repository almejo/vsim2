package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.SymbolDraft;
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

/**
 * A multiline or polygon being drawn on the shape canvas, a click or a drag per node, until it is finished or dropped;
 * and a two-click line (Línea started by a click) waiting for its second point.
 */
final class ShapePathDrawing {
    private final ShapeCanvas canvas;
    /** The nodes placed so far, in eighths; empty when nothing is being drawn. */
    final List<SymbolPoint> pathNodes = new ArrayList<>();

    /** A drag in a path tool is this many pixels at least: less is a click. */
    static final int CLICK_TOLERANCE = 3;
    /** The first point of a two-click line, in eighths; null when none is waiting for its second point. */
    SymbolPoint lineAnchor;
    /** A drag in a path tool has begun: the press point is already a node. */
    private boolean pathDragStarted;

    ShapePathDrawing(ShapeCanvas canvas) {
        this.canvas = canvas;
    }

    /** Whether a multiline or a polygon is being drawn: Ctrl+Z takes back its last node instead of an edit. */
    boolean drawingPath() {
        return !pathNodes.isEmpty() || lineAnchor != null;
    }

    /** Ctrl+Z while drawing: the last placed node goes; with the first one, the drawing ends. */
    void removeLastNode() {
        if (lineAnchor != null) {
            lineAnchor = null;
            canvas.changed.run();
            canvas.repaint();
        } else if (!pathNodes.isEmpty()) {
            pathNodes.removeLast();
            canvas.changed.run();
            canvas.repaint();
        }
    }

    /** The nodes placed so far by Multilínea or Polígono, in eighths. */
    List<SymbolPoint> placedNodes() {
        return List.copyOf(pathNodes);
    }

    /** The pointer has moved far enough from the press to be a drag, not a click. */
    static boolean moved(Point pressed, Point released) {
        return pressed.distance(released) >= CLICK_TOLERANCE;
    }

    /** A drag in Multilínea or Polígono begins: its press point becomes a node unless it is on the last one. */
    void dragPath(Point pressed, Point where) {
        if (pathDragStarted || !moved(pressed, where)) {
            return;
        }
        pathDragStarted = true;
        SymbolPoint at = canvas.point(pressed);
        if (pathNodes.isEmpty() || !pathNodes.getLast().equals(at)) {
            addNode(at);
        }
        canvas.repaint();
    }

    /** The release of a press in Multilínea or Polígono: the end of a drag places a node; no drag is a click. */
    void releasePath(MouseEvent event, Point pressed) {
        if (!moved(pressed, event.getPoint())) {
            pathDragStarted = false;
            clickPathTool(event);
            return;
        }
        boolean started = pathDragStarted;
        pathDragStarted = false;
        if (!started && pathNodes.isEmpty()) {
            dragPath(pressed, event.getPoint());
        }
        if (pathNodes.isEmpty()) {
            return;
        }
        if (closesOnFirstNode(event.getPoint())) {
            commitPath(true);
        } else {
            SymbolPoint at = canvas.point(event);
            if (!pathNodes.getLast().equals(at)) {
                addNode(at);
            }
        }
        canvas.repaint();
    }

    private void addNode(SymbolPoint at) {
        if (pathNodes.size() >= SymbolPath.MAXIMUM_NODES) {
            canvas.refuse("shape.nodes.limit");
        } else {
            pathNodes.add(at);
            canvas.changed.run();
        }
    }

    /**
     * The release of a press in Línea: a click starts a two-click line (or ends the one waiting); a drag draws the line
     * as always. Returns the draft to publish, or null for none.
     */
    SymbolDraft releaseLine(MouseEvent event, Point pressed,
            SymbolDraft base,
            SymbolDraft dragged) {
        boolean click = !moved(pressed, event.getPoint());
        SymbolPoint anchor = lineAnchor;
        if (anchor == null) {
            if (click) {
                lineAnchor = canvas.point(pressed);
                canvas.changed.run();
                return null;
            }
            return dragged;
        }
        lineAnchor = null;
        canvas.changed.run();
        SymbolPoint end = canvas.point(event);
        if (!click && dragged != null) {
            return dragged;
        }
        return anchor.equals(end) ? null : base.addFigure(SymbolTemplates.line(anchor, end, event.isShiftDown()));
    }

    /** Forgets what is being drawn (Esc, a tool change). */
    void reset() {
        pathNodes.clear();
        lineAnchor = null;
        pathDragStarted = false;
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
            if (pathNodes.isEmpty() || !pathNodes.getLast().equals(at)) {
                addNode(at);
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
