package cl.almejo.vsim.ui.actions;

/**
 * What the window's zoom actions (Ver ▸ Zoom, Ctrl++ / Ctrl+-, the status bar's zoom control) act on while a view
 * shows another content instead of its circuit panes: a machine's diagram (spec 2026-11-14 §2.4). EDT only.
 */
public interface ContentZoom {

    /** The zoom shown now (1.0 is 100 %). */
    double zoom();

    /** Zooms around the middle of the content to {@code zoom}, clamped to what it can show. */
    void zoomAroundCenter(double zoom);

    /** One step closer, around the middle. */
    void zoomIn();

    /** One step farther, around the middle. */
    void zoomOut();

    /** Back to 100 %, around the middle. */
    void resetZoom();

    /** Zooms until everything the content holds fills it («Ver todo el circuito»). */
    void zoomToFit();

    /** Moves the view so what the content holds is in the middle, keeping the zoom («Centrar circuito»). */
    void center();

    /** Runs {@code listener} after every zoom or pan; returns its removal (the status bar follows the zoom). */
    Runnable addViewListener(Runnable listener);
}
