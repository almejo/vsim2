package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ViewState;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Keeps the circuit in view while presentation mode rearranges the window around the canvas: the bars go or come
 * back, the frame grows to the whole monitor and returns, and the window manager resizes it again a moment later (the
 * user, 2026-11-08: after leaving, the view ended panned away). Each pane keeps its zoom and the grid point at its
 * centre ({@link ViewState}), given back after every resize or move of the pane until the window settled
 * ({@link #SETTLE_MILLISECONDS}) or the user acts on the pane: a press, or any zoom or pan (the wheel, Ctrl+=, the
 * status bar's slider), seen as the view no longer being where this class left it. Every split pane keeps its own.
 * EDT only in the editor; synchronized because tests drive it off the EDT while the panes' resize events reach it on
 * the EDT, and checking where a view was left must not interleave with putting it back.
 */
final class CircuitAnchor {

    /**
     * How long after entering or leaving the panes' resizes are still the mode's: longer than the frame's re-sync with
     * the window manager ({@link FrameResync#TIMEOUT_MILLISECONDS}), which may resize the window once more.
     */
    static final long SETTLE_MILLISECONDS = 3_000;

    private final JRootPane rootPane;
    private final LongSupplier clock;
    private final Map<CircuitCanvas, ViewState> savedViews = new IdentityHashMap<>();
    /** Where each held pane's view was left (by the user before holding, or by this class since). */
    private final Map<CircuitCanvas, Placement> leftAt = new IdentityHashMap<>();
    private final List<Runnable> removals = new ArrayList<>();
    private long followUntil;

    CircuitAnchor(JRootPane rootPane, LongSupplier clock) {
        this.rootPane = Objects.requireNonNull(rootPane, "rootPane");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Remembers what {@code panes} show now; until the window settles they come back to it after every resize. */
    synchronized void hold(List<CircuitCanvas> panes) {
        release();
        followUntil = clock.getAsLong() + SETTLE_MILLISECONDS;
        for (CircuitCanvas pane : panes) {
            if (pane.getWidth() <= 0 || pane.getHeight() <= 0 || !SwingUtilities.isDescendingFrom(pane, rootPane)) {
                continue; // never laid out (a tab not shown yet): there is nothing on screen to keep
            }
            savedViews.put(pane, pane.viewState());
            leftAt.put(pane, Placement.of(pane));
            listenTo(pane);
        }
    }

    private void listenTo(CircuitCanvas pane) {
        ComponentListener resizes = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                paneChanged(pane);
            }

            @Override
            public void componentResized(ComponentEvent event) {
                paneChanged(pane);
            }
        };
        HierarchyBoundsListener ancestorResizes = new HierarchyBoundsAdapter() {
            @Override
            public void ancestorResized(HierarchyEvent event) {
                paneChanged(pane);
            }
        };
        pane.addComponentListener(resizes);
        pane.addHierarchyBoundsListener(ancestorResizes);
        Runnable stopPressObserver = pane.addPressObserver(() -> forget(pane)); // the user works on it now
        removals.add(() -> {
            pane.removeComponentListener(resizes);
            pane.removeHierarchyBoundsListener(ancestorResizes);
            stopPressObserver.run();
        });
    }

    /** Gives every held pane its saved view (after the window was laid out anew). */
    synchronized void follow() {
        List.copyOf(savedViews.keySet()).forEach(this::paneChanged);
    }

    /** {@code pane} was resized or moved: it shows its saved view again, unless the window already settled. */
    synchronized void paneChanged(CircuitCanvas pane) {
        if (clock.getAsLong() > followUntil) {
            release();
            return;
        }
        ViewState saved = savedViews.get(pane);
        if (saved == null || pane.getWidth() <= 0 || pane.getHeight() <= 0) {
            return;
        }
        if (!Placement.of(pane).equals(leftAt.get(pane))) {
            forget(pane); // the user zoomed or panned it since: it is theirs now
            return;
        }
        pane.applyViewState(saved);
        leftAt.put(pane, Placement.of(pane));
    }

    private synchronized void forget(CircuitCanvas pane) {
        savedViews.remove(pane);
        leftAt.remove(pane);
    }

    /**
     * The zoom and the grid point at a pane's top-left corner: a resize keeps them (the view grows or shrinks from
     * that corner), a zoom or a pan changes them.
     */
    private record Placement(double zoom, double originX, double originY) {

        static Placement of(CircuitCanvas pane) {
            Point2D origin = pane.viewport().screenToGrid(0, 0);
            return new Placement(pane.viewport().zoom(), origin.getX(), origin.getY());
        }
    }

    /** Stops following the panes. */
    synchronized void release() {
        removals.forEach(Runnable::run);
        removals.clear();
        savedViews.clear();
        leftAt.clear();
    }
}
