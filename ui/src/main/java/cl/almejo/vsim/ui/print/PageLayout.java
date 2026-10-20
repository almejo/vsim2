package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;

/**
 * How an area of the circuit is laid out over sheets (spec §3.4). Pure page math in points and {@code double}; grid
 * units only for {@link PrintPage#gridRectangle()}. 100 % prints one canvas pixel at zoom 100 % as one point.
 * <p>
 * The window of a page is the paper minus the margins and the header and footer bands; a single page centres the
 * circuit in it. When the circuit does not fit one window, the pages are tiled: the window also loses the marks'
 * strip on its four sides, the circuit starts at its top-left corner, and pages advance by the constant step
 * {@code window − overlap}. Pages are counted, never stored: {@link #page(int)} builds one on demand.
 */
public final class PageLayout {

    /** The message key shown when the paper leaves no room to print. */
    public static final String NO_ROOM_KEY = "print.layout.noRoom";
    /** Points of slack so that exact fits do not add a page. */
    static final double EPSILON = 1e-6;

    private final Rectangle2D gridArea;
    private final PageGeometry geometry;
    private final double scale;
    private final boolean tiled;
    private final long columns;
    private final long rows;
    private final Rectangle2D window;
    private final double overlapPoints;
    private final String problem;

    private PageLayout(Rectangle2D gridArea, PageGeometry geometry, double scale, boolean tiled, long columns,
                       long rows, Rectangle2D window, double overlapPoints, String problem) {
        this.gridArea = gridArea;
        this.geometry = geometry;
        this.scale = scale;
        this.tiled = tiled;
        this.columns = columns;
        this.rows = rows;
        this.window = window;
        this.overlapPoints = overlapPoints;
        this.problem = problem;
    }

