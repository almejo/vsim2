package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

/**
 * Where a zoom the user asks for takes the view (zoom spec §3): a part of the circuit to fit in the pane («Ver todo el
 * circuito», «Zoom a la selección»), a rectangle framed on screen (the zoom tool) or another zoom at the same centre
 * (100 %, a preset, a typed value). Pure: the canvas goes there with {@link CircuitCanvas#zoomTo}. Every zoom is
 * clamped to the viewport's limits.
 */
public final class ZoomTargets {

    private ZoomTargets() {
    }

    /**
     * The largest zoom at which {@code grid} (grid units) fills at most {@code fraction} of a pane of the given size
     * each way, centred on {@code grid}'s centre. A side of zero size (a straight wire) leaves only the other side to
     * fit; with neither (a lone point) the view keeps {@code currentZoom} and is only centred.
     */
    public static ViewState fitting(Bounds grid, int paneWidth, int paneHeight, double fraction, double currentZoom) {
        Objects.requireNonNull(grid, "grid");
        if (!(fraction > 0 && fraction <= 1)) {
            throw new IllegalArgumentException("Not a fraction of the pane: " + fraction);
        }
        double fittingZoom = Math.min(
                zoomToFit(grid.width() * (double) GridPoint.PIXELS_PER_UNIT, paneWidth * fraction),
                zoomToFit(grid.height() * (double) GridPoint.PIXELS_PER_UNIT, paneHeight * fraction));
        double zoom = Double.isInfinite(fittingZoom) ? currentZoom : fittingZoom;
        return new ViewState(Viewport.clampZoom(zoom), grid.x() + grid.width() / 2.0, grid.y() + grid.height() / 2.0);
    }

    /**
     * The view in which {@code screen} (pixels of a pane of the given size, shown through {@code viewport}) fills the
     * pane: it touches two opposite edges and fits inside the other two («what I framed is what I get»), with the grid
     * point under its centre in the middle. A rectangle without size keeps the current zoom.
     */
    public static ViewState region(Rectangle2D screen, Viewport viewport, int paneWidth, int paneHeight) {
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(viewport, "viewport");
        double scale = Math.min(zoomToFit(screen.getWidth(), paneWidth), zoomToFit(screen.getHeight(), paneHeight));
        double zoom = Double.isInfinite(scale) ? viewport.zoom() : viewport.zoom() * scale;
        Point2D centre = viewport.screenToGrid(screen.getCenterX(), screen.getCenterY());
        return new ViewState(Viewport.clampZoom(zoom), centre.getX(), centre.getY());
    }

    /** {@code current}'s centre at {@code zoom}: 100 %, a preset or a typed value, about the middle of the pane. */
    public static ViewState atZoom(ViewState current, double zoom) {
        Objects.requireNonNull(current, "current");
        return new ViewState(Viewport.clampZoom(zoom), current.centerX(), current.centerY());
    }

    /** The scale that makes {@code size} fill {@code room}; infinite when either has no size (it constrains nothing). */
    private static double zoomToFit(double size, double room) {
        if (size <= 0 || room <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return room / size;
    }
}
