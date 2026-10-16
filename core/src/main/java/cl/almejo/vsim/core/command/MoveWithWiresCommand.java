package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.wiring.FollowPlan;
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
 */
public final class MoveWithWiresCommand implements Command {

    private final List<ElementId> movedIds;
    private final int deltaX;
    private final int deltaY;
    private final ComponentTypeRegistry registry;
    private final boolean nudge;
    private final List<ElementId> reusableWireIds;
    private FollowPlan plan;
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
        return new MoveWithWiresCommand(movedIds, deltaX, deltaY,
                WireFollow.plan(document, registry, movedIds, deltaX, deltaY));
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
            List<ElementId> reusable = new ArrayList<>();
            plan.addedWires().forEach(wire -> reusable.add(wire.id()));
            reusable.addAll(reusableWireIds);
            return Optional.of(new MoveWithWiresCommand(movedIds, deltaX + following.deltaX,
                    deltaY + following.deltaY, registry, true, reusable, null));
        }
        return Optional.empty();
    }

    @Override
    public void apply(CircuitDocument document) {
        if (plan == null) {
            Deque<ElementId> reusable = new ArrayDeque<>(reusableWireIds);
            plan = WireFollow.plan(document, registry, new LinkedHashSet<>(movedIds), deltaX, deltaY,
                    () -> reusable.isEmpty() ? document.newWireId() : reusable.removeFirst());
        }
        replacedOriginals.clear();
        removedOriginals.clear();
        removedOrders.clear();
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

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
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
