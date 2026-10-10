package cl.almejo.vsim.ui.canvas;

/**
 * What a view of a circuit shows: its zoom and the grid point (grid units) in the middle of its canvas. Restored by
 * Archivo ▸ Recientes and the last session (M6-B) and copied into a new view (M6-C).
 */
public record ViewState(double zoom, double centerX, double centerY) {

    public ViewState {
        if (!Double.isFinite(zoom) || zoom <= 0 || !Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            throw new IllegalArgumentException("Not a view state: zoom " + zoom + ", center " + centerX + ", " + centerY);
        }
    }
}
