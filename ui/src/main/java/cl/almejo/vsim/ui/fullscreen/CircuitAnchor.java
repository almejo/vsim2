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
import java.lang.reflect.InvocationTargetException;
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
 * Confined to the EDT, so checking where a view was left never interleaves with putting it back: the editor calls it
 * there, and a call from another thread (tests drive presentation mode off the EDT) is carried out on the EDT and
 * waited for. No lock of its own: the panes' resize events reach it on the EDT holding the AWT tree lock, and the
 * former {@code synchronized} methods, holding their lock while adding or removing a pane's listeners (which take the
 * tree lock), deadlocked the tests (2026-11-09).
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
    void hold(List<CircuitCanvas> panes) {
        onTheEdt(() -> holdOnTheEdt(panes));
    }

    private void holdOnTheEdt(List<CircuitCanvas> panes) {
        releaseOnTheEdt();
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
                fromAnEvent(() -> paneChangedOnTheEdt(pane));
            }

            @Override
            public void componentResized(ComponentEvent event) {
                fromAnEvent(() -> paneChangedOnTheEdt(pane));
            }
        };
        HierarchyBoundsListener ancestorResizes = new HierarchyBoundsAdapter() {
            @Override
            public void ancestorResized(HierarchyEvent event) {
                fromAnEvent(() -> paneChangedOnTheEdt(pane));
            }
        };
        pane.addComponentListener(resizes);
        pane.addHierarchyBoundsListener(ancestorResizes);
        // the user works on it now
        Runnable stopPressObserver = pane.addPressObserver(() -> fromAnEvent(() -> forget(pane)));
        removals.add(() -> {
            pane.removeComponentListener(resizes);
            pane.removeHierarchyBoundsListener(ancestorResizes);
            stopPressObserver.run();
        });
    }

    /** Gives every held pane its saved view (after the window was laid out anew). */
    void follow() {
        onTheEdt(() -> List.copyOf(savedViews.keySet()).forEach(this::paneChangedOnTheEdt));
    }

    /** {@code pane} was resized or moved: it shows its saved view again, unless the window already settled. */
    void paneChanged(CircuitCanvas pane) {
        onTheEdt(() -> paneChangedOnTheEdt(pane));
    }

    private void paneChangedOnTheEdt(CircuitCanvas pane) {
        if (clock.getAsLong() > followUntil) {
            releaseOnTheEdt();
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

    private void forget(CircuitCanvas pane) {
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
    void release() {
        onTheEdt(this::releaseOnTheEdt);
    }

    private void releaseOnTheEdt() {
        removals.forEach(Runnable::run);
        removals.clear();
        savedViews.clear();
        leftAt.clear();
    }

    /**
     * Runs the reaction to a pane's event on the EDT: at once there, else later and never waiting, since an off-EDT
     * layout (tests) hands the panes their ancestor-resized events holding the tree lock, which the EDT may be waiting
     * for. Later calls to this class run after it (the event queue keeps their order).
     */
    private static void fromAnEvent(Runnable reaction) {
        if (SwingUtilities.isEventDispatchThread()) {
            reaction.run();
        } else {
            SwingUtilities.invokeLater(reaction);
        }
    }

    /**
     * Runs {@code work} on the EDT: at once there, else waiting for it (rethrowing what it threw). A caller off the
     * EDT must hold no Swing lock (the tree lock), or the EDT could never run it.
     */
    private static void onTheEdt(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the EDT", interrupted);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure.getCause());
        }
    }
}
