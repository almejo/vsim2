package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.ui.app.AppPreferences.GridStyle;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.OptionalInt;

/**
 * The grid behind a circuit (dots or engineering-paper lines), in screen coordinates and in the colours of the
 * scheme it is given: shared by the circuit canvas and the shape editor, so both look the same.
 */
public final class GridPainter {
    private static final double MINIMUM_DOT_SPACING = 6;
    /** The grid of lines: a stronger line every fifth minor line (engineering paper: every 10 units at 2). */
    private static final int MINOR_LINES_PER_MAJOR = 5;

    private GridPainter() {
    }

    /**
     * The grid in {@code style} over the {@code width} by {@code height} pane (only where {@code graphics} can paint);
     * the background is the caller's.
     */
    public static void paint(Graphics2D graphics, Viewport viewport, int width, int height, GridStyle style,
                             int lineSpacing, ColorScheme scheme) {
        switch (style) {
            case DOTS -> paintDots(graphics, viewport, width, height, scheme);
            case LINES -> paintLines(graphics, viewport, width, height, lineSpacing, scheme);
            case NONE -> {
            }
        }
    }

    /** Today's grid: a dot on every unit (every fourth one when they would be too close). */
    public static void paintDots(Graphics2D graphics, Viewport viewport, int width, int height,
                                 ColorScheme scheme) {
        int step = viewport.pixelsPerUnit() < MINIMUM_DOT_SPACING ? 4 : 1;
        Rectangle area = paintedArea(graphics, width, height);
        if (area.isEmpty()) {
            return;
        }
        Point2D topLeft = viewport.screenToGrid(area.x, area.y);
        Point2D bottomRight = viewport.screenToGrid(area.getMaxX(), area.getMaxY());
        int firstX = Math.floorDiv((int) Math.floor(topLeft.getX()), step) * step;
        int firstY = Math.floorDiv((int) Math.floor(topLeft.getY()), step) * step;
        int dotSize = viewport.zoom() >= 2 ? 2 : 1;
        graphics.setColor(scheme.grid());
        for (int gridX = firstX; gridX <= bottomRight.getX(); gridX += step) {
            for (int gridY = firstY; gridY <= bottomRight.getY(); gridY += step) {
                Point2D screen = viewport.gridToScreen(gridX, gridY);
                graphics.fillRect((int) Math.round(screen.getX()), (int) Math.round(screen.getY()), dotSize, dotSize);
            }
        }
    }

    /**
     * Engineering paper (spec §4.6): a 1 px line every {@code lineSpacing} units and a stronger one every
     * {@value #MINOR_LINES_PER_MAJOR} of them; the lines closer than the dots' minimum spacing are left out.
     */
    public static void paintLines(Graphics2D graphics, Viewport viewport, int width, int height, int lineSpacing,
                                  ColorScheme scheme) {
        OptionalInt finest = finestLineStep(viewport.pixelsPerUnit(), lineSpacing);
        int majorStep = lineSpacing * MINOR_LINES_PER_MAJOR;
        if (finest.isEmpty()) {
            return;
        }
        int step = finest.getAsInt();
        Rectangle area = paintedArea(graphics, width, height);
        if (area.isEmpty()) {
            return;
        }
        Point2D topLeft = viewport.screenToGrid(area.x, area.y);
        Point2D bottomRight = viewport.screenToGrid(area.getMaxX(), area.getMaxY());
        for (int gridX = firstMultiple(topLeft.getX(), step); gridX <= bottomRight.getX(); gridX += step) {
            graphics.setColor(lineColor(scheme, gridX, majorStep));
            graphics.fillRect((int) Math.round(viewport.gridToScreen(gridX, 0).getX()), area.y, 1, area.height);
        }
        for (int gridY = firstMultiple(topLeft.getY(), step); gridY <= bottomRight.getY(); gridY += step) {
            graphics.setColor(lineColor(scheme, gridY, majorStep));
            graphics.fillRect(area.x, (int) Math.round(viewport.gridToScreen(0, gridY).getY()), area.width, 1);
        }
    }

    /**
     * The part of the {@code width} by {@code height} pane the grid has to cover: where {@code graphics} can paint (a
     * repaint of a small area, the lens moving or a hover, clips to it), so a big pane does not cost every dot.
     */
    private static Rectangle paintedArea(Graphics2D graphics, int width, int height) {
        Rectangle pane = new Rectangle(0, 0, width, height);
        Rectangle clip = graphics.getClipBounds();
        return clip == null ? pane : pane.intersection(clip);
    }

    private static Color lineColor(ColorScheme scheme, int gridCoordinate, int majorStep) {
        return Math.floorMod(gridCoordinate, majorStep) == 0 ? scheme.gridLineMajor() : scheme.gridLine();
    }

    private static int firstMultiple(double gridCoordinate, int step) {
        return Math.floorDiv((int) Math.floor(gridCoordinate), step) * step;
    }

    /**
     * The step in grid units of the finest lines worth drawing at {@code pixelsPerUnit} with minor lines every
     * {@code lineSpacing} units: the minor lines, only the major ones when the minor would be under
     * {@code MINIMUM_DOT_SPACING} pixels apart, none when even those are.
     */
    static OptionalInt finestLineStep(double pixelsPerUnit, int lineSpacing) {
        if (lineSpacing * pixelsPerUnit >= MINIMUM_DOT_SPACING) {
            return OptionalInt.of(lineSpacing);
        }
        int majorStep = lineSpacing * MINOR_LINES_PER_MAJOR;
        if (majorStep * pixelsPerUnit >= MINIMUM_DOT_SPACING) {
            return OptionalInt.of(majorStep);
        }
        return OptionalInt.empty();
    }
}
