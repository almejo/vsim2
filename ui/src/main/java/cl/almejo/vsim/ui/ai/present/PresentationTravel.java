package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The view follows the AI's work like a camera while it presents (spec §5, §6; the user: «debería ir siguiendo la
 * acción al vuelo»): at each stage start and each step (proposed or applied) the active pane glides so that the change
 * sits in the middle of the pane's free part (the pane less the floating panel's rectangle, wherever the user put
 * it), at the user's zoom unless the change does not fit there (then out just enough). No move when the change already
 * sits in a small zone around that middle; a new step during a glide retargets it; reduced motion jumps
 * ({@link CircuitCanvas#travelTo}). When the user pans or zooms by hand during a task, the view stops following until
 * the next stage starts (or the next task). A change shown by another pane of a split view, and not by the active
 * one, is left to that pane. EDT only.
 */
final class PresentationTravel {

    /** The room a stage starts with to the right of and below its origin, in grid units. */
    static final int STAGE_WIDTH = 30;
    static final int STAGE_HEIGHT = 20;
    /**
     * The zone around the free part's middle where a change needs no move: its centre within this fraction of the free
     * width and height of the middle.
     */
    static final double CENTRAL_FRACTION = 0.2;
    /** Pixels a change keeps from the free part's edges to count as comfortably inside it. */
    static final int EDGE_ROOM = 16;

    private final EditorView view;
    private final Function<CircuitCanvas, Optional<Rectangle>> covered;
    private final Runnable removeViewListeners;
    private boolean following = true;
    /** True while this class itself moves the view (a jump is a travel too, but be safe with listeners). */
    private boolean travelling;

    /** @param covered the part of a pane the floating panel covers, in that pane's pixels */
    PresentationTravel(EditorView view, Function<CircuitCanvas, Optional<Rectangle>> covered) {
        this.view = Objects.requireNonNull(view, "view");
        this.covered = Objects.requireNonNull(covered, "covered");
        this.removeViewListeners = view.forEachPane(pane -> pane.addViewTakenListener(this::userTookTheView));
    }

    /** The area a stage starting at {@code origin} first builds in: the origin at its top left. */
    static Bounds stageArea(GridPoint origin) {
        return new Bounds(origin.x(), origin.y(), STAGE_WIDTH, STAGE_HEIGHT);
    }

    /** A stage starts: the view follows again (also after the user took it) and goes there. */
    void toStageOrigin(GridPoint origin) {
        following = true;
        follow(stageArea(Objects.requireNonNull(origin, "origin")));
    }

    /** A new task: the view follows again. */
    void resume() {
        following = true;
    }

    void toStep(Bounds changed) {
        follow(Objects.requireNonNull(changed, "changed"));
    }

    boolean isFollowing() {
        return following;
    }

    private void userTookTheView() {
        if (!travelling) {
            following = false;
        }
    }

    private void follow(Bounds bounds) {
        if (!following) {
            return;
        }
        CircuitCanvas active = view.canvas();
        if (active.getWidth() <= 0 || active.getHeight() <= 0) {
            return;
        }
        if (!active.shows(bounds) && view.panes().stream().anyMatch(pane -> pane != active && pane.shows(bounds))) {
            return; // the user watches it in another pane of the split view
        }
        Rectangle2D free = freeArea(active);
        if (sitsInTheMiddle(active, bounds, free)) {
            return;
        }
        travelling = true;
        try {
            active.travelTo(bounds, free);
        } finally {
            travelling = false;
        }
    }

    /** The largest part of {@code pane} the floating panel leaves free: left, right, above or below it. */
    Rectangle2D freeArea(CircuitCanvas pane) {
        int width = pane.getWidth();
        int height = pane.getHeight();
        return covered.apply(pane).map(panel -> StepCallout.freePart(panel, width, height))
                .orElse(new Rectangle(0, 0, width, height));
    }

    /** True when the change lies inside the free part, with room, and its centre is near the free part's middle. */
    private static boolean sitsInTheMiddle(CircuitCanvas pane, Bounds bounds, Rectangle2D free) {
        Point2D corner = pane.viewport().gridToScreen(bounds.x(), bounds.y());
        Point2D end = pane.viewport().gridToScreen(bounds.maximumX(), bounds.maximumY());
        Rectangle2D onScreen = new Rectangle2D.Double(corner.getX(), corner.getY(), end.getX() - corner.getX(),
                end.getY() - corner.getY());
        Rectangle2D roomy = new Rectangle2D.Double(free.getX() + EDGE_ROOM, free.getY() + EDGE_ROOM,
                Math.max(0, free.getWidth() - 2 * EDGE_ROOM), Math.max(0, free.getHeight() - 2 * EDGE_ROOM));
        if (!roomy.contains(onScreen)) {
            return false;
        }
        return Math.abs(onScreen.getCenterX() - free.getCenterX()) <= free.getWidth() * CENTRAL_FRACTION
                && Math.abs(onScreen.getCenterY() - free.getCenterY()) <= free.getHeight() * CENTRAL_FRACTION;
    }

    void dispose() {
        removeViewListeners.run();
    }
}
