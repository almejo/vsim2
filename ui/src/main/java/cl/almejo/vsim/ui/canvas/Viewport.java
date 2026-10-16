package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Maps grid coordinates to screen pixels: a zoom factor plus the screen position of the grid origin. */
public final class Viewport {

    public static final double MINIMUM_ZOOM = 0.25;
    public static final double MAXIMUM_ZOOM = 4.0;
    public static final double ZOOM_STEP = 1.25;

    private double zoom = 1.0;
    private double originX;
    private double originY;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    /** Runs {@code listener} after every pan or zoom (M6.5: the inline editor follows the text). Returns its removal. */
    public Runnable addChangeListener(Runnable listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    private void changed() {
        changeListeners.forEach(Runnable::run);
    }

    public double zoom() {
        return zoom;
    }

    public double pixelsPerUnit() {
        return GridPoint.PIXELS_PER_UNIT * zoom;
    }

    public Point2D gridToScreen(double gridX, double gridY) {
        return new Point2D.Double(originX + gridX * pixelsPerUnit(), originY + gridY * pixelsPerUnit());
    }

    public Point2D screenToGrid(double screenX, double screenY) {
        return new Point2D.Double((screenX - originX) / pixelsPerUnit(), (screenY - originY) / pixelsPerUnit());
    }

    public GridPoint snap(double screenX, double screenY) {
        Point2D grid = screenToGrid(screenX, screenY);
        return new GridPoint((int) Math.round(grid.getX()), (int) Math.round(grid.getY()));
    }

    public void panBy(double deltaX, double deltaY) {
        originX += deltaX;
        originY += deltaY;
        changed();
    }

    public void zoomAt(double screenX, double screenY, double requestedZoom) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY) || !Double.isFinite(requestedZoom)) {
            return;
        }
        Point2D anchor = screenToGrid(screenX, screenY);
        zoom = Math.max(MINIMUM_ZOOM, Math.min(MAXIMUM_ZOOM, requestedZoom));
        originX = screenX - anchor.getX() * pixelsPerUnit();
        originY = screenY - anchor.getY() * pixelsPerUnit();
        changed();
    }

    public void zoomIn(double screenX, double screenY) {
        zoomAt(screenX, screenY, zoom * ZOOM_STEP);
    }

    public void zoomOut(double screenX, double screenY) {
        zoomAt(screenX, screenY, zoom / ZOOM_STEP);
    }

    public void resetZoom(double screenX, double screenY) {
        zoomAt(screenX, screenY, 1.0);
    }

    public void center(Bounds bounds, int screenWidth, int screenHeight) {
        double centerX = bounds.x() + bounds.width() / 2.0;
        double centerY = bounds.y() + bounds.height() / 2.0;
        originX = screenWidth / 2.0 - centerX * pixelsPerUnit();
        originY = screenHeight / 2.0 - centerY * pixelsPerUnit();
        changed();
    }

    /** Shows grid point ({@code centerX}, {@code centerY}) in the middle of a screen of the given size, at the clamped zoom. */
    public void showAt(double requestedZoom, double centerX, double centerY, int screenWidth, int screenHeight) {
        zoom = Math.max(MINIMUM_ZOOM, Math.min(MAXIMUM_ZOOM, requestedZoom));
        originX = screenWidth / 2.0 - centerX * pixelsPerUnit();
        originY = screenHeight / 2.0 - centerY * pixelsPerUnit();
        changed();
    }

    /** Maps document coordinates (8 px per grid unit, what painters draw in) to the screen. */
    public AffineTransform documentTransform() {
        AffineTransform transform = AffineTransform.getTranslateInstance(originX, originY);
        transform.scale(zoom, zoom);
        return transform;
    }
}
