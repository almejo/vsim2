package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The border of a locked frame is a wall for its contents: a move of elements inside a locked frame that does not move
 * the frame itself stops at the border (agreed with the user on 2026-10-16). To take something out, open the padlock.
 * Elements coming from outside may enter; a move that carries the frame (its block) is never limited by it.
 */
public final class LockedFrameWalls {

    private LockedFrameWalls() {
    }

    /** The delta, shortened on each axis so every moved content of a locked frame that stays put stays inside it. */
    public static GridPoint limit(CircuitDocument document, ComponentTypeRegistry registry,
                                  Collection<ElementId> movedIds, int deltaX, int deltaY) {
        Set<ElementId> moved = new HashSet<>(movedIds);
        int limitedX = deltaX;
        int limitedY = deltaY;
        for (Frame frame : document.frames()) {
            if (!frame.locked() || moved.contains(frame.id())) {
                continue;
            }
            Bounds inner = null;
            for (ElementId id : FrameContents.of(document, registry, frame)) {
                if (moved.contains(id)) {
                    Bounds bounds = boundsOf(document, registry, id);
                    if (bounds != null) {
                        inner = inner == null ? bounds : inner.union(bounds);
                    }
                }
            }
            if (inner == null) {
                continue;
            }
            int left = frame.topLeft().x();
            int top = frame.topLeft().y();
            limitedX = clamp(limitedX, left - inner.x(), left + frame.width() - inner.maximumX());
            limitedY = clamp(limitedY, top - inner.y(), top + frame.height() - inner.maximumY());
        }
        return new GridPoint(limitedX, limitedY);
    }

    private static int clamp(int value, int minimum, int maximum) {
        // contents lie inside, so minimum <= 0 <= maximum; keep 0 reachable even if a rounding ever says otherwise
        return Math.max(Math.min(minimum, 0), Math.min(Math.max(maximum, 0), value));
    }

    private static Bounds boundsOf(CircuitDocument document, ComponentTypeRegistry registry, ElementId id) {
        DocumentElement element = document.element(id).orElse(null);
        if (element instanceof Component component) {
            return ElementBounds.of(component, registry);
        }
        if (element instanceof Wire wire) {
            return Bounds.of(wire.start(), wire.end());
        }
        if (element instanceof TextLabel label) {
            return Bounds.of(label.position(), label.position());
        }
        if (element instanceof Frame nested) {
            return Bounds.of(nested.topLeft(),
                    nested.topLeft().translate(nested.width(), nested.height()));
        }
        return null;
    }
}
