package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.geometry.SnapResult;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where something not yet in the circuit lands after the magnet (spec §3.4): a component being placed, a floating
 * paste, a dropped fragment. Its elements are given at the origin; the raw position is where the pointer puts that
 * origin. Every part of the circuit is a target (nothing is excluded: the ghost is not in the document).
 *
 * @param box the ghost's components and frames at {@code position}, for drawing the lines; empty when it has none
 */
public record SnappedPlacement(GridPoint position, Optional<Bounds> box, List<SnapMatch> matches) {

    public SnappedPlacement {
        matches = List.copyOf(matches);
    }

    /** Snaps placing {@code elements} (at the origin, types resolved with {@code registry}) at {@code raw}. */
    public static SnappedPlacement of(ToolContext context, List<? extends DocumentElement> elements,
                                      ComponentTypeRegistry registry, GridPoint raw) {
        List<DocumentElement> parts = new ArrayList<>();
        for (DocumentElement element : elements) {
            if (element instanceof Component || element instanceof Frame) {
                parts.add(element);
            }
        }
        return ofBox(context, ElementBounds.union(parts, registry), registry, raw);
    }

    /** Snaps placing something whose components and frames fill {@code box} at the origin at {@code raw}. */
    public static SnappedPlacement ofBox(ToolContext context, Optional<Bounds> box, ComponentTypeRegistry registry,
                                         GridPoint raw) {
        if (box.isEmpty()) {
            return new SnappedPlacement(raw, Optional.empty(), List.of());
        }
        SnapResult result = context.snapper().snapBox(context, new MovingSet(Set.of(), box, registry), raw.x(),
                raw.y());
        return new SnappedPlacement(new GridPoint(result.deltaX(), result.deltaY()),
                box.map(bounds -> bounds.translate(result.deltaX(), result.deltaY())), result.matches());
    }

    /** Snaps a paste or a drop of {@code fragment} with its origin at {@code raw}, with the fragment's own types. */
    public static SnappedPlacement ofFragment(ToolContext context, CircuitFragment fragment,
                                              ComponentTypeRegistry fragmentTypes, GridPoint raw) {
        List<DocumentElement> parts = new ArrayList<>(fragment.components());
        parts.addAll(fragment.frames());
        return of(context, parts, fragmentTypes, raw);
    }
}
