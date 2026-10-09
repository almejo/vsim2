package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.tools.WireRouting.Segment;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.List;

public final class WireTool implements Tool {

    private GridPoint start;
    private GridPoint current;
    private Boolean horizontalFirst;

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        start = event.snapped();
        current = start;
        horizontalFirst = null;
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (start == null) {
            return;
        }
        if (horizontalFirst == null) {
            double movedX = Math.abs(event.gridPosition().getX() - start.x());
            double movedY = Math.abs(event.gridPosition().getY() - start.y());
            if (Math.max(movedX, movedY) >= 0.5) {
                horizontalFirst = movedX >= movedY;
            }
        }
        current = event.snapped();
        context.repaint();
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (start == null) {
            return;
        }
        current = event.snapped();
        List<Segment> segments = route();
        if (!segments.isEmpty()) {
            CircuitDocument document = context.openDocument().document();
            List<Command> commands = segments.stream()
                    .map(segment -> (Command) new AddWireCommand(
                            new Wire(document.newWireId(), segment.start(), segment.end())))
                    .toList();
            context.openDocument().execute(commands.size() == 1
                    ? commands.get(0)
                    : new CompositeCommand("command.wire", commands));
        }
        start = null;
        context.repaint();
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (start != null) {
            start = null;
            context.repaint();
        } else {
            context.useDefaultTool();
        }
        return true;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (start == null) {
            return;
        }
        List<Segment> segments = route();
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            context.paintGhost(graphics, new Wire(new ElementId("preview-" + index), segment.start(), segment.end()));
        }
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    private List<Segment> route() {
        return WireRouting.route(start, current, horizontalFirst == null || horizontalFirst);
    }
}
