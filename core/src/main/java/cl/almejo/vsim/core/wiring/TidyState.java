package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The working copy of one tidy run: the wires as they are now, the scope, the baseline connectivity every change is
 * checked against, and the counters. Never touches the document.
 */
final class TidyState {

    private final CircuitDocument document;
    private final ComponentTypeRegistry registry;
    private final Set<ElementId> scope;
    private final Map<GridPoint, List<PinRef>> pinsAtPoint;
    private final Connectivity baseline;
    private final List<Wire> original;
    private final Map<ElementId, ElementId> retargets = new LinkedHashMap<>();
    private List<Wire> wires;
    private WireGraph graph;
    private Set<ElementId> protectedWires;
    private long nextWireNumber;
    private int zeroLengthRemoved;
    private int stubsRemoved;
    private int segmentsMerged;
    private int detoursFlattened;

    TidyState(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> scope) {
        this.document = document;
        this.registry = registry;
        this.scope = Set.copyOf(scope);
        this.pinsAtPoint = WireGraph.pinLocations(document, registry);
        this.original = document.wires();
        this.wires = new ArrayList<>(original);
        this.baseline = Connectivity.of(original, document, registry);
        long highest = 0;
        for (DocumentElement element : document.elements()) {
            String digits = element.id().value().replaceFirst("^\\D+", "");
            if (!digits.isEmpty() && digits.length() < 18 && digits.chars().allMatch(Character::isDigit)) {
                highest = Math.max(highest, Long.parseLong(digits));
            }
        }
        this.nextWireNumber = highest + 1;
    }

    /** The current wires, in document draw order (replacements take the place of the first wire they replace). */
    List<Wire> wires() {
        return wires;
    }

    CircuitDocument document() {
        return document;
    }

    ComponentTypeRegistry registry() {
        return registry;
    }

    WireGraph graph() {
        if (graph == null) {
            graph = WireGraph.of(wires, pinsAtPoint);
        }
        return graph;
    }

    boolean inScope(ElementId wireId) {
        return scope.contains(wireId);
    }

    /** A wire id that no element of the document uses and no earlier call returned. */
    ElementId newWireId() {
        return new ElementId("w" + nextWireNumber++);
    }

    /**
     * Wires that must not disappear: wires a probe watches (after the retargets so far) and every wire of a net
     * without pins that a probe watches, so a watched floating network stays whole.
     */
    Set<ElementId> protectedWires() {
        if (protectedWires == null) {
            Set<ElementId> watched = new HashSet<>();
            for (Probe probe : document.probes()) {
                if (probe.target() instanceof ProbeTarget.WireTarget target) {
                    watched.add(retargets.getOrDefault(target.wireId(), target.wireId()));
                }
            }
            Set<ElementId> result = new HashSet<>(watched);
            Netlist netlist = Connectivity.compile(wires, document, registry);
            for (ElementId wireId : watched) {
                netlist.netForWire(wireId).filter(net -> net.pins().isEmpty()).map(Net::wires).ifPresent(result::addAll);
            }
            protectedWires = result;
        }
        return protectedWires;
    }

    /**
     * Replaces {@code removed} by {@code added} when every removed wire is in scope and the circuit stays
     * equivalent. The added wires take the place of the first removed one; an added wire may reuse the id of a
     * removed wire. {@code droppedTo} maps the id of a removed wire that no added wire reuses to the wire that now
     * covers it (probes on it are retargeted). Returns whether the change was accepted.
     */
    boolean tryChange(List<Wire> removed, List<Wire> added, Map<ElementId, ElementId> droppedTo) {
        Set<ElementId> removedIds = new HashSet<>();
        for (Wire wire : removed) {
            if (!inScope(wire.id())) {
                return false;
            }
            removedIds.add(wire.id());
        }
        List<Wire> candidate = new ArrayList<>();
        boolean inserted = false;
        for (Wire wire : wires) {
            if (removedIds.contains(wire.id())) {
                if (!inserted) {
                    candidate.addAll(added);
                    inserted = true;
                }
            } else {
                candidate.add(wire);
            }
        }
        if (!inserted) {
            return false;
        }
        Map<ElementId, ElementId> candidateRetargets = new LinkedHashMap<>(retargets);
        droppedTo.forEach((dropped, survivor) -> {
            candidateRetargets.replaceAll((key, value) -> value.equals(dropped) ? survivor : value);
            candidateRetargets.put(dropped, survivor);
        });
        if (!baseline.equals(Connectivity.of(candidate, document, registry, candidateRetargets))) {
            return false;
        }
        wires = candidate;
        retargets.clear();
        retargets.putAll(candidateRetargets);
        graph = null;
        protectedWires = null;
        return true;
    }

    void countZeroLength(int count) {
        zeroLengthRemoved += count;
    }

    void countStubs(int count) {
        stubsRemoved += count;
    }

    void countMerges(int count) {
        segmentsMerged += count;
    }

    void countDetours(int count) {
        detoursFlattened += count;
    }

    /** The plan for the wires as they are now; empty if, against all expectations, the end result is not equivalent. */
    TidyPlan toPlan() {
        if (!baseline.equals(Connectivity.of(wires, document, registry, flattenedRetargets()))) {
            return TidyPlan.EMPTY;
        }
        Set<Wire> finalWires = new HashSet<>(wires);
        Set<Wire> originalWires = new HashSet<>(original);
        List<Wire> removed = original.stream().filter(wire -> !finalWires.contains(wire)).toList();
        List<Wire> added = wires.stream().filter(wire -> !originalWires.contains(wire)).toList();
        if (removed.isEmpty() && added.isEmpty()) {
            return TidyPlan.EMPTY;
        }
        return new TidyPlan(removed, added, flattenedRetargets(), zeroLengthRemoved, stubsRemoved, segmentsMerged,
                detoursFlattened);
    }

    /** Only the retargets some probe needs: its wire is gone and the survivor exists. */
    private Map<ElementId, ElementId> flattenedRetargets() {
        Set<ElementId> alive = new HashSet<>();
        wires.forEach(wire -> alive.add(wire.id()));
        Map<ElementId, ElementId> needed = new HashMap<>();
        for (Probe probe : document.probes()) {
            if (probe.target() instanceof ProbeTarget.WireTarget target && !alive.contains(target.wireId())
                    && retargets.containsKey(target.wireId())) {
                needed.put(target.wireId(), retargets.get(target.wireId()));
            }
        }
        return needed;
    }
}
