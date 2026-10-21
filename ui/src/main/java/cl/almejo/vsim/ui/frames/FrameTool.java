package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.snap.PointResolution;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The «Marco» tool (spec §4): dragging draws a rectangle snapped to the grid; releasing creates an unlocked frame
 * titled «Bloque» (at least the minimum size) as one undo step, then the tool returns to Seleccionar with the new frame
 * selected and its title editor open. A click without dragging creates nothing.
 *
 * <p>Ajustar (alignment-guides spec §4.4): both corners go to a guide or a grid line within reach (never to parts in
 * v1). The start corner is resolved at the press, the other one while dragging, and the release reuses what the
 * preview showed; {@link #refreshSnap} redoes the dragged corner. A guide counts as reached only when an edge of the
 * frame as it would be created (after the minimum size) lies on it.
 */
public final class FrameTool implements Tool {

    private static final float OUTLINE_WIDTH = 1.5f;
    private static final float[] DASH = {4f, 3f};

    private GridPoint start;
    private List<Guide> startGuides = List.of();
    private GridPoint rawCurrent;
    private GridPoint current;
    private List<Guide> currentGuides = List.of();

    @Override
    public boolean animatesOutline() {
        return true;
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        PointResolution resolution = context.snapper().snapPoint(context, event.snapped());
        start = resolution.point();
        startGuides = resolution.guides();
        rawCurrent = event.snapped();
        current = start;
        currentGuides = List.of();
        context.repaint();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (start == null) {
            return;
        }
        resolveCurrent(context, event.snapped());
        context.repaint();
    }

    @Override
    public boolean refreshSnap(ToolContext context) {
        if (start == null) {
            return false;
        }
        GridPoint before = current;
        List<Guide> guidesBefore = currentGuides;
        resolveCurrent(context, rawCurrent);
        if (current.equals(before) && currentGuides.equals(guidesBefore)) {
            return false;
        }
        context.repaint();
        return true;
    }

    /** The guides an edge of the frame being drawn lies on, from the frame as it would be created. */
    @Override
    public List<Guide> snappedGuides() {
        Rectangle2D rectangle = draggedRectangle();
        if (rectangle == null) {
            return List.of();
        }
        List<Guide> guides = new ArrayList<>();
        for (Guide guide : startGuides) {
            addIfOnAnEdge(guides, guide, rectangle);
        }
        for (Guide guide : currentGuides) {
            addIfOnAnEdge(guides, guide, rectangle);
        }
        return List.copyOf(guides);
    }

    private static void addIfOnAnEdge(List<Guide> guides, Guide guide, Rectangle2D rectangle) {
        boolean onAnEdge = guide.orientation() == Guide.Orientation.VERTICAL
                ? guide.position() == rectangle.getMinX() || guide.position() == rectangle.getMaxX()
                : guide.position() == rectangle.getMinY() || guide.position() == rectangle.getMaxY();
        if (onAnEdge && !guides.contains(guide)) {
            guides.add(guide);
        }
    }

    private void resolveCurrent(ToolContext context, GridPoint raw) {
        PointResolution resolution = context.snapper().snapPoint(context, raw);
        rawCurrent = raw;
        current = resolution.point();
        currentGuides = resolution.guides();
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (start == null) {
            return;
        }
        if (!event.snapped().equals(rawCurrent)) {
            resolveCurrent(context, event.snapped());
        }
        int left = Math.min(start.x(), current.x());
        int top = Math.min(start.y(), current.y());
        int width = Math.abs(current.x() - start.x());
        int height = Math.abs(current.y() - start.y());
        reset();
        if (width == 0 && height == 0) {
            context.repaint();
            return;
        }
        Frame frame = new Frame(context.openDocument().document().newFrameId(), new GridPoint(left, top),
                Math.max(Frame.MINIMUM_WIDTH, width), Math.max(Frame.MINIMUM_HEIGHT, height),
                Messages.text("frames.defaultTitle"), FrameTint.NONE, false);
        context.openDocument().execute(new CreateFrameCommand(frame));
        context.selection().select(frame.id());
        context.useDefaultTool();
        context.editLabel(frame);
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (start != null) {
            reset();
            context.repaint();
        } else {
            context.useDefaultTool(); // like Cable and Mover vista
        }
        return true;
    }

    @Override
    public void deactivated(ToolContext context) {
        reset();
    }

    /** The rectangle being dragged, in grid units, as the frame would be created; null when nothing is dragged. */
    Rectangle2D draggedRectangle() {
        if (start == null || (start.equals(current))) {
            return null;
        }
        return new Rectangle2D.Double(Math.min(start.x(), current.x()), Math.min(start.y(), current.y()),
                Math.max(Frame.MINIMUM_WIDTH, Math.abs(current.x() - start.x())),
                Math.max(Frame.MINIMUM_HEIGHT, Math.abs(current.y() - start.y())));
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        Rectangle2D rectangle = draggedRectangle();
        if (rectangle == null) {
            return;
        }
        double scale = GridPoint.PIXELS_PER_UNIT;
        graphics.setColor(context.scheme().selection());
        graphics.setStroke(new BasicStroke(OUTLINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH,
                (float) context.selectionDashPhase()));
        graphics.draw(new Rectangle2D.Double(rectangle.getX() * scale, rectangle.getY() * scale,
                rectangle.getWidth() * scale, rectangle.getHeight() * scale));
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    private void reset() {
        start = null;
        current = null;
        rawCurrent = null;
        startGuides = List.of();
        currentGuides = List.of();
    }
}
