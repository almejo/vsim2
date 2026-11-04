package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sheets of a {@link PageLayout} as the canvas draws them (sheets spec §3), in grid units: each sheet's whole
 * window (not cut to the area, so the last column and row show the room left on them), the overlap bands between
 * neighbours, the «Hoja n» numbers and which parts a border cuts. Kept apart from {@code PageLayout}, which stays the
 * paper's contract; built from the same layout, so the canvas and the paper agree sheet by sheet.
 * <p>
 * Sheet {@code (column, row)} starts at the laid-out area's corner plus {@code column} and {@code row} steps
 * ({@code window − overlap}); a single sheet is the window centred on the area, as the paper centres it. A layout
 * with no room has no sheets. Immutable.
 */
public final class SheetGrid {

    /** Grid units of slack in the containment test, so a part exactly on a border counts as inside. */
    private static final double EPSILON = 1e-9;

    /**
     * Consecutive column or row indices {@code start} (included) to {@code end} (excluded); empty when they are
     * equal. Iterate with {@code for (int column = range.start(); column < range.end(); column++)}.
     */
    public record Range(int start, int end) {

        static final Range EMPTY = new Range(0, 0);

        public Range {
            if (end < start) {
                throw new IllegalArgumentException("a range ends before it starts: " + start + ".." + end);
            }
        }

        public boolean isEmpty() {
            return start == end;
        }

        public int size() {
            return end - start;
        }

        public boolean contains(int index) {
            return index >= start && index < end;
        }
    }

    private final PageLayout layout;
    private final int columns;
    private final int rows;
    private final double left;
    private final double top;
    private final double sheetWidth;
    private final double sheetHeight;
    private final double stepX;
    private final double stepY;

    private SheetGrid(PageLayout layout) {
        this.layout = layout;
        this.columns = layout.columns();
        this.rows = layout.rows();
        double pointsPerUnit = GridPoint.PIXELS_PER_UNIT * layout.scale();
        Rectangle2D area = layout.gridArea();
        Rectangle2D window = layout.window();
        this.sheetWidth = window.getWidth() / pointsPerUnit;
        this.sheetHeight = window.getHeight() / pointsPerUnit;
        this.stepX = (window.getWidth() - layout.overlapPoints()) / pointsPerUnit;
        this.stepY = (window.getHeight() - layout.overlapPoints()) / pointsPerUnit;
        this.left = layout.tiled() ? area.getX() : area.getCenterX() - sheetWidth / 2;
        this.top = layout.tiled() ? area.getY() : area.getCenterY() - sheetHeight / 2;
    }

    /** The sheets of {@code layout}, as {@link SheetPlan#layout} or {@link PageLayout#of} made it. */
    public static SheetGrid of(PageLayout layout) {
        return new SheetGrid(Objects.requireNonNull(layout, "layout"));
    }

    public PageLayout layout() {
        return layout;
    }

    /** The columns of sheets; 0 when the paper has no room. */
    public int columns() {
        return columns;
    }

    /** The rows of sheets; 0 when the paper has no room. */
    public int rows() {
        return rows;
    }

    /** Whether there are several sheets (then they have numbers, overlap bands and cut parts). */
    public boolean tiled() {
        return layout.tiled();
    }

    /** The whole window of sheet {@code (column, row)} in grid units, not cut to the area. */
    public Rectangle2D sheetRectangle(int column, int row) {
        requireSheet(column, row);
        return new Rectangle2D.Double(left + column * stepX, top + row * stepY, sheetWidth, sheetHeight);
    }

    /** The columns whose sheets reach into {@code fromX..toX} (touching counts), e.g. the visible part of a pane. */
    public Range columnsBetween(double fromX, double toX) {
        return between(fromX, toX, left, stepX, sheetWidth, columns);
    }

    /** The rows whose sheets reach into {@code fromY..toY} (touching counts). */
    public Range rowsBetween(double fromY, double toY) {
        return between(fromY, toY, top, stepY, sheetHeight, rows);
    }

