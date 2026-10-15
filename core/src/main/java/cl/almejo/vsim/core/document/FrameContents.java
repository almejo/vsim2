package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.HashSet;
import java.util.LinkedHashSet;
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
