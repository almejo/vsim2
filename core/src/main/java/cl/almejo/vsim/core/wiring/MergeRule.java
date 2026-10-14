package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;
import java.util.Map;

/**
 * Joins two collinear wires that overlap or share an end into one wire that keeps the id of the earlier one. Refused
 * when an end that would become an interior point lies in the interior of a perpendicular wire: that end is what
 * joins the other wire, and an interior point of the merged wire would only cross it (same idea as
 * {@code AddWireCommand}). A wire that merely ends there stays joined to the merged wire.
 */
final class MergeRule implements TidyRule {

    @Override
    public boolean apply(TidyState state) {
        boolean changed = false;
        while (mergePass(state)) {
            changed = true;
        }
        return changed;
    }

    /**
     * One scan over all pairs. After a merge the merged wire (which takes the place of the earlier one) is tried
     * again against the wires after it, and the scan goes on from there instead of starting over.
     */
    private boolean mergePass(TidyState state) {
        boolean changed = false;
        for (int firstIndex = 0; firstIndex < state.wires().size(); firstIndex++) {
            while (mergeWithALaterWire(state, firstIndex)) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean mergeWithALaterWire(TidyState state, int firstIndex) {
        List<Wire> wires = state.wires();
        Wire first = wires.get(firstIndex);
        if (!state.inScope(first.id())) {
            return false;
        }
        for (int secondIndex = firstIndex + 1; secondIndex < wires.size(); secondIndex++) {
            Wire second = wires.get(secondIndex);
            if (!joinable(first, second) || !state.inScope(second.id())) {
                continue;
            }
            Wire merged = first.withEndpoints(first.start().compareTo(second.start()) <= 0
                            ? first.start() : second.start(),
                    first.end().compareTo(second.end()) >= 0 ? first.end() : second.end());
            if (keepsConnections(wires, first, second, merged)
                    && state.tryChange(List.of(first, second), List.of(merged), Map.of(second.id(), first.id()))) {
                state.countMerges(1);
                return true;
            }
        }
        return false;
    }

    /** Same line, same orientation, and they overlap or touch at an end. */
    private static boolean joinable(Wire first, Wire second) {
        if (first.isHorizontal() != second.isHorizontal()) {
            return false;
        }
        if (first.isHorizontal()) {
            return first.start().y() == second.start().y()
                    && Math.max(first.start().x(), second.start().x()) <= Math.min(first.end().x(), second.end().x());
        }
        return first.start().x() == second.start().x()
                && Math.max(first.start().y(), second.start().y()) <= Math.min(first.end().y(), second.end().y());
    }

    private static boolean keepsConnections(List<Wire> wires, Wire first, Wire second, Wire merged) {
        for (GridPoint end : List.of(first.start(), first.end(), second.start(), second.end())) {
            if (!WireGraph.isInterior(merged, end)) {
                continue;
            }
            for (Wire other : wires) {
                boolean otherWire = !other.id().equals(first.id()) && !other.id().equals(second.id());
                if (otherWire && other.isHorizontal() != merged.isHorizontal() && WireGraph.isInterior(other, end)) {
                    return false;
                }
            }
        }
        return true;
    }
}
