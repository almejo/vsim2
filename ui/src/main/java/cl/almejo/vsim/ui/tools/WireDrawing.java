package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.snap.PointResolution;
import cl.almejo.vsim.ui.tools.WireRouting.Segment;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * One wire being drawn with the mouse: an "L" from {@link #start()} to the mouse. Movement is measured from where
 * the mouse went down, which may be up to a pin radius away from where the mouse went down.
 *
 * <p>Ajustar (alignment-guides spec §4.4): the end is the grid point under the mouse as the canvas's snapper resolves
 * it (a guide or a grid line, never away from a connection). The resolution is worked out once per grid point and the
 * commit reuses it, so what is released is what the preview showed; {@link #refreshSnap} redoes it from the same raw
 * point.
 */
public final class WireDrawing {

    private static final double MOVEMENT_THRESHOLD = 0.5;
    private static final BasicStroke BUS_PREVIEW = new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final float PREVIEW_ALPHA = 0.5f;

    private final GridPoint start;
    private final List<Guide> startGuides;
    private final Point2D pressPosition;
    private final int startWidth;
    private GridPoint current;
    private GridPoint rawCurrent;
    private List<Guide> currentGuides = List.of();
    private Boolean horizontalFirst;

    public WireDrawing(GridPoint start, Point2D pressPosition) {
        this(start, pressPosition, 1);
    }

    /** {@code startWidth} is the width of the pin the wire starts from (1 from anything else): above 1 it previews as a bus. */
    public WireDrawing(GridPoint start, Point2D pressPosition, int startWidth) {
        this(new PointResolution(start, PointResolution.Reason.GRID), pressPosition, startWidth);
    }

    /** A wire from a start the snapper already resolved: its guides stay highlighted while the wire is drawn. */
    public WireDrawing(PointResolution start, Point2D pressPosition, int startWidth) {
        this.start = start.point();
        this.startGuides = start.guides();
        this.pressPosition = pressPosition;
        this.current = this.start;
        this.rawCurrent = this.start;
        this.startWidth = startWidth;
    }

    public GridPoint start() {
        return start;
    }

    public void dragTo(ToolContext context, CanvasMouseEvent event) {
        if (horizontalFirst == null) {
            double movedX = Math.abs(event.gridPosition().getX() - pressPosition.getX());
            double movedY = Math.abs(event.gridPosition().getY() - pressPosition.getY());
            if (Math.max(movedX, movedY) >= MOVEMENT_THRESHOLD) {
                horizontalFirst = movedX >= movedY;
            }
        }
        resolveEnd(context, event.snapped());
    }

    /** Resolves the end again from the last raw point (a snap preference, the zoom or the guides changed). */
    public boolean refreshSnap(ToolContext context) {
        GridPoint before = current;
        List<Guide> guidesBefore = currentGuides;
        resolveEnd(context, rawCurrent);
        return !current.equals(before) || !currentGuides.equals(guidesBefore);
    }

    /** The guides the start and the end lie on because they snapped to them. */
    public List<Guide> snappedGuides() {
        if (currentGuides.isEmpty()) {
            return startGuides;
        }
        List<Guide> guides = new ArrayList<>(startGuides);
        currentGuides.stream().filter(guide -> !guides.contains(guide)).forEach(guides::add);
        return List.copyOf(guides);
    }

    private void resolveEnd(ToolContext context, GridPoint raw) {
        PointResolution resolution = context.snapper().snapPoint(context, raw);
        rawCurrent = raw;
        current = resolution.point();
        currentGuides = resolution.guides();
    }

    /** True once the mouse has gone at least half a grid unit away from the start. */
    public boolean hasMoved() {
        return horizontalFirst != null;
    }

    public List<Segment> route() {
        return WireRouting.route(start, current, horizontalFirst == null || horizontalFirst);
    }

    /**
     * Adds the route ending where the raw grid point {@code end} resolves as one undo step; false when it has no
     * length. The end the preview shows is reused when {@code end} is the point it was resolved from.
     */
    public boolean commit(ToolContext context, GridPoint end) {
        if (!end.equals(rawCurrent)) {
            resolveEnd(context, end);
        }
        List<Segment> segments = route();
        if (segments.isEmpty()) {
            return false;
        }
        CircuitDocument document = context.openDocument().document();
        List<Command> commands = segments.stream()
                .map(segment -> (Command) new AddWireCommand(
                        new Wire(document.newWireId(), segment.start(), segment.end())))
                .toList();
        context.openDocument().execute(commands.size() == 1
                ? commands.get(0)
                : new CompositeCommand("command.wire", commands));
        return true;
    }

    public void paintPreview(Graphics2D graphics, ToolContext context) {
        List<Segment> segments = route();
        if (startWidth > 1) {
            paintBusPreview(graphics, context, segments);
            return;
        }
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            context.paintGhost(graphics, new Wire(new ElementId("preview-" + index), segment.start(), segment.end()));
        }
    }

    private static void paintBusPreview(Graphics2D graphics, ToolContext context, List<Segment> segments) {
        double unit = GridPoint.PIXELS_PER_UNIT;
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, PREVIEW_ALPHA));
        try {
            graphics.setStroke(BUS_PREVIEW);
            graphics.setColor(context.scheme().preview());
            for (Segment segment : segments) {
                graphics.draw(new Line2D.Double(segment.start().x() * unit, segment.start().y() * unit,
                        segment.end().x() * unit, segment.end().y() * unit));
            }
        } finally {
            graphics.setComposite(previous);
        }
    }
}
