package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.snap.PointResolution;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Optional mode: draws wires wherever the pointer starts, in one of two {@link WireMode}s read when a gesture starts.
 *
 * <ul>
 *   <li>{@link WireMode#CONTINUOUS}: a click starts a path, each click fixes a corner, a click on a pin or a wire ends
 *   it there; a double click or a right click ends it where the pointer is. A press and release that moved less than
 *   {@link WireDrawing#MOVEMENT_THRESHOLD} is a click at the press; a longer drag fixes a corner at the release. Espacio
 *   flips the elbow being drawn, Retroceso drops the last corner, Esc cancels. The tool stays after a path ends.</li>
 *   <li>{@link WireMode#PIECES}: every drag draws one «L», as VSim always did (Espacio flips its elbow).</li>
 * </ul>
 *
 * <p>With Ajustar (alignment-guides spec §4.4) the points go to a guide or a grid line within reach unless they already
 * are a connection; {@link #refreshSnap} redoes the end under the pointer.
 */
public final class WireTool implements Tool {

    private final Supplier<WireMode> mode;
    /** The drag under way in {@link WireMode#PIECES}. */
    private WireDrawing drawing;
    /** The path under way in {@link WireMode#CONTINUOUS}. */
    private ContinuousWirePath path;
    /** Where the primary button went down on the path (grid units), while it is held; null otherwise. */
    private Point2D pressPosition;
    /** The grid point of that press: the point of a click, which a tiny drag does not move. */
    private GridPoint pressPoint;
    /** The held press is the one that started the path, so its release only fixes a corner if it dragged. */
    private boolean pressStartedThePath;
    /** See {@link #hoverJunction()}; null when none. */
    private GridPoint hoverJunction;

    /** A Wire tool drawing as {@code mode} says when each gesture starts (the Cable button's choice). */
    public WireTool(Supplier<WireMode> mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            secondaryPressed(context, event);
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        hoverJunction = null; // the path's own preview shows where it joins from now on
        if (path != null) {
            holdPress(event, false);
            return;
        }
        if (mode.get() == WireMode.PIECES) {
            startDrawing(context, event);
        } else if (event.clickCount() == 1) {
            // the second press of a double click that ended a path on a pin or a wire starts nothing
            PointResolution start = resolveStart(context, event);
            path = new ContinuousWirePath(start, startWidth(context, start.point()));
            holdPress(event, true);
        }
    }

    /** A right click ends the path where it is; with no path it leaves the tool, as it always did. */
    private void secondaryPressed(ToolContext context, CanvasMouseEvent event) {
        if (path == null) {
            context.useDefaultTool();
            return;
        }
        endAt(context, event.snapped());
    }

    private void holdPress(CanvasMouseEvent event, boolean startsThePath) {
        pressPosition = event.gridPosition();
        pressPoint = event.snapped();
        pressStartedThePath = startsThePath;
    }

    private void startDrawing(ToolContext context, CanvasMouseEvent event) {
        PointResolution start = resolveStart(context, event);
        drawing = new WireDrawing(start, event.gridPosition(), startWidth(context, start.point()));
    }

    private static PointResolution resolveStart(ToolContext context, CanvasMouseEvent event) {
        return context.snapper().snapPoint(context, event.snapped());
    }

    /** The width of the pin at {@code start} (1 from anything else): a wire from a bus previews as a bus. */
    private static int startWidth(ToolContext context, GridPoint start) {
        OpenDocument openDocument = context.openDocument();
        return new ConnectionPoints(openDocument.registry())
                .widthAt(openDocument.document(), openDocument.netlist(), start);
    }

    /** A wire being drawn goes on over the other panes of the window. */
    @Override
    public boolean crossesPanes() {
        return holdsGesture();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (drawing != null) {
            drawing.dragTo(context, event);
            context.repaint();
        } else if (path != null) {
            path.previewTo(context, event.snapped());
            context.repaint();
        }
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        if (path != null) {
            path.previewTo(context, event.snapped());
            context.repaint();
        } else if (updateHoverJunction(context, event)) {
            context.repaint();
        }
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (drawing != null) {
            drawing.commit(context, event.snapped());
            drawing = null;
            context.repaint();
            return;
        }
        if (path == null || pressPosition == null || event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        boolean dragged = dragged(event);
        GridPoint clicked = dragged ? event.snapped() : pressPoint;
        boolean startedHere = pressStartedThePath;
        pressPosition = null;
        if (event.clickCount() >= 2) {
            endAt(context, clicked);
        } else if (startedHere && !dragged) {
            path.previewTo(context, clicked);
        } else if (path.addCorner(context, clicked)) {
            commitPath(context);
        }
        context.repaint();
    }

    /** True when the pointer went at least {@link WireDrawing#MOVEMENT_THRESHOLD} from the held press. */
    private boolean dragged(CanvasMouseEvent event) {
        double movedX = Math.abs(event.gridPosition().getX() - pressPosition.getX());
        double movedY = Math.abs(event.gridPosition().getY() - pressPosition.getY());
        return Math.max(movedX, movedY) >= WireDrawing.MOVEMENT_THRESHOLD;
    }

    /** Ends the path at {@code point} (a double click, a right click): its last corner, unless it already ended. */
    private void endAt(ToolContext context, GridPoint point) {
        path.addCorner(context, point);
        commitPath(context);
    }

    private void commitPath(ToolContext context) {
        path.commit(context);
        cancelPath(context);
    }

    private void cancelPath(ToolContext context) {
        path = null;
        pressPosition = null;
        context.repaint();
    }

    @Override
    public boolean refreshSnap(ToolContext context) {
        boolean changed = drawing != null ? drawing.refreshSnap(context) : path != null && path.refreshSnap(context);
        if (changed) {
            context.repaint();
        }
        return changed;
    }

    @Override
    public List<Guide> snappedGuides() {
        if (drawing != null) {
            return drawing.snappedGuides();
        }
        return path == null ? List.of() : path.snappedGuides();
    }

    @Override
    public boolean holdsGesture() {
        return drawing != null || path != null;
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        return switch (keyCode) {
            case KeyEvent.VK_ESCAPE -> escape(context);
            case KeyEvent.VK_BACK_SPACE -> dropLastStep(context);
            default -> false;
        };
    }

    /** Esc cancels the wire under way; with none, it leaves the tool. */
    private boolean escape(ToolContext context) {
        if (path != null) {
            cancelPath(context);
        } else if (drawing != null) {
            drawing = null;
            context.repaint();
        } else {
            context.useDefaultTool();
        }
        return true;
    }

    /** Drops the path's last corner (Retroceso, and Deshacer while drawing); with none left, cancels the path. */
    @Override
    public boolean dropLastStep(ToolContext context) {
        if (path == null) {
            return false;
        }
        if (path.dropLastCorner()) {
            context.repaint();
        } else {
            cancelPath(context);
        }
        return true;
    }

    @Override
    public boolean flipElbow(ToolContext context) {
        if (path != null) {
            path.flipElbow();
        } else if (drawing != null) {
            drawing.flipElbow();
        } else {
            return false;
        }
        context.repaint();
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (drawing != null) {
            drawing.paintPreview(graphics, context);
        } else if (path != null) {
            path.paintPreview(graphics, context);
        } else if (hoverJunction != null) {
            ContinuousWirePath.paintJunction(graphics, context, hoverJunction);
        }
    }

    /**
     * Where a click would start on (or join) a wire, shown as the preview's junction dot while the pointer is over one
     * in {@link WireMode#CONTINUOUS} with no path in progress; empty elsewhere, over a pin (it shows itself) and in
     * {@link WireMode#PIECES}.
     */
    Optional<GridPoint> hoverJunction() {
        return Optional.ofNullable(hoverJunction);
    }

    /** Works out {@link #hoverJunction()} for the pointer at {@code event}; true when it changed. */
    private boolean updateHoverJunction(ToolContext context, CanvasMouseEvent event) {
        GridPoint before = hoverJunction;
        hoverJunction = path == null && drawing == null && mode.get() == WireMode.CONTINUOUS
                ? wireJoinedAt(context, resolveStart(context, event).point()) : null;
        return !Objects.equals(before, hoverJunction);
    }

    /** {@code point} when a wire passes through it (end or interior) and no pin is there; null otherwise. */
    private static GridPoint wireJoinedAt(ToolContext context, GridPoint point) {
        OpenDocument openDocument = context.openDocument();
        boolean onAWire = openDocument.document().wires().stream().anyMatch(wire -> wire.contains(point));
        if (!onAWire) {
            return null;
        }
        boolean onAPin = new ConnectionPoints(openDocument.registry()).pinLocations(openDocument.document())
                .contains(point);
        return onAPin ? null : point;
    }

    private void clearHoverJunction(ToolContext context) {
        if (hoverJunction != null) {
            hoverJunction = null;
            context.repaint();
        }
    }

    /** A continuous wire's path is in progress (no button needs to be held). */
    @Override
    public boolean drawsBetweenClicks() {
        return path != null;
    }

    /** A tool or tab change, or the AI starting a task, cancels the path as Esc does; a drag in pieces is left alone. */
    @Override
    public Optional<String> interrupt(ToolContext context) {
        if (path == null) {
            return Optional.empty();
        }
        cancelPath(context);
        return Optional.of(Messages.text("wire.cancelled"));
    }

    /** Another tool was chosen: a path in progress goes, with «Cable cancelado». */
    @Override
    public void deactivated(ToolContext context) {
        interrupt(context).ifPresent(context::showNotice);
        clearHoverJunction(context);
    }

    @Override
    public void mouseExited(ToolContext context) {
        clearHoverJunction(context);
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
