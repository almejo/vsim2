package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.edges.EdgeGesture;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * The summary label of one pane (sheets spec §3.1 point 4, §5.5): «A4 vertical · 100 % · 2 × 3 = 6 hojas» just above
 * the first sheet, the only piece of the sheets that takes the mouse. An {@link EdgeHandle} only for the press, the
 * hand cursor, the hover and the tooltip: it takes the press inside its own text box, before the tool, and opens the
 * quick options popup; everywhere else clicks reach the circuit as always. Below the guides' handles, which win where
 * they overlap it.
 */
final class SheetSummaryHandle implements EdgeHandle {

    /** Lower than the guide lines (10), the rulers (20) and their corner (30). */
    static final int PRIORITY = 5;
    /** The gap between the label and the sheet's top border, in logical pixels. */
    private static final int GAP = 3;

    /** The press opens the popup at once; nothing follows it. */
    private static final EdgeGesture NOTHING_MORE = new EdgeGesture() {
        @Override
        public void drag(CanvasMouseEvent event) {
        }

        @Override
        public void release(CanvasMouseEvent event) {
        }

        @Override
        public void cancel() {
        }
    };

    private final SheetsFeatures features;
    private final CircuitCanvas pane;

    SheetSummaryHandle(SheetsFeatures features, CircuitCanvas pane) {
        this.features = features;
        this.pane = pane;
    }

    /** Where the label stands on this pane now, in logical pixels; empty while there are no sheets to sum up. */
    Optional<Rectangle> box() {
        return features.picture().map(picture -> {
            Point2D corner = pane.viewport().gridToScreen(picture.summaryCorner().getX(),
                    picture.summaryCorner().getY());
            Rectangle box = SheetText.box(pane, picture.summary(), (int) Math.round(corner.getX()), 0);
            box.y = (int) Math.round(corner.getY()) - GAP - box.height;
            return box;
        });
    }

    @Override
    public Shape hitArea(Dimension canvasSize) {
        return box().orElseGet(Rectangle::new);
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    @Override
    public String tooltip() {
        return Messages.text("sheets.summary.tooltip");
    }

    @Override
    public void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered) {
        Optional<SheetPicture> picture = features.picture();
        Optional<Rectangle> box = box();
        if (picture.isPresent() && box.isPresent()) {
            SheetText.paint(screenGraphics, pane, pane.scheme(), picture.get().summary(), box.get(), hovered);
        }
    }

    @Override
    public EdgeGesture press(CanvasMouseEvent event) {
        box().ifPresent(box -> features.showQuickOptions(pane, box.x, box.y + box.height));
        return NOTHING_MORE;
    }
}
