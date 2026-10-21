package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.snap.AlignmentLinesPainter;
import cl.almejo.vsim.ui.snap.SnappedPlacement;
import cl.almejo.vsim.ui.tunnel.NewTunnels;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/**
 * Places a component where the user clicks, then returns to the default tool (Shift keeps placing). Ajustar
 * (alignment-guides spec §3.4): the ghost is snapped to the circuit's parts, guides and grid lines and shows its
 * alignment lines; a press places it where the magnet takes the press point (the ghost's place when the pointer has
 * not moved), and R or {@link #refreshSnap} redo the ghost from the last raw point.
 */
public final class PlaceComponentTool implements Tool {

    private static final ElementId PREVIEW_ID = new ElementId("preview");

    private final ComponentType type;
    private Rotation rotation = Rotation.R0;
    private GridPoint hover;
    /** The grid point under the pointer before the magnet. */
    private GridPoint rawHover;
    private SnappedPlacement snapped;

    public PlaceComponentTool(ComponentType type) {
        this.type = type;
    }

    public ComponentType type() {
        return type;
    }

    public Rotation rotation() {
        return rotation;
    }

    @Override
    public boolean placesFromPalette() {
        return true;
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        rawHover = event.snapped();
        snapHover(context);
        context.repaint();
    }

    @Override
    public boolean refreshSnap(ToolContext context) {
        if (rawHover == null) {
            return false;
        }
        snapHover(context);
        context.repaint();
        return true;
    }

    /** For tests: where the ghost is (after the magnet), or null before the pointer came over the canvas. */
    GridPoint hover() {
        return hover;
    }

    private void snapHover(ToolContext context) {
        snapped = ComponentPlacement.snapped(context, type, rawHover, rotation);
        hover = snapped.position();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        mouseMoved(context, event);
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        if (!event.snapped().equals(rawHover)) {
            rawHover = event.snapped();
            snapHover(context);
        }
        ComponentPlacement.place(context, type, hover, rotation);
        if (!event.shiftDown()) {
            context.useDefaultTool();
        }
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode == KeyEvent.VK_R) {
            rotation = shiftDown ? rotation.counterClockwise() : rotation.clockwise();
            if (rawHover != null) {
                snapHover(context);
            }
            context.repaint();
            return true;
        }
        if (keyCode == KeyEvent.VK_ESCAPE) {
            context.useDefaultTool();
            return true;
        }
        return false;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (hover != null) {
            context.paintGhost(graphics, type.newComponent(PREVIEW_ID, hover, rotation,
                    NewTunnels.parameters(context.openDocument(), type), ""));
            AlignmentLinesPainter.paint(graphics, context, snapped.box(), snapped.matches());
        }
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
