package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.UnionFind;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The things «Ordenar circuito» separates, read from a document (spec 2026-11-06 §4.1–§4.2): one {@link Item} per
 * component with its own label, components joined pin on pin (no wire between them) made one rigid item, and one per
 * block of touching free texts and sticky notes. An item is movable only when all its members are in scope and it
 * lies wholly inside one area (the circuit, or the innermost frame around it); one that straddles a frame border
 * stays.
 *
 * <p>Parts and texts are separated apart. Each area's parts ({@link #separationGroups}): its movable parts, every
 * fixed part, a thin box on each side just outside the area's frame (the walls keep its items in), every other frame
 * as an obstacle (so nothing enters it) and the wires that run through a foreign body (so the body moves off them).
 * Then each block of texts that covers a body ({@link #coveredBodies}) is moved off it alone. Frames are
 * otherwise not boxes, and guides are ignored. What covers what follows the user's eye ({@link #covers}).
 */
final class LayoutItems {

    /** Space wanted around a part: two bodies end at least one grid unit apart (spec §4.3). */
    static final int BODY_GAP = 1;
    /** Texts (labels, free texts, sticky notes) may touch what they are next to. */
    static final int TEXT_GAP = 0;
    /** What a free text or a sticky note weighs in the separation: less than any part ({@link #weightOf}). */
    static final long TEXT_WEIGHT = 1;
    /**
     * How many times lighter an overlapping item must be for the heavier one not to make way for it: 8 is one pin
     * against three ({@link #weightOf}: 8 against 64), a tunnel or a lamp against a gate.
     */
    static final long MUCH_LIGHTER = 8;
    /**
     * How many columns of a text's box, in grid units, may lie over a body without counting: text boxes are rounded
     * outward to whole units, and a box's width grows by up to one unit (about one character) that may hold no ink, so
     * a text drawn beside a part may reach into it by that column (the user's hand test, 2026-11-06). Its height grows
     * only by a couple of pixels (a 14 px line takes 2 units of 8 px), so one shared row is a real overlap: a line
     * half over a tunnel covers half of it (the hand test on lrv32im-importado, same day).
     */
    static final int TEXT_ROUNDING_COLUMNS = 1;
    /** Texts closer than this (touching, or overlapping) form one block: the lines of a table, a split caption. */
    private static final int TEXT_BLOCK_DISTANCE = 1;

    /**
     * Element ids in natural order: the letters, then the number after them as a number ({@code c2} before
     * {@code c10}), so ties are broken the way a person reads the ids.
     */
    static final Comparator<ElementId> ID_ORDER = Comparator.comparing(LayoutItems::idPrefix)
            .thenComparing(LayoutItems::idNumber)
            .thenComparing(ElementId::value);

    /** What separation boxes that stand for no element are named by: they never get a delta. */
    private static final String WALL_PREFIX = "wall-";
    private static final String FRAME_OBSTACLE_PREFIX = "frame-";
    private static final String WIRE_OBSTACLE_PREFIX = "wire-";

    /**
     * One thing that moves as a whole: a component (with its label), a pin-on-pin cluster of components, or a block
     * of texts (free texts and sticky notes that touch each other, such as the lines of a table, which must keep
     * their order). {@code id} is the smallest member id; {@code region} the innermost frame around it (empty: the
     * circuit itself); {@code parts} and {@code texts} count its members for the summary; {@code partlyInScope} says
     * at least one member may change (a fixed item that is partly in scope and overlaps something is reported).
     * {@code bounds} is the box the separation moves; {@code shapes} are what the user sees of it (each body and each
     * label, or each text), so two items whose boxes meet only where neither draws anything do not cover each other;
     * {@code bodies} are its components' bodies (none for texts). {@code weight} is how much it resists moving
     * ({@link #weightOf}).
     */
    record Item(ElementId id, List<ElementId> members, Bounds bounds, List<Bounds> shapes, List<Bounds> bodies,
                int gap, long weight, boolean movable, boolean partlyInScope, Optional<ElementId> region, int parts,
                int texts) {

        Item {
            members = List.copyOf(members);
            shapes = List.copyOf(shapes);
            bodies = List.copyOf(bodies);
        }

        /** True for a block of texts: it only gets out of the way of bodies (spec §4.1, the user, 2026-11-06). */
        boolean isText() {
            return parts == 0;
        }

        /** Its labels or texts: the shapes that are not bodies. */
        List<Bounds> writing() {
            List<Bounds> writing = new ArrayList<>(shapes);
            bodies.forEach(writing::remove);
            return writing;
        }

        /** The box around its bodies, without its labels: what the user lines up by eye (a text: its own box). */
        Bounds bodyBox() {
            return bodies.stream().reduce(Bounds::union).orElse(bounds);
        }
    }

    private final List<Item> items;
    /** The items that hold parts (not texts), read once: they are looked through for every place tried. */
    private final List<Item> parts;
    private final Map<ElementId, Item> itemsById;
    private final List<Frame> frames;
    private final List<LayoutBox> wireObstacles;

    private LayoutItems(List<Item> items, List<Frame> frames, List<LayoutBox> wireObstacles) {
        this.items = List.copyOf(items);
        this.parts = this.items.stream().filter(item -> !item.isText()).toList();
        this.frames = List.copyOf(frames);
        this.wireObstacles = List.copyOf(wireObstacles);
        Map<ElementId, Item> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.put(item.id(), item));
        this.itemsById = byId;
    }

    /**
     * The items of {@code document}. {@code scope} holds the ids allowed to change; {@code texts} measures labels
     * and free texts. Components of an unknown type and free texts with no size cover nothing and are left out.
     */
    static LayoutItems of(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> scope,
                          TextBoxes texts) {
        List<Frame> frames = document.frames();
        List<Item> items = new ArrayList<>();
        for (List<Component> cluster : clusters(document, registry)) {
            items.add(componentItem(cluster, registry, scope, texts, frames));
        }
        Map<ElementId, Bounds> textBoxes = new LinkedHashMap<>();
        for (TextLabel label : document.labels()) {
            Bounds box = texts.freeText(label);
            // A text with no size (TextBoxes.NONE) covers nothing: as a point inside a body it would still be pushed.
            if (box.width() > 0 && box.height() > 0) {
                textBoxes.put(label.id(), box);
            }
        }
        for (StickyNote note : document.stickyNotes()) {
            textBoxes.put(note.id(), new Bounds(note.position().x(), note.position().y(), note.visibleWidth(),
                    note.visibleHeight()));
        }
        for (List<ElementId> block : textBlocks(textBoxes)) {
            items.add(textItem(block, textBoxes, scope, frames));
        }
        items.sort(Comparator.comparing(Item::id, ID_ORDER));
        return new LayoutItems(items, frames, wireObstacles(document, registry));
    }

    /** Every item, in {@link #ID_ORDER}. */
    List<Item> items() {
        return items;
    }

    /** The item named {@code id} (the smallest id of a cluster); empty for any other id. */
    Optional<Item> item(ElementId id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    /**
     * One list of boxes per area that has a movable item that overlaps something: what
     * {@link OverlapSeparation#separate} gets for it. Areas in order: the circuit first, then frames in document
     * order. An item that overlaps nothing is a fixed box: only what overlaps moves (spec §4.3, «only overlapping
     * neighbours push each other»), so a part next to a crowded spot is never pushed away by it; see
     * {@link #takesPart}.
     */
    List<List<LayoutBox>> separationGroups() {
        List<List<LayoutBox>> groups = new ArrayList<>();
        for (Optional<ElementId> region : regions()) {
            List<LayoutBox> obstacles = new ArrayList<>(regionObstacles(region, Optional.empty()));
            obstacles.addAll(wireObstacles);
            List<LayoutBox> boxes = new ArrayList<>();
            for (Item item : parts()) {
                if (item.movable() && !item.region().equals(region)) {
                    continue;
                }
                boolean moves = item.movable() && takesPart(item, obstacles);
                boxes.add(new LayoutBox(item.id(), item.bounds(), moves, item.gap(), item.weight()));
            }
            addIfAnyMoves(groups, boxes, obstacles);
        }
        return groups;
    }

    private static void addIfAnyMoves(List<List<LayoutBox>> groups, List<LayoutBox> boxes, List<LayoutBox> obstacles) {
        if (boxes.stream().anyMatch(LayoutBox::movable)) {
            List<LayoutBox> group = new ArrayList<>(boxes);
            group.addAll(obstacles);
            groups.add(group);
        }
    }

    /** The areas items are separated in: the circuit first, then frames in document order. */
    private List<Optional<ElementId>> regions() {
        List<Optional<ElementId>> regions = new ArrayList<>();
        regions.add(Optional.empty());
        frames.forEach(frame -> regions.add(Optional.of(frame.id())));
        return regions;
    }

    private List<Item> parts() {
        return parts;
    }

    /**
     * True when {@code item} has to take part in the separation: the inside of its box meets (a line through it counts,
     * touching does not) one of {@code obstacles}, or it covers ({@link #covers}) a fixed item or a movable item not
     * {@value #MUCH_LIGHTER} times lighter than it. A part never makes way for a much simpler one (a register for a
     * tunnel): the simpler one moves, or, boxed in, is reported, rather than push the part and its wires away.
     */
    private boolean takesPart(Item item, List<LayoutBox> obstacles) {
        for (Item other : parts()) {
            boolean muchLighter = other.movable() && other.weight() * MUCH_LIGHTER <= item.weight();
            if (other != item && !muchLighter && covers(item, item.bounds(), other, other.bounds())) {
                return true;
            }
        }
        return obstacles.stream().anyMatch(obstacle -> interiorsMeet(item.bounds(), obstacle.bounds()));
    }

    private static boolean interiorsMeet(Bounds first, Bounds second) {
        return first.x() < second.maximumX() && second.x() < first.maximumX()
                && first.y() < second.maximumY() && second.y() < first.maximumY();
    }

    /**
     * The fixed boxes that are not items and that {@code item} must keep clear of: walls, frames and, for a part, the
     * wires that would run through its body (a text over a wire is fine).
     */
    List<LayoutBox> obstacles(Item item) {
        List<LayoutBox> obstacles = new ArrayList<>(regionObstacles(item.region(), Optional.of(item.bounds())));
        if (!item.isText()) {
            obstacles.addAll(wireObstacles);
        }
        return obstacles;
    }

    /**
     * True when {@code item} at {@code candidate} keeps clear of every other item where {@code placed} says it is
     * (items missing from it are where they started) and of its {@link #obstacles}, and stays in the same frames, by
     * the separation's own rule ({@link OverlapSeparation}): no shared area, and no coming closer than the gap,
     * unless the two were drawn that close.
     */
    boolean isFree(Item item, Bounds candidate, Map<ElementId, Bounds> placed) {
        return isFree(item, candidate, placed, Set.of());
    }

    /**
     * {@link #isFree(Item, Bounds, Map)}, leaving out the items in {@code together}: they move by the same shift as
     * {@code item}, so what lies between them does not change.
     */
    boolean isFree(Item item, Bounds candidate, Map<ElementId, Bounds> placed, Set<ElementId> together) {
        for (Item other : parts()) {
            if (other.id().equals(item.id()) || together.contains(other.id())) {
                continue;
            }
            Bounds otherBounds = placed.getOrDefault(other.id(), other.bounds());
            if (covers(item, candidate, other, otherBounds)
                    || bodiesNewlyTooClose(item, item.bounds(), candidate, other, other.bounds(), otherBounds)) {
                return false;
            }
        }
        for (LayoutBox obstacle : obstacles(item)) {
            int gap = Math.max(item.gap(), obstacle.gap());
            if (newlyTooClose(item.bounds(), candidate, obstacle.bounds(), obstacle.bounds(), gap)) {
                return false;
            }
        }
        return keepsItsFrames(item, candidate);
    }

    /**
     * True when a body of {@code item} (a line, for a block of texts), moved from {@code from} to {@code to}, comes
     * closer than {@link #BODY_GAP} to a body of {@code other} (moved from {@code otherFrom} to {@code otherTo})
     * without having been drawn that close ({@link #touchedAsDrawn}): what moves keeps the gap from bodies.
     */
    private static boolean bodiesNewlyTooClose(Item item, Bounds from, Bounds to, Item other, Bounds otherFrom,
                                               Bounds otherTo) {
        // A text's lines keep the bodies' gap too when the text moves (the pair keeps the larger gap, spec §4.3).
        List<Bounds> own = item.isText() ? item.shapes() : item.bodies();
        List<Bounds> bodiesFrom = movedWith(own, item, from);
        List<Bounds> bodiesTo = movedWith(own, item, to);
        List<Bounds> otherBodiesFrom = movedWith(other.bodies(), other, otherFrom);
        List<Bounds> otherBodiesTo = movedWith(other.bodies(), other, otherTo);
        for (int index = 0; index < bodiesTo.size(); index++) {
            for (int otherIndex = 0; otherIndex < otherBodiesTo.size(); otherIndex++) {
                if (overlaps(bodiesTo.get(index), otherBodiesTo.get(otherIndex), BODY_GAP)
                        && !touchedAsDrawn(bodiesFrom.get(index), otherBodiesFrom.get(otherIndex), BODY_GAP)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True when a box moved from {@code from} to {@code to} meets the inside of the other box (from {@code otherFrom}
     * to {@code otherTo}; a line through it counts), or is closer than {@code gap} to it without having been drawn
     * that close ({@link #touchedAsDrawn}).
     */
    private static boolean newlyTooClose(Bounds from, Bounds to, Bounds otherFrom, Bounds otherTo, int gap) {
        return interiorsMeet(to, otherTo) || overlaps(to, otherTo, gap) && !touchedAsDrawn(from, otherFrom, gap);
    }

    /**
     * True when the two boxes are closer than the gap without meeting inside: drawn touching (or nearly), which is
     * fine and may stay. Two that overlapped are separated by the whole gap.
     */
    private static boolean touchedAsDrawn(Bounds first, Bounds second, int gap) {
        return overlaps(first, second, gap) && !interiorsMeet(first, second);
    }

    /**
     * True when {@code item} at {@code candidate} makes no new overlap with any other item, each where {@code now}
     * says it is (items missing from it are where they started): it meets the inside of none it did not already meet
     * from where it is now, nor comes closer than the gap to one it was not that close to. A move never makes a new
     * overlap with a neighbour that may still have to stay.
     */
    boolean addsNoOverlap(Item item, Bounds candidate, Map<ElementId, Bounds> now) {
        return addsNoOverlap(item, candidate, now, Set.of());
    }

    /** {@link #addsNoOverlap(Item, Bounds, Map)}, leaving out the items in {@code together} (moved by the same shift). */
    boolean addsNoOverlap(Item item, Bounds candidate, Map<ElementId, Bounds> now, Set<ElementId> together) {
        Bounds from = now.getOrDefault(item.id(), item.bounds());
        for (Item other : parts()) {
            if (other.id().equals(item.id()) || together.contains(other.id())) {
                continue;
            }
            Bounds otherNow = now.getOrDefault(other.id(), other.bounds());
            boolean newlyCovers = covers(item, candidate, other, otherNow) && !covers(item, from, other, otherNow);
            if (newlyCovers || bodiesNewlyTooClose(item, from, candidate, other, otherNow, otherNow)) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the writing of {@code item} (its texts or labels) at {@code candidate} lies over the writing of an
     * item it did not lie over from where it is now, each item where {@code now} says (items missing from it are where
     * they started); by the rule of {@link #covers}: at least one row and more than {@value #TEXT_ROUNDING_COLUMNS}
     * column. Writing over writing is never a reason to move, but a text moved off a body must not land where it can
     * no longer be read (the final review, 2026-11-06).
     */
    boolean writesOverNewWriting(Item item, Bounds candidate, Map<ElementId, Bounds> now) {
        return writesOverNewWriting(item, candidate, now, Set.of());
    }

    /**
     * {@link #writesOverNewWriting(Item, Bounds, Map)}, leaving out the items in {@code together} (moved by the same
     * shift).
     */
    boolean writesOverNewWriting(Item item, Bounds candidate, Map<ElementId, Bounds> now, Set<ElementId> together) {
        Bounds from = now.getOrDefault(item.id(), item.bounds());
        List<Bounds> writingThere = movedWith(item.writing(), item, candidate);
        List<Bounds> writingHere = movedWith(item.writing(), item, from);
        for (Item other : items) {
            if (other.id().equals(item.id()) || together.contains(other.id()) || other.writing().isEmpty()) {
                continue;
            }
            List<Bounds> otherWriting = movedWith(other.writing(), other,
                    now.getOrDefault(other.id(), other.bounds()));
            if (share(writingThere, otherWriting, TEXT_ROUNDING_COLUMNS, 0)
                    && !share(writingHere, otherWriting, TEXT_ROUNDING_COLUMNS, 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ids of the other items that {@code item} covers, every item where {@code placed} says it is (items missing
     * from it are where they started), as the user sees it (spec §4.1): a body shares an area with a body, a label or
     * a text. Texts over texts or labels are not overlaps (placed so on purpose); being closer than the gap,
     * touching, walls and frames do not count either.
     */
    List<ElementId> coveredItems(Item item, Map<ElementId, Bounds> placed) {
        Bounds at = placed.getOrDefault(item.id(), item.bounds());
        List<ElementId> covered = new ArrayList<>();
        for (Item other : items) {
            Bounds otherAt = placed.getOrDefault(other.id(), other.bounds());
            if (!other.id().equals(item.id()) && covers(item, at, other, otherAt)) {
                covered.add(other.id());
            }
        }
        return covered;
    }

    /** The ids of the parts whose bodies {@code item} covers ({@link #covers}), each where {@code placed} says. */
    List<ElementId> coveredBodies(Item item, Map<ElementId, Bounds> placed) {
        Bounds at = placed.getOrDefault(item.id(), item.bounds());
        List<ElementId> covered = new ArrayList<>();
        for (Item other : parts()) {
            if (!other.id().equals(item.id())
                    && covers(item, at, other, placed.getOrDefault(other.id(), other.bounds()))) {
                covered.add(other.id());
            }
        }
        return covered;
    }

    /**
     * True when the two items, at the places given, cover each other as the user sees it: a body shares an area with
     * a body, or with a label or text by at least one row and more than {@value #TEXT_ROUNDING_COLUMNS} column.
     * Writing over writing never counts.
     */
    static boolean covers(Item first, Bounds firstAt, Item second, Bounds secondAt) {
        if (!overlaps(firstAt, secondAt, 0)) {
            return false;
        }
        List<Bounds> firstBodies = movedWith(first.bodies(), first, firstAt);
        List<Bounds> secondBodies = movedWith(second.bodies(), second, secondAt);
        return share(firstBodies, secondBodies, 0, 0)
                || share(firstBodies, movedWith(second.writing(), second, secondAt), TEXT_ROUNDING_COLUMNS, 0)
                || share(movedWith(first.writing(), first, firstAt), secondBodies, TEXT_ROUNDING_COLUMNS, 0);
    }

    /** True when a box of each list share more than {@code marginX} columns and more than {@code marginY} rows. */
    private static boolean share(List<Bounds> firstBoxes, List<Bounds> secondBoxes, int marginX, int marginY) {
        for (Bounds firstBox : firstBoxes) {
            for (Bounds secondBox : secondBoxes) {
                int sharedX = Math.min(firstBox.maximumX(), secondBox.maximumX())
                        - Math.max(firstBox.x(), secondBox.x());
                int sharedY = Math.min(firstBox.maximumY(), secondBox.maximumY())
                        - Math.max(firstBox.y(), secondBox.y());
                if (sharedX > marginX && sharedY > marginY) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Bounds> movedWith(List<Bounds> boxes, Item item, Bounds at) {
        int deltaX = at.x() - item.bounds().x();
        int deltaY = at.y() - item.bounds().y();
        return boxes.stream().map(box -> box.translate(deltaX, deltaY)).toList();
    }

    /**
     * True when the two boxes overlap for separation: their interiors share an area or, with a {@code gap} above 0,
     * they are closer than it (so with a gap, touching counts; one exactly {@code gap} away does not).
     */
    static boolean overlaps(Bounds first, Bounds second, int gap) {
        int overlapX = Math.min(first.maximumX(), second.maximumX()) - Math.max(first.x(), second.x());
        int overlapY = Math.min(first.maximumY(), second.maximumY()) - Math.max(first.y(), second.y());
        int threshold = gap == 0 ? 0 : -gap;
        return overlapX > threshold && overlapY > threshold;
    }

    /**
     * True when a wire of {@code document} runs through the inside of a body of {@code members} and is not part of
     * that member's own wiring (its net as drawn holds none of the member's pins). A part's own wire over its body is
     * a routing matter, which moving the part does not fix; a foreign one is an overlap (spec §4.1).
     */
    static boolean crossedByForeignWire(CircuitDocument document, ComponentTypeRegistry registry,
                                        Collection<ElementId> members) {
        Map<Wire, List<Component>> crossings = new LinkedHashMap<>();
        for (ElementId id : members) {
            Optional<Component> component = document.component(id);
            Optional<ComponentType> type = component.flatMap(registry::findTypeOf);
            if (type.isEmpty()) {
                continue;
            }
            Bounds body = type.get().bounds(component.get());
            for (Wire wire : document.wires()) {
                if (runsInside(wire, body)) {
                    crossings.computeIfAbsent(wire, key -> new ArrayList<>()).add(component.get());
                }
            }
        }
        if (crossings.isEmpty()) {
            return false;
        }
        Netlist asDrawn = new NetlistCompiler(registry).compileAsDrawn(document);
        for (Map.Entry<Wire, List<Component>> crossing : crossings.entrySet()) {
            for (Component component : crossing.getValue()) {
                if (!isOwnWire(asDrawn, crossing.getKey(), component, registry.typeOf(component))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when {@code wire}'s net, as drawn, holds a pin of {@code component}. */
    private static boolean isOwnWire(Netlist asDrawn, Wire wire, Component component, ComponentType type) {
        Optional<Net> net = asDrawn.netForWire(wire.id());
        if (net.isEmpty()) {
            return false;
        }
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            if (net.get().pins().contains(new PinRef(component.id(), pinIndex))) {
                return true;
            }
        }
        return false;
    }

    // --- building the items ---

    /** Components joined pin on pin, each cluster in id order; clusters in the order of their first member. */
    private static List<List<Component>> clusters(CircuitDocument document, ComponentTypeRegistry registry) {
        List<Component> known = new ArrayList<>();
        for (Component component : document.components()) {
            if (registry.findTypeOf(component).isPresent()) {
                known.add(component);
            }
        }
        UnionFind<ElementId> joins = new UnionFind<>();
        Map<GridPoint, ElementId> firstOnPin = new HashMap<>();
        for (Component component : known) {
            joins.add(component.id());
            for (GridPoint pin : registry.typeOf(component).pinLocations(component)) {
                ElementId previous = firstOnPin.putIfAbsent(pin, component.id());
                if (previous != null) {
                    joins.union(previous, component.id());
                }
            }
        }
        Map<ElementId, List<Component>> byRoot = new LinkedHashMap<>();
        known.stream().sorted(Comparator.comparing(Component::id, ID_ORDER))
                .forEach(component -> byRoot.computeIfAbsent(joins.find(component.id()), root -> new ArrayList<>())
                        .add(component));
        return List.copyOf(byRoot.values());
    }

    private static Item componentItem(List<Component> cluster, ComponentTypeRegistry registry, Set<ElementId> scope,
                                      TextBoxes texts, List<Frame> frames) {
        Bounds bounds = null;
        long weight = 0;
        List<Bounds> shapes = new ArrayList<>();
        List<Bounds> bodies = new ArrayList<>();
        List<ElementId> members = new ArrayList<>();
        for (Component component : cluster) {
            Bounds body = registry.typeOf(component).bounds(component);
            weight += weightOf(registry.typeOf(component).pinCount());
            Optional<Bounds> label = texts.componentLabel(component);
            shapes.add(body);
            bodies.add(body);
            label.ifPresent(shapes::add);
            Bounds withLabel = label.map(body::union).orElse(body);
            bounds = bounds == null ? withLabel : bounds.union(withLabel);
            members.add(component.id());
        }
        return item(members, bounds, shapes, bodies, BODY_GAP, weight, scope, frames, cluster.size(), 0);
    }

    /**
     * Pins beyond this many make a part no heavier: a part with 15 pins is already far heavier than a gate (4096
     * against 64), and the separation's exact arithmetic multiplies weights, so a block of large subcircuits with
     * hundreds of pins each would overflow a {@code long} (the final review, 2026-11-06).
     */
    static final int WEIGHT_PIN_CAP = 15;

    /**
     * How much a part with {@code pinCount} pins resists moving: (pins + 1)³, pins counted up to
     * {@value #WEIGHT_PIN_CAP}, so of two overlapping parts the simpler one (a tunnel, a lamp, a constant) moves and
     * the one with many wires stays, unless they are alike, when they share the push. A text weighs
     * {@value #TEXT_WEIGHT}: it is the cheapest thing to move.
     */
    static long weightOf(int pinCount) {
        long base = Math.min(pinCount, WEIGHT_PIN_CAP) + 1L;
        return base * base * base;
    }

    /**
     * The texts that touch or overlap each other (closer than one unit), each block in id order: the lines of a table
     * or a caption split in two move together, so their order and their pairing never change.
     */
    private static List<List<ElementId>> textBlocks(Map<ElementId, Bounds> textBoxes) {
        UnionFind<ElementId> joins = new UnionFind<>();
        List<ElementId> ids = new ArrayList<>(textBoxes.keySet());
        ids.forEach(joins::add);
        for (int first = 0; first < ids.size(); first++) {
            for (int second = first + 1; second < ids.size(); second++) {
                if (overlaps(textBoxes.get(ids.get(first)), textBoxes.get(ids.get(second)), TEXT_BLOCK_DISTANCE)) {
                    joins.union(ids.get(first), ids.get(second));
                }
            }
        }
        Map<ElementId, List<ElementId>> byRoot = new LinkedHashMap<>();
        ids.stream().sorted(ID_ORDER)
                .forEach(id -> byRoot.computeIfAbsent(joins.find(id), root -> new ArrayList<>()).add(id));
        return List.copyOf(byRoot.values());
    }

    private static Item textItem(List<ElementId> block, Map<ElementId, Bounds> textBoxes, Set<ElementId> scope,
                                 List<Frame> frames) {
        List<Bounds> shapes = block.stream().map(textBoxes::get).toList();
        Bounds bounds = shapes.stream().reduce(Bounds::union).orElseThrow();
        return item(block, bounds, shapes, List.of(), TEXT_GAP, TEXT_WEIGHT * block.size(), scope, frames, 0,
                block.size());
    }

    private static Item item(List<ElementId> members, Bounds bounds, List<Bounds> shapes, List<Bounds> bodies, int gap,
                             long weight, Set<ElementId> scope, List<Frame> frames, int parts, int texts) {
        boolean inScope = scope.containsAll(members);
        boolean partlyInScope = members.stream().anyMatch(scope::contains);
        boolean straddles = frames.stream().anyMatch(frame -> straddles(frame, bounds));
        Optional<ElementId> region = straddles ? Optional.empty() : innermostAround(frames, bounds);
        return new Item(members.get(0), members, bounds, shapes, bodies, gap, weight, inScope && !straddles,
                partlyInScope, region, parts, texts);
    }

    /** True when {@code bounds} shares an area with {@code frame} without lying inside it (across its border). */
    private static boolean straddles(Frame frame, Bounds bounds) {
        return overlaps(rectangleOf(frame), bounds, 0) && !encloses(frame, bounds);
    }

    /**
     * The smallest frame that encloses {@code bounds} (ties: the later one in document order, as
     * {@link cl.almejo.vsim.core.document.FrameContents#parents} nests them).
     */
    private static Optional<ElementId> innermostAround(List<Frame> frames, Bounds bounds) {
        Frame innermost = null;
        for (Frame frame : frames) {
            if (encloses(frame, bounds) && (innermost == null || area(frame) <= area(innermost))) {
                innermost = frame;
            }
        }
        return Optional.ofNullable(innermost).map(Frame::id);
    }

    // --- obstacles ---

    /**
     * The walls of {@code region}'s frame, and every other frame that does not hold the region, as obstacles. With
     * {@code skipFramesTouching}, frames that box already overlaps are left out (it may not be pushed by them).
     */
    private List<LayoutBox> regionObstacles(Optional<ElementId> region, Optional<Bounds> skipFramesTouching) {
        Optional<Frame> regionFrame = region.flatMap(this::frame);
        List<LayoutBox> obstacles = new ArrayList<>();
        regionFrame.ifPresent(frame -> obstacles.addAll(walls(frame)));
        for (Frame frame : frames) {
            boolean holdsTheRegion = regionFrame.isPresent() && encloses(frame, rectangleOf(regionFrame.get()));
            boolean alreadyTouched = skipFramesTouching.isPresent()
                    && overlaps(rectangleOf(frame), skipFramesTouching.get(), 0);
            if (!holdsTheRegion && !alreadyTouched) {
                obstacles.add(frameObstacle(frame));
            }
        }
        return obstacles;
    }

    private Optional<Frame> frame(ElementId id) {
        return frames.stream().filter(frame -> frame.id().equals(id)).findFirst();
    }

    /**
     * Four lines one unit outside the frame's border: a body one unit from a line (with its gap of 1) lies on or
     * inside the border, so the walls keep the frame's contents in without pushing what touches the border.
     */
    private static List<LayoutBox> walls(Frame frame) {
        int left = frame.topLeft().x() - 1;
        int top = frame.topLeft().y() - 1;
        int right = frame.topLeft().x() + frame.width() + 1;
        int bottom = frame.topLeft().y() + frame.height() + 1;
        String name = WALL_PREFIX + frame.id().value() + "-";
        return List.of(
                fixed(name + "left", new Bounds(left, top, 0, bottom - top)),
                fixed(name + "right", new Bounds(right, top, 0, bottom - top)),
                fixed(name + "top", new Bounds(left, top, right - left, 0)),
                fixed(name + "bottom", new Bounds(left, bottom, right - left, 0)));
    }

    /**
     * The frame one unit smaller on each side: a body outside (with its gap of 1) may touch the border but not
     * enter. Frames are at least {@value Frame#MINIMUM_WIDTH} x {@value Frame#MINIMUM_HEIGHT}, so the box is never
     * empty.
     */
    private static LayoutBox frameObstacle(Frame frame) {
        return fixed(FRAME_OBSTACLE_PREFIX + frame.id().value(), new Bounds(frame.topLeft().x() + 1,
                frame.topLeft().y() + 1, frame.width() - 2, frame.height() - 2));
    }

    /**
     * A line for every wire that runs through a body it is foreign to ({@link #crossedByForeignWire}), one unit
     * shorter at each end that is a pin (so the parts at its ends, one unit away, are not pushed by it).
     */
    private static List<LayoutBox> wireObstacles(CircuitDocument document, ComponentTypeRegistry registry) {
        Set<GridPoint> allPins = new HashSet<>();
        List<Component> known = new ArrayList<>();
        for (Component component : document.components()) {
            registry.findTypeOf(component).ifPresent(type -> {
                allPins.addAll(type.pinLocations(component));
                known.add(component);
            });
        }
        Netlist asDrawn = null;
        List<LayoutBox> obstacles = new ArrayList<>();
        for (Wire wire : document.wires()) {
            for (Component component : known) {
                ComponentType type = registry.typeOf(component);
                if (!runsInside(wire, type.bounds(component))) {
                    continue;
                }
                if (asDrawn == null) {
                    asDrawn = new NetlistCompiler(registry).compileAsDrawn(document);
                }
                if (!isOwnWire(asDrawn, wire, component, type)) {
                    obstacles.add(fixed(WIRE_OBSTACLE_PREFIX + wire.id().value(), shortened(wire, allPins)));
                    break;
                }
            }
        }
        return obstacles;
    }

    private static Bounds shortened(Wire wire, Set<GridPoint> pins) {
        int startTrim = pins.contains(wire.start()) ? 1 : 0;
        int endTrim = pins.contains(wire.end()) ? 1 : 0;
        if (wire.isHorizontal()) {
            int startX = wire.start().x() + startTrim;
            int endX = Math.max(startX, wire.end().x() - endTrim);
            return new Bounds(startX, wire.start().y(), endX - startX, 0);
        }
        int startY = wire.start().y() + startTrim;
        int endY = Math.max(startY, wire.end().y() - endTrim);
        return new Bounds(wire.start().x(), startY, 0, endY - startY);
    }

    /** True when a stretch of positive length of {@code wire} lies strictly inside {@code body}. */
    private static boolean runsInside(Wire wire, Bounds body) {
        if (wire.isHorizontal()) {
            int y = wire.start().y();
            return y > body.y() && y < body.maximumY()
                    && Math.min(wire.end().x(), body.maximumX()) - Math.max(wire.start().x(), body.x()) > 0;
        }
        int x = wire.start().x();
        return x > body.x() && x < body.maximumX()
                && Math.min(wire.end().y(), body.maximumY()) - Math.max(wire.start().y(), body.y()) > 0;
    }

    // --- frames ---

    /** True when {@code candidate} lies in the same frames as {@code item} does now and enters no other. */
    private boolean keepsItsFrames(Item item, Bounds candidate) {
        for (Frame frame : frames) {
            boolean enclosedNow = encloses(frame, item.bounds());
            if (enclosedNow != encloses(frame, candidate)) {
                return false;
            }
            if (!enclosedNow && !overlaps(rectangleOf(frame), item.bounds(), 0)
                    && overlaps(rectangleOf(frame), candidate, 0)) {
                return false;
            }
        }
        return true;
    }

    private static boolean encloses(Frame frame, Bounds bounds) {
        return frame.encloses(bounds.x(), bounds.y(), bounds.maximumX(), bounds.maximumY());
    }

    private static Bounds rectangleOf(Frame frame) {
        return new Bounds(frame.topLeft().x(), frame.topLeft().y(), frame.width(), frame.height());
    }

    private static long area(Frame frame) {
        return (long) frame.width() * frame.height();
    }

    // --- small helpers ---

    private static LayoutBox fixed(String name, Bounds bounds) {
        return new LayoutBox(new ElementId(name), bounds, false, 0);
    }

    private static String idPrefix(ElementId id) {
        return id.value().substring(0, digitsStart(id.value()));
    }

    private static BigInteger idNumber(ElementId id) {
        String digits = id.value().substring(digitsStart(id.value()));
        return digits.isEmpty() ? BigInteger.valueOf(-1) : new BigInteger(digits);
    }

    /** Where the run of digits that ends the id starts (the length when it ends in no digit). */
    private static int digitsStart(String value) {
        int start = value.length();
        while (start > 0 && isAsciiDigit(value.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
