package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.ui.canvas.edges.EdgeGesture;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;

/**
 * The small square where the rulers meet (canvas-edges spec §3): tooltip «Guías», a click opens the guides menu, a
 * drag from it does nothing (VSim has no origin to move).
 */
final class RulerCorner implements EdgeHandle {

    static final int PRIORITY = 30;
    private static final int MARK_INSET = 3;

    private final PaneGuides features;

    RulerCorner(PaneGuides features) {
        this.features = features;
    }

    private static Rectangle area() {
        return new Rectangle(0, 0, GuidesFeatures.RULER_SIZE, GuidesFeatures.RULER_SIZE);
    }

    @Override
    public Shape hitArea(Dimension canvasSize) {
        return area();
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Cursor cursor() {
        return Cursor.getDefaultCursor();
    }

    @Override
    public String tooltip() {
        return Messages.text("guides.corner.tooltip");
    }

    @Override
    public void paint(Graphics2D screenGraphics, Dimension canvasSize, boolean hovered) {
        RulerColors colors = RulerColors.current(hovered);
        int size = GuidesFeatures.RULER_SIZE;
        screenGraphics.setColor(colors.background());
        screenGraphics.fill(area());
        screenGraphics.setColor(colors.border());
        screenGraphics.fillRect(0, size - 1, size, 1);
        screenGraphics.fillRect(size - 1, 0, 1, size);
        screenGraphics.setColor(colors.tick()); // a small «┼»: guides start here
        screenGraphics.fillRect(MARK_INSET, size / 2 - 1, size - 2 * MARK_INSET - 1, 1);
        screenGraphics.fillRect(size / 2 - 1, MARK_INSET, 1, size - 2 * MARK_INSET - 1);
    }

    @Override
    public EdgeGesture press(CanvasMouseEvent event) {
        return new EdgeGesture() {
            @Override
            public void drag(CanvasMouseEvent drag) {
            }

            @Override
            public void release(CanvasMouseEvent release) {
                if (area().contains(release.screenPosition())) {
                    features.showCornerMenu();
                }
            }

            @Override
            public void cancel() {
            }
        };
    }
}
