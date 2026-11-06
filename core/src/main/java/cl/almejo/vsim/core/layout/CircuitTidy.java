package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.WireTidy;

import java.util.ArrayList;
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
 * applied on the copy one item at a time, in id order, with the wires following ({@link Placement}: the rules every stage shares).
 * A move that would change which pins are connected (a pin landing on a foreign wire or pin) is undone and other
 * places are tried: the other axis, the opposite side, then one and two units further; with none left the item stays
 * and is counted in {@link CircuitTidyPlan#notSeparated()}.
 *
 * <p>Parts are separated first, in up to {@value #MAXIMUM_PART_ROUNDS} rounds, each reading the boxes (and the wires
 * through bodies) again from the copy as the moves left it; then the blocks of texts that still cover a body move off
 * it. Texts never push parts, and texts over texts are left alone.
 */
public final class CircuitTidy {

    /**
     * How many times the items whose every place was refused are tried again after the others moved (a place may be
     * refused only because a neighbour that is about to leave still sits there).
     */
    private static final int MAXIMUM_PLACING_PASSES = 3;
    /**
     * How many times the parts are separated, each time from the boxes and wires the moves before left: a move drags
     * wires along, which may now run through a body. Two or three rounds settle real circuits.
     */
    private static final int MAXIMUM_PART_ROUNDS = 3;
    /**
     * How much further than its own size, in grid units, a fallback place is looked for on one axis: an item moves at
     * most about its size to find room. Further is no longer «the least it can» (spec §2 decision 2: the circuit stays
     * compact); the item is reported instead.
     */
    private static final int FALLBACK_SLACK_UNITS = 8;

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
        return plan(document, registry, scope, options, texts, cancelled, progress, Separator.REAL);
    }

    /**
     * Like {@link #plan(CircuitDocument, ComponentTypeRegistry, Set, CircuitTidyOptions, TextBoxes, BooleanSupplier,
     * IntConsumer)}, reporting every stage to {@code progress} ({@link CircuitTidyProgress.Stage}): {@code SEPARATING}
     * with each round of parts (of at most {@value #MAXIMUM_PART_ROUNDS}), {@code MOVING} with each part placed in the
     * round's first pass (of the parts to place), {@code MOVING_TEXTS} with each block of texts (of the blocks over a
     * body) and {@code WIRES} with each round of the wire rules (total unknown, 0). No count starts again within one
     * round of its stage.
     */
    public static Optional<CircuitTidyPlan> plan(CircuitDocument document, ComponentTypeRegistry registry,
                                                 Set<ElementId> scope, CircuitTidyOptions options, TextBoxes texts,
                                                 BooleanSupplier cancelled, CircuitTidyProgress progress) {
        Objects.requireNonNull(progress, "progress");
        return run(document, registry, scope, options, texts, cancelled, progress, Separator.REAL);
    }

    /** How boxes are moved apart: {@link OverlapSeparation#separate} outside tests. */
    @FunctionalInterface
    interface Separator {

        /** The real geometry. */
        Separator REAL = OverlapSeparation::separate;

        Separation separate(List<LayoutBox> boxes, BooleanSupplier cancelled);
    }

    /** Like the public {@code plan}, with the geometry given (tests use a simple stand-in). */
    static Optional<CircuitTidyPlan> plan(CircuitDocument document, ComponentTypeRegistry registry,
                                          Set<ElementId> scope, CircuitTidyOptions options, TextBoxes texts,
                                          BooleanSupplier cancelled, IntConsumer progress, Separator separator) {
        Objects.requireNonNull(progress, "progress");
        CircuitTidyProgress wireRounds = (stage, done, total) -> {
            if (stage == CircuitTidyProgress.Stage.WIRES) {
                progress.accept(done);
            }
        };
        return run(document, registry, scope, options, texts, cancelled, wireRounds, separator);
    }

    private static Optional<CircuitTidyPlan> run(CircuitDocument document, ComponentTypeRegistry registry,
                                                 Set<ElementId> scope, CircuitTidyOptions options, TextBoxes texts,
                                                 BooleanSupplier cancelled, CircuitTidyProgress progress,
                                                 Separator separator) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(texts, "texts");
        if (cancelled.getAsBoolean()) {
            return Optional.empty();
        }
        CircuitDocument copy = document.copy();
        Set<ElementId> notSeparated = Set.of();
        if (options.separateOverlaps()) {
            try {
                notSeparated = new Separating(copy, registry, scope, texts, cancelled, progress, separator).run();
            } catch (CancellationException cancellation) {
                return Optional.empty();
            }
        }
        Optional<TidyPlan> wires = WireTidy.plan(copy, registry, wiresInScope(document, copy, scope),
                options.wires(), cancelled, round -> progress.reached(CircuitTidyProgress.Stage.WIRES, round, 0));
        if (wires.isEmpty()) {
            return Optional.empty();
        }
        new TidyWiresCommand(wires.get()).apply(copy);
        return Optional.of(new CircuitTidyPlan(copy, partsMoved(document, copy), textsMoved(document, copy),
                notSeparated, wires.get()));
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

    /** The components whose place differs between the two documents. */
    private static int partsMoved(CircuitDocument before, CircuitDocument after) {
        int moved = 0;
        for (Component component : after.components()) {
            if (!before.component(component.id()).map(Component::position).equals(Optional.of(component.position()))) {
                moved++;
            }
        }
        return moved;
    }

    /** The free texts and sticky notes whose place differs between the two documents. */
    private static int textsMoved(CircuitDocument before, CircuitDocument after) {
        int moved = 0;
        for (TextLabel label : after.labels()) {
            if (!before.label(label.id()).map(TextLabel::position).equals(Optional.of(label.position()))) {
                moved++;
            }
        }
        for (StickyNote note : after.stickyNotes()) {
            if (!before.stickyNote(note.id()).map(StickyNote::position).equals(Optional.of(note.position()))) {
                moved++;
            }
        }
        return moved;
    }

    /** The separation of one run on the copy: the rounds of parts, then the texts, then what is left counted. */
    private static final class Separating {

        private final CircuitDocument copy;
        private final ComponentTypeRegistry registry;
        private final Set<ElementId> scope;
        private final TextBoxes texts;
        private final BooleanSupplier cancelled;
        private final CircuitTidyProgress progress;
        private final Separator separator;
        /** Which pins are connected to which before any move: every move must keep it. */
        private final Set<Set<PinRef>> originalNets;
        /** The items whose every place was refused in some round: counted first, when they still cover something. */
        private final Set<ElementId> refused = new LinkedHashSet<>();

        Separating(CircuitDocument copy, ComponentTypeRegistry registry, Set<ElementId> scope, TextBoxes texts,
                   BooleanSupplier cancelled, CircuitTidyProgress progress, Separator separator) {
            this.copy = copy;
            this.registry = registry;
            this.scope = scope;
            this.texts = texts;
            this.cancelled = cancelled;
            this.progress = progress;
            this.separator = separator;
            this.originalNets = Placement.netsOf(copy, registry);
        }

        /** Separates and returns what still covers something (items partly in scope only). */
        Set<ElementId> run() {
            // The items are read again only after something moved: reading them is a good part of the time.
            LayoutItems items = currentItems();
            for (int round = 0; round < MAXIMUM_PART_ROUNDS; round++) {
                progress.reached(CircuitTidyProgress.Stage.SEPARATING, round + 1, MAXIMUM_PART_ROUNDS);
                if (!new Round(items).place(items.separationGroups())) {
                    break;
                }
                items = currentItems();
            }
            if (new Round(items).placeTexts()) {
                items = currentItems();
            }
            return whatStillOverlaps(items);
        }

        private LayoutItems currentItems() {
            checkCancelled();
            return LayoutItems.of(copy, registry, scope, texts);
        }

        /**
         * The items in scope that still cover something at the end, as the user sees it (spec §4.1: bodies under
         * bodies, labels or texts; closer than the gap is fine): an overlap is counted once (an item whose every
         * partner is already counted is not), and a part still crossed by a foreign wire counts too.
         */
        private Set<ElementId> whatStillOverlaps(LayoutItems items) {
            List<LayoutItems.Item> refusedFirst = new ArrayList<>();
            items.items().stream().filter(item -> refused.contains(item.id())).forEach(refusedFirst::add);
            items.items().stream().filter(item -> !refused.contains(item.id())).forEach(refusedFirst::add);
            Set<ElementId> overlapping = new LinkedHashSet<>();
            for (LayoutItems.Item item : refusedFirst) {
                if (!item.partlyInScope()) {
                    continue;
                }
                boolean newOverlap = items.coveredItems(item, Map.of()).stream()
                        .anyMatch(other -> !overlapping.contains(other));
                if (newOverlap || !item.isText() && LayoutItems.crossedByForeignWire(copy, registry,
                        item.members())) {
                    overlapping.add(item.id());
                }
            }
            return overlapping;
        }

        private void checkCancelled() {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
        }

        /** One round: the separation's deltas for some groups of boxes, applied item by item on the copy. */
        private final class Round {

            private final LayoutItems items;
            private final Placement placement;

            Round(LayoutItems items) {
                this.items = items;
                this.placement = new Placement(copy, registry, items, originalNets);
            }

            /**
             * Moves every block of texts that covers a body, in id order, to the nearest place where it covers none:
             * along its own row first (right before left at the same distance), then up or down, at most its own
             * size plus {@value #FALLBACK_SLACK_UNITS} away, and not onto other writing (a text or a label) it was not
             * on already. Texts only make way for bodies, so no separation is needed: a block never pushes anything,
             * and its lines move as one, keeping their order. True when any moved.
             */
            boolean placeTexts() {
                boolean anyMoved = false;
                List<LayoutItems.Item> covering = items.items().stream().filter(item -> item.isText()
                        && item.movable() && !items.coveredBodies(item, Map.of()).isEmpty()).toList();
                for (int index = 0; index < covering.size(); index++) {
                    LayoutItems.Item block = covering.get(index);
                    progress.reached(CircuitTidyProgress.Stage.MOVING_TEXTS, index + 1, covering.size());
                    checkCancelled();
                    Optional<GridPoint> shift = nearestFree(block, true, List.of(1, -1))
                            .or(() -> nearestFree(block, false, List.of(1, -1)));
                    if (shift.isPresent() && placement.tryMove(block, shift.get())) {
                        anyMoved = true;
                    } else {
                        refused.add(block.id());
                    }
                }
                return anyMoved;
            }

            /** Separates {@code groups} and moves what it says; true when anything moved. */
            boolean place(List<List<LayoutBox>> groups) {
                Map<ElementId, GridPoint> deltas = plannedDeltas(groups);
                List<LayoutItems.Item> pending = new ArrayList<>();
                for (LayoutItems.Item item : items.items()) {
                    GridPoint delta = deltas.get(item.id());
                    placement.plan(item.id(), delta == null ? item.bounds() : Placement.moved(item, delta));
                    if (delta != null) {
                        pending.add(item);
                    }
                }
                return placeAll(pending, deltas);
            }

            /** The separation's delta for each movable item it moves, every area at a time; zero deltas left out. */
            private Map<ElementId, GridPoint> plannedDeltas(List<List<LayoutBox>> groups) {
                Map<ElementId, GridPoint> deltas = new LinkedHashMap<>();
                for (List<LayoutBox> group : groups) {
                    checkCancelled();
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

            /**
             * Places the items in id order; those refused everywhere are tried again after the others moved (quietly:
             * the count of the first pass does not start again). True when any moved.
             */
            private boolean placeAll(List<LayoutItems.Item> pending, Map<ElementId, GridPoint> deltas) {
                List<LayoutItems.Item> waiting = new ArrayList<>(pending);
                boolean anyMoved = false;
                for (int pass = 0; pass < MAXIMUM_PLACING_PASSES && !waiting.isEmpty(); pass++) {
                    List<LayoutItems.Item> refusedThisPass = new ArrayList<>();
                    for (int index = 0; index < waiting.size(); index++) {
                        LayoutItems.Item item = waiting.get(index);
                        if (pass == 0) {
                            progress.reached(CircuitTidyProgress.Stage.MOVING, index + 1, waiting.size());
                        }
                        if (placeOne(item, deltas.get(item.id()))) {
                            anyMoved = true;
                        } else {
                            refusedThisPass.add(item);
                        }
                    }
                    if (refusedThisPass.size() == waiting.size()) {
                        break;
                    }
                    waiting = refusedThisPass;
                }
                for (LayoutItems.Item item : waiting) {
                    placement.plan(item.id(), item.bounds());
                    refused.add(item.id());
                }
                return anyMoved;
            }

            /** Moves {@code item} to the first place that keeps it clear and the circuit's meaning; false when none. */
            private boolean placeOne(LayoutItems.Item item, GridPoint delta) {
                for (Supplier<Optional<GridPoint>> candidate : candidates(item, delta)) {
                    checkCancelled();
                    Optional<GridPoint> shift = candidate.get();
                    if (shift.isPresent() && placement.tryMove(item, shift.get())) {
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

            /**
             * The smallest shift along one axis, in the directions given (in that order at each distance), that is
             * free; at most the item's own size on that axis plus {@value #FALLBACK_SLACK_UNITS}.
             */
            private Optional<GridPoint> nearestFree(LayoutItems.Item item, boolean alongX, List<Integer> directions) {
                int limit = (alongX ? item.bounds().width() : item.bounds().height()) + FALLBACK_SLACK_UNITS;
                for (int distance = 1; distance <= limit; distance++) {
                    for (int direction : directions) {
                        int step = direction * distance;
                        GridPoint shift = alongX ? new GridPoint(step, 0) : new GridPoint(0, step);
                        if (placement.isClear(item, Placement.moved(item, shift))) {
                            return Optional.of(shift);
                        }
                    }
                }
                return Optional.empty();
            }
        }
    }
}
