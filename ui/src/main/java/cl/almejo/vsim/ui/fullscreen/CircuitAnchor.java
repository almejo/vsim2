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
 * ({@link #SETTLE_MILLISECONDS}) or the user presses on the pane. Every split pane keeps its own. EDT only.
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
    private final List<Runnable> removals = new ArrayList<>();
    private long followUntil;

    CircuitAnchor(JRootPane rootPane, LongSupplier clock) {
        this.rootPane = Objects.requireNonNull(rootPane, "rootPane");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Remembers what {@code panes} show now; until the window settles they come back to it after every resize. */
    void hold(List<CircuitCanvas> panes) {
        release();
        followUntil = clock.getAsLong() + SETTLE_MILLISECONDS;
        for (CircuitCanvas pane : panes) {
            if (pane.getWidth() <= 0 || pane.getHeight() <= 0 || !SwingUtilities.isDescendingFrom(pane, rootPane)) {
                continue; // never laid out (a tab not shown yet): there is nothing on screen to keep
            }
            savedViews.put(pane, pane.viewState());
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
    void follow() {
        List.copyOf(savedViews.keySet()).forEach(this::paneChanged);
    }

    /** {@code pane} was resized or moved: it shows its saved view again, unless the window already settled. */
    void paneChanged(CircuitCanvas pane) {
        if (clock.getAsLong() > followUntil) {
            release();
            return;
        }
        ViewState saved = savedViews.get(pane);
        if (saved != null && pane.getWidth() > 0 && pane.getHeight() > 0) {
            pane.applyViewState(saved);
        }
    }

    private void forget(CircuitCanvas pane) {
        savedViews.remove(pane);
    }

    /** Stops following the panes. */
    void release() {
        removals.forEach(Runnable::run);
        removals.clear();
        savedViews.clear();
    }
}
