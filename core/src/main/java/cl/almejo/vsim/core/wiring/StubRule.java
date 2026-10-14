package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loose ends: a wire is trimmed from a free end back to the nearest point where it joins a pin or another wire
 * end; a wire that joins nothing, or only at one point, goes entirely. Floating networks with no pin (also closed
 * loops) go entirely, unless a probe watches them.
 */
final class StubRule implements TidyRule {

    @Override
    public boolean apply(TidyState state) {
        boolean changed = removeFloatingNetworks(state);
        for (Wire snapshot : new ArrayList<>(state.wires())) {
            Wire current = state.wire(snapshot.id()).orElse(null);
            if (current != null && state.inScope(current.id())) {
                changed |= tidyWire(state, current);
            }
        }
        return changed;
    }

    private boolean tidyWire(TidyState state, Wire wire) {
        TreeSet<GridPoint> joints = new TreeSet<>(state.graph().connectionPointsOn(wire));
        if (joints.size() < 2) {
            if (state.protectedWires().contains(wire.id())) {
                return false;
            }
            return accept(state, state.tryChange(List.of(wire), List.of(), Map.of()));
        }
        if (joints.first().equals(wire.start()) && joints.last().equals(wire.end())) {
            return false;
        }
        Wire trimmed = wire.withEndpoints(joints.first(), joints.last());
        return accept(state, state.tryChange(List.of(wire), List.of(trimmed), Map.of()));
    }

    private boolean removeFloatingNetworks(TidyState state) {
        boolean changed = false;
        for (ConnectivityChecker.WireNet net : state.nets()) {
            if (!net.pins().isEmpty() || net.wires().isEmpty()) {
                continue;
            }
            Set<ElementId> ids = net.wires();
            if (ids.stream().anyMatch(id -> state.protectedWires().contains(id) || !state.inScope(id))) {
                continue;
            }
            List<Wire> members = state.wires().stream().filter(wire -> ids.contains(wire.id())).toList();
            changed |= accept(state, state.tryChange(members, List.of(), Map.of()), members.size());
        }
        return changed;
    }

    private static boolean accept(TidyState state, boolean accepted) {
        return accept(state, accepted, 1);
    }

    private static boolean accept(TidyState state, boolean accepted, int count) {
        if (accepted) {
            state.countStubs(count);
        }
        return accepted;
    }
}
