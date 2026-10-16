package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Plans how the wires follow a move (spec M6.7 §2–§4). Pure and deterministic: it reads the document and changes
 * nothing but the id counter (new wires reserve their ids).
 *
 * <p>A <em>moved point</em> is a pin of a moved component or any point of a moved wire. A wire that is not moved but
 * has both ends on moved points moves whole (repeated until nothing changes, so a wire ending on a wire moved whole
 * follows too). A wire with exactly one end {@code P} on a moved point is <em>attached</em>; with {@code F} its other
 * end it stretches by rule 1 (along its axis), 2 (a simple corner at {@code F}: the neighbour stretches), 3 (an L
 * keeping the wire's direction at {@code F}) or 4 ({@code F} free: moves whole). Rules 2 and 4 move the wire sideways,
 * so they apply only when nothing is joined to the wire's interior; no rule ever drops a stretch of a wire that holds a
 * junction (the wire keeps covering it).
 *
 * <p>Two things the rules alone would break are repaired with a short bridge (one wire, or an L) from the old point to
 * the moved one: a moved pin or moved wire end that touched a fixed pin or the interior of a fixed wire, and a fixed
 * pin or wire end on a moved wire's interior. A bridge is added only when the two are not on one net after the move.
 *
 * <p>Accidental contacts are points where things from two nets of before the move meet. For an L that causes one,
 * the other orientation (vertical first for a horizontal wire) is used when it is clean.
 */
public final class WireFollow {

    private static final String PLACEHOLDER_PREFIX = "follow-new-";

    private final CircuitDocument document;
    private final int deltaX;
    private final int deltaY;
    private final List<Wire> documentWires;
    private final WireGraph before;
    private final PointNets netsBefore;
    private final Map<PinRef, GridPoint> pinsAfter = new LinkedHashMap<>();
    private final Map<GridPoint, List<PinRef>> pinsAtPointAfter = new HashMap<>();
    private final Map<PinRef, String> pinNames = new HashMap<>();
    private final Set<ElementId> movedComponentIds = new HashSet<>();
    /** Selected wires and wires moved whole, in document order. */
    private final Set<ElementId> movedWireIds = new LinkedHashSet<>();
    private final Set<ElementId> movedWholeWireIds = new LinkedHashSet<>();
    private final List<Wire> attachedWires = new ArrayList<>();
    /** The end of each attached wire that is on a moved point. */
    private final Map<ElementId, GridPoint> attachedPoints = new HashMap<>();
    private final List<Junction> junctions = new ArrayList<>();

    private WireFollow(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> movedIds, int deltaX,
                       int deltaY) {
        this.document = document;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.documentWires = document.wires();
        Map<GridPoint, List<PinRef>> pinsAtPointBefore = new HashMap<>();
        Map<PinRef, GridPoint> pinsBefore = new LinkedHashMap<>();
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            boolean moved = movedIds.contains(component.id());
            if (moved) {
                movedComponentIds.add(component.id());
            }
            Component placed = moved ? component.translate(deltaX, deltaY) : component;
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                GridPoint location = type.pinLocation(component, pinIndex);
                pinsBefore.put(pin, location);
                pinsAtPointBefore.computeIfAbsent(location, point -> new ArrayList<>()).add(pin);
                GridPoint locationAfter = type.pinLocation(placed, pinIndex);
                pinsAfter.put(pin, locationAfter);
                pinsAtPointAfter.computeIfAbsent(locationAfter, point -> new ArrayList<>()).add(pin);
                pinNames.put(pin, type.pin(pinIndex).name());
            }
        }
        before = WireGraph.of(documentWires, pinsAtPointBefore);
        netsBefore = new PointNets(pinsBefore, documentWires);
        for (Wire wire : documentWires) {
            if (movedIds.contains(wire.id())) {
                movedWireIds.add(wire.id());
            }
        }
        findWiresMovedWhole(pinsAtPointBefore);
        findJunctions(pinsBefore);
    }

    /** {@link #plan(CircuitDocument, ComponentTypeRegistry, Set, int, int, Supplier)} with the document's ids. */
    public static FollowPlan plan(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> movedIds,
                                  int deltaX, int deltaY) {
        return plan(document, registry, movedIds, deltaX, deltaY, document::newWireId);
    }

    /**
     * How the wires follow the move of {@code movedIds} by the delta; new wires take their ids from
     * {@code newWireIds}, in order (only for the wires the final plan adds). Ids that are not in the document, labels
     * and frames move no wire.
     */
    public static FollowPlan plan(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> movedIds,
                                  int deltaX, int deltaY, Supplier<ElementId> newWireIds) {
        if (deltaX == 0 && deltaY == 0) {
            return FollowPlan.EMPTY;
        }
        return new WireFollow(document, registry, movedIds, deltaX, deltaY).plan(newWireIds);
    }

    // --- what moves ---

    private void findWiresMovedWhole(Map<GridPoint, List<PinRef>> pinsAtPointBefore) {
        Set<GridPoint> movedPinPoints = new HashSet<>();
        pinsAtPointBefore.forEach((point, pins) -> {
            if (pins.stream().anyMatch(pin -> movedComponentIds.contains(pin.componentId()))) {
                movedPinPoints.add(point);
            }
        });
        boolean changed = true;
        while (changed) {
            changed = false;
            WireGraph movedGraph = WireGraph.of(wiresOf(movedWireIds), Map.of());
            for (Wire wire : documentWires) {
                if (!movedWireIds.contains(wire.id())
                        && isMovedPoint(wire.start(), movedPinPoints, movedGraph)
                        && isMovedPoint(wire.end(), movedPinPoints, movedGraph)) {
                    movedWireIds.add(wire.id());
                    movedWholeWireIds.add(wire.id());
                    changed = true;
                }
            }
        }
        WireGraph movedGraph = WireGraph.of(wiresOf(movedWireIds), Map.of());
        for (Wire wire : documentWires) {
            if (!movedWireIds.contains(wire.id())
                    && (isMovedPoint(wire.start(), movedPinPoints, movedGraph)
                    || isMovedPoint(wire.end(), movedPinPoints, movedGraph))) {
                attachedWires.add(wire);
            }
        }
        // keep the selected-and-whole set in document order
        List<ElementId> ordered = documentWires.stream().map(Wire::id).filter(movedWireIds::contains).toList();
        movedWireIds.clear();
        movedWireIds.addAll(ordered);
        for (Wire wire : attachedWires) {
            boolean startMoved = isMovedPoint(wire.start(), movedPinPoints, movedGraph);
            attachedPoints.put(wire.id(), startMoved ? wire.start() : wire.end());
        }
    }

    private static boolean isMovedPoint(GridPoint point, Set<GridPoint> movedPinPoints, WireGraph movedGraph) {
        return movedPinPoints.contains(point) || !movedGraph.wiresThrough(point).isEmpty();
    }

    private List<Wire> wiresOf(Set<ElementId> ids) {
        return documentWires.stream().filter(wire -> ids.contains(wire.id())).toList();
    }

    private boolean isMovedPin(PinRef pin) {
        return movedComponentIds.contains(pin.componentId());
    }

    /** Places where something that moves touched something that stays, for the bridges. */
    private void findJunctions(Map<PinRef, GridPoint> pinsBefore) {
        Set<Junction> found = new LinkedHashSet<>();
        pinsBefore.forEach((pin, point) -> {
            if (isMovedPin(pin)) {
                addFixedAt(found, point, new PinItem(pin), true);
            }
        });
        for (Wire wire : wiresOf(movedWireIds)) {
            WireItem moved = new WireItem(wire.id());
            addFixedAt(found, wire.start(), moved, true);
            addFixedAt(found, wire.end(), moved, true);
            for (GridPoint point : before.connectionPointsOn(wire)) {
                if (WireGraph.isInterior(wire, point)) {
                    addFixedAt(found, point, moved, false);
                }
            }
        }
        junctions.addAll(found);
    }

    /**
     * The fixed things at {@code point} joined to {@code moved}: fixed pins, and fixed wires through it ({@code
     * movedIsAnEnd}: a pin or an end touches a fixed wire anywhere; a moved interior only joins fixed wire ends).
     */
    private void addFixedAt(Set<Junction> found, GridPoint point, Item moved, boolean movedIsAnEnd) {
        for (PinRef pin : before.pinsAt(point)) {
            if (!isMovedPin(pin)) {
                found.add(new Junction(point, moved, new PinItem(pin)));
            }
        }
        for (Wire wire : before.wiresThrough(point)) {
            boolean endsHere = wire.start().equals(point) || wire.end().equals(point);
            if (!movedWireIds.contains(wire.id()) && (movedIsAnEnd || endsHere)) {
                found.add(new Junction(point, moved, new WireItem(wire.id())));
            }
        }
    }

    // --- the plan ---

    private FollowPlan plan(Supplier<ElementId> newWireIds) {
        Set<ElementId> flipped = new HashSet<>();
        Draft draft = build(flipped);
        List<NewContact> contacts = contacts(draft);
        for (ElementId flippable : draft.flippable) {
            if (touches(contacts, draft.piecesOf(flippable))) {
                Set<ElementId> candidateFlipped = new HashSet<>(flipped);
                candidateFlipped.add(flippable);
                Draft candidate = build(candidateFlipped);
                List<NewContact> candidateContacts = contacts(candidate);
                if (!touches(candidateContacts, candidate.piecesOf(flippable))) {
                    flipped = candidateFlipped;
                    draft = candidate;
                    contacts = candidateContacts;
                }
            }
        }
        return draft.toPlan(contacts, newWireIds);
    }

    private static boolean touches(List<NewContact> contacts, List<Wire> pieces) {
        return contacts.stream().anyMatch(contact -> pieces.stream().anyMatch(piece -> piece.contains(contact.point())));
    }

    private Draft build(Set<ElementId> flipped) {
        Draft draft = new Draft();
        Set<ElementId> claimed = new HashSet<>();
        Set<ElementId> attachedIds = new HashSet<>();
        attachedWires.forEach(wire -> attachedIds.add(wire.id()));
        for (Wire wire : attachedWires) {
            stretch(draft, wire, attachedPoints.get(wire.id()), flipped.contains(wire.id()), attachedIds, claimed);
        }
        repair(draft);
        return draft;
    }

    private void stretch(Draft draft, Wire wire, GridPoint moved, boolean flipped, Set<ElementId> attachedIds,
                         Set<ElementId> claimed) {
        GridPoint far = moved.equals(wire.start()) ? wire.end() : wire.start();
        GridPoint target = moved.translate(deltaX, deltaY);
        List<GridPoint> junctionsInside = interiorConnections(wire);
        boolean alongAxis = wire.isHorizontal() ? deltaY == 0 : deltaX == 0;
        if (alongAxis) {
            // rule 1
            if (junctionsInside.stream().anyMatch(point -> !between(far, target, point))) {
                draft.replace(wire, span(wire.id(), far, moved, target));
            } else if (far.equals(target)) {
                draft.remove(wire.id());
            } else {
                draft.replace(wire, new Wire(wire.id(), far, target));
            }
            return;
        }
        if (junctionsInside.isEmpty()) {
            if (isFree(far, wire)) {
                // rule 4
                draft.replace(wire, wire.translate(deltaX, deltaY));
                return;
            }
            Optional<Wire> corner = simpleCorner(wire, far, attachedIds, claimed);
            if (corner.isPresent() && cornerFollows(draft, wire, far, target, corner.get(), claimed)) {
                // rule 2
                return;
            }
        }
        // rule 3
        boolean canFlip = junctionsInside.isEmpty();
        if (canFlip) {
            draft.flippable.add(wire.id());
        }
        boolean horizontalFirst = wire.isHorizontal() != (canFlip && flipped);
        GridPoint corner = horizontalFirst ? new GridPoint(target.x(), far.y()) : new GridPoint(far.x(), target.y());
        List<GridPoint> firstLeg = new ArrayList<>(List.of(far, corner));
        boolean sameLine = horizontalFirst == wire.isHorizontal();
        if (sameLine && junctionsInside.stream().anyMatch(point -> !between(far, corner, point))) {
            firstLeg.add(moved);
        }
        Wire first = firstLeg.stream().distinct().count() > 1
                ? span(wire.id(), firstLeg.toArray(GridPoint[]::new)) : null;
        boolean secondExists = !corner.equals(target);
        if (first == null) {
            draft.replace(wire, new Wire(wire.id(), corner, target));
        } else {
            draft.replace(wire, first);
            if (secondExists) {
                draft.add(new Wire(draft.placeholder(), corner, target), new WireItem(wire.id()), wire.id());
            }
        }
    }

    /** Rule 2: moves {@code wire} sideways and stretches its neighbour at {@code far}, when that loses nothing. */
    private boolean cornerFollows(Draft draft, Wire wire, GridPoint far, GridPoint target, Wire neighbour,
                                  Set<ElementId> claimed) {
        GridPoint sideways = wire.isHorizontal() ? new GridPoint(0, deltaY) : new GridPoint(deltaX, 0);
        GridPoint newCorner = far.plus(sideways);
        GridPoint other = neighbour.start().equals(far) ? neighbour.end() : neighbour.start();
        for (GridPoint point : interiorConnections(neighbour)) {
            if (!between(other, newCorner, point)) {
                return false;
            }
        }
        claimed.add(neighbour.id());
        if (newCorner.equals(other)) {
            draft.remove(neighbour.id());
        } else {
            draft.replace(neighbour, new Wire(neighbour.id(), other, newCorner));
        }
        if (newCorner.equals(target)) {
            draft.remove(wire.id());
        } else {
            draft.replace(wire, new Wire(wire.id(), newCorner, target));
        }
        return true;
    }

    /** The only other wire at {@code far} when it ends there, is perpendicular and free to stretch; no pin there. */
    private Optional<Wire> simpleCorner(Wire wire, GridPoint far, Set<ElementId> attachedIds, Set<ElementId> claimed) {
        if (!before.pinsAt(far).isEmpty()) {
            return Optional.empty();
        }
        List<Wire> others = before.wiresThrough(far).stream().filter(other -> !other.id().equals(wire.id())).toList();
        if (others.size() != 1) {
            return Optional.empty();
        }
        Wire neighbour = others.get(0);
        boolean endsHere = neighbour.start().equals(far) || neighbour.end().equals(far);
        if (!endsHere || neighbour.isHorizontal() == wire.isHorizontal() || movedWireIds.contains(neighbour.id())
                || attachedIds.contains(neighbour.id()) || claimed.contains(neighbour.id())) {
            return Optional.empty();
        }
        return Optional.of(neighbour);
    }

    private boolean isFree(GridPoint point, Wire wire) {
        return before.pinsAt(point).isEmpty()
                && before.wiresThrough(point).stream().allMatch(other -> other.id().equals(wire.id()));
    }

    /** Points strictly inside {@code wire} where a pin or another wire's end joins it. */
    private List<GridPoint> interiorConnections(Wire wire) {
        return before.connectionPointsOn(wire).stream().filter(point -> WireGraph.isInterior(wire, point)).toList();
    }

    /** True when {@code point} lies on the closed segment between two points of one line. */
    private static boolean between(GridPoint first, GridPoint second, GridPoint point) {
        return point.x() >= Math.min(first.x(), second.x()) && point.x() <= Math.max(first.x(), second.x())
                && point.y() >= Math.min(first.y(), second.y()) && point.y() <= Math.max(first.y(), second.y());
    }

    /** The shortest wire covering collinear {@code points}. */
    private static Wire span(ElementId id, GridPoint... points) {
        GridPoint lowest = points[0];
        GridPoint highest = points[0];
        for (GridPoint point : points) {
            lowest = point.compareTo(lowest) < 0 ? point : lowest;
            highest = point.compareTo(highest) > 0 ? point : highest;
        }
        return new Wire(id, lowest, highest);
    }

    // --- bridges ---

    private void repair(Draft draft) {
        List<Wire> wiresAfter = draft.wiresAfter();
        Map<ElementId, Wire> wiresAfterById = new HashMap<>();
        wiresAfter.forEach(wire -> wiresAfterById.put(wire.id(), wire));
        PointNets netsAfter = new PointNets(pinsAfter, wiresAfter);
        for (Junction junction : junctions) {
            Optional<GridPoint> movedRoot = rootAfter(netsAfter, junction.moved(), wiresAfterById);
            Optional<GridPoint> fixedRoot = rootAfter(netsAfter, junction.fixed(), wiresAfterById);
            if (movedRoot.isEmpty() || fixedRoot.isEmpty() || movedRoot.get().equals(fixedRoot.get())) {
                continue;
            }
            GridPoint from = junction.point();
            Optional<Wire> anchor = Optional.empty();
            if (junction.fixed() instanceof WireItem(ElementId wireId)) {
                anchor = draft.piecesOf(wireId, wiresAfterById).stream().filter(piece -> piece.contains(from))
                        .findFirst();
                if (anchor.isEmpty()) {
                    continue;
                }
            }
            GridPoint to = from.translate(deltaX, deltaY);
            boolean horizontalFirst = anchor.map(wire -> !wire.isHorizontal()).orElse(true);
            GridPoint corner = horizontalFirst ? new GridPoint(to.x(), from.y()) : new GridPoint(from.x(), to.y());
            for (Wire bridge : legs(draft, from, corner, to)) {
                draft.add(bridge, junction.moved(), null);
            }
            netsAfter.union(movedRoot.get(), fixedRoot.get());
        }
    }

    private static List<Wire> legs(Draft draft, GridPoint from, GridPoint corner, GridPoint to) {
        List<Wire> legs = new ArrayList<>();
        if (!from.equals(corner)) {
            legs.add(new Wire(draft.placeholder(), from, corner));
        }
        if (!corner.equals(to)) {
            legs.add(new Wire(draft.placeholder(), corner, to));
        }
        return legs;
    }

    private Optional<GridPoint> rootAfter(PointNets nets, Item item, Map<ElementId, Wire> wiresAfterById) {
        return switch (item) {
            case PinItem(PinRef pin) -> Optional.of(nets.find(pinsAfter.get(pin)));
            case WireItem(ElementId wireId) -> Optional.ofNullable(wiresAfterById.get(wireId))
                    .map(wire -> nets.find(wire.start()));
        };
    }

    // --- contacts ---

    private List<NewContact> contacts(Draft draft) {
        List<Wire> wiresAfter = draft.wiresAfter();
        WireGraph after = WireGraph.of(wiresAfter, pinsAtPointAfter);
        Set<GridPoint> candidates = new TreeSet<>();
        for (Wire wire : wiresAfter) {
            if (draft.isChanged(wire.id())) {
                candidates.addAll(after.connectionPointsOn(wire));
            }
        }
        pinsAfter.forEach((pin, point) -> {
            if (isMovedPin(pin)) {
                candidates.add(point);
            }
        });
        List<NewContact> contacts = new ArrayList<>();
        for (GridPoint point : candidates) {
            List<PinRef> pins = after.pinsAt(point);
            List<Wire> through = after.wiresThrough(point);
            boolean anEndIsHere = !pins.isEmpty() || through.stream()
                    .anyMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
            if (!anEndIsHere) {
                continue;
            }
            Map<GridPoint, Item> netsHere = new LinkedHashMap<>();
            for (Wire wire : through) {
                netsHere.putIfAbsent(draft.rootBefore(wire.id()), new WireItem(wire.id()));
            }
            for (PinRef pin : pins) {
                netsHere.putIfAbsent(netsBefore.find(netsBefore.pinPoint(pin)), new PinItem(pin));
            }
            if (netsHere.size() > 1) {
                contacts.add(contactAt(point, pins, through, draft));
            }
        }
        return contacts;
    }

    /** Names the pin touched by something of another net, preferring a pin that stays over one that moved. */
    private NewContact contactAt(GridPoint point, List<PinRef> pins, List<Wire> through, Draft draft) {
        Set<GridPoint> wireNets = new HashSet<>();
        through.forEach(wire -> wireNets.add(draft.rootBefore(wire.id())));
        Optional<PinRef> named = pins.stream()
                .sorted(Comparator.comparing((PinRef pin) -> isMovedPin(pin))
                        .thenComparing(pin -> !wireNets.isEmpty()
                                && wireNets.contains(netsBefore.find(netsBefore.pinPoint(pin))))
                        .thenComparing(pin -> document.orderOf(pin.componentId()))
                        .thenComparingInt(PinRef::pinIndex))
                .findFirst();
        return new NewContact(point, named.map(PinRef::componentId), named.map(pinNames::get));
    }

    // --- model ---

    private sealed interface Item permits PinItem, WireItem {
    }

    private record PinItem(PinRef pin) implements Item {
    }

    private record WireItem(ElementId wireId) implements Item {
    }

    /** {@code moved} (moves) touched {@code fixed} (stays) at {@code point} before the move. */
    private record Junction(GridPoint point, Item moved, Item fixed) {
    }

    /** The wire changes being built, with placeholder ids for the new wires. */
    private final class Draft {

        private final Map<ElementId, Wire> replaced = new LinkedHashMap<>();
        private final Set<ElementId> removed = new LinkedHashSet<>();
        private final List<Wire> added = new ArrayList<>();
        /** The net (before the move) each new wire belongs to, as the root of {@link #netsBefore}. */
        private final Map<ElementId, GridPoint> addedNets = new HashMap<>();
        /** The attached wire each new L leg comes from. */
        private final Map<ElementId, ElementId> addedFrom = new HashMap<>();
        private final List<ElementId> flippable = new ArrayList<>();
        private int placeholders;

        ElementId placeholder() {
            return new ElementId(PLACEHOLDER_PREFIX + placeholders++);
        }

        void replace(Wire original, Wire replacement) {
            if (!replacement.equals(original)) {
                replaced.put(replacement.id(), replacement);
            }
        }

        void remove(ElementId id) {
            replaced.remove(id);
            removed.add(id);
        }

        void add(Wire wire, Item netOf, ElementId from) {
            added.add(wire);
            addedNets.put(wire.id(), switch (netOf) {
                case PinItem(PinRef pin) -> netsBefore.find(netsBefore.pinPoint(pin));
                case WireItem(ElementId wireId) -> netsBefore.find(netsBefore.wireStart(wireId));
            });
            if (from != null) {
                addedFrom.put(wire.id(), from);
            }
        }

        GridPoint rootBefore(ElementId wireId) {
            GridPoint root = addedNets.get(wireId);
            return root != null ? root : netsBefore.find(netsBefore.wireStart(wireId));
        }

        boolean isChanged(ElementId wireId) {
            return replaced.containsKey(wireId) || addedNets.containsKey(wireId) || movedWireIds.contains(wireId);
        }

        List<Wire> wiresAfter() {
            List<Wire> wires = new ArrayList<>();
            for (Wire wire : documentWires) {
                if (removed.contains(wire.id())) {
                    continue;
                }
                if (movedWireIds.contains(wire.id())) {
                    wires.add(wire.translate(deltaX, deltaY));
                } else {
                    wires.add(replaced.getOrDefault(wire.id(), wire));
                }
            }
            wires.addAll(added);
            return wires;
        }

        /** The wires after the move that come from attached wire {@code wireId}: itself and its L leg. */
        List<Wire> piecesOf(ElementId wireId) {
            Map<ElementId, Wire> byId = new HashMap<>();
            wiresAfter().forEach(wire -> byId.put(wire.id(), wire));
            return piecesOf(wireId, byId);
        }

        List<Wire> piecesOf(ElementId wireId, Map<ElementId, Wire> wiresAfterById) {
            List<Wire> pieces = new ArrayList<>();
            Optional.ofNullable(wiresAfterById.get(wireId)).ifPresent(pieces::add);
            for (Wire wire : added) {
                if (wireId.equals(addedFrom.get(wire.id()))) {
                    pieces.add(wire);
                }
            }
            return pieces;
        }

        FollowPlan toPlan(List<NewContact> contacts, Supplier<ElementId> newWireIds) {
            List<Wire> addedWires = new ArrayList<>();
            for (Wire wire : added) {
                addedWires.add(new Wire(newWireIds.get(), wire.start(), wire.end()));
            }
            List<Wire> replacedWires = new ArrayList<>();
            for (Wire wire : documentWires) {
                if (replaced.containsKey(wire.id())) {
                    replacedWires.add(replaced.get(wire.id()));
                }
            }
            Set<ElementId> removedWireIds = new LinkedHashSet<>();
            for (Wire wire : documentWires) {
                if (removed.contains(wire.id())) {
                    removedWireIds.add(wire.id());
                }
            }
            return new FollowPlan(movedWholeWireIds, replacedWires, addedWires, removedWireIds, contacts);
        }
    }

    /**
     * Union-find over the points of interest (pins and wire ends) of one layout, with the connection rule of
     * {@code NetlistCompiler}: a wire joins every point of interest it contains.
     */
    private static final class PointNets {

        private final Map<GridPoint, GridPoint> parents = new HashMap<>();
        private final Map<PinRef, GridPoint> pinPoints;
        private final Map<ElementId, GridPoint> wireStarts = new HashMap<>();

        PointNets(Map<PinRef, GridPoint> pinPoints, List<Wire> wires) {
            this.pinPoints = pinPoints;
            Map<Integer, TreeMap<Integer, GridPoint>> rows = new HashMap<>();
            Map<Integer, TreeMap<Integer, GridPoint>> columns = new HashMap<>();
            List<GridPoint> points = new ArrayList<>(pinPoints.values());
            for (Wire wire : wires) {
                points.add(wire.start());
                points.add(wire.end());
                wireStarts.put(wire.id(), wire.start());
            }
            for (GridPoint point : points) {
                parents.putIfAbsent(point, point);
                rows.computeIfAbsent(point.y(), row -> new TreeMap<>()).put(point.x(), point);
                columns.computeIfAbsent(point.x(), column -> new TreeMap<>()).put(point.y(), point);
            }
            for (Wire wire : wires) {
                Map<Integer, GridPoint> contained = wire.isHorizontal()
                        ? rows.get(wire.start().y()).subMap(wire.start().x(), true, wire.end().x(), true)
                        : columns.get(wire.start().x()).subMap(wire.start().y(), true, wire.end().y(), true);
                for (GridPoint point : contained.values()) {
                    union(wire.start(), point);
                }
            }
        }

        GridPoint pinPoint(PinRef pin) {
            return pinPoints.get(pin);
        }

        GridPoint wireStart(ElementId wireId) {
            return wireStarts.get(wireId);
        }

        GridPoint find(GridPoint point) {
            GridPoint root = point;
            while (!parents.get(root).equals(root)) {
                root = parents.get(root);
            }
            GridPoint current = point;
            while (!current.equals(root)) {
                GridPoint next = parents.get(current);
                parents.put(current, root);
                current = next;
            }
            return root;
        }

        void union(GridPoint first, GridPoint second) {
            GridPoint firstRoot = find(first);
            GridPoint secondRoot = find(second);
            if (!firstRoot.equals(secondRoot)) {
                parents.put(secondRoot, firstRoot);
            }
        }
    }
}