    /** Lays {@code gridArea} (grid units, any origin, zero width or height allowed) out on sheets of {@code geometry}. */
    public static PageLayout of(Rectangle2D gridArea, PageGeometry geometry, ScaleMode mode) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(mode, "mode");
        Rectangle2D area = validArea(gridArea);
        double widthPixels = area.getWidth() * GridPoint.PIXELS_PER_UNIT;
        double heightPixels = area.getHeight() * GridPoint.PIXELS_PER_UNIT;
        double singleLeft = geometry.marginPoints();
        double singleTop = geometry.marginPoints() + geometry.headerBandPoints();
        double singleWidth = geometry.paperWidthPoints() - 2 * geometry.marginPoints();
        double singleHeight = geometry.paperHeightPoints() - 2 * geometry.marginPoints()
                - 2 * geometry.headerBandPoints();
        double scale = switch (mode) {
            case ScaleMode.FitToOnePage fit -> fitScale(widthPixels, heightPixels, singleWidth, singleHeight);
            case ScaleMode.Percentage percentage -> percentage.percent() / 100.0;
        };
        if (singleWidth <= 0 || singleHeight <= 0) {
            return refused(area, geometry, scale);
        }
        double printedWidth = widthPixels * scale;
        double printedHeight = heightPixels * scale;
        if (printedWidth <= singleWidth + EPSILON && printedHeight <= singleHeight + EPSILON) {
            Rectangle2D window = new Rectangle2D.Double(singleLeft, singleTop, singleWidth, singleHeight);
            return new PageLayout(area, geometry, scale, false, 1, 1, window, 0, null);
        }
        double strip = geometry.marksStripPoints();
        double tiledWidth = singleWidth - 2 * strip;
        double tiledHeight = singleHeight - 2 * strip;
        if (tiledWidth <= 0 || tiledHeight <= 0) {
            return refused(area, geometry, scale);
        }
        // at most half the window, so a sheet only ever overlaps its direct neighbours
        double overlap = Math.min(geometry.overlapPoints(), Math.min(tiledWidth, tiledHeight) / 2);
        Rectangle2D window = new Rectangle2D.Double(singleLeft + strip, singleTop + strip, tiledWidth, tiledHeight);
        return new PageLayout(area, geometry, scale, true, count(printedWidth, tiledWidth, overlap),
                count(printedHeight, tiledHeight, overlap), window, overlap, null);
    }

    private static Rectangle2D validArea(Rectangle2D gridArea) {
        Objects.requireNonNull(gridArea, "gridArea");
        boolean finite = Double.isFinite(gridArea.getX()) && Double.isFinite(gridArea.getY())
                && Double.isFinite(gridArea.getWidth()) && Double.isFinite(gridArea.getHeight());
        if (!finite || gridArea.getWidth() < 0 || gridArea.getHeight() < 0) {
            throw new IllegalArgumentException("the grid area must be finite with a size >= 0: " + gridArea);
        }
        return new Rectangle2D.Double(gridArea.getX(), gridArea.getY(), gridArea.getWidth(), gridArea.getHeight());
    }

    /** The largest scale that fits the window, never above 100 %; a zero dimension does not limit it. */
    private static double fitScale(double widthPixels, double heightPixels, double windowWidth, double windowHeight) {
        double scale = 1.0;
        if (widthPixels > 0) {
            scale = Math.min(scale, windowWidth / widthPixels);
        }
        if (heightPixels > 0) {
            scale = Math.min(scale, windowHeight / heightPixels);
        }
        return scale > 0 ? scale : 1.0;
    }

    /** {@code 1} if the length fits, else {@code 1 + ceil((L − W) / (W − O) − ε)}, saturated to {@code long}. */
    private static long count(double printedLength, double windowLength, double overlap) {
        if (printedLength <= windowLength + EPSILON) {
            return 1;
        }
        double count = 1 + Math.ceil((printedLength - windowLength) / (windowLength - overlap) - EPSILON);
        return count >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) count;
    }

    private static PageLayout refused(Rectangle2D area, PageGeometry geometry, double scale) {
        return new PageLayout(area, geometry, scale, false, 0, 0, new Rectangle2D.Double(), 0, NO_ROOM_KEY);
    }

    public int columns() {
        return saturatedInt(columns);
    }

    public int rows() {
        return saturatedInt(rows);
    }

    /** The number of sheets, {@link Integer#MAX_VALUE} when larger; see {@link #pageCountExact()}. */
    public int pageCount() {
        return saturatedInt(pageCountExact());
    }

    /** The number of sheets, {@link Long#MAX_VALUE} when larger; 0 when refused. */
    public long pageCountExact() {
        if (columns != 0 && rows > Long.MAX_VALUE / columns) {
            return Long.MAX_VALUE;
        }
        return columns * rows;
    }

    /** Printed points per canvas pixel at zoom 100 %; never above 1 when fitting to one page. */
    public double scale() {
        return scale;
    }

    /** Whether the circuit is split over several sheets (then they carry marks and labels). */
    public boolean tiled() {
        return tiled;
    }

    /** How big text of {@code canvasTextPixels} at zoom 100 % comes out on paper, in points. */
    public double smallestTextPoints(double canvasTextPixels) {
        return canvasTextPixels * scale;
    }

    /** The message key of why nothing can be printed (the paper leaves no window), when so. */
    public Optional<String> problem() {
        return Optional.ofNullable(problem);
    }

    /** The area being laid out, in grid units. */
    public Rectangle2D gridArea() {
        return (Rectangle2D) gridArea.clone();
    }

    public PageGeometry geometry() {
        return geometry;
    }

    /**
     * The window of every page, in points from the sheet's top-left corner: inside the margins and the bands, and
     * inside the marks' strip when tiled. Empty when refused.
     */
    public Rectangle2D window() {
        return (Rectangle2D) window.clone();
    }

    /** The overlap actually used between neighbouring sheets (after clamping), in points; 0 on a single page. */
    public double overlapPoints() {
        return overlapPoints;
    }

    /** The sheet at {@code index} in reading order (row by row, columns left to right), built on demand. */
    public PrintPage page(int index) {
        if (index < 0 || index >= pageCountExact()) {
            throw new IndexOutOfBoundsException("page " + index + " of " + pageCountExact());
        }
        int row = (int) (index / columns);
        int column = (int) (index % columns);
        double printedWidth = gridArea.getWidth() * GridPoint.PIXELS_PER_UNIT * scale;
        double printedHeight = gridArea.getHeight() * GridPoint.PIXELS_PER_UNIT * scale;
        if (!tiled) {
            Rectangle2D paper = new Rectangle2D.Double(
                    window.getX() + (window.getWidth() - printedWidth) / 2,
                    window.getY() + (window.getHeight() - printedHeight) / 2, printedWidth, printedHeight);
            return new PrintPage(index, row, column, gridArea, paper);
        }
        double offsetX = column * (window.getWidth() - overlapPoints);
        double offsetY = row * (window.getHeight() - overlapPoints);
        double endX = Math.min(offsetX + window.getWidth(), printedWidth);
        double endY = Math.min(offsetY + window.getHeight(), printedHeight);
        Rectangle2D paper = new Rectangle2D.Double(window.getX(), window.getY(), Math.max(0, endX - offsetX),
                Math.max(0, endY - offsetY));
        double pointsPerGridUnit = GridPoint.PIXELS_PER_UNIT * scale;
        double gridLeft = gridArea.getX() + offsetX / pointsPerGridUnit;
        double gridTop = gridArea.getY() + offsetY / pointsPerGridUnit;
        Rectangle2D grid = new Rectangle2D.Double(gridLeft, gridTop,
                Math.max(0, gridArea.getX() + endX / pointsPerGridUnit - gridLeft),
                Math.max(0, gridArea.getY() + endY / pointsPerGridUnit - gridTop));
        return new PrintPage(index, row, column, grid, paper);
    }

    private static int saturatedInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
