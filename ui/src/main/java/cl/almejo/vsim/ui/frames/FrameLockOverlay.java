package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;

import java.awt.Graphics2D;
import java.util.function.DoubleSupplier;

/**
 * The faint closed padlock every locked frame always shows (spec §5), whatever tool is active. A canvas overlay, so
 * never part of an image export; the select tool paints the full controls of the hovered or selected frames on top.
 */
final class FrameLockOverlay implements CanvasOverlay {

    private final DoubleSupplier pixelsPerGrid;

    FrameLockOverlay(DoubleSupplier pixelsPerGrid) {
        this.pixelsPerGrid = pixelsPerGrid;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        double scale = pixelsPerGrid.getAsDouble();
        for (Frame frame : state.document().frames()) {
            if (frame.locked()) {
                FramePainter.paintControls(graphics, frame, false, scale);
            }
        }
    }
}
