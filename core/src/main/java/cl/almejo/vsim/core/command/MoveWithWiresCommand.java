package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.TouchedWiresCleanup;
import cl.almejo.vsim.core.wiring.WireFollow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Moves elements and makes the wires attached to them follow (spec M6.7): translates the moved ids and the plan's
 * wires moved whole, then replaces, removes and adds the plan's wires. One undo step; revert restores ids and draw
 * order exactly, and redo reapplies the same plan (same new wire ids).
 *
 * <p>A run of {@link #nudge nudges} merges into one undo step whose result is <em>one</em> move by the total delta
 * from the state before the run: the merged command plans again, lazily, when {@link History} applies it after
 * reverting the run so far (never composing two plans, so no L corners pile up). The wire ids the run already
 * reserved are used again first.
 *
 * <p>Commands made by {@link #of} and {@link #nudge} also tidy what the move touched (spec §6), in the same undo
 * step: {@link TouchedWiresCleanup} merges the wires the move changed with each other and with the untouched wires
 * they now end in line with (only at an end the move put there), never changing connectivity. The clean-up is planned
 * on the document right after the first apply and replayed as is on redo.
 */
public final class MoveWithWiresCommand implements Command {

    private final List<ElementId> movedIds;
    private final int deltaX;
    private final int deltaY;
    private final ComponentTypeRegistry registry;
    private final boolean nudge;
    private final List<ElementId> reusableWireIds;
    private FollowPlan plan;
    /** The clean-up after the move: planned on the first apply (null until then), empty when there is nothing to do. */
    private TidyWiresCommand cleanUp;
    private boolean cleanUpPlanned;
    private final Map<ElementId, Wire> replacedOriginals = new LinkedHashMap<>();
    private final Map<ElementId, Wire> removedOriginals = new LinkedHashMap<>();
    private final Map<ElementId, Long> removedOrders = new LinkedHashMap<>();

    /** Applies {@code plan}, computed by {@link WireFollow#plan} for the same ids and delta on the current document. */
    public MoveWithWiresCommand(Set<ElementId> movedIds, int deltaX, int deltaY, FollowPlan plan) {
        this(movedIds, deltaX, deltaY, null, false, List.of(), Objects.requireNonNull(plan, "plan"));
    }

    private MoveWithWiresCommand(Collection<ElementId> movedIds, int deltaX, int deltaY,
                                 ComponentTypeRegistry registry, boolean nudge, List<ElementId> reusableWireIds,
                                 FollowPlan plan) {
        this.movedIds = List.copyOf(new LinkedHashSet<>(movedIds));
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.registry = registry;
        this.nudge = nudge;
        this.reusableWireIds = List.copyOf(reusableWireIds);
        this.plan = plan;
    }

    /** The move of {@code movedIds} by the delta with the wires following, planned now on {@code document}. */
    public static MoveWithWiresCommand of(CircuitDocument document, ComponentTypeRegistry registry,
                                          Set<ElementId> movedIds, int deltaX, int deltaY) {
        return new MoveWithWiresCommand(movedIds, deltaX, deltaY, Objects.requireNonNull(registry, "registry"),
                false, List.of(), WireFollow.plan(document, registry, movedIds, deltaX, deltaY));
    }

    /**
     * A keyboard move: planned when first applied, and a run of nudges of the same ids, one right after the other,
     * merges into one undo step equal to one move by the total delta (see the class comment).
     */
    public static MoveWithWiresCommand nudge(ComponentTypeRegistry registry, Set<ElementId> movedIds, int deltaX,
                                             int deltaY) {
        return new MoveWithWiresCommand(movedIds, deltaX, deltaY, Objects.requireNonNull(registry, "registry"),
                true, List.of(), null);
    }

    /** The plan this command applies; empty for a nudge that was never applied. */
    public Optional<FollowPlan> plan() {
        return Optional.ofNullable(plan);
    }

    /**
     * The wires the clean-up of the last apply merged into another wire, each mapped to the wire that survived
     * (chains resolved); empty before the first apply or when the clean-up merged nothing. A selection that held a
     * moved wire follows it to its survivor.
     */
    public Map<ElementId, ElementId> mergedInto() {
        return cleanUp == null ? Map.of() : cleanUp.plan().mergedInto();
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaY() {
        return deltaY;
    }

    @Override
    public Optional<Command> mergedWith(Command next) {
        if (nudge && plan != null && next instanceof MoveWithWiresCommand following && following.nudge
                && following.registry == registry
                && new HashSet<>(movedIds).equals(new HashSet<>(following.movedIds))) {
            // The top's plan already took some of its reusable ids: keep each id once, or a later plan that needs
            // more new wires would hand the same id out twice. All of them are free once History reverts the top.
            Set<ElementId> reusable = new LinkedHashSet<>();
            plan.addedWires().forEach(wire -> reusable.add(wire.id()));
            reusable.addAll(reusableWireIds);
            return Optional.of(new MoveWithWiresCommand(movedIds, deltaX + following.deltaX,
                    deltaY + following.deltaY, registry, true, List.copyOf(reusable), null));
        }
        return Optional.empty();
    }

    /**
     * True for a move by (0, 0) whose plan, if already made, changes no wire: a merged nudge run that came back to
     * where it started.
     */
    @Override
    public boolean changesNothing() {
        return deltaX == 0 && deltaY == 0 && (plan == null || plan.replacedWires().isEmpty()
                && plan.addedWires().isEmpty() && plan.removedWireIds().isEmpty());
    }

    @Override
    public void apply(CircuitDocument document) {
        if (plan == null) {
            Deque<ElementId> reusable = new ArrayDeque<>(reusableWireIds);
            plan = WireFollow.plan(document, registry, new LinkedHashSet<>(movedIds), deltaX, deltaY,
                    () -> freeWireId(document, reusable));
        }
        replacedOriginals.clear();
        removedOriginals.clear();
        removedOrders.clear();
        document.batch(() -> {
            Map<ElementId, Set<GridPoint>> endsBefore = cleanUpPlanned ? Map.of() : endsOfWires(document);
            applyPlan(document);
            // A run that came back to where it started (total delta 0) moved nothing, so there is nothing to tidy.
            if (!cleanUpPlanned && registry != null && (deltaX != 0 || deltaY != 0)) {
                TidyPlan tidy = TouchedWiresCleanup.plan(document, registry, cleanUpScope(document, endsBefore));
                cleanUp = tidy.isEmpty() ? null : new TidyWiresCommand(tidy);
            }
            cleanUpPlanned = true;
            if (cleanUp != null) {
                cleanUp.apply(document);
            }
        });
    }

    /** The next reserved id that is free in {@code document}, or a new one once they run out. */
    private static ElementId freeWireId(CircuitDocument document, Deque<ElementId> reusable) {
        while (!reusable.isEmpty()) {
            ElementId candidate = reusable.removeFirst();
            if (document.element(candidate).isEmpty()) {
                return candidate;
            }
        }
        return document.newWireId();
    }

    private void applyPlan(CircuitDocument document) {
        document.batch(() -> {
            translate(document, deltaX, deltaY);
            for (Wire replacement : plan.replacedWires()) {
                replacedOriginals.put(replacement.id(), document.wire(replacement.id()).orElseThrow(
                        () -> new IllegalStateException("no wire with id " + replacement.id())));
                document.replace(replacement);
            }
            for (ElementId removedId : plan.removedWireIds()) {
                document.wire(removedId).ifPresent(wire -> {
                    removedOriginals.put(removedId, wire);
                    removedOrders.put(removedId, document.orderOf(removedId));
                    document.remove(removedId);
                });
            }
            plan.addedWires().forEach(document::add);
        });
    }

    /** The ends of every wire of {@code document}, by id. */
    private static Map<ElementId, Set<GridPoint>> endsOfWires(CircuitDocument document) {
        Map<ElementId, Set<GridPoint>> ends = new LinkedHashMap<>();
        for (Wire wire : document.wires()) {
            ends.put(wire.id(), Set.of(wire.start(), wire.end()));
        }
        return ends;
    }

    /**
     * The wires the clean-up may merge: the moved wires and the ones the plan changed, plus each untouched wire that
     * now continues one of them in line from an end the move put there (a wire that was already split in line before
     * the move, somewhere the move did not reach, stays as the user drew it).
     */
    private Set<ElementId> cleanUpScope(CircuitDocument document, Map<ElementId, Set<GridPoint>> endsBefore) {
        Set<ElementId> touched = new LinkedHashSet<>();
        for (ElementId id : movedIds) {
            if (document.wire(id).isPresent()) {
                touched.add(id);
            }
        }
        touched.addAll(plan.changedWireIds());
        touched.removeIf(id -> document.wire(id).isEmpty());
        Map<GridPoint, List<Wire>> newEnds = new LinkedHashMap<>();
        for (ElementId id : touched) {
            Wire wire = document.wire(id).orElseThrow();
            Set<GridPoint> before = endsBefore.getOrDefault(id, Set.of());
            for (GridPoint end : List.of(wire.start(), wire.end())) {
                if (!before.contains(end)) {
                    newEnds.computeIfAbsent(end, point -> new ArrayList<>()).add(wire);
                }
            }
        }
        Set<ElementId> scope = new LinkedHashSet<>(touched);
        if (newEnds.isEmpty()) {
            return scope;
        }
        for (Wire candidate : document.wires()) {
            if (touched.contains(candidate.id())) {
                continue;
            }
            for (GridPoint end : List.of(candidate.start(), candidate.end())) {
                List<Wire> endingThere = newEnds.getOrDefault(end, List.of());
                if (endingThere.stream().anyMatch(wire -> wire.isHorizontal() == candidate.isHorizontal())) {
                    scope.add(candidate.id());
                }
            }
        }
        return scope;
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            if (cleanUp != null) {
                cleanUp.revert(document);
            }
            plan.addedWires().forEach(wire -> document.remove(wire.id()));
            removedOriginals.forEach((id, wire) -> document.restore(wire, removedOrders.get(id)));
            replacedOriginals.values().forEach(document::replace);
            translate(document, -deltaX, -deltaY);
        });
    }

    private void translate(CircuitDocument document, int byX, int byY) {
        for (ElementId id : movedIds) {
            document.element(id).ifPresent(element -> document.replace(element.translate(byX, byY)));
        }
        for (ElementId id : plan.movedWholeWireIds()) {
            document.wire(id).ifPresent(wire -> document.replace(wire.translate(byX, byY)));
        }
    }

    @Override
    public String description() {
        return "command.move";
    }
}
