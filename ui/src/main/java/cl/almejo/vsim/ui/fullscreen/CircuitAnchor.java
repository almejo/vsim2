package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.CircuitCanvas;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Point;
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
 * Keeps the circuit where it is on the screen while presentation mode rearranges the window around the canvas: the
 * bars go or come back and the frame grows to the whole monitor or returns. Each pane is panned by exactly what its
 * top-left corner moved on screen, as the find bar does when it opens above the canvas. The frame's own change of
 * size arrives later from the window manager, so the anchor keeps following the panes' moves for a short while after
 * it was set. EDT only.
 */
final class CircuitAnchor {

    /** How long after entering or leaving the panes' moves are still the mode's (the window manager is not instant). */
    static final long SETTLE_MILLISECONDS = 1_500;

    private final JRootPane rootPane;
    private final LongSupplier clock;
    private final Map<CircuitCanvas, Point> lastPositions = new IdentityHashMap<>();
    private final List<Runnable> removals = new ArrayList<>();
    private long followUntil;

    CircuitAnchor(JRootPane rootPane, LongSupplier clock) {
        this.rootPane = Objects.requireNonNull(rootPane, "rootPane");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Remembers where {@code panes} are on screen now; the next {@link #follow()} keeps their circuits there. */
    void hold(List<CircuitCanvas> panes) {
        release();
        followUntil = clock.getAsLong() + SETTLE_MILLISECONDS;
        for (CircuitCanvas pane : panes) {
            if (pane.getWidth() <= 0 || pane.getHeight() <= 0 || !SwingUtilities.isDescendingFrom(pane, rootPane)) {
                continue; // never laid out (a tab not shown yet): there is nothing on screen to keep
            }
            lastPositions.put(pane, screenPosition(pane));
            ComponentListener moves = new ComponentAdapter() {
                @Override
                public void componentMoved(ComponentEvent event) {
                    paneMoved();
                }

                @Override
                public void componentResized(ComponentEvent event) {
                    paneMoved();
                }
            };
            HierarchyBoundsListener ancestorMoves = new HierarchyBoundsAdapter() {
                @Override
                public void ancestorMoved(HierarchyEvent event) {
                    paneMoved();
                }

                @Override
                public void ancestorResized(HierarchyEvent event) {
                    paneMoved();
                }
            };
            pane.addComponentListener(moves);
            pane.addHierarchyBoundsListener(ancestorMoves);
            removals.add(() -> {
                pane.removeComponentListener(moves);
                pane.removeHierarchyBoundsListener(ancestorMoves);
            });
        }
    }

    /** Pans every held pane by what it moved on screen since the last time, so its circuit stays put. */
    void follow() {
        for (Map.Entry<CircuitCanvas, Point> entry : lastPositions.entrySet()) {
            CircuitCanvas pane = entry.getKey();
            if (pane.getWidth() <= 0 || !SwingUtilities.isDescendingFrom(pane, rootPane)) {
                continue;
            }
            Point now = screenPosition(pane);
            Point last = entry.getValue();
            if (!now.equals(last)) {
                pane.compensateCanvasMove(last.x - now.x, last.y - now.y);
                entry.setValue(now);
            }
        }
    }

    /** Stops following the panes. */
    void release() {
        removals.forEach(Runnable::run);
        removals.clear();
        lastPositions.clear();
    }

    private void paneMoved() {
        if (clock.getAsLong() > followUntil) {
            release();
            return;
        }
        follow();
    }

    /** The pane's top-left corner on screen: the window's place plus the pane's place in it. */
    private Point screenPosition(CircuitCanvas pane) {
        Point inRoot = SwingUtilities.convertPoint(pane, 0, 0, rootPane);
        if (rootPane.isShowing()) {
            Point origin = rootPane.getLocationOnScreen();
            inRoot.translate(origin.x, origin.y);
        }
        return inRoot;
    }
}
