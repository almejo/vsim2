package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.WireRouting.Segment;

import java.awt.Graphics2D;
import java.util.List;

/** One wire being drawn with the mouse: an "L" from {@link #start()} to the mouse. */
public final class WireDrawing {

    private static final double MOVEMENT_THRESHOLD = 0.5;

    private final GridPoint start;
    private GridPoint current;
    private Boolean horizontalFirst;

    public WireDrawing(GridPoint start) {
        this.start = start;
        this.current = start;
    }

    public GridPoint start() {
        return start;
    }

    public void dragTo(CanvasMouseEvent event) {
        if (horizontalFirst == null) {
            double movedX = Math.abs(event.gridPosition().getX() - start.x());
            double movedY = Math.abs(event.gridPosition().getY() - start.y());
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
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            context.paintGhost(graphics, new Wire(new ElementId("preview-" + index), segment.start(), segment.end()));
        }
    }
}
