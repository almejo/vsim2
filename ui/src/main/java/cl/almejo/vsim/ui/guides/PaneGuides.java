package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.edges.EdgeHandle;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The guides on one pane of a window (split views): its rulers, their corner, the grab of the lines, the lines'
 * overlay and the drag under way there. The guides themselves, the menus and «Mostrar guías» belong to
 * {@link GuidesFeatures}, shared by every pane.
 */
final class PaneGuides {

    /** Length of a dash and of the gap after it, in logical pixels. */
    private static final int DASH = 4;

    private final GuidesFeatures features;
    private final CircuitCanvas canvas;
    private final List<EdgeHandle> rulers;
    private final GuideGrab grab;
    private final GuideOverlay overlay;
    private final List<Runnable> lineRemovers = new ArrayList<>();
    private final List<Runnable> rulerRemovers = new ArrayList<>();
    private GuideDrag currentDrag;

    PaneGuides(GuidesFeatures features, CircuitCanvas canvas) {
        this.features = features;
        this.canvas = canvas;
        this.rulers = List.of(new RulerCorner(this), new RulerHandle(this, RulerHandle.Side.TOP),
                new RulerHandle(this, RulerHandle.Side.LEFT));
        this.grab = new GuideGrab(this);
        this.overlay = new GuideOverlay(this);
    }

    /**
     * Puts the lines, their grab and the rulers on the pane, or takes them away, cancelling a drag under way:
     * {@code linesShown} follows «Mostrar guías»; {@code rulersShown} also needs the view out of presentation mode,
     * which hides the rulers (an editing grip, the user 2026-11-08) but keeps the placed guides.
     */
    void show(boolean linesShown, boolean rulersShown) {
        if (!rulersShown && !rulerRemovers.isEmpty()) {
            rulerRemovers.forEach(Runnable::run);
            rulerRemovers.clear();
        }
        if (linesShown && lineRemovers.isEmpty()) {
            lineRemovers.add(canvas.addEdgeHandle(grab));
            canvas.addOverlay(overlay);
            lineRemovers.add(() -> canvas.removeOverlay(overlay));
        } else if (!linesShown && !lineRemovers.isEmpty()) {
            lineRemovers.forEach(Runnable::run);
            lineRemovers.clear();
        }
        if (rulersShown && rulerRemovers.isEmpty()) {
            for (EdgeHandle ruler : rulers) {
                rulerRemovers.add(canvas.addEdgeHandle(ruler));
            }
        }
    }

    /** The list changed (this pane, another one, undo): a drag in progress was identified by value, so it ends. */
    void guidesChanged() {
        if (currentDrag != null) {
            canvas.cancelEdgeGesture();
        }
        canvas.repaint();
    }

    /** The pane goes away, or the guides do: everything it added is taken back. */
    void dispose() {
        show(false, false);
        currentDrag = null;
    }

    CircuitCanvas canvas() {
        return canvas;
    }

    CircuitDocument document() {
        return features.document();
    }

    void showCornerMenu() {
        features.showCornerMenu(canvas);
    }

    void addGuide(Guide guide) {
        features.addGuide(guide);
    }

    void moveGuide(Guide from, Guide to) {
        features.moveGuide(from, to);
    }

    void removeGuide(Guide guide) {
        features.removeGuide(guide);
    }

    GuideDrag startDrag(Orientation orientation, Optional<Guide> moving, CanvasMouseEvent press) {
        currentDrag = new GuideDrag(this, orientation, moving, press);
        canvas.repaint();
        return currentDrag;
    }

    /** The drag released or was cancelled: guide changes from now on are its own, or nobody's. */
    void dragEnded(GuideDrag drag) {
        if (currentDrag == drag) {
            currentDrag = null;
        }
        canvas.repaint();
    }

    boolean isHiddenByTheDrag(Guide guide) {
        return currentDrag != null && currentDrag.moving().filter(guide::equals).isPresent()
                && currentDrag.hidesTheMovedGuide();
    }

    /** Where {@code guide} crosses this pane: its y for a horizontal guide, its x for a vertical one. */
    double screenCoordinate(Guide guide) {
        return GuidesFeatures.screenCoordinate(canvas.viewport(), guide);
    }

    /** The guide within {@link GuidesFeatures#GRAB_TOLERANCE} pixels of {@code screenPoint}, the nearest if several. */
    Optional<Guide> guideNear(Point2D screenPoint) {
        return GuidesFeatures.guideNear(document(), canvas.viewport(), screenPoint);
    }

    /**
     * Paints {@code guide} across the whole pane, dashed, 1 logical pixel wide, in the scheme's guide colour read now;
     * {@code screenGraphics} is in logical pixels.
     */
    void paintGuideLine(Graphics2D screenGraphics, Guide guide) {
        paintGuideLine(screenGraphics, guide, false);
    }

    /**
     * Like {@link #paintGuideLine(Graphics2D, Guide)}; a {@code highlighted} guide (the one the gesture under way
     * snapped to, spec §4.4) is drawn slightly stronger: solid and opaque instead of dashed.
     */
    void paintGuideLine(Graphics2D screenGraphics, Guide guide, boolean highlighted) {
        Color color = canvas.scheme().guide();
        screenGraphics.setColor(highlighted ? new Color(color.getRGB() | 0xFF000000) : color);
        int at = (int) Math.round(screenCoordinate(guide));
        boolean horizontal = guide.orientation() == Orientation.HORIZONTAL;
        int length = horizontal ? canvas.getWidth() : canvas.getHeight();
        int step = highlighted ? length : 2 * DASH;
        for (int start = 0; start < length; start += step) {
            int piece = highlighted ? length : Math.min(DASH, length - start);
            if (horizontal) {
                screenGraphics.fillRect(start, at, piece, 1);
            } else {
                screenGraphics.fillRect(at, start, 1, piece);
            }
        }
    }
}
