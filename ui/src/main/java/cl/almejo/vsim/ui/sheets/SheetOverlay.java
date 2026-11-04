package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.print.SheetGrid;

import java.awt.BasicStroke;
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
 * the sheet's own is scrolled away), and a solid amber outline around each part or frame a border cuts. In logical
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
    /** How finely the logical-pixel transform is rounded ({@link #withoutRoundingErrors}). */
    private static final double MATRIX_PRECISION = 1e6;

    private final SheetsFeatures features;
    private final CircuitCanvas pane;

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
        screen.setColor(scheme.sheetBand());
        screen.fill(drawing.bands());
        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{DASH, DASH},
                0);
        screen.setStroke(dashed);
        screen.setColor(scheme.sheet());
        for (Rectangle border : drawing.borders()) {
            screen.drawRect(border.x, border.y, border.width, border.height);
        }
        screen.setStroke(new BasicStroke(1));
        screen.setColor(scheme.sheetCut());
        for (Rectangle cut : drawing.cuts()) {
            screen.drawRect(cut.x, cut.y, cut.width, cut.height);
        }
        for (SheetLabel label : drawing.labels()) {
            SheetText.paint(screen, pane, scheme, label.text(), label.box(), false);
        }
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
        List<Rectangle> borders = new ArrayList<>();
        Area bands = new Area();
        List<SheetLabel> labels = new ArrayList<>();
        for (int row = rows.start(); row < rows.end(); row++) {
            for (int column = columns.start(); column < columns.end(); column++) {
                Rectangle sheet = onScreen(grid.sheetRectangle(column, row));
                borders.add(sheet);
                for (Rectangle2D band : grid.overlapBands(column, row)) {
                    bands.add(new Area(onScreen(band))); // joined: where four sheets meet the fill stays even
                }
                if (grid.tiled()) {
                    label(sheet, grid.sheetNumber(column, row)).ifPresent(labels::add);
                }
            }
        }
        return new SheetDrawing(borders, bands, labels, cuts(picture.get()));
    }

    /**
     * «Hoja n» inside {@code sheet}'s top-left corner, or inside the top-left of its visible part when that corner is
     * scrolled out; none when the sheet is small on screen or its visible part has no room for it.
     */
    private Optional<SheetLabel> label(Rectangle sheet, int number) {
        if (sheet.width < LABEL_MINIMUM_WIDTH || sheet.height < LABEL_MINIMUM_HEIGHT) {
            return Optional.empty();
        }
        String text = Messages.format("sheets.label", number);
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
