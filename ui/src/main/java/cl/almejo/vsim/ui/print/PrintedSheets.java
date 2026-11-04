package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.geometry.Bounds;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Which sheets of a {@link PageLayout} go out, in reading order: every sheet, or with «Saltar hojas vacías» only the
 * ones something of the circuit falls on. The one rule the dialog, the printer, the PDF, the AI's export and the
 * canvas share.
 * <p>
 * A sheet is empty when no drawn element of the printed area (its {@code DrawnBounds}, cut to a frame's crop)
 * reaches into the sheet's whole window, the overlap strips included: an element in a strip counts for both sheets,
 * and an element only touching a border does not count for the sheet beyond it. Nothing is skipped on a single
 * sheet, when every sheet would be empty (then the layout goes out as always), or above a million sheets (which
 * never go out anyway). The refusal above {@link PdfExport#REFUSE_ABOVE_PAGES} counts the sheets that go out. Sheets keep their numbers: a skipped sheet
 * leaves a gap, «Hoja 5 de 6» stays «Hoja 5 de 6». Immutable.
 */
public final class PrintedSheets {

    /** Above this many sheets of the grid nothing is told apart: such a layout is refused whatever is empty. */
    static final int SCAN_LIMIT = 1_000_000;

    private final int sheetCount;
    private final int columns;
    /** The layout indices that go out, in order; {@code null} when every sheet does (it may be a huge layout). */
    private final int[] printedIndices;

    private PrintedSheets(int sheetCount, int columns, int[] printedIndices) {
        this.sheetCount = sheetCount;
        this.columns = columns;
        this.printedIndices = printedIndices;
    }

    /** Every sheet of {@code layout} goes out, as printing always did. */
    public static PrintedSheets all(PageLayout layout) {
        Objects.requireNonNull(layout, "layout");
        return new PrintedSheets(layout.pageCount(), layout.columns(), null);
    }

    /**
     * The sheets of {@code grid} that go out when what is printed is drawn at {@code drawnBounds} (grid units) and,
     * for a frame, cut to {@code clip}: all of them unless {@code skipEmptySheets}.
     */
    public static PrintedSheets of(SheetGrid grid, Collection<Bounds> drawnBounds, Optional<Rectangle2D> clip,
                                   boolean skipEmptySheets) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(drawnBounds, "drawnBounds");
        Objects.requireNonNull(clip, "clip");
        PageLayout layout = grid.layout();
        if (!skipEmptySheets || !grid.tiled() || layout.pageCountExact() > SCAN_LIMIT) {
            return all(layout);
        }
        boolean[] used = new boolean[layout.pageCount()];
        for (Bounds bounds : drawnBounds) {
            drawn(bounds, clip).ifPresent(drawn -> markSheetsReached(grid, drawn, used));
        }
        int[] printed = new int[used.length];
        int count = 0;
        for (int index = 0; index < used.length; index++) {
            if (used[index]) {
                printed[count++] = index;
            }
        }
        if (count == 0 || count == used.length) {
            return all(layout);
        }
        return new PrintedSheets(used.length, grid.columns(), Arrays.copyOf(printed, count));
    }

    /**
     * The sheets of {@code layout} that go out for {@code area} of {@code input} with {@code options}, the elements
     * measured with the capture's frozen font: what {@link PagePainter} and the dialog use.
     */
    static PrintedSheets of(PrintInput input, PrintArea area, PrintOptions options, PageLayout layout) {
        if (!options.skipEmptySheets() || !layout.tiled()) {
            return all(layout);
        }
        return of(SheetGrid.of(layout), PrintScopes.drawnBounds(input, area, options.blackAndWhite()), area.clip(), true);
    }

    /** Where {@code bounds} is drawn once cut to {@code clip}; empty when the clip cuts it all away. */
    private static Optional<Rectangle2D> drawn(Bounds bounds, Optional<Rectangle2D> clip) {
        Rectangle2D drawn = new Rectangle2D.Double(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (clip.isEmpty()) {
            return Optional.of(drawn);
        }
        Rectangle2D crop = clip.get();
        double left = Math.max(drawn.getMinX(), crop.getMinX());
        double top = Math.max(drawn.getMinY(), crop.getMinY());
        double right = Math.min(drawn.getMaxX(), crop.getMaxX());
        double bottom = Math.min(drawn.getMaxY(), crop.getMaxY());
        if (right < left || bottom < top) {
            return Optional.empty();
        }
        return Optional.of(new Rectangle2D.Double(left, top, right - left, bottom - top));
    }

    /** Marks every sheet whose window {@code drawn} reaches into (a line or a point counts; touching does not). */
    private static void markSheetsReached(SheetGrid grid, Rectangle2D drawn, boolean[] used) {
        SheetGrid.Range columns = grid.columnsBetween(drawn.getMinX(), drawn.getMaxX());
        SheetGrid.Range rows = grid.rowsBetween(drawn.getMinY(), drawn.getMaxY());
        for (int row = rows.start(); row < rows.end(); row++) {
            for (int column = columns.start(); column < columns.end(); column++) {
                if (reaches(drawn, grid.sheetRectangle(column, row))) {
                    used[row * grid.columns() + column] = true;
                }
            }
        }
    }

    private static boolean reaches(Rectangle2D drawn, Rectangle2D sheet) {
        return drawn.getMinX() < sheet.getMaxX() && drawn.getMaxX() > sheet.getMinX()
                && drawn.getMinY() < sheet.getMaxY() && drawn.getMaxY() > sheet.getMinY();
    }

    /** How many sheets go out. */
    public int count() {
        return printedIndices == null ? sheetCount : printedIndices.length;
    }

    /** Whether too many sheets go out ({@link PdfExport#REFUSE_ABOVE_PAGES}): they are refused, not printed. */
    public boolean tooMany() {
        return count() > PdfExport.REFUSE_ABOVE_PAGES;
    }

    /** How many empty sheets are skipped; 0 when every sheet goes out. */
    public int skippedCount() {
        return sheetCount - count();
    }

    /** The layout index (from 0, reading order) of the {@code position}-th sheet that goes out. */
    public int layoutIndex(int position) {
        Objects.checkIndex(position, count());
        return printedIndices == null ? position : printedIndices[position];
    }

    /** Whether sheet {@code layoutIndex} is skipped as empty. */
    public boolean isSkipped(int layoutIndex) {
        Objects.checkIndex(layoutIndex, sheetCount);
        return printedIndices != null && Arrays.binarySearch(printedIndices, layoutIndex) < 0;
    }

    /** Whether sheet {@code (column, row)} is skipped as empty. */
    public boolean isSkipped(int column, int row) {
        return isSkipped(row * columns + column);
    }

    /** Where sheet {@code layoutIndex} is among the sheets that go out; empty when it is skipped. */
    public OptionalInt positionOf(int layoutIndex) {
        Objects.checkIndex(layoutIndex, sheetCount);
        if (printedIndices == null) {
            return OptionalInt.of(layoutIndex);
        }
        int position = Arrays.binarySearch(printedIndices, layoutIndex);
        return position < 0 ? OptionalInt.empty() : OptionalInt.of(position);
    }
}
