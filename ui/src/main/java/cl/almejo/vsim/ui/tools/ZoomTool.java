package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.canvas.ZoomCursors;
import cl.almejo.vsim.ui.canvas.ZoomTargets;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.PainterSupport;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Optional;

/**
 * «Zoom» (zoom spec §4): drag a rectangle and the pane travels until it fills the pane exactly; a click zooms one step
 * in around the point, Alt+click, Shift+click or a right click one step out. While dragging, the rectangle shows the
 * zoom the release would give. Esc drops a rectangle being dragged; with none, it goes back to the default tool.
 * A view tool like Mover vista: no document change, no autoscroll, and the gesture stays in the pane pressed.
 */
public final class ZoomTool implements Tool {

    /** How far (screen pixels, on either axis) the pointer must move from the press before a rectangle starts. */
    static final double DRAG_THRESHOLD = 5;
    private static final int BAND_FILL_ALPHA = 40;
    private static final float BAND_WIDTH = 1f;
    /** The gap between the rectangle's bottom-right corner and its label, in screen pixels. */
    private static final double LABEL_GAP = 4;

    /** Where the button went down (screen pixels), or null with no press under way (or after Esc dropped it). */
    private Point2D pressPoint;
    private int pressButton;
    private boolean pressZoomsOut;
    /** The pointer, clamped to the pane (screen pixels), while a press is under way. */
    private Point2D pointer;
    /** True once the pointer went past the threshold: the press is a rectangle (primary) or nothing (secondary). */
    private boolean movedAway;
    private CanvasCursor shownCursor = CanvasCursor.ZOOM_IN;

