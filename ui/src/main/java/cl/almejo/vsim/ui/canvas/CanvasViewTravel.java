package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** The animated moves of a canvas's view (travels, zooms the user asked for) and the user's zoom they rest at. */
final class CanvasViewTravel {

    private static final double FIT_FRACTION = 0.9;

    private final CircuitCanvas canvas;
    private final Viewport viewport;
    /** Told when the view moves by anything but a travel (the user's pan or zoom, a centring). */
    private final List<Runnable> viewTakenListeners;
    private BooleanSupplier animateViewMoves = () -> false;
    /** True while the view moves in a way that must not stop a travel (a travel frame, a layout compensation). */
    private boolean movingWithTheTravel;
    /**
     * The zoom the user chose (wheel, buttons, a restored view): where {@link #travelTo} rests, even after a travel
     * that zoomed out for its target to fit. Moves made by travels never change it.
     */
    private double userZoom = 1;
    private final ViewAnimator viewAnimator;

    CanvasViewTravel(CircuitCanvas canvas, Viewport viewport, List<Runnable> viewTakenListeners) {
        this.canvas = canvas;
        this.viewport = viewport;
        this.viewTakenListeners = viewTakenListeners;
        viewAnimator = new ViewAnimator(FrameTicker.swing(CircuitCanvas.FRAME_MILLISECONDS),
                () -> System.nanoTime() / 1_000_000, this::showTravelFrame, () -> animateViewMoves.getAsBoolean());
    }

    /** Follows the moves of the view that are not travels: they stop a travel and set the user's zoom. */
    void listenForUserMoves() {
        viewport.addChangeListener(() -> {
            if (!movingWithTheTravel) {
                viewAnimator.stop(); // the user (or another move) took the view: it stays where it is
                userZoom = viewport.zoom();
                viewTakenListeners.forEach(Runnable::run);
            }
        });
    }

    /** See {@link CircuitCanvas#travelTo(Bounds, Rectangle2D)}. */
    void travelTo(Bounds bounds, Rectangle2D within) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        double zoom = Math.min(userZoom, ZoomTargets.fitting(bounds, (int) Math.round(within.getWidth()),
                (int) Math.round(within.getHeight()), FIT_FRACTION, userZoom).zoom());
        double pixels = GridPoint.PIXELS_PER_UNIT * zoom;
        ViewState target = new ViewState(zoom,
                bounds.x() + bounds.width() / 2.0 - (within.getCenterX() - width / 2.0) / pixels,
                bounds.y() + bounds.height() / 2.0 - (within.getCenterY() - height / 2.0) / pixels);
        if (canAnimateViewMoves()) {
            viewAnimator.travel(canvas.viewState(), target, width, height);
        } else {
            jumpTo(target);
        }
    }

    /** See {@link CircuitCanvas#zoomTo(ViewState)}. */
    void zoomTo(ViewState target) {
        ViewState clamped = ZoomTargets.atZoom(Objects.requireNonNull(target, "target"), target.zoom());
        userZoom = clamped.zoom(); // a travel frame never touches the user's zoom: set it now
        viewTakenListeners.forEach(Runnable::run);
        if (canAnimateViewMoves() && canvas.onScreen()) {
            viewAnimator.travel(canvas.viewState(), clamped, canvas.getWidth(), canvas.getHeight());
        } else {
            jumpTo(clamped);
        }
    }

    /** See {@link CircuitCanvas#zoomToFit(Bounds)}. */
    void zoomToFit(Bounds bounds) {
        zoomTo(ZoomTargets.fitting(bounds, canvas.getWidth(), canvas.getHeight(), FIT_FRACTION, viewport.zoom()));
    }

    /** True when a move of the view may travel: animations on, the canvas sized and not disposed. */
    private boolean canAnimateViewMoves() {
        return !canvas.isDisposed() && canvas.getWidth() > 0 && canvas.getHeight() > 0
                && animateViewMoves.getAsBoolean();
    }

    /** Shows {@code target} at once, stopping any travel; like a travel's frame, it does not change the user's zoom. */
    private void jumpTo(ViewState target) {
        viewAnimator.stop();
        movingWithTheTravel = true;
        try {
            viewport.showAt(target.zoom(), target.centerX(), target.centerY(), canvas.getWidth(), canvas.getHeight());
        } finally {
            movingWithTheTravel = false;
        }
        canvas.viewChanged();
    }

    private void showTravelFrame(ViewState state) {
        movingWithTheTravel = true;
        try {
            viewport.showAt(state.zoom(), state.centerX(), state.centerY(), canvas.getWidth(), canvas.getHeight());
        } finally {
            movingWithTheTravel = false;
        }
        canvas.viewChanged();
    }

    /** See {@link CircuitCanvas#compensateCanvasMove}. */
    void compensateCanvasMove(double deltaX, double deltaY) {
        movingWithTheTravel = true;
        try {
            viewport.panBy(deltaX, deltaY);
        } finally {
            movingWithTheTravel = false;
        }
        canvas.viewChanged();
    }

    /** Whether {@link #travelTo} animates; read at each call and each frame (the preference can change mid-flight). */
    void setAnimateViewMoves(BooleanSupplier animate) {
        animateViewMoves = animate;
    }

    /** True while the view travels to a target. */
    boolean isTravelling() {
        return viewAnimator.isTravelling();
    }

    /** Drives the travels with {@code ticker} and {@code clock} (milliseconds) instead of a Swing timer: for tests. */
    void useViewTicker(FrameTicker ticker, LongSupplier clock) {
        viewAnimator.use(ticker, clock);
    }

    /** Stops a travel in progress where the view is now. */
    void stop() {
        viewAnimator.stop();
    }

    /** Jumps to the target of the travel in progress, if any. */
    void finish() {
        viewAnimator.finish();
    }
}
