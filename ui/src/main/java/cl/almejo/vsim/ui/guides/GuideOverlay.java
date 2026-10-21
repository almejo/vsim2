package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;

/**
 * Draws the circuit's guides (spec §4.5) as full-length dashed lines, 1 logical pixel wide whatever the zoom, over the
 * circuit and under the tool overlay (so a move preview stays on top). It is a canvas overlay, which image export and
 * the PDF never run, so guides never reach paper or a file. The guide being moved is drawn where the drag shows it.
 */
final class GuideOverlay implements CanvasOverlay {

    private final GuidesFeatures features;

    GuideOverlay(GuidesFeatures features) {
        this.features = features;
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        Graphics2D screen = (Graphics2D) graphics.create();
        try {
            AffineTransform documentToScreen = features.canvas().viewport().documentTransform();
            screen.transform(documentToScreen.createInverse()); // back to logical pixels, the device transform kept
            for (Guide guide : features.document().guides()) {
                if (!features.isHiddenByTheDrag(guide)) {
                    features.paintGuideLine(screen, guide);
                }
            }
        } catch (NoninvertibleTransformException exception) {
            // a zero zoom never happens (the viewport clamps it); nothing sensible to draw then
        } finally {
            screen.dispose();
        }
    }
}
