package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.geometry.Axis;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.geometry.SnapTarget;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

/**
 * The alignment lines of a drag (spec §3.3): for each matched coordinate one solid line from the moved box to the
 * farthest matched part, one grid unit past both ends, with a short tick across each end so it never reads as a wire.
 * Painted by the tools in their overlay right after the move preview, 1 logical pixel wide whatever the zoom (the
 * document transform is taken off), in {@code scheme.alignmentGuide()} read at paint time; a guide matched adds no
 * length (it already crosses the canvas). Tool overlays never reach image export or paper.
 */
public final class AlignmentLinesPainter {

    /** How far the lines run past the aligned boxes, in grid units. */
    static final int OVERHANG_UNITS = 1;
    /** Half the length of the end ticks, in logical pixels. */
    static final int TICK_HALF_LENGTH = 3;

    private AlignmentLinesPainter() {
    }

    /**
     * Paints {@code matches} for a moved set whose box is now {@code movedBox}, on {@code graphics} in document
     * coordinates (the tool overlay).
     */
    public static void paint(Graphics2D graphics, ToolContext context, Optional<Bounds> movedBox,
                             List<SnapMatch> matches) {
        if (movedBox.isEmpty() || matches.isEmpty()) {
            return;
        }
        AffineTransform current = graphics.getTransform();
        AffineTransform document = context.viewport().documentTransform();
        AffineTransform screen = new AffineTransform(current);
        try {
            screen.concatenate(document.createInverse());
        } catch (NoninvertibleTransformException exception) {
            return; // a zero zoom never happens; nothing sensible to draw
        }
        Color previous = graphics.getColor();
        graphics.setTransform(screen);
        graphics.setColor(context.scheme().alignmentGuide());
        for (SnapMatch match : matches) {
            paintLine(graphics, document, movedBox.get(), match);
        }
        graphics.setTransform(current);
        graphics.setColor(previous);
    }

    private static void paintLine(Graphics2D graphics, AffineTransform document, Bounds moved, SnapMatch match) {
        boolean vertical = match.axis() == Axis.X;
        int start = vertical ? moved.y() : moved.x();
        int end = vertical ? moved.maximumY() : moved.maximumX();
        for (SnapTarget target : match.targets()) {
            if (target.ownerBox().isPresent()) {
                Bounds owner = target.ownerBox().get();
                start = Math.min(start, vertical ? owner.y() : owner.x());
                end = Math.max(end, vertical ? owner.maximumY() : owner.maximumX());
            }
        }
        start -= OVERHANG_UNITS;
        end += OVERHANG_UNITS;
        double unit = GridPoint.PIXELS_PER_UNIT;
        Point2D first = vertical ? new Point2D.Double(match.position() * unit, start * unit)
                : new Point2D.Double(start * unit, match.position() * unit);
        Point2D last = vertical ? new Point2D.Double(match.position() * unit, end * unit)
                : new Point2D.Double(end * unit, match.position() * unit);
        document.transform(first, first);
        document.transform(last, last);
        if (vertical) {
            int x = (int) Math.floor(first.getX());
            int top = (int) Math.round(first.getY());
            int bottom = (int) Math.round(last.getY());
            graphics.fillRect(x, top, 1, bottom - top + 1);
            graphics.fillRect(x - TICK_HALF_LENGTH, top, 2 * TICK_HALF_LENGTH + 1, 1);
            graphics.fillRect(x - TICK_HALF_LENGTH, bottom, 2 * TICK_HALF_LENGTH + 1, 1);
        } else {
            int y = (int) Math.floor(first.getY());
            int left = (int) Math.round(first.getX());
            int right = (int) Math.round(last.getX());
            graphics.fillRect(left, y, right - left + 1, 1);
            graphics.fillRect(left, y - TICK_HALF_LENGTH, 1, 2 * TICK_HALF_LENGTH + 1);
            graphics.fillRect(right, y - TICK_HALF_LENGTH, 1, 2 * TICK_HALF_LENGTH + 1);
        }
    }
}
