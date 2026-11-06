package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.WireTidy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * «Ordenar circuito» (spec 2026-11-06 §4): separates parts, labels and notes that cover each other, keeping what the
 * circuit means, then runs the wire rules. Pure: it works on a copy and never changes {@code document}.
 *
 * <p>The boxes come from {@link LayoutItems}; {@link OverlapSeparation} says how far each one moves. The moves are
 * applied on the copy one item at a time, in id order, with the wires following ({@link MoveWithWiresCommand#of}).
 * A move that would change which pins are connected (a pin landing on a foreign wire or pin) is undone and other
 * places are tried: the other axis, the opposite side, then one and two units further; with none left the item stays
 * and is counted in {@link CircuitTidyPlan#notSeparated()}.
 */
public final class CircuitTidy {

    /**
     * How many times the items whose every place was refused are tried again after the others moved (a place may be
     * refused only because a neighbour that is about to leave still sits there).
     */
    private static final int MAXIMUM_PLACING_PASSES = 3;
    /**
     * How far, in grid units, a fallback place is looked for on one axis. Further than this is no longer «the least
     * it can» (spec §2 decision 2: the circuit stays compact); the item is reported instead.
     */
    private static final int FALLBACK_SEARCH_UNITS = 64;

    private CircuitTidy() {
    }

    /**
     * @param scope    ids of the elements allowed to change (components, texts, notes, wires)
     * @param progress gets the number of each round of the wire rules as it starts
     * @return empty when {@code cancelled} said yes (it is polled before every round and every move)
     */
    public static Optional<CircuitTidyPlan> plan(CircuitDocument document, ComponentTypeRegistry registry,
                                                 Set<ElementId> scope, CircuitTidyOptions options, TextBoxes texts,
                                                 BooleanSupplier cancelled, IntConsumer progress) {
        return plan(document, registry, scope, options, texts, cancelled, progress, OverlapSeparation::separate);
    }

    /**
     * Like {@link #plan(CircuitDocument, ComponentTypeRegistry, Set, CircuitTidyOptions, TextBoxes, BooleanSupplier,
     * IntConsumer)}, reporting every stage (separation rounds, moves applied, wire rounds) to {@code progress}.
     */
    public static Optional<CircuitTidyPlan> plan(CircuitDocument document, ComponentTypeRegistry registry,
                                                 Set<ElementId> scope, CircuitTidyOptions options, TextBoxes texts,
                                                 BooleanSupplier cancelled, CircuitTidyProgress progress) {
        // only the wire rounds are reported for now; the separating and moving stages come with the core improvements
        return plan(document, registry, scope, options, texts, cancelled,
                round -> progress.reached(CircuitTidyProgress.Stage.WIRES, round, 0));
    }

    /** How boxes are moved apart: {@link OverlapSeparation#separate} outside tests. */
    @FunctionalInterface
    interface Separator {
        Separation separate(List<LayoutBox> boxes, BooleanSupplier cancelled);
    }

    /** Like the public {@code plan}, with the geometry given (tests use a simple stand-in). */
    static Optional<CircuitTidyPlan> plan(CircuitDocument document, ComponentTypeRegistry registry,
                                          Set<ElementId> scope, CircuitTidyOptions options, TextBoxes texts,
                                          BooleanSupplier cancelled, IntConsumer progress, Separator separator) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(texts, "texts");
        if (cancelled.getAsBoolean()) {
            return Optional.empty();
        }
        CircuitDocument copy = document.copy();
        Placement placement = new Placement(copy, registry, cancelled);
        if (options.separateOverlaps()) {
            try {
                placement.separate(LayoutItems.of(document, registry, scope, texts), separator);
            } catch (CancellationException cancellation) {
                return Optional.empty();
            }
        }
        Optional<TidyPlan> wires = WireTidy.plan(copy, registry, wiresInScope(document, copy, scope),
                options.wires(), cancelled, progress);
        if (wires.isEmpty()) {
            return Optional.empty();
        }
        new TidyWiresCommand(wires.get()).apply(copy);
        return Optional.of(new CircuitTidyPlan(copy, placement.partsMoved, placement.textsMoved,
                placement.notSeparated, wires.get()));
    }

    /** The wires of {@code copy} the wire rules may change: those in scope plus the ones the moves made or changed. */
    private static Set<ElementId> wiresInScope(CircuitDocument document, CircuitDocument copy, Set<ElementId> scope) {
        Set<ElementId> wires = new LinkedHashSet<>();
        for (Wire wire : copy.wires()) {
            if (scope.contains(wire.id()) || !document.wire(wire.id()).equals(Optional.of(wire))) {
                wires.add(wire.id());
            }
        }
        return wires;
    }

    /** The moves of one run on the copy, with what they moved and what they could not separate. */
    private static final class Placement {

        private final CircuitDocument copy;
        private final ComponentTypeRegistry registry;
        private final BooleanSupplier cancelled;
        /** Which pins are connected to which before any move: every move must keep it. */
        private Set<Set<PinRef>> originalNets;
        /** Where each item is or will be: its planned place until it is placed or refused. */
        private final Map<ElementId, Bounds> places = new HashMap<>();
        private final Set<ElementId> notSeparated = new LinkedHashSet<>();
        private LayoutItems items;
        private int partsMoved;
        private int textsMoved;

        Placement(CircuitDocument copy, ComponentTypeRegistry registry, BooleanSupplier cancelled) {
            this.copy = copy;
            this.registry = registry;
            this.cancelled = cancelled;
        }

        void separate(LayoutItems layoutItems, Separator separator) {
            items = layoutItems;
            originalNets = nets();
            Map<ElementId, GridPoint> deltas = plannedDeltas(separator);
            List<LayoutItems.Item> pending = new ArrayList<>();
            for (LayoutItems.Item item : items.items()) {
                GridPoint delta = deltas.get(item.id());
                places.put(item.id(), delta == null ? item.bounds() : moved(item, delta));
                if (delta != null) {
                    pending.add(item);
                }
            }
            placeAll(pending, deltas);
            countWhatStillOverlaps();
        }

        /** The separation's delta for each movable item it moves, every area at a time; zero deltas left out. */
        private Map<ElementId, GridPoint> plannedDeltas(Separator separator) {
            Map<ElementId, GridPoint> deltas = new LinkedHashMap<>();
            for (List<LayoutBox> group : items.separationGroups()) {
                checkCancelled();
                if (!anyMovableOverlaps(group)) {
                    continue;
                }
                Separation separation = separator.separate(group, cancelled);
                for (LayoutBox box : group) {
                    GridPoint delta = separation.deltas().get(box.id());
                    if (box.movable() && delta != null && !delta.equals(GridPoint.ORIGIN)) {
                        deltas.put(box.id(), delta);
                    }
                }
            }
            return deltas;
        }

        private static boolean anyMovableOverlaps(List<LayoutBox> group) {
            for (LayoutBox box : group) {
                if (!box.movable()) {
                    continue;
                }
                for (LayoutBox other : group) {
                    if (other != box && LayoutItems.overlaps(box.bounds(), other.bounds(),
                            Math.max(box.gap(), other.gap()))) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Places the items in id order; those refused everywhere are tried again after the others moved. */
        private void placeAll(List<LayoutItems.Item> pending, Map<ElementId, GridPoint> deltas) {
            List<LayoutItems.Item> waiting = new ArrayList<>(pending);
            for (int pass = 0; pass < MAXIMUM_PLACING_PASSES && !waiting.isEmpty(); pass++) {
                List<LayoutItems.Item> refused = new ArrayList<>();
                for (LayoutItems.Item item : waiting) {
                    if (!place(item, deltas.get(item.id()))) {
                        refused.add(item);
                    }
                }
                if (refused.size() == waiting.size()) {
                    break;
                }
                waiting = refused;
            }
            for (LayoutItems.Item item : waiting) {
                places.put(item.id(), item.bounds());
                notSeparated.add(item.id());
            }
        }

        /** Moves {@code item} to the first place that keeps it clear and the circuit's meaning; false when none. */
        private boolean place(LayoutItems.Item item, GridPoint delta) {
            for (Supplier<Optional<GridPoint>> candidate : candidates(item, delta)) {
                checkCancelled();
                Optional<GridPoint> shift = candidate.get();
                if (shift.isPresent() && tryMove(item, shift.get())) {
                    places.put(item.id(), moved(item, shift.get()));
                    partsMoved += item.parts();
                    textsMoved += item.texts();
                    return true;
                }
            }
            return false;
        }

        /**
         * The places to try, in order (spec §4.4): the separation's, the nearest free one on the other axis, the
         * nearest free one on the opposite side, then the first one lengthened by one and by two units. Each is
         * computed only when the one before failed.
         */
        private List<Supplier<Optional<GridPoint>>> candidates(LayoutItems.Item item, GridPoint delta) {
            boolean alongX = Math.abs(delta.x()) >= Math.abs(delta.y());
            int direction = Integer.signum(alongX ? delta.x() : delta.y());
            return List.of(
                    () -> Optional.of(delta),
                    () -> nearestFree(item, !alongX, List.of(1, -1)),
                    () -> nearestFree(item, alongX, List.of(-direction)),
                    () -> Optional.of(lengthened(delta, alongX, direction)),
                    () -> Optional.of(lengthened(delta, alongX, 2 * direction)));
        }

        private static GridPoint lengthened(GridPoint delta, boolean alongX, int extra) {
            return alongX ? delta.translate(extra, 0) : delta.translate(0, extra);
        }

        /** The smallest shift along one axis, in the directions given (in that order at each distance), that is free. */
        private Optional<GridPoint> nearestFree(LayoutItems.Item item, boolean alongX, List<Integer> directions) {
            for (int distance = 1; distance <= FALLBACK_SEARCH_UNITS; distance++) {
                for (int direction : directions) {
                    int step = direction * distance;
                    GridPoint shift = alongX ? new GridPoint(step, 0) : new GridPoint(0, step);
                    if (items.isFree(item, moved(item, shift), places)) {
                        return Optional.of(shift);
                    }
                }
            }
            return Optional.empty();
        }

        /**
         * Applies the move on the copy when the place is clear (of items, walls, frames and foreign wires) and keeps it
         * when every pin stays in the net it was in; otherwise undoes it.
         */
        private boolean tryMove(LayoutItems.Item item, GridPoint shift) {
            if (shift.equals(GridPoint.ORIGIN) || !items.isFree(item, moved(item, shift), places)
                    || LayoutItems.crossedByForeignWire(copy, registry, item.members(), shift.x(), shift.y())) {
                return false;
            }
            MoveWithWiresCommand move = MoveWithWiresCommand.of(copy, registry, new LinkedHashSet<>(item.members()),
                    shift.x(), shift.y());
            move.apply(copy);
            if (nets().equals(originalNets)) {
                return true;
            }
            move.revert(copy);
            return false;
        }

        /**
         * Adds the items in scope that still overlap something at the end: an overlap is counted once (an item whose
         * every partner is already counted is not), and a part still crossed by a foreign wire counts too.
         */
        private void countWhatStillOverlaps() {
            for (LayoutItems.Item item : items.items()) {
                if (notSeparated.contains(item.id()) || !item.partlyInScope()) {
                    continue;
                }
                List<ElementId> overlapped = items.overlappedItems(item, places.get(item.id()), places);
                boolean newOverlap = overlapped.stream().anyMatch(other -> !notSeparated.contains(other));
                if (newOverlap || LayoutItems.crossedByForeignWire(copy, registry, item.members(), 0, 0)) {
                    notSeparated.add(item.id());
                }
            }
        }

        private Set<Set<PinRef>> nets() {
            return Connectivity.of(copy.wires(), copy, registry).pinNets();
        }

        private static Bounds moved(LayoutItems.Item item, GridPoint shift) {
            return item.bounds().translate(shift.x(), shift.y());
        }

        private void checkCancelled() {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
        }
    }
}
