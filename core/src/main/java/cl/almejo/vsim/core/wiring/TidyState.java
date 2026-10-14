package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
    private final ConnectivityChecker checker;
    private final Connectivity checkedBaseline;
    private final List<Wire> original;
    private final Map<ElementId, ElementId> retargets = new LinkedHashMap<>();
    private List<Wire> wires;
    private WireGraph graph;
    private Map<ElementId, Wire> wiresById;
    private Set<ElementId> protectedWires;
    private int stubsRemoved;
    private int segmentsMerged;
    private int detoursFlattened;
    private boolean verifyEachChange;
    private BooleanSupplier cancelled = () -> false;

    /** Thrown inside a run when the caller asked to stop; {@link WireTidy} turns it into an empty result. */
    static final class Cancelled extends RuntimeException {
        Cancelled() {
            super("tidy cancelled", null, false, false);
        }
    }

    void cancellation(BooleanSupplier newCancelled) {
        cancelled = newCancelled;
    }

    void checkCancelled() {
        if (cancelled.getAsBoolean()) {
            throw new Cancelled();
        }
    }

    TidyState(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> scope) {
        this.document = document;
        this.registry = registry;
        this.scope = Set.copyOf(scope);
        this.pinsAtPoint = WireGraph.pinLocations(document, registry);
        this.original = document.wires();
        this.wires = new ArrayList<>(original);
        this.baseline = Connectivity.of(original, document, registry);
        this.checker = new ConnectivityChecker(document, pinsAtPoint);
        this.checkedBaseline = checker.of(original, Map.of());
    }

    /** The current wires, in document draw order (replacements take the place of the first wire they replace). */
    List<Wire> wires() {
        return wires;
    }

    /** The current wire with {@code wireId}, if it still exists. */
    Optional<Wire> wire(ElementId wireId) {
        if (wiresById == null) {
            wiresById = new HashMap<>();
            wires.forEach(wire -> wiresById.put(wire.id(), wire));
        }
        return Optional.ofNullable(wiresById.get(wireId));
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
            if (!watched.isEmpty()) {
                for (ConnectivityChecker.WireNet net : checker.nets(wires)) {
                    if (net.pins().isEmpty() && net.wires().stream().anyMatch(watched::contains)) {
                        result.addAll(net.wires());
                    }
                }
            }
            protectedWires = result;
        }
        return protectedWires;
    }

    /** The nets of the current wires that have a wire, ordered by their first wire. */
    List<ConnectivityChecker.WireNet> nets() {
        return checker.nets(wires);
    }

    /**
     * Applies {@code rule} optimistically: its changes are accepted without checking each one, and the connectivity
     * is checked once at the end. Only when that check fails (a rule proposed a change the guard would have
     * refused) is the pass undone and run again with every change checked, which gives exactly the per-change
     * guarantee at the cost of one check per change. Returns whether the rule changed something.
     */
    boolean run(TidyRule rule) {
        checkCancelled();
        List<Wire> wiresBefore = wires;
        Map<ElementId, ElementId> retargetsBefore = new LinkedHashMap<>(retargets);
        int[] countersBefore = {stubsRemoved, segmentsMerged, detoursFlattened};
        boolean changed = rule.apply(this);
        if (!changed || checkedBaseline.equals(checker.of(wires, retargets))) {
            return changed;
        }
        wires = wiresBefore;
        retargets.clear();
        retargets.putAll(retargetsBefore);
        stubsRemoved = countersBefore[0];
        segmentsMerged = countersBefore[1];
        detoursFlattened = countersBefore[2];
        graph = null;
        wiresById = null;
        protectedWires = null;
        verifyEachChange = true;
        try {
            return rule.apply(this);
        } finally {
            verifyEachChange = false;
        }
    }

    /**
     * Replaces {@code removed} by {@code added} when every removed wire is in scope and the circuit stays
     * equivalent (checked here only while {@link #run} replays a pass change by change). The added wires take the place of the first removed one; an added wire may reuse the id of a
     * removed wire. {@code droppedTo} maps the id of a removed wire that no added wire reuses to the wire that now
     * covers it (probes on it are retargeted). Returns whether the change was accepted.
     */
    boolean tryChange(List<Wire> removed, List<Wire> added, Map<ElementId, ElementId> droppedTo) {
        checkCancelled();
        Set<ElementId> removedIds = new HashSet<>();
        for (Wire wire : removed) {
            if (!inScope(wire.id())) {
                return false;
            }
            removedIds.add(wire.id());
        }
        List<Wire> candidate = new ArrayList<>(wires.size() + added.size());
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
        if (verifyEachChange && !checkedBaseline.equals(checker.of(candidate, candidateRetargets))) {
            return false;
        }
        wires = candidate;
        retargets.clear();
        retargets.putAll(candidateRetargets);
        if (graph != null) {
            graph.update(removed, added, candidate);
        }
        if (wiresById != null) {
            removed.forEach(wire -> wiresById.remove(wire.id()));
            added.forEach(wire -> wiresById.put(wire.id(), wire));
        }
        protectedWires = null;
        return true;
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
        // no zero-length wire to remove: Wire rejects them, so a document cannot hold one
        return new TidyPlan(removed, added, flattenedRetargets(), 0, stubsRemoved, segmentsMerged,
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
