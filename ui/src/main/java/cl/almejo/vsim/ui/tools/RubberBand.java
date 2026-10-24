package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.ui.canvas.MarchingAnts;
import cl.almejo.vsim.ui.paint.PainterSupport;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/**
 * The rubber band two tools drag (the selection's in Selección, the zoom tool's rectangle): a faint fill of
 * {@code color} under marching ants of that colour. The caller reads the colour from the scheme at paint time.
 */
final class RubberBand {

    private RubberBand() {
    }

    /**
     * Paints {@code rectangle} (in the graphics' units) filled with {@code color} at {@code fillAlpha} and outlined by
     * marching ants {@code width} wide at dash phase {@code phase}.
     */
    static void paint(Graphics2D graphics, Rectangle2D rectangle, Color color, int fillAlpha, float width,
                      double phase) {
        graphics.setColor(PainterSupport.withAlpha(color, fillAlpha));
        graphics.fill(rectangle);
        MarchingAnts.draw(graphics, rectangle, color, width, phase);
    }
}
