package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.print.PrintedSheets;
import cl.almejo.vsim.ui.print.SheetGrid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws the print sheets on one pane (sheets spec §3, §6, §7): each visible sheet's window as a 1 px dashed rectangle,
 * the overlap bands lightly filled, «Hoja n» inside each sheet big enough on screen (stuck to the visible corner when
 * the sheet's own is scrolled away), and a solid amber outline around each part or frame a border cuts. With «Saltar
 * hojas vacías» an empty sheet is drawn dimmer, says «Hoja n · vacía» and has no band toward it. While the sheets are
 * tiled, their lattice goes on over the rest of the pane, much fainter (no numbers, no bands; left out where it would
 * be too dense to read), and a part or frame a move under way drags is outlined in amber where a border of that
 * lattice would cut it (read from the pane's move preview; the sheets do not move during the drag). In logical
 * screen pixels, so lines stay 1 px and text keeps its size at any zoom; only the sheets the pane shows are visited.
 * A canvas overlay, which image export and the PDF never run: the sheets never reach a file or paper. The summary
 * label is the pane's {@link SheetSummaryHandle}.
 */
final class SheetOverlay implements CanvasOverlay {

    /** Under this width or height on screen a sheet has no «Hoja n» (it would cover it). */
    static final int LABEL_MINIMUM_WIDTH = 48;
    static final int LABEL_MINIMUM_HEIGHT = 24;
    /** How far from the pane's edge a number stuck to the visible corner stays (clear of the guides' rulers). */
    static final int STICKY_INSET = 12;
    /** How far inside the sheet's corner its number starts. */
    private static final int LABEL_INSET = 4;
    /** How far outside a cut part's drawn bounds its mark is drawn, in logical pixels. */
    private static final int CUT_MARGIN = 2;
    /** Length of a dash and of the gap after it, in logical pixels (as the guide lines). */
    private static final float DASH = 4;
    /** How much of the sheet colour's opacity an empty sheet «Saltar hojas vacías» skips keeps: dimmer, still there. */
    private static final float EMPTY_SHEET_OPACITY = 0.4f;
    /**
     * How much of the sheet colour's opacity the lattice outside the sheets keeps: clearly fainter than their borders
     * (the mockup's 0.22 against 0.8), so the sheets that print still stand out.
     */
    private static final float LATTICE_OPACITY = 0.28f;
    /**
     * Under this distance on screen between two columns (or rows) of sheets the lattice outside them is left out: with
     * a pair of lines per step it would be denser than a line every 8 logical pixels, a grey wash rather than a grid.
     */
    static final double MINIMUM_LATTICE_STEP = 16;
    /** How finely the logical-pixel transform is rounded ({@link #withoutRoundingErrors}). */
    private static final double MATRIX_PRECISION = 1e6;

    private final SheetsFeatures features;
    private final CircuitCanvas pane;
    /** What the last measure of the moving parts was made on, and gave (see {@link #cutsOfTheMoving}). */
    private SheetGrid measuredGrid;
    private List<DocumentElement> measuredMoving = List.of();
    private List<Rectangle2D> measuredCuts = List.of();
    private int movingMeasurements;

    SheetOverlay(SheetsFeatures features, CircuitCanvas pane) {
        this.features = features;
        this.pane = pane;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        SheetDrawing drawing = drawing();
        if (drawing.isEmpty()) {
            return;
        }
        Graphics2D screen = (Graphics2D) graphics.create();
        try {
            screen.transform(pane.viewport().documentTransform().createInverse()); // logical pixels again
            screen.setTransform(withoutRoundingErrors(screen.getTransform()));
            paint(screen, drawing, pane.scheme());
        } catch (NoninvertibleTransformException exception) {
            // the viewport clamps the zoom, so this never happens; nothing sensible to draw then
        } finally {
            screen.dispose();
        }
    }

    /**
     * {@code transform} with its entries rounded to a millionth: undoing the zoom leaves errors like 191.99999 for
     * 192, which would put a 1 px line one pixel off where {@link #drawing()} says it is.
     */
    private static AffineTransform withoutRoundingErrors(AffineTransform transform) {
        double[] matrix = new double[6];
        transform.getMatrix(matrix);
        for (int index = 0; index < matrix.length; index++) {
            matrix[index] = Math.rint(matrix[index] * MATRIX_PRECISION) / MATRIX_PRECISION;
        }
        return new AffineTransform(matrix);
    }

    private void paint(Graphics2D screen, SheetDrawing drawing, ColorScheme scheme) {
        // crisp 1 px lines on the pixels drawing() names (the circuit paints with pure, antialiased strokes)
        screen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        screen.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{DASH, DASH},
                0);
        screen.setStroke(dashed);
        paintLattice(screen, drawing, scheme);
        screen.setColor(scheme.sheetBand());
        screen.fill(drawing.bands());
        Color sheet = scheme.sheet();
        screen.setColor(new Color(sheet.getRed(), sheet.getGreen(), sheet.getBlue(),
                Math.round(sheet.getAlpha() * EMPTY_SHEET_OPACITY)));
        for (Rectangle border : drawing.emptyBorders()) {
            screen.drawRect(border.x, border.y, border.width, border.height);
        }
        screen.setColor(sheet);
        for (Rectangle border : drawing.borders()) {
            screen.drawRect(border.x, border.y, border.width, border.height);
        }
        screen.setStroke(new BasicStroke(1));
        screen.setColor(scheme.sheetCut());
        for (Rectangle cut : drawing.cuts()) {
            screen.drawRect(cut.x, cut.y, cut.width, cut.height);
        }
        for (Rectangle cut : drawing.movingCuts()) {
            screen.drawRect(cut.x, cut.y, cut.width, cut.height);
        }
        for (SheetLabel label : drawing.labels()) {
            SheetText.paint(screen, pane, scheme, label.text(), label.box(), false);
        }
    }

    /**
     * The lattice's lines across the whole pane, faint and dashed as the sheets' borders, everywhere but over the block
     * the sheets cover (their own borders are drawn there; two dash patterns on one line would blur it).
     */
    private void paintLattice(Graphics2D screen, SheetDrawing drawing, ColorScheme scheme) {
        if (drawing.latticeColumns().isEmpty() && drawing.latticeRows().isEmpty()) {
            return;
        }
        Graphics2D outside = (Graphics2D) screen.create();
        try {
            Area clip = new Area(new Rectangle(0, 0, pane.getWidth(), pane.getHeight()));
            Rectangle occupied = drawing.occupied();
            // a border drawn with drawRect covers its last column and row too
            clip.subtract(new Area(new Rectangle(occupied.x, occupied.y, occupied.width + 1, occupied.height + 1)));
            outside.clip(clip);
            outside.setColor(latticeColor(scheme));
            // each line starts a whole dash period before the pane's edge, counted from the sheets' own corner: the dashes
            // scroll with the circuit and keep the phase of the sheets' borders, instead of crawling with the pane
            int fromY = dashStart(occupied.y);
            int fromX = dashStart(occupied.x);
            for (int x : drawing.latticeColumns()) {
                outside.drawLine(x, fromY, x, pane.getHeight());
            }
            for (int y : drawing.latticeRows()) {
                outside.drawLine(fromX, y, pane.getWidth(), y);
            }
        } finally {
            outside.dispose();
        }
    }

    /**
     * Where a lattice line crossing the pane starts so that its dashes keep the phase of a line that starts at
     * {@code anchor} (the sheets' corner, which moves with the circuit): the last whole dash period at or before the
     * pane's edge (0).
     */
    static int dashStart(int anchor) {
        int period = (int) (2 * DASH);
        return anchor + Math.floorDiv(-anchor, period) * period;
    }

    /** The colour of the lattice outside the sheets: {@code scheme}'s sheet grey, much fainter; read at paint time. */
    static Color latticeColor(ColorScheme scheme) {
        Color sheet = scheme.sheet();
        return new Color(sheet.getRed(), sheet.getGreen(), sheet.getBlue(),
                Math.round(sheet.getAlpha() * LATTICE_OPACITY));
    }

    /** Whether the lattice outside the sheets is sparse enough on screen to draw (see {@link #MINIMUM_LATTICE_STEP}). */
    static boolean latticeIsReadable(double columnStepPixels, double rowStepPixels) {
        return columnStepPixels >= MINIMUM_LATTICE_STEP && rowStepPixels >= MINIMUM_LATTICE_STEP;
    }

    /** What this pane shows of the sheets now, in logical screen pixels; empty while hidden or with nothing to show. */
    SheetDrawing drawing() {
        Optional<SheetPicture> picture = features.picture();
        if (picture.isEmpty()) {
            return SheetDrawing.EMPTY;
        }
        SheetGrid grid = picture.get().grid();
        Viewport viewport = pane.viewport();
        Point2D visibleTopLeft = viewport.screenToGrid(0, 0);
        Point2D visibleBottomRight = viewport.screenToGrid(pane.getWidth(), pane.getHeight());
        SheetGrid.Range columns = grid.columnsBetween(visibleTopLeft.getX(), visibleBottomRight.getX());
        SheetGrid.Range rows = grid.rowsBetween(visibleTopLeft.getY(), visibleBottomRight.getY());
        PrintedSheets printed = picture.get().printed();
        List<Rectangle> borders = new ArrayList<>();
        List<Rectangle> emptyBorders = new ArrayList<>();
        Area bands = new Area();
        List<SheetLabel> labels = new ArrayList<>();
        for (int row = rows.start(); row < rows.end(); row++) {
            for (int column = columns.start(); column < columns.end(); column++) {
                Rectangle sheet = onScreen(grid.sheetRectangle(column, row));
                boolean empty = printed.isSkipped(column, row);
                (empty ? emptyBorders : borders).add(sheet);
                addBands(bands, grid, printed, column, row);
                if (grid.tiled()) {
                    label(sheet, grid.sheetNumber(column, row), empty).ifPresent(labels::add);
                }
            }
        }
        if (!grid.tiled() || grid.columns() == 0 || grid.rows() == 0) {
            return new SheetDrawing(borders, emptyBorders, bands, labels, cuts(picture.get()), List.of(), List.of(),
                    new Rectangle(), List.of());
        }
        Rectangle2D first = grid.sheetRectangle(0, 0);
        Rectangle2D last = grid.sheetRectangle(grid.columns() - 1, grid.rows() - 1);
        Rectangle occupied = onScreen(new Rectangle2D.Double(first.getX(), first.getY(),
                last.getMaxX() - first.getX(), last.getMaxY() - first.getY()));
        List<Integer> latticeColumns = new ArrayList<>();
        List<Integer> latticeRows = new ArrayList<>();
        double pixelsPerUnit = viewport.pixelsPerUnit();
        if (latticeIsReadable(grid.columnStep() * pixelsPerUnit, grid.rowStep() * pixelsPerUnit)) {
            for (double edge : grid.latticeEdgesX(visibleTopLeft.getX(), visibleBottomRight.getX())) {
                addOnce(latticeColumns, (int) Math.round(viewport.gridToScreen(edge, 0).getX()));
            }
            for (double edge : grid.latticeEdgesY(visibleTopLeft.getY(), visibleBottomRight.getY())) {
                addOnce(latticeRows, (int) Math.round(viewport.gridToScreen(0, edge).getY()));
            }
        }
        return new SheetDrawing(borders, emptyBorders, bands, labels, cuts(picture.get()), latticeColumns,
                latticeRows, occupied, movingCuts(grid));
    }

    /** Adds {@code pixel} unless it is the last one added (two edges of the lattice rounded onto one pixel). */
    private static void addOnce(List<Integer> pixels, int pixel) {
        if (pixels.isEmpty() || pixels.getLast() != pixel) {
            pixels.add(pixel);
        }
    }

    /**
     * The outlines of the parts and frames a move under way drags where a border of the lattice would cut them although
     * they would fit one sheet (spec §3.4 against the lattice continued over the plane, so outside the sheets too):
     * read from the pane's move preview, nothing committed; the sheets themselves stay put during the drag (§5.3).
     */
    private List<Rectangle> movingCuts(SheetGrid grid) {
        List<DocumentElement> moving = pane.movingElements();
        if (moving.isEmpty()) {
            return List.of();
        }
        Rectangle visible = new Rectangle(0, 0, pane.getWidth(), pane.getHeight());
        List<Rectangle> marks = new ArrayList<>();
        for (Rectangle2D drawn : cutsOfTheMoving(grid, moving)) {
            Rectangle mark = onScreen(drawn);
            mark.grow(CUT_MARGIN, CUT_MARGIN);
            if (mark.intersects(visible)) {
                marks.add(mark);
            }
        }
        return marks;
    }

    /**
     * Where the moving parts and frames are drawn, those a border cuts, in grid units: measured once per change of the
     * preview (another offset or element set, or other sheets), not at every repaint of this pane.
     */
    private List<Rectangle2D> cutsOfTheMoving(SheetGrid grid, List<DocumentElement> moving) {
        if (grid != measuredGrid || !moving.equals(measuredMoving)) {
            List<Rectangle2D> cut = new ArrayList<>();
            for (DocumentElement element : moving) {
                if (!(element instanceof Component) && !(element instanceof Frame)) {
                    continue;
                }
                Bounds bounds = DrawnBounds.of(element, pane.openDocument().registry());
                Rectangle2D drawn = new Rectangle2D.Double(bounds.x(), bounds.y(), bounds.width(), bounds.height());
                if (grid.isCutOnLattice(drawn)) {
                    cut.add(drawn);
                }
            }
            measuredGrid = grid;
            measuredMoving = List.copyOf(moving);
            measuredCuts = List.copyOf(cut);
            movingMeasurements++;
        }
        return measuredCuts;
    }

    /** How many times the moving parts were measured (for tests: a repaint without a change measures nothing). */
    int movingMeasurements() {
        return movingMeasurements;
    }

    /**
     * The overlap bands of sheet {@code (column, row)} toward its right and lower neighbours, each only when both
     * sheets go out (nothing is glued to a skipped sheet), joined into {@code bands}: where four sheets meet the fill
     * stays even.
     */
    private void addBands(Area bands, SheetGrid grid, PrintedSheets printed, int column, int row) {
        if (printed.isSkipped(column, row)) {
            return;
        }
        Optional<Rectangle2D> right = grid.rightOverlap(column, row);
        if (right.isPresent() && !printed.isSkipped(column + 1, row)) {
            bands.add(new Area(onScreen(right.get())));
        }
        Optional<Rectangle2D> below = grid.belowOverlap(column, row);
        if (below.isPresent() && !printed.isSkipped(column, row + 1)) {
            bands.add(new Area(onScreen(below.get())));
        }
    }

    /**
     * «Hoja n» (or «Hoja n · vacía» for a skipped sheet) inside {@code sheet}'s top-left corner, or inside the
     * top-left of its visible part when that corner is scrolled out; none when the sheet is small on screen or its
     * visible part has no room for it.
     */
    private Optional<SheetLabel> label(Rectangle sheet, int number, boolean empty) {
        if (sheet.width < LABEL_MINIMUM_WIDTH || sheet.height < LABEL_MINIMUM_HEIGHT) {
            return Optional.empty();
        }
        String text = Messages.format(empty ? "sheets.label.empty" : "sheets.label", number);
        Rectangle box = SheetText.box(pane, text, Math.max(sheet.x, STICKY_INSET) + LABEL_INSET,
                Math.max(sheet.y, STICKY_INSET) + LABEL_INSET);
        return sheet.contains(box) ? Optional.of(new SheetLabel(text, box)) : Optional.empty();
    }

    /** The marks around the cut parts and frames this pane shows. */
    private List<Rectangle> cuts(SheetPicture picture) {
        Rectangle visible = new Rectangle(0, 0, pane.getWidth(), pane.getHeight());
        List<Rectangle> marks = new ArrayList<>();
        for (Rectangle2D cut : picture.cuts()) {
            Rectangle mark = onScreen(cut);
            mark.grow(CUT_MARGIN, CUT_MARGIN);
            if (mark.intersects(visible)) {
                marks.add(mark);
            }
        }
        return marks;
    }

    /** {@code gridRectangle} on this pane, its corners rounded to whole logical pixels. */
    private Rectangle onScreen(Rectangle2D gridRectangle) {
        return onScreen(pane.viewport(), gridRectangle);
    }

    static Rectangle onScreen(Viewport viewport, Rectangle2D gridRectangle) {
        Point2D topLeft = viewport.gridToScreen(gridRectangle.getX(), gridRectangle.getY());
        Point2D bottomRight = viewport.gridToScreen(gridRectangle.getMaxX(), gridRectangle.getMaxY());
        int left = (int) Math.round(topLeft.getX());
        int top = (int) Math.round(topLeft.getY());
        return new Rectangle(left, top, (int) Math.round(bottomRight.getX()) - left,
                (int) Math.round(bottomRight.getY()) - top);
    }
}
