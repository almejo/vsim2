package cl.almejo.vsim.ui.magnifier;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.EnterableInstances;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private static final int FRAME_MILLISECONDS = 15;

    private final PainterRegistry painters;
    private final Timer repaint = new Timer(REPAINT_MILLISECONDS, event -> repaintPicture());
    private final Timer frames = new Timer(FRAME_MILLISECONDS, event -> animate());
    private final PopupAnimation animation = new PopupAnimation(System::nanoTime);
    private Point anchor;
    private Rectangle fullBounds;
    private JWindow window;
    private OpenDocument place;
    private MagnifierPicture picture;
    private CircuitCanvas shownFor;
    private Runnable pointerLeftPanel = () -> { };

    MagnifierPopup(PainterRegistry painters) {
        this.painters = painters;
    }

    @Override
    public void show(CircuitCanvas pane, Component instance, Point2D screenPoint) {
        close();
        Optional<String> definitionId = EnterableInstances.definitionOf(pane.openDocument(), instance);
        if (definitionId.isEmpty()) {
            return;
        }
        place = pane.openDocument().openBody(definitionId.get(), EnterableInstances.contextOf(pane.openDocument(),
                instance));
        picture = new MagnifierPicture(place, painters, pane::scheme);
        picture.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent event) {
                pointerLeftThePanelFor(event.getLocationOnScreen());
            }
        });
        shownFor = pane;
        window = new JWindow(SwingUtilities.getWindowAncestor(pane));
        window.setFocusableWindowState(false);
        window.setContentPane(picture);
        window.pack();
        Point pointer = new Point((int) screenPoint.getX(), (int) screenPoint.getY());
        SwingUtilities.convertPointToScreen(pointer, pane);
        MagnifierPlacement placement = MagnifierPlacement.beside(pointer, window.getSize(),
                Optional.ofNullable(pane.getGraphicsConfiguration()).map(GraphicsConfiguration::getBounds));
        fullBounds = placement.full();
        anchor = placement.anchor();
        window.setBounds(PopupAnimation.boundsAt(anchor, fullBounds, 0.0));
        animation.grow();
        window.setVisible(true);
        repaint.start();
        frames.start();
    }

    @Override
    public boolean covers(Point2D screenLocation) {
        return window != null && !animation.isShrinking() && fullBounds.contains(screenLocation);
    }

    @Override
    public void whenThePointerLeavesThePanel(Runnable action) {
        pointerLeftPanel = action;
    }

    /** Back onto the canvas its own moves decide; anywhere else the panel goes. */
    private void pointerLeftThePanelFor(Point screenLocation) {
        if (window == null || shownFor == null) {
            return;
        }
        Point onPane = new Point(screenLocation);
        SwingUtilities.convertPointFromScreen(onPane, shownFor);
        if (!shownFor.contains(onPane)) {
            pointerLeftPanel.run();
        }
    }

    /** Shrinks back to the piece and then goes. */
    @Override
    public void hide() {
        if (window == null || animation.isShrinking()) {
            return;
        }
        animation.shrink();
        frames.start();
    }

    private void animate() {
        if (window == null) {
            frames.stop();
            return;
        }
        Rectangle bounds = PopupAnimation.boundsAt(anchor, fullBounds, animation.scale());
        window.setBounds(bounds.x, bounds.y, Math.max(1, bounds.width), Math.max(1, bounds.height));
        if (animation.isFinished()) {
            frames.stop();
            if (animation.isShrinking()) {
                close();
            }
        }
    }

    /** Takes the panel away at once (a new one is about to show, or the view goes). */
    @Override
    public void close() {
        repaint.stop();
        frames.stop();
        if (window != null) {
            window.dispose();
            window = null;
        }
        if (place != null) {
            place.close();
            place = null;
        }
        picture = null;
        shownFor = null;
    }

    private void repaintPicture() {
        if (picture != null) {
            picture.repaint();
        }
    }
}
