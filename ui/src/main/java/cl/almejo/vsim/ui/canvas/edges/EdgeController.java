package cl.almejo.vsim.ui.canvas.edges;

import cl.almejo.vsim.ui.tools.CanvasMouseEvent;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The edge handles of one canvas (one pane) and the gesture one of them is running (canvas-edges spec §4). The canvas
 * asks it first on every press after the pan gesture: the highest-priority handle whose hit area holds the point and
 * that {@link EdgeHandle#claims claims} it takes the press. A primary press starts that handle's gesture, which gets
 * every drag and the release until it ends or is cancelled; a press of another button is swallowed (the handles are
 * dead zones for the tools). Handles of any kind live here side by side (rulers and the guide lines today, the split
 * grips later), each with its priority. EDT only.
 */
public final class EdgeController {

    private final Supplier<Dimension> canvasSize;
    private final List<EdgeHandle> handles = new CopyOnWriteArrayList<>();
    private EdgeHandle hovered;
    private Cursor hoveredCursor;
    private EdgeHandle owner;
    private EdgeGesture gesture;
    private Cursor gestureCursor;
    /** A press taken by a handle whose drags and release must not reach the tool, gesture or not. */
    private boolean pressHeld;

    public EdgeController(Supplier<Dimension> canvasSize) {
        this.canvasSize = Objects.requireNonNull(canvasSize, "canvasSize");
    }

    /** Adds {@code handle}; the returned runnable removes it again, cancelling its gesture if it is running. */
    public Runnable add(EdgeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        handles.add(handle);
        return () -> {
            if (owner == handle) {
                cancel();
            }
            if (hovered == handle) {
                hovered = null;
            }
            handles.remove(handle);
        };
    }

    /** The handles installed now, in installation order. */
    public List<EdgeHandle> handles() {
        return List.copyOf(handles);
    }

    /** The handle that would take a press at {@code event}: the highest priority one under the point that claims it. */
    public Optional<EdgeHandle> handleAt(CanvasMouseEvent event) {
        Dimension size = canvasSize.get();
        return handles.stream()
                .filter(handle -> handle.hitArea(size).contains(event.screenPosition()))
                .filter(handle -> handle.claims(event))
                .max(Comparator.comparingInt(EdgeHandle::priority));
    }

    /** True when a handle took the press: the tool and the press interceptors must not see it. */
    public boolean press(CanvasMouseEvent event) {
        if (pressHeld) {
            return true; // a second button pressed during an edge press belongs to it too
        }
        Optional<EdgeHandle> handle = handleAt(event);
        if (handle.isEmpty()) {
            return false;
        }
        pressHeld = true;
        if (event.button() == CanvasMouseEvent.PRIMARY) {
            owner = handle.get();
            gestureCursor = owner.cursorAt(event);
            gesture = owner.press(event);
        }
        return true;
    }

    /** True when the drag belongs to an edge press (its gesture got it, if it is still running). */
    public boolean drag(CanvasMouseEvent event) {
        if (!pressHeld) {
            return false;
        }
        if (gesture != null) {
            gesture.drag(event);
        }
        return true;
    }

    /** True when the release ends an edge press; its gesture, if still running, ends with it. */
    public boolean release(CanvasMouseEvent event) {
        if (!pressHeld) {
            return false;
        }
        pressHeld = false;
        EdgeGesture ending = gesture;
        clearGesture();
        if (ending != null) {
            ending.release(event);
        }
        return true;
    }

    /** Cancels the running gesture (Escape, a tool change, a closed pane…); false when none was running. */
    public boolean cancel() {
        if (gesture == null) {
            return false;
        }
        EdgeGesture cancelled = gesture;
        clearGesture();
        cancelled.cancel();
        return true;
    }

    private void clearGesture() {
        gesture = null;
        owner = null;
        gestureCursor = null;
    }

    /** True while a gesture started on a handle is running. */
    public boolean isActive() {
        return gesture != null;
    }

    /** The pointer moved to {@code event} with no button held; true when the hovered handle changed. */
    public boolean hover(CanvasMouseEvent event) {
        EdgeHandle now = handleAt(event).orElse(null);
        boolean changed = now != hovered;
        hovered = now;
        hoveredCursor = now == null ? null : now.cursorAt(event);
        return changed;
    }

    /** The pointer left the canvas; true when a handle was hovered. */
    public boolean clearHover() {
        boolean changed = hovered != null;
        hovered = null;
        hoveredCursor = null;
        return changed;
    }

    /** The cursor the edges want now: the running gesture's, else the hovered handle's; empty for the tool's own. */
    public Optional<Cursor> cursor() {
        if (gestureCursor != null) {
            return Optional.of(gestureCursor);
        }
        return Optional.ofNullable(hoveredCursor);
    }

    /** The tooltip of the handle under {@code event}, if it has one. */
    public Optional<String> tooltip(CanvasMouseEvent event) {
        return handleAt(event).map(EdgeHandle::tooltip).filter(text -> !text.isEmpty());
    }

    /**
     * Paints the running gesture's preview, then every handle from the lowest priority up (the highest on top), in
     * screen coordinates: {@code screenGraphics} has the device transform and no document transform.
     */
    public void paint(Graphics2D screenGraphics) {
        Dimension size = canvasSize.get();
        if (gesture != null) {
            Graphics2D preview = (Graphics2D) screenGraphics.create();
            try {
                gesture.paint(preview);
            } finally {
                preview.dispose();
            }
        }
        List<EdgeHandle> ordered = handles.stream().sorted(Comparator.comparingInt(EdgeHandle::priority)).toList();
        for (EdgeHandle handle : ordered) {
            Graphics2D handleGraphics = (Graphics2D) screenGraphics.create();
            try {
                handle.paint(handleGraphics, size, handle == hovered || handle == owner);
            } finally {
                handleGraphics.dispose();
            }
        }
    }
}
