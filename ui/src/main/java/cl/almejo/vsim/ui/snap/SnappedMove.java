package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Axis;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.geometry.SnapResult;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A move of document elements after the magnet (spec §3.1, §4.4): the delta to apply, the box that defines the
 * alignment lines (before the move) and those lines. The components and frames moved make the box; with none, the
 * moved wires' end points are the moving lines (guides and grid lines only); texts alone never snap. A move whose
 * raw delta is zero stays put, so a click that jitters a pixel never becomes a one-unit move.
 *
 * @param box the components and frames moved (or the wires, with none), before the move; empty when nothing snaps
 */
public record SnappedMove(int deltaX, int deltaY, Optional<Bounds> box, List<SnapMatch> matches) {

    public SnappedMove {
        matches = List.copyOf(matches);
    }

    /** Not snapped: the raw delta, no lines. */
    public static SnappedMove unsnapped(int deltaX, int deltaY) {
        return new SnappedMove(deltaX, deltaY, Optional.empty(), List.of());
    }

    /**
     * Snaps moving the elements {@code ids} by the raw delta. {@code copy} (a Ctrl copy) keeps the originals as
     * targets: the copy aligns with what it was copied from.
     */
    public static SnappedMove of(ToolContext context, Collection<ElementId> ids, boolean copy, int rawDeltaX,
                                 int rawDeltaY) {
        if (rawDeltaX == 0 && rawDeltaY == 0) {
            return unsnapped(0, 0);
        }
        Moved moved = moved(context, ids);
        if (moved.box().isPresent()) {
            MovingSet moving = movingSet(context, ids, copy, moved.box());
            SnapResult result = context.snapper().snapBox(context, moving, rawDeltaX, rawDeltaY);
            return new SnappedMove(result.deltaX(), result.deltaY(), moved.box(), result.matches());
        }
        if (!moved.wireIds().isEmpty()) {
            SnapResult result = context.snapper().snapWires(context, moved.wireIds(), rawDeltaX, rawDeltaY);
            return new SnappedMove(result.deltaX(), result.deltaY(), moved.wireBox(), result.matches());
        }
        return unsnapped(rawDeltaX, rawDeltaY);
    }

    /**
     * Like {@link #of} for a segment drag (spec §3.4): the one wire {@code wireId} moves only across itself, so the
     * axis along it stays at zero whatever a guide says, and only the lines that hold then are drawn.
     */
    public static SnappedMove segment(ToolContext context, Wire wire, int rawDeltaX, int rawDeltaY) {
        boolean horizontal = wire.start().y() == wire.end().y();
        int lockedX = horizontal ? 0 : rawDeltaX;
        int lockedY = horizontal ? rawDeltaY : 0;
        if (lockedX == 0 && lockedY == 0) {
            return unsnapped(0, 0);
        }
        Set<ElementId> ids = Set.of(wire.id());
        SnapResult result = context.snapper().snapWires(context, ids, lockedX, lockedY);
        int deltaX = horizontal ? 0 : result.deltaX();
        int deltaY = horizontal ? result.deltaY() : 0;
        Axis along = horizontal ? Axis.X : Axis.Y;
        List<SnapMatch> matches = result.matches();
        if (deltaX != result.deltaX() || deltaY != result.deltaY()) {
            matches = context.snapper().wiresMatchesAt(context, ids, deltaX, deltaY);
        }
        List<SnapMatch> across = matches.stream().filter(match -> match.axis() != along).toList();
        return new SnappedMove(deltaX, deltaY, Optional.of(Bounds.of(wire.start(), wire.end())), across);
    }

    /**
     * This move after a clamp (the walls of a locked frame) changed its delta to {@code deltaX, deltaY}: the lines
     * are worked out again so only the alignments that really hold are drawn.
     */
    public SnappedMove clampedTo(ToolContext context, Collection<ElementId> ids, boolean copy, int clampedX,
                                 int clampedY) {
        if (clampedX == deltaX && clampedY == deltaY) {
            return this;
        }
        if (box.isEmpty()) {
            return new SnappedMove(clampedX, clampedY, box, List.of());
        }
        Moved moved = moved(context, ids);
        List<SnapMatch> held = moved.box().isPresent()
                ? context.snapper().boxMatchesAt(context, movingSet(context, ids, copy, moved.box()), clampedX,
                clampedY)
                : context.snapper().wiresMatchesAt(context, moved.wireIds(), clampedX, clampedY);
        return new SnappedMove(clampedX, clampedY, box, held);
    }

    /** The box at the move's place, for drawing the lines; empty when nothing snaps. */
    public Optional<Bounds> movedBox() {
        return box.map(bounds -> bounds.translate(deltaX, deltaY));
    }

    private static MovingSet movingSet(ToolContext context, Collection<ElementId> ids, boolean copy,
                                       Optional<Bounds> box) {
        return new MovingSet(copy ? Set.of() : new LinkedHashSet<>(ids), box, context.openDocument().registry());
    }

    private static Moved moved(ToolContext context, Collection<ElementId> ids) {
        CircuitDocument document = context.openDocument().document();
        ComponentTypeRegistry registry = context.openDocument().registry();
        List<DocumentElement> parts = new ArrayList<>();
        List<Wire> wires = new ArrayList<>();
        for (ElementId id : ids) {
            Optional<DocumentElement> element = document.element(id);
            if (element.isEmpty()) {
                continue;
            }
            if (element.get() instanceof Component || element.get() instanceof Frame) {
                parts.add(element.get());
            } else if (element.get() instanceof Wire wire) {
                wires.add(wire);
            }
        }
        Set<ElementId> wireIds = new LinkedHashSet<>();
        wires.forEach(wire -> wireIds.add(wire.id()));
        return new Moved(ElementBounds.union(parts, registry), wireIds, ElementBounds.union(wires, registry));
    }

    private record Moved(Optional<Bounds> box, Set<ElementId> wireIds, Optional<Bounds> wireBox) {
    }
}