    @Override
    public void deactivated(ToolContext context) {
        dropPress();
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.MIDDLE) {
            return;
        }
        pressPoint = event.screenPosition();
        pointer = clampToPane(context, pressPoint);
        pressButton = event.button();
        pressZoomsOut = zoomsOut(event.button(), event.shiftDown(), event.altDown());
        movedAway = false;
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        pointer = clampToPane(context, event.screenPosition());
        Point2D position = event.screenPosition();
        if (!movedAway && (Math.abs(position.getX() - pressPoint.getX()) >= DRAG_THRESHOLD
                || Math.abs(position.getY() - pressPoint.getY()) >= DRAG_THRESHOLD)) {
            movedAway = true;
            if (pressButton == CanvasMouseEvent.PRIMARY) {
                show(context, CanvasCursor.CROSSHAIR);
            }
        }
        if (band() != null) {
            context.repaint();
        }
    }

    @Override
    public void mouseReleased(ToolContext context, CanvasMouseEvent event) {
        if (pressPoint == null) {
            return;
        }
        Rectangle2D band = band();
        Point2D clicked = pressPoint;
        boolean click = !movedAway;
        boolean zoomsOut = pressZoomsOut;
        dropPress();
        if (band != null) {
            Dimension size = context.viewSize();
            context.zoomTo(ZoomTargets.region(band, context.viewport(), size.width, size.height));
        } else if (click) {
            double zoom = context.viewport().zoom();
            context.zoomAt(clicked, zoomsOut ? zoom / Viewport.ZOOM_STEP : zoom * Viewport.ZOOM_STEP);
        }
        show(context, magnifier(event.shiftDown(), event.altDown()));
        context.repaint();
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        show(context, magnifier(event.shiftDown(), event.altDown()));
    }

    /** Escape drops the rectangle being dragged (the tool stays); with none, it goes back to the default tool. */
    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (pressPoint != null) {
            boolean hadBand = band() != null;
            dropPress();
            show(context, magnifier(shiftDown, false));
            if (hadBand) {
                context.repaint();
            }
            return true;
        }
        context.useDefaultTool();
        return true;
    }

    /** Alt or Shift turn the magnifier's «+» into a «−» (Ctrl means nothing here); a rectangle keeps the crosshair. */
    @Override
    public boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        if (band() == null) {
            show(context, magnifier(shiftDown, altDown));
        }
        return false;
    }

    @Override
    public boolean animatesOutline() {
        return band() != null;
    }

    /**
     * The rectangle and the zoom it would give, in screen pixels: the graphics come in document units, so the
     * viewport's transform is undone and the band keeps one logical pixel at any zoom.
     */
    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        Rectangle2D band = band();
        if (band == null) {
            return;
        }
        Graphics2D screen = (Graphics2D) graphics.create();
        try {
            AffineTransform document = context.viewport().documentTransform();
            screen.transform(document.createInverse());
            RubberBand.paint(screen, band, context.scheme().selection(), BAND_FILL_ALPHA, BAND_WIDTH,
                    context.selectionDashPhase());
            Optional<String> label = targetLabel(context);
            if (label.isPresent()) {
                screen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics metrics = screen.getFontMetrics();
                double width = metrics.stringWidth(label.get()) + 4;
                double height = metrics.getAscent() + metrics.getDescent();
                Rectangle2D box = labelBounds(band, width, height, context.viewSize());
                PainterSupport.paintTag(screen, metrics, label.get(), box.getX() + 2,
                        box.getY() + metrics.getAscent(), context.scheme().text(), context.scheme().background());
            }
        } catch (NoninvertibleTransformException exception) {
            // a viewport always has a positive zoom; nothing to paint otherwise
        } finally {
            screen.dispose();
        }
    }

    @Override
    public Cursor cursor() {
        return ZoomCursors.zoomIn();
    }

    /** The rectangle being dragged in screen pixels of the pane (clamped to it), or null when none is. */
    Rectangle2D band() {
        if (pressPoint == null || !movedAway || pressButton != CanvasMouseEvent.PRIMARY) {
            return null;
        }
        Point2D corner = pointer;
        double left = Math.min(pressPoint.getX(), corner.getX());
        double top = Math.min(pressPoint.getY(), corner.getY());
        return new Rectangle2D.Double(left, top, Math.abs(corner.getX() - pressPoint.getX()),
                Math.abs(corner.getY() - pressPoint.getY()));
    }

    /** «250 %»: the zoom the release would give, while a rectangle is dragged. */
    Optional<String> targetLabel(ToolContext context) {
        Rectangle2D band = band();
        if (band == null) {
            return Optional.empty();
        }
        Dimension size = context.viewSize();
        ViewState target = ZoomTargets.region(band, context.viewport(), size.width, size.height);
        return Optional.of(Messages.format("zoom.percent", Math.round(target.zoom() * 100)));
    }

    /**
     * Where the label of {@code band} goes: just outside its bottom-right corner, moved back inside {@code pane}
     * when it would leave it.
     */
    static Rectangle2D labelBounds(Rectangle2D band, double labelWidth, double labelHeight, Dimension pane) {
        double left = Math.max(0, Math.min(band.getMaxX() + LABEL_GAP, pane.width - labelWidth));
        double top = Math.max(0, Math.min(band.getMaxY() + LABEL_GAP, pane.height - labelHeight));
        return new Rectangle2D.Double(left, top, labelWidth, labelHeight);
    }

    private static Point2D clampToPane(ToolContext context, Point2D point) {
        Dimension size = context.viewSize();
        return new Point2D.Double(Math.max(0, Math.min(size.width, point.getX())),
                Math.max(0, Math.min(size.height, point.getY())));
    }

    private static boolean zoomsOut(int button, boolean shiftDown, boolean altDown) {
        return button == CanvasMouseEvent.SECONDARY || shiftDown || altDown;
    }

    private static CanvasCursor magnifier(boolean shiftDown, boolean altDown) {
        return shiftDown || altDown ? CanvasCursor.ZOOM_OUT : CanvasCursor.ZOOM_IN;
    }

    private void show(ToolContext context, CanvasCursor cursor) {
        if (cursor != shownCursor) {
            shownCursor = cursor;
            context.setCursor(cursor);
        }
    }

    private void dropPress() {
        pressPoint = null;
        pointer = null;
        movedAway = false;
    }
}
