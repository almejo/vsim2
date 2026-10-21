package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.edges.EdgeGesture;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * Pulling a new guide out of its ruler, or moving one grabbed by its line or its marker (spec §4.3). The guide is
 * identified by value at press time; the document is never touched during the preview, and the command is built at
 * the release from the guides as they are then (the features cancel the drag if the list changes meanwhile). The
 * position follows the pointer's grid point, worked out at paint and release time from the last screen point, so a
 * zoom or a scroll during the drag keeps the preview under the pointer.
 */
final class GuideDrag implements EdgeGesture {

    /** Where the pointer is, for this drag. */
    enum Zone { CANVAS, OWN_RULER, OTHER_RULER, OUTSIDE }

    private final GuidesFeatures features;
    private final Orientation orientation;
    private final Optional<Guide> moving;
    private Point2D lastScreen;
    private boolean enteredCanvas;

    GuideDrag(GuidesFeatures features, Orientation orientation, Optional<Guide> moving, CanvasMouseEvent press) {
        this.features = features;
        this.orientation = orientation;
        this.moving = moving;
        this.lastScreen = press.screenPosition();
        this.enteredCanvas = zone() == Zone.CANVAS;
    }

    /** The guide being moved, empty while a new one is pulled. */
    Optional<Guide> moving() {
        return moving;
    }

    /**
     * True when the moved guide is not drawn where it was: the preview shows it elsewhere, or a release now would take
     * it away. Over the other ruler or outside the pane a release leaves it where it was, so it shows there.
     */
    boolean hidesTheMovedGuide() {
        Zone zone = zone();
        return moving.isPresent() && (zone == Zone.CANVAS || (zone == Zone.OWN_RULER && enteredCanvas));
    }

    @Override
    public void drag(CanvasMouseEvent event) {
        lastScreen = event.screenPosition();
        if (zone() == Zone.CANVAS) {
            enteredCanvas = true;
        }
    }

    @Override
    public void release(CanvasMouseEvent event) {
        lastScreen = event.screenPosition();
        features.dragEnded(this);
        switch (zone()) {
            case CANVAS -> {
                Guide target = new Guide(orientation, position());
                moving.ifPresentOrElse(guide -> features.moveGuide(guide, target), () -> features.addGuide(target));
            }
            case OWN_RULER -> {
                if (enteredCanvas) {
                    moving.ifPresent(features::removeGuide); // dragged back onto its ruler
                }
            }
            case OTHER_RULER, OUTSIDE -> {
                // nothing: the preview had already disappeared
            }
        }
    }

    @Override
    public void cancel() {
        features.dragEnded(this);
    }

    @Override
    public void paint(Graphics2D screenGraphics) {
        if (zone() == Zone.CANVAS) {
            features.paintGuideLine(screenGraphics, new Guide(orientation, position()));
        }
    }

    /** The grid position the guide would take now. */
    private int position() {
        GridPoint point = features.canvas().viewport().snap(lastScreen.getX(), lastScreen.getY());
        return orientation == Orientation.HORIZONTAL ? point.y() : point.x();
    }

    /** Its own ruler is the one it is pulled from: the top one for a horizontal guide, the left one for a vertical. */
    Zone zone() {
        Dimension size = features.canvas().getSize();
        double x = lastScreen.getX();
        double y = lastScreen.getY();
        if (x < 0 || y < 0 || x >= size.width || y >= size.height) {
            return Zone.OUTSIDE;
        }
        boolean overTop = y < GuidesFeatures.RULER_SIZE;
        boolean overLeft = x < GuidesFeatures.RULER_SIZE;
        boolean overOwn = orientation == Orientation.HORIZONTAL ? overTop : overLeft;
        boolean overOther = orientation == Orientation.HORIZONTAL ? overLeft : overTop;
        if (overOwn) {
            return Zone.OWN_RULER;
        }
        return overOther ? Zone.OTHER_RULER : Zone.CANVAS;
    }
}
