package cl.almejo.vsim.ui.canvas;

import java.awt.Graphics2D;

/** Something painted over the circuit, in document coordinates (8 px per grid unit), before the tool overlay. */
@FunctionalInterface
public interface CanvasOverlay {

    void paint(Graphics2D graphics, RenderState state);
}
