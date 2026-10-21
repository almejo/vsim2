package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.snap.PointResolution;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Optional mode: every drag draws a wire, wherever it starts. With Ajustar (alignment-guides spec §4.4) the start and
 * the end go to a guide or a grid line within reach unless they already are a connection; the start is resolved once
 * at the press, the end while dragging, and {@link #refreshSnap} redoes the end.
 */
public final class WireTool implements Tool {

    private WireDrawing drawing;

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() == CanvasMouseEvent.PRIMARY) {
            OpenDocument openDocument = context.openDocument();
            PointResolution start = context.snapper().snapPoint(context, event.snapped());
            drawing = new WireDrawing(start, event.gridPosition(),
                    new ConnectionPoints(openDocument.registry())
                            .widthAt(openDocument.document(), openDocument.netlist(), start.point()));
        }
    }

    /** A wire being drawn goes on over the other panes of the window. */
    @Override
    public boolean crossesPanes() {
        return drawing != null;
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (drawing != null) {
            drawing.dragTo(context, event);
            context.repaint();
        }
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (drawing == null) {
            return;
        }
        drawing.commit(context, event.snapped());
        drawing = null;
        context.repaint();
    }

    @Override
    public boolean refreshSnap(ToolContext context) {
        if (drawing == null || !drawing.refreshSnap(context)) {
            return false;
        }
        context.repaint();
        return true;
    }

    @Override
    public List<Guide> snappedGuides() {
        return drawing == null ? List.of() : drawing.snappedGuides();
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (drawing != null) {
            drawing = null;
            context.repaint();
        } else {
            context.useDefaultTool();
        }
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (drawing != null) {
            drawing.paintPreview(graphics, context);
        }
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
