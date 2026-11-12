package cl.almejo.vsim.ui.canvas;

import java.awt.Graphics2D;

/**
 * Something painted over a whole pane in its logical pixels, after the circuit, the edge bars and the empty-canvas
 * hint: the accessibility lens. Screen chrome only, never in an export.
 */
@FunctionalInterface
public interface ScreenOverlay {

    /** Paints on {@code screen} (logical pixels of {@code pane}, the device transform only). */
    void paint(Graphics2D screen, CircuitCanvas pane);
}
