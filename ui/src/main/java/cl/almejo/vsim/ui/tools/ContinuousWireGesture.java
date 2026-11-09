package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.snap.PointResolution;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

/**
 * The clicks of a continuous wire drawn from Selección ({@link WireMode#CONTINUOUS}): once started on a pin or a wire,
 * each click fixes a corner and a click (or a release) on a pin or a wire ends there; a double click ends at the corner
 * its first click fixed, a right click where the pointer is. A press and release that moved less than
 * {@link WireDrawing#MOVEMENT_THRESHOLD} is a click at the press; a longer drag fixes a corner at the release. The
 * whole path is one undo step ({@link ContinuousWirePath}).
 *
 * <p>Ajustar (alignment-guides spec §4.4) as {@link WireDrawing}: the start is resolved once, each corner when fixed and
 * the pointer's end while it moves.
 */
final class ContinuousWireGesture {

    /** The path under way; null when none. */
    private ContinuousWirePath path;
    /** Where the primary button went down on the path (grid units), while it is held; null otherwise. */
    private Point2D pressPosition;
    /** The grid point of that press: the point of a click, which a tiny drag does not move. */
    private GridPoint pressPoint;
    /** The held press is the one that started the path, so its release only fixes a corner if it dragged. */
    private boolean pressStartedThePath;

    /** True while a path is in progress (no button needs to be held). */
    boolean inProgress() {
        return path != null;
    }

    /** Starts a path at {@code start} (a pin, a point on a wire) with {@code press}, still held, as its first press. */
    void start(ToolContext context, GridPoint start, CanvasMouseEvent press) {
        startAt(context, start);
        holdPress(press, true);
    }

    /** Starts a path at {@code start} after a click whose release already came. */
    void startAfterClick(ToolContext context, GridPoint start) {
        startAt(context, start);
        context.repaint();
    }

    private void startAt(ToolContext context, GridPoint start) {
        PointResolution resolution = context.snapper().snapPoint(context, start);
        path = new ContinuousWirePath(resolution, startWidth(context, resolution.point()));
    }

    /** The width of the pin at {@code start} (1 from anything else): a wire from a bus previews as a bus. */
    private static int startWidth(ToolContext context, GridPoint start) {
        OpenDocument openDocument = context.openDocument();
        return new ConnectionPoints(openDocument.registry())
                .widthAt(openDocument.document(), openDocument.netlist(), start);
    }

    /** A press while the path is in progress: the primary button holds it until its release, the secondary ends. */
    void pressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            endAt(context, event.snapped());
        } else if (event.button() == CanvasMouseEvent.PRIMARY) {
            holdPress(event, false);
        }
    }

    private void holdPress(CanvasMouseEvent event, boolean startsThePath) {
        pressPosition = event.gridPosition();
        pressPoint = event.snapped();
        pressStartedThePath = startsThePath;
    }

    /** The pointer moved (with or without a button held): the segment being drawn follows it. */
    void pointerAt(ToolContext context, CanvasMouseEvent event) {
        path.previewTo(context, event.snapped());
        context.repaint();
    }

    /**
     * The release of a primary press held by the path: fixes a corner, ends the path (on a pin or a wire, or the second
     * click of a double click) or, for the click that started it, only lets it go on. True when the path ended.
     */
    boolean released(ToolContext context, CanvasMouseEvent event) {
        if (pressPosition == null) {
            return false;
        }
        boolean dragged = dragged(event);
        GridPoint clicked = dragged ? event.snapped() : pressPoint;
        boolean startedHere = pressStartedThePath;
        pressPosition = null;
        boolean ended;
        if (event.clickCount() >= 2) {
            // the first click of the double click fixed the last corner: the second one only ends there, so a pointer
            // that wobbled across a grid line adds no stub; a real drag still ends where it was released
            if (dragged) {
                endAt(context, clicked);
            } else {
                commit(context);
            }
            ended = true;
        } else if (startedHere && !dragged) {
            path.previewTo(context, clicked);
            ended = false;
        } else {
            ended = path.addCorner(context, clicked);
            if (ended) {
                commit(context);
            }
        }
        context.repaint();
        return ended;
    }

    /** True when the pointer went at least {@link WireDrawing#MOVEMENT_THRESHOLD} from the held press. */
    private boolean dragged(CanvasMouseEvent event) {
        double movedX = Math.abs(event.gridPosition().getX() - pressPosition.getX());
        double movedY = Math.abs(event.gridPosition().getY() - pressPosition.getY());
        return Math.max(movedX, movedY) >= WireDrawing.MOVEMENT_THRESHOLD;
    }

    /** Ends the path at {@code point} (a double click that dragged, a right click): its last corner. */
    private void endAt(ToolContext context, GridPoint point) {
        path.addCorner(context, point);
        commit(context);
    }

    private void commit(ToolContext context) {
        path.commit(context);
        cancel(context);
    }

    /** Drops the path without adding anything (Esc). */
    void cancel(ToolContext context) {
        path = null;
        pressPosition = null;
        context.repaint();
    }

    /** Drops the path's last corner (Retroceso, Deshacer while drawing); with none left, cancels the path. */
    void dropLastStep(ToolContext context) {
        if (!path.dropLastCorner()) {
            cancel(context);
        }
        context.repaint();
    }

    /** Puts the elbow of the segment being drawn on the other side (a tap of Espacio). */
    void flipElbow(ToolContext context) {
        path.flipElbow();
        context.repaint();
    }

    /** Cancels the path as Esc does and returns the notice that says so («Cable cancelado»); empty with none. */
    Optional<String> interrupt(ToolContext context) {
        if (path == null) {
            return Optional.empty();
        }
        cancel(context);
        return Optional.of(Messages.text("wire.cancelled"));
    }

    /** Resolves the pointer's end again (a snap preference, the zoom or the guides changed); true when it changed. */
    boolean refreshSnap(ToolContext context) {
        boolean changed = path != null && path.refreshSnap(context);
        if (changed) {
            context.repaint();
        }
        return changed;
    }

    List<Guide> snappedGuides() {
        return path == null ? List.of() : path.snappedGuides();
    }

    void paint(Graphics2D graphics, ToolContext context) {
        if (path != null) {
            path.paintPreview(graphics, context);
        }
    }
}
