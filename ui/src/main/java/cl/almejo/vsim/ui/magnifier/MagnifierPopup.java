package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.EnterableInstances;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * The floating panel of the magnifier (spec §8.2): a window that takes no focus, with a {@link MagnifierPicture} of
 * the body of the instance under the pointer. The picture reads a throw-away body place opened for that instance (the
 * values of its path, no editor view), closed when the panel goes. A timer repaints it while it is up. EDT only; never
 * built in tests (they paint the picture to an image).
 */
final class MagnifierPopup implements MagnifierPresenter {

    private static final int REPAINT_MILLISECONDS = 100;
    /** Distance from the pointer, so the panel never sits under it and takes its hover. */
    private static final int POINTER_OFFSET = 16;

    private final PainterRegistry painters;
    private final Timer repaint = new Timer(REPAINT_MILLISECONDS, event -> repaintPicture());
    private JWindow window;
    private OpenDocument place;
    private MagnifierPicture picture;

    MagnifierPopup(PainterRegistry painters) {
        this.painters = painters;
    }

    @Override
    public void show(CircuitCanvas pane, Component instance, Point2D screenPoint) {
        hide();
        Optional<String> definitionId = EnterableInstances.definitionOf(pane.openDocument(), instance);
        if (definitionId.isEmpty()) {
            return;
        }
        place = pane.openDocument().openBody(definitionId.get(), EnterableInstances.contextOf(pane.openDocument(),
                instance));
        picture = new MagnifierPicture(place, painters, pane::scheme);
        window = new JWindow(SwingUtilities.getWindowAncestor(pane));
        window.setFocusableWindowState(false);
        window.setContentPane(picture);
        window.pack();
        window.setLocation(locationFor(pane, screenPoint, window.getSize()));
        window.setVisible(true);
        repaint.start();
    }

    @Override
    public void hide() {
        repaint.stop();
        if (window != null) {
            window.dispose();
            window = null;
        }
        if (place != null) {
            place.close();
            place = null;
        }
        picture = null;
    }

    private void repaintPicture() {
        if (picture != null) {
            picture.repaint();
        }
    }

    /** Beside the pointer, kept inside the screen of the pane. */
    private static Point locationFor(CircuitCanvas pane, Point2D screenPoint, Dimension size) {
        Point location = new Point((int) screenPoint.getX() + POINTER_OFFSET, (int) screenPoint.getY() + POINTER_OFFSET);
        SwingUtilities.convertPointToScreen(location, pane);
        GraphicsConfiguration configuration = pane.getGraphicsConfiguration();
        if (configuration == null) {
            return location;
        }
        Rectangle screen = configuration.getBounds();
        location.x = Math.max(screen.x, Math.min(location.x, screen.x + screen.width - size.width));
        location.y = Math.max(screen.y, Math.min(location.y, screen.y + screen.height - size.height));
        return location;
    }
}
