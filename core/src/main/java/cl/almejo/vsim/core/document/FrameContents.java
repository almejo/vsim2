package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a frame holds, computed at the moment of a gesture and never stored: components completely inside, wires
 * with both endpoints inside, labels whose anchor is inside, and frames completely inside with their own contents.
 */
public final class FrameContents {

    private FrameContents() {
    }

    /** The ids of everything inside {@code frame}, without the frame itself. */
    public static Set<ElementId> of(CircuitDocument document, ComponentTypeRegistry registry, Frame frame) {
        Set<ElementId> contents = new LinkedHashSet<>();
        collect(document, registry, frame, contents, new HashSet<>(Set.of(frame.id())));
        return contents;
    }

    /** The frame id plus its contents. */
    public static Set<ElementId> block(CircuitDocument document, ComponentTypeRegistry registry, Frame frame) {
        Set<ElementId> block = new LinkedHashSet<>();
        block.add(frame.id());
        block.addAll(of(document, registry, frame));
        return block;
    }

    /**
     * What an operation on {@code ids} acts on: the ids plus the contents of every locked frame among them (nested
     * frames and their contents once). Unlocked frames add nothing.
     */
    public static Set<ElementId> withLockedBlocks(CircuitDocument document, ComponentTypeRegistry registry,
                                                  Collection<ElementId> ids) {
        Set<ElementId> expanded = new LinkedHashSet<>(ids);
        for (ElementId id : ids) {
            document.frame(id).filter(Frame::locked)
                    .ifPresent(frame -> expanded.addAll(of(document, registry, frame)));
        }
        return expanded;
    }

    /**
     * The innermost frame enclosing each component and frame that lies completely inside at least one frame (the
     * component tree nests rows by it). The innermost frame is the enclosing one with the smallest area (components of an unknown type have
     * no parent); identical
     * frames nest by document order (the later one inside the earlier one), so no frame is its own parent and there
     * are no cycles. Components first, then frames, each in document order.
     */
    public static Map<ElementId, ElementId> parents(CircuitDocument document, ComponentTypeRegistry registry) {
        List<Frame> frames = document.frames();
        Map<ElementId, ElementId> parents = new LinkedHashMap<>();
        if (frames.isEmpty()) {
            return parents;
        }
        for (Component component : document.components()) {
            Optional<Bounds> found = registry.findTypeOf(component).map(type -> type.bounds(component));
            if (found.isEmpty()) {
                continue; // an unknown type has no size: it stays at the top
            }
            Bounds bounds = found.get();
            Frame innermost = null;
            for (Frame candidate : frames) {
                if (candidate.encloses(bounds.x(), bounds.y(), bounds.maximumX(), bounds.maximumY())
                        && (innermost == null || area(candidate) <= area(innermost))) {
                    innermost = candidate; // on equal areas the later frame (deeper by document order) wins
                }
            }
            if (innermost != null) {
                parents.put(component.id(), innermost.id());
            }
        }
        for (int index = 0; index < frames.size(); index++) {
            Frame frame = frames.get(index);
            Frame innermost = null;
            for (int candidateIndex = 0; candidateIndex < frames.size(); candidateIndex++) {
                Frame candidate = frames.get(candidateIndex);
                boolean encloses = candidateIndex != index && encloses(candidate, frame)
                        && (area(candidate) > area(frame) || candidateIndex < index);
                if (encloses && (innermost == null || area(candidate) <= area(innermost))) {
                    innermost = candidate;
                }
            }
            if (innermost != null) {
                parents.put(frame.id(), innermost.id());
            }
        }
        return parents;
    }

    private static boolean encloses(Frame outer, Frame inner) {
        return outer.encloses(inner.topLeft().x(), inner.topLeft().y(),
                inner.topLeft().x() + inner.width(), inner.topLeft().y() + inner.height());
    }

    private static long area(Frame frame) {
        return (long) frame.width() * frame.height();
    }

    private static void collect(CircuitDocument document, ComponentTypeRegistry registry, Frame frame,
                                Set<ElementId> contents, Set<ElementId> visitedFrames) {
        for (Component component : document.components()) {
            Bounds bounds = ElementBounds.of(component, registry);
            if (frame.encloses(bounds.x(), bounds.y(), bounds.maximumX(), bounds.maximumY())) {
                contents.add(component.id());
            }
        }
        for (Wire wire : document.wires()) {
            if (frame.contains(wire.start()) && frame.contains(wire.end())) {
                contents.add(wire.id());
            }
        }
        for (TextLabel label : document.labels()) {
            if (frame.contains(label.position())) {
                contents.add(label.id());
            }
        }
        for (Frame nested : document.frames()) {
            boolean inside = frame.encloses(nested.topLeft().x(), nested.topLeft().y(),
                    nested.topLeft().x() + nested.width(), nested.topLeft().y() + nested.height());
            if (inside && visitedFrames.add(nested.id())) {
                contents.add(nested.id());
                collect(document, registry, nested, contents, visitedFrames);
            }
        }
    }
}
