package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.WireRouting.Segment;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * One wire being drawn with the mouse: an "L" from {@link #start()} to the mouse. Movement is measured from where
 * the mouse went down, which may be up to a pin radius away from where the mouse went down.
 */
public final class WireDrawing {

    private static final double MOVEMENT_THRESHOLD = 0.5;
    private static final BasicStroke BUS_PREVIEW = new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final float PREVIEW_ALPHA = 0.5f;

    private final GridPoint start;
    private final Point2D pressPosition;
    private final int startWidth;
    private GridPoint current;
    private Boolean horizontalFirst;

    public WireDrawing(GridPoint start, Point2D pressPosition) {
        this(start, pressPosition, 1);
    }

    /** {@code startWidth} is the width of the pin the wire starts from (1 from anything else): above 1 it previews as a bus. */
    public WireDrawing(GridPoint start, Point2D pressPosition, int startWidth) {
        this.start = start;
        this.pressPosition = pressPosition;
        this.current = start;
        this.startWidth = startWidth;
    }

    public GridPoint start() {
        return start;
    }

    public void dragTo(CanvasMouseEvent event) {
        if (horizontalFirst == null) {
            double movedX = Math.abs(event.gridPosition().getX() - pressPosition.getX());
            double movedY = Math.abs(event.gridPosition().getY() - pressPosition.getY());
            if (Math.max(movedX, movedY) >= MOVEMENT_THRESHOLD) {
                horizontalFirst = movedX >= movedY;
            }
        }
        current = event.snapped();
    }

    /** True once the mouse has gone at least half a grid unit away from the start. */
    public boolean hasMoved() {
        return horizontalFirst != null;
    }

    public List<Segment> route() {
        return WireRouting.route(start, current, horizontalFirst == null || horizontalFirst);
    }

    /** Adds the route ending at {@code end} as one undo step; false when it has no length. */
    public boolean commit(ToolContext context, GridPoint end) {
        current = end;
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
