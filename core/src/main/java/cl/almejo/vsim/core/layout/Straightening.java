package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.UnionFind;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * «Enderezar cables moviendo partes» (spec 2026-11-06 §5.2): moves parts up or down so the wires between a driver and
 * its receivers run straight.
 *
 * <p>Every net with exactly one output pin (the driver) gives a pair for each input pin of another item on it. Only
 * the pairs that are horizontal when the stage starts (|dx| ≥ |dy|) and whose two pins sit on the left or right side
 * of their parts ({@link #isOnTopOrBottom}: a wire into a LED's bottom input is never straight) count, and the set
 * never changes while it runs: a part pushed far enough to make a pair vertical gains nothing by it. Each round
 * visits the movable parts left to right (then top, then id); a part's candidate shifts are the distinct dy that zero
 * one of its pairs, and only those that leave more of its pairs straight (dy = 0) than before count: a move that only
 * shortens the wires, with none more straight, is no gain (the user's hand test, 2026-11-07). They are tried most
 * pairs straightened first (ties: less Σ|dy|, then the smaller shift, then up), and the first that passes every rule
 * is taken: {@link Placement}'s, the order in its column ({@link Placement#keepsOrder}), no more wire corners than
 * before ({@link Placement#tryTidyingMove}) and the circuit's bounds grown by at most {@value #BOUNDS_SLACK} units
 * above and below. Every taken move strictly raises the number of straight pairs, so the rounds settle; at most
 * {@value #MAXIMUM_ROUNDS}.
 *
 * <p>A row the user lined up ({@link #rows}: parts side by side with the same top edge or the same centre) is never
 * broken: it is visited as one, its candidate shifts come from all its members' pairs, and it moves whole by one
 * shift only when every member's place passes the rules; otherwise nobody in it moves (the user, 2026-11-06).
 */
final class Straightening {

    /** Rounds of the local search at most (spec §5.2): real circuits settle in two or three. */
    static final int MAXIMUM_ROUNDS = 10;
    /**
     * How far above the topmost or below the bottommost part, in grid units, a part may go (spec §5.2): a long chain
     * pulled one way cannot drift the whole circuit.
     */
    static final int BOUNDS_SLACK = 2;
    /**
     * How far apart, in grid units, two parts side by side may be and still be one row: a row of flag LEDs stays a
     * row (the user's hand test, 2026-11-06).
     */
    static final int ROW_GAP = 6;

    /** What the stage did: the pairs that ended straight that were not, and how many moves it made. */
    record Result(int pairsStraightened, int moves) {
    }

    /** A driver's output pin and one receiver's input pin on its net. */
    record Pair(PinRef driver, PinRef receiver) {
    }

    private final CircuitDocument copy;
    private final ComponentTypeRegistry registry;
    private final Set<Set<PinRef>> originalNets;
    private final Supplier<LayoutItems> reader;
    private final CircuitTidyProgress progress;
    private final BooleanSupplier cancelled;
    /** The horizontal pairs, fixed when the stage starts. */
    private List<Pair> pairs = List.of();
    /** The pairs each component's pins are in. */
    private final Map<ElementId, List<Pair>> pairsByComponent = new HashMap<>();
    /** Where each pin of a pair is now. */
    private final Map<PinRef, GridPoint> pins = new HashMap<>();
    /** The pins on the top or bottom edge of their part's body: a wire reaches them from above or below. */
    private final Set<PinRef> topOrBottomPins = new LinkedHashSet<>();
    /** The ids of the items of each row, by the id of each of its items; fixed when the stage starts. */
    private final Map<ElementId, List<ElementId>> rowOf = new HashMap<>();
    private int topLimit;
    private int bottomLimit;
    private int moves;

    private Straightening(CircuitDocument copy, ComponentTypeRegistry registry, Set<Set<PinRef>> originalNets,
                          Supplier<LayoutItems> reader, CircuitTidyProgress progress, BooleanSupplier cancelled) {
        this.copy = copy;
        this.registry = registry;
        this.originalNets = originalNets;
        this.reader = reader;
        this.progress = progress;
        this.cancelled = cancelled;
    }

    /**
     * Straightens on {@code copy}, reading its items with {@code reader} at the start of every round and reporting
     * {@code STRAIGHTENING} with each round (of {@value #MAXIMUM_ROUNDS}).
     *
     * @param originalNets which pins are connected to which before the clean-up: every move keeps it
     * @return how many pairs ended straight that were not (dy ≠ 0 before, 0 after), and the moves made
     * @throws CancellationException when {@code cancelled} says yes (polled before every part)
     */
    static Result straighten(CircuitDocument copy, ComponentTypeRegistry registry, Set<Set<PinRef>> originalNets,
                          Supplier<LayoutItems> reader, CircuitTidyProgress progress, BooleanSupplier cancelled) {
        return new Straightening(copy, registry, originalNets, reader, progress, cancelled).run();
    }

    private Result run() {
        LayoutItems items = reader.get();
        readPins();
        pairs = horizontalPairs(items);
        if (pairs.isEmpty()) {
            return new Result(0, 0);
        }
        pairs.forEach(pair -> {
            pairsByComponent.computeIfAbsent(pair.driver().componentId(), id -> new ArrayList<>()).add(pair);
            pairsByComponent.computeIfAbsent(pair.receiver().componentId(), id -> new ArrayList<>()).add(pair);
        });
        setBoundsLimits(items);
        for (List<ElementId> row : rows(items)) {
            row.forEach(id -> rowOf.put(id, row));
        }
        Set<Pair> crookedAtStart = new LinkedHashSet<>();
        pairs.stream().filter(pair -> deltaY(pair) != 0).forEach(crookedAtStart::add);
        for (int round = 1; round <= MAXIMUM_ROUNDS; round++) {
            progress.reached(CircuitTidyProgress.Stage.STRAIGHTENING, round, MAXIMUM_ROUNDS);
            if (round > 1) {
                items = reader.get();
            }
            if (!runRound(items)) {
                break;
            }
        }
        return new Result((int) crookedAtStart.stream().filter(pair -> deltaY(pair) == 0).count(), moves);
    }

    /** One visit of every movable part or row; true when any moved. */
    private boolean runRound(LayoutItems items) {
        Placement placement = new Placement(copy, registry, items, originalNets);
        boolean anyMoved = false;
        for (List<LayoutItems.Item> unit : visitingOrder(items)) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            for (Candidate candidate : candidates(unit)) {
                if (fitsAround(placement, unit, candidate.shift())
                        && placement.tryTidyingMove(unit, candidate.shift())) {
                    unit.forEach(item -> shiftPins(item, candidate.shift()));
                    moves++;
                    anyMoved = true;
                    break;
                }
            }
        }
        return anyMoved;
    }

    /** True when every item of {@code unit}, moved by {@code shift}, stays within the limits and keeps its order. */
    private boolean fitsAround(Placement placement, List<LayoutItems.Item> unit, GridPoint shift) {
        for (LayoutItems.Item item : unit) {
            Bounds place = Placement.moved(item, shift);
            if (!withinLimits(place) || !placement.keepsOrder(item, place)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What moves, each a single part or a whole row, left to right (by its first item), then top to bottom, then by
     * id: only those with a pair, all of whose items are movable.
     */
    private List<List<LayoutItems.Item>> visitingOrder(LayoutItems items) {
        Comparator<LayoutItems.Item> order = Comparator.comparingInt((LayoutItems.Item item) -> item.bounds().x())
                .thenComparingInt(item -> item.bounds().y())
                .thenComparing(LayoutItems.Item::id, LayoutItems.ID_ORDER);
        Set<ElementId> visited = new LinkedHashSet<>();
        List<List<LayoutItems.Item>> units = new ArrayList<>();
        for (LayoutItems.Item item : items.items().stream().filter(item -> !item.isText()).sorted(order).toList()) {
            if (visited.contains(item.id())) {
                continue;
            }
            List<ElementId> ids = rowOf.getOrDefault(item.id(), List.of(item.id()));
            visited.addAll(ids);
            List<LayoutItems.Item> unit = ids.stream().map(items::item).flatMap(Optional::stream).toList();
            boolean whole = unit.size() == ids.size();
            boolean hasAPair = unit.stream().flatMap(member -> member.members().stream())
                    .anyMatch(pairsByComponent::containsKey);
            if (whole && hasAPair && unit.stream().allMatch(LayoutItems.Item::movable)) {
                units.add(unit);
            }
        }
        return units;
    }

    /**
     * The rows of {@code items}: groups of two or more parts chained by side-by-side pairs (the same top edge or the
     * same centre, their bodies' x ranges apart by at most {@value #ROW_GAP} units and not overlapping), each in id
     * order. Texts are in none.
     */
    static List<List<ElementId>> rows(LayoutItems items) {
        List<LayoutItems.Item> parts = items.items().stream().filter(item -> !item.isText()).toList();
        UnionFind<ElementId> joins = new UnionFind<>();
        parts.forEach(part -> joins.add(part.id()));
        for (int first = 0; first < parts.size(); first++) {
            for (int second = first + 1; second < parts.size(); second++) {
                if (sideBySide(parts.get(first).bodyBox(), parts.get(second).bodyBox())) {
                    joins.union(parts.get(first).id(), parts.get(second).id());
                }
            }
        }
        Map<ElementId, List<ElementId>> byRoot = new LinkedHashMap<>();
        parts.forEach(part -> byRoot.computeIfAbsent(joins.find(part.id()), root -> new ArrayList<>()).add(part.id()));
        List<List<ElementId>> rows = new ArrayList<>();
        for (List<ElementId> group : byRoot.values()) {
            if (group.size() > 1) {
                group.sort(LayoutItems.ID_ORDER);
                rows.add(List.copyOf(group));
            }
        }
        return rows;
    }

    /** True when the two bodies sit side by side in a row: same top or same centre, close but apart on x. */
    static boolean sideBySide(Bounds first, Bounds second) {
        boolean sameTop = first.y() == second.y();
        boolean sameCentre = 2 * first.y() + first.height() == 2 * second.y() + second.height();
        int gap = Math.max(first.x(), second.x()) - Math.min(first.maximumX(), second.maximumX());
        return (sameTop || sameCentre) && gap >= 0 && gap <= ROW_GAP;
    }

    /**
     * A vertical shift and what it changes: how many more pairs end straight, and how much less Σ|dy| (only a
     * tie-breaker between shifts that straighten as many).
     */
    private record Candidate(GridPoint shift, int straightGained, long distanceGained) {

        /** True when it leaves more pairs straight than before: shorter wires alone are no reason to move. */
        boolean straightensMore() {
            return straightGained > 0;
        }
    }

    /**
     * The shifts of {@code unit} (moved as one) that zero one of its pairs and leave more pairs straight, best first.
     */
    private List<Candidate> candidates(List<LayoutItems.Item> unit) {
        Set<ElementId> members = new LinkedHashSet<>();
        unit.forEach(item -> members.addAll(item.members()));
        List<Pair> own = ownPairs(members);
        Set<Integer> shifts = new LinkedHashSet<>();
        for (Pair pair : own) {
            int deltaY = deltaY(pair);
            if (deltaY != 0) {
                shifts.add(members.contains(pair.receiver().componentId()) ? -deltaY : deltaY);
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int shift : shifts) {
            Candidate candidate = scored(members, own, shift);
            if (candidate.straightensMore()) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::straightGained).reversed()
                .thenComparing(Comparator.comparingLong(Candidate::distanceGained).reversed())
                .thenComparingInt(candidate -> Math.abs(candidate.shift().y()))
                .thenComparingInt(candidate -> candidate.shift().y()));
        return candidates;
    }

    /** The pairs with one end in {@code members} and the other outside (a pair inside one item never moves). */
    private List<Pair> ownPairs(Set<ElementId> members) {
        Set<Pair> own = new LinkedHashSet<>();
        for (ElementId member : members) {
            for (Pair pair : pairsByComponent.getOrDefault(member, List.of())) {
                if (members.contains(pair.driver().componentId()) != members.contains(pair.receiver().componentId())) {
                    own.add(pair);
                }
            }
        }
        return List.copyOf(own);
    }

    /** {@code members} moved by {@code shift} on y: how many of {@code own} become straight and how much closer. */
    private Candidate scored(Set<ElementId> members, List<Pair> own, int shift) {
        int straightGained = 0;
        long distanceGained = 0;
        for (Pair pair : own) {
            int before = deltaY(pair);
            int after = members.contains(pair.receiver().componentId()) ? before + shift : before - shift;
            straightGained += (after == 0 ? 1 : 0) - (before == 0 ? 1 : 0);
            distanceGained += Math.abs(before) - Math.abs(after);
        }
        return new Candidate(new GridPoint(0, shift), straightGained, distanceGained);
    }

    private int deltaY(Pair pair) {
        return pins.get(pair.receiver()).y() - pins.get(pair.driver()).y();
    }

    private boolean withinLimits(Bounds place) {
        return place.y() >= topLimit && place.maximumY() <= bottomLimit;
    }

    private void shiftPins(LayoutItems.Item item, GridPoint shift) {
        for (ElementId member : item.members()) {
            for (Pair pair : pairsByComponent.getOrDefault(member, List.of())) {
                for (PinRef pin : List.of(pair.driver(), pair.receiver())) {
                    if (pin.componentId().equals(member)) {
                        pins.put(pin, pins.get(pin).plus(shift));
                    }
                }
            }
        }
    }

    // --- reading the pairs ---

    private void readPins() {
        for (Component component : copy.components()) {
            Optional<ComponentType> type = registry.findTypeOf(component);
            if (type.isEmpty()) {
                continue;
            }
            List<GridPoint> locations = type.get().pinLocations(component);
            Bounds body = type.get().bounds(component);
            for (int pinIndex = 0; pinIndex < locations.size(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                pins.put(pin, locations.get(pinIndex));
                if (isOnTopOrBottom(locations.get(pinIndex), body)) {
                    topOrBottomPins.add(pin);
                }
            }
        }
    }

    /**
     * The driver–receiver pairs of the original nets that are horizontal now, both pins on a side of their part (left
     * or right, not top or bottom), in a stable order.
     */
    private List<Pair> horizontalPairs(LayoutItems items) {
        Map<ElementId, ElementId> itemOf = new HashMap<>();
        items.items().forEach(item -> item.members().forEach(member -> itemOf.put(member, item.id())));
        List<Pair> found = new ArrayList<>();
        for (Set<PinRef> net : originalNets) {
            List<PinRef> drivers = net.stream().filter(pin -> hasDirection(pin, PinDirection.OUTPUT)).toList();
            if (drivers.size() != 1) {
                continue;
            }
            PinRef driver = drivers.get(0);
            for (PinRef receiver : net) {
                boolean otherItem = itemOf.containsKey(receiver.componentId())
                        && !itemOf.get(receiver.componentId()).equals(itemOf.get(driver.componentId()));
                if (otherItem && hasDirection(receiver, PinDirection.INPUT) && isHorizontal(driver, receiver)) {
                    found.add(new Pair(driver, receiver));
                }
            }
        }
        found.sort(Comparator.comparing((Pair pair) -> pair.driver().componentId(), LayoutItems.ID_ORDER)
                .thenComparingInt(pair -> pair.driver().pinIndex())
                .thenComparing(pair -> pair.receiver().componentId(), LayoutItems.ID_ORDER)
                .thenComparingInt(pair -> pair.receiver().pinIndex()));
        return List.copyOf(found);
    }

    /**
     * True when {@code location} lies on the top or bottom edge of {@code body} (or beyond it), strictly between its
     * left and right edges: like a LED's input, such a pin is reached from above or below, so moving its part to its
     * driver's height never makes the wire straight, only longer (riscv-maniqui.vsim's flag LEDs, 2026-11-07). A pin
     * on a corner counts as on a side.
     */
    static boolean isOnTopOrBottom(GridPoint location, Bounds body) {
        boolean between = location.x() > body.x() && location.x() < body.maximumX();
        return between && (location.y() <= body.y() || location.y() >= body.maximumY());
    }

    private boolean isHorizontal(PinRef driver, PinRef receiver) {
        if (topOrBottomPins.contains(driver) || topOrBottomPins.contains(receiver)) {
            return false;
        }
        GridPoint from = pins.get(driver);
        GridPoint to = pins.get(receiver);
        int deltaX = Math.abs(to.x() - from.x());
        return deltaX != 0 && deltaX >= Math.abs(to.y() - from.y());
    }

    /** True for a pin of a known part with that direction and its own width (tunnel pins neither drive nor read). */
    private boolean hasDirection(PinRef pin, PinDirection direction) {
        if (!pins.containsKey(pin)) {
            return false;
        }
        Optional<Component> component = copy.component(pin.componentId());
        Optional<ComponentType> type = component.flatMap(registry::findTypeOf);
        if (type.isEmpty() || pin.pinIndex() >= type.get().pinCount()) {
            return false;
        }
        PinSpec spec = type.get().pin(pin.pinIndex());
        return spec.direction() == direction && !spec.isAdaptiveWidth();
    }

    private void setBoundsLimits(LayoutItems items) {
        Optional<Bounds> all = items.items().stream().filter(item -> !item.isText()).map(LayoutItems.Item::bounds)
                .reduce(Bounds::union);
        topLimit = all.map(Bounds::y).orElse(0) - BOUNDS_SLACK;
        bottomLimit = all.map(Bounds::maximumY).orElse(0) + BOUNDS_SLACK;
    }
}
