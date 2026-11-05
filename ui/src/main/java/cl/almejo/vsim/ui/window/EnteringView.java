package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.canvas.ViewState;

/**
 * Where a component tab's view starts when entering animates (spec §4.4): the body drawn on screen where the instance
 * was, as big as the instance's box, so the travel to the fitted body reads as the instance opening up. The new tab's
 * pane has the size of the one entered from (they share the window's card area), so a pane-centre offset carries over.
 */
final class EnteringView {

    private EnteringView() {
    }

    /**
     * @param parent   what the pane entered from showed
     * @param instance the instance's box there (grid units)
     * @param body     the body's bounds (grid units, its own coordinates)
     */
    static ViewState start(ViewState parent, Bounds instance, Bounds body) {
        if (body.width() <= 0 || body.height() <= 0) {
            return parent;
        }
        double scale = Math.min((double) instance.width() / body.width(), (double) instance.height() / body.height());
        double zoom = parent.zoom() * scale;
        // the body's centre lands on screen where the instance's centre was
        double centerX = centerX(body) - (centerX(instance) - parent.centerX()) / scale;
        double centerY = centerY(body) - (centerY(instance) - parent.centerY()) / scale;
        return new ViewState(zoom, centerX, centerY);
    }

    private static double centerX(Bounds bounds) {
        return bounds.x() + bounds.width() / 2.0;
    }

    private static double centerY(Bounds bounds) {
        return bounds.y() + bounds.height() / 2.0;
    }
}