    private static Range between(double from, double to, double origin, double step, double length, int count) {
        if (count == 0 || to < from) {
            return Range.EMPTY;
        }
        // sheet k spans origin + k·step .. origin + k·step + length
        double first = Math.ceil((from - origin - length) / step);
        double last = Math.floor((to - origin) / step);
        int start = (int) Math.max(0, first);
        int end = (int) Math.min(count, last + 1);
        return end > start ? new Range(start, end) : Range.EMPTY;
    }

    /**
     * Where sheet {@code (column, row)} overlaps its right neighbour and its lower neighbour, in that order, each
     * pair of neighbours once: what lies there is printed on both sheets. None on the last column and row, none on
     * a single sheet. Bands of different pairs overlap where four sheets meet; a painter wanting an even fill joins
     * them first.
     */
    public List<Rectangle2D> overlapBands(int column, int row) {
        List<Rectangle2D> bands = new ArrayList<>(2);
        rightOverlap(column, row).ifPresent(bands::add);
        belowOverlap(column, row).ifPresent(bands::add);
        return List.copyOf(bands);
    }

    /** Where sheet {@code (column, row)} overlaps its right neighbour; none on the last column or a single sheet. */
    public Optional<Rectangle2D> rightOverlap(int column, int row) {
        requireSheet(column, row);
        double overlapX = sheetWidth - stepX;
        if (column + 1 >= columns || overlapX <= 0) {
            return Optional.empty();
        }
        Rectangle2D sheet = sheetRectangle(column, row);
        return Optional.of(new Rectangle2D.Double(sheet.getMaxX() - overlapX, sheet.getY(), overlapX, sheetHeight));
    }

    /** Where sheet {@code (column, row)} overlaps its lower neighbour; none on the last row or a single sheet. */
    public Optional<Rectangle2D> belowOverlap(int column, int row) {
        requireSheet(column, row);
        double overlapY = sheetHeight - stepY;
        if (row + 1 >= rows || overlapY <= 0) {
            return Optional.empty();
        }
        Rectangle2D sheet = sheetRectangle(column, row);
        return Optional.of(new Rectangle2D.Double(sheet.getX(), sheet.getMaxY() - overlapY, sheetWidth, overlapY));
    }

    /**
     * Whether a sheet border cuts a part or frame with these drawn bounds although it would fit one sheet (spec
     * §3.4): no sheet holds it whole, and one sheet is at least as big. Bounds inside an overlap band are held by
     * both sheets, so they are not cut; bounds larger than a sheet are cut anyway and not marked; a single sheet cuts
     * nothing. O(1): the lattice gives the only sheets that could hold it.
     */
    public boolean isCut(Rectangle2D drawnBounds) {
        Objects.requireNonNull(drawnBounds, "drawnBounds");
        if (!tiled() || drawnBounds.getWidth() > sheetWidth + EPSILON
                || drawnBounds.getHeight() > sheetHeight + EPSILON) {
            return false;
        }
        boolean heldAcross = held(drawnBounds.getX(), drawnBounds.getMaxX(), left, stepX, sheetWidth, columns);
        boolean heldDown = held(drawnBounds.getY(), drawnBounds.getMaxY(), top, stepY, sheetHeight, rows);
        return !(heldAcross && heldDown);
    }

    /** Whether one sheet of the lattice spans {@code from..to} on this axis. */
    private static boolean held(double from, double to, double origin, double step, double length, int count) {
        // sheet k holds it when origin + k·step <= from and to <= origin + k·step + length
        double lowest = Math.ceil((to - origin - length) / step - EPSILON);
        double highest = Math.floor((from - origin) / step + EPSILON);
        return Math.max(0, lowest) <= Math.min(count - 1, highest);
    }

    /**
     * The «Hoja n» of sheet {@code (column, row)}: reading order (row by row, columns left to right) from 1, as
     * {@link PageLayout#page}; {@link Integer#MAX_VALUE} past it (far beyond what the PDF accepts).
     */
    public int sheetNumber(int column, int row) {
        requireSheet(column, row);
        return (int) Math.min(Integer.MAX_VALUE, (long) row * columns + column + 1);
    }

    private void requireSheet(int column, int row) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("sheet (" + column + ", " + row + ") of " + columns + " × " + rows);
        }
    }
}
