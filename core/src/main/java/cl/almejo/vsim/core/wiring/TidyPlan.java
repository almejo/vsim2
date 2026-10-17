package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;

import java.util.List;
import java.util.Map;

/**
 * What tidying proposes. {@code removed} are wires of the document as they were; {@code added} are the wires that
 * replace them. A trimmed or merged wire keeps the id of the wire it came from, so the same id can appear in both
 * lists (the old geometry in {@code removed}, the new one in {@code added}). {@code probeRetargets} maps the id of a
 * wire that no longer exists to the surviving wire a waveform probe must watch instead. {@code mergedInto} maps
 * every wire id the plan absorbed into another wire (a merge, a flattened detour) to the id of the wire that now
 * covers it, chains resolved (so a selection can follow the survivor).
 */
public record TidyPlan(List<Wire> removed, List<Wire> added, Map<ElementId, ElementId> probeRetargets,
                       int zeroLengthRemoved, int stubsRemoved, int segmentsMerged, int detoursFlattened,
                       Map<ElementId, ElementId> mergedInto) {

    public static final TidyPlan EMPTY = new TidyPlan(List.of(), List.of(), Map.of(), 0, 0, 0, 0);

    public TidyPlan {
        removed = List.copyOf(removed);
        added = List.copyOf(added);
        probeRetargets = Map.copyOf(probeRetargets);
        mergedInto = Map.copyOf(mergedInto);
    }

    /** A plan that absorbed no wire into another. */
    public TidyPlan(List<Wire> removed, List<Wire> added, Map<ElementId, ElementId> probeRetargets,
                    int zeroLengthRemoved, int stubsRemoved, int segmentsMerged, int detoursFlattened) {
        this(removed, added, probeRetargets, zeroLengthRemoved, stubsRemoved, segmentsMerged, detoursFlattened,
                Map.of());
    }

    /** True when nothing would change: no wire removed and none added. */
    public boolean isEmpty() {
        return removed.isEmpty() && added.isEmpty();
    }
}
