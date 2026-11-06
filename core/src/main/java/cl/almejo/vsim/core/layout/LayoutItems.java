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
 * component with its own label, free text and sticky note, components joined pin on pin (no wire between them) made
 * one rigid item. An item is movable only when all its members are in scope and it lies wholly inside one area (the
 * circuit, or the innermost frame around it); one that straddles a frame border stays.
 *
 * <p>Each area is separated on its own ({@link #separationGroups}): its movable items, every fixed item, a thin box
 * on each side just outside the area's frame (the walls keep its items in), every other frame as an obstacle (so
 * nothing enters it) and the wires that run through a foreign body (so the body moves off them). Frames are
 * otherwise not boxes, and guides are ignored.
 */
final class LayoutItems {

    /** Space wanted around a part: two bodies end at least one grid unit apart (spec §4.3). */
    static final int BODY_GAP = 1;
    /** Texts (labels, free texts, sticky notes) may touch what they are next to. */
    static final int TEXT_GAP = 0;

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
     * One thing that moves as a whole: a component (with its label), a pin-on-pin cluster of components, a free text
     * or a sticky note. {@code id} is the smallest member id; {@code region} the innermost frame around it (empty: the
     * circuit itself); {@code parts} and {@code texts} count its members for the summary; {@code partlyInScope} says
     * at least one member may change (a fixed item that is partly in scope and overlaps something is reported).
     */
    record Item(ElementId id, List<ElementId> members, Bounds bounds, int gap, boolean movable,
                boolean partlyInScope, Optional<ElementId> region, int parts, int texts) {

        Item {
            members = List.copyOf(members);
        }
    }

    private final List<Item> items;
    private final Map<ElementId, Item> itemsById;
    private final List<Frame> frames;
    private final List<LayoutBox> wireObstacles;

    private LayoutItems(List<Item> items, List<Frame> frames, List<LayoutBox> wireObstacles) {
        this.items = List.copyOf(items);
        this.frames = List.copyOf(frames);
        this.wireObstacles = List.copyOf(wireObstacles);
        Map<ElementId, Item> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.put(item.id(), item));
        this.itemsById = byId;
    }

    /**
     * The items of {@code document}. {@code scope} holds the ids allowed to change; {@code texts} measures labels
     * and free texts. Components of an unknown type have no size and are left out.
     */
    static LayoutItems of(CircuitDocument document, ComponentTypeRegistry registry, Set<ElementId> scope,
                          TextBoxes texts) {
        List<Frame> frames = document.frames();
        List<Item> items = new ArrayList<>();
        for (List<Component> cluster : clusters(document, registry)) {
            items.add(componentItem(cluster, registry, scope, texts, frames));
        }
        for (TextLabel label : document.labels()) {
            items.add(textItem(label.id(), texts.freeText(label), scope, frames));
        }
        for (StickyNote note : document.stickyNotes()) {
            items.add(textItem(note.id(), new Bounds(note.position().x(), note.position().y(), note.visibleWidth(),
                    note.visibleHeight()), scope, frames));
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
     * One list of boxes per area that has a movable item: what {@link OverlapSeparation#separate} gets for it. Areas
     * in order: the circuit first, then frames in document order.
     */
    List<List<LayoutBox>> separationGroups() {
        List<Optional<ElementId>> regions = new ArrayList<>();
        regions.add(Optional.empty());
        frames.forEach(frame -> regions.add(Optional.of(frame.id())));
        List<List<LayoutBox>> groups = new ArrayList<>();
        for (Optional<ElementId> region : regions) {
            List<LayoutBox> boxes = new ArrayList<>();
            for (Item item : items) {
                if (item.movable() && item.region().equals(region)) {
                    boxes.add(new LayoutBox(item.id(), item.bounds(), true, item.gap()));
                }
            }
            if (boxes.isEmpty()) {
                continue;
            }
            for (Item item : items) {
                if (!item.movable()) {
                    boxes.add(new LayoutBox(item.id(), item.bounds(), false, item.gap()));
                }
            }
            boxes.addAll(regionObstacles(region, Optional.empty()));
            boxes.addAll(wireObstacles);
            groups.add(boxes);
        }
        return groups;
    }

    /** The fixed boxes that are not items and that {@code item} must keep clear of: walls, frames and wires. */
    List<LayoutBox> obstacles(Item item) {
        List<LayoutBox> obstacles = new ArrayList<>(regionObstacles(item.region(), Optional.of(item.bounds())));
        obstacles.addAll(wireObstacles);
        return obstacles;
    }

    /**
     * True when {@code item} at {@code candidate} keeps clear of every other item where {@code placed} says it is
     * (items missing from it are where they started), of its {@link #obstacles}, and stays in the same frames.
     */
    boolean isFree(Item item, Bounds candidate, Map<ElementId, Bounds> placed) {
        for (Item other : items) {
            if (other.id().equals(item.id())) {
                continue;
            }
            Bounds otherBounds = placed.getOrDefault(other.id(), other.bounds());
            if (overlaps(candidate, otherBounds, Math.max(item.gap(), other.gap()))) {
                return false;
            }
        }
        for (LayoutBox obstacle : obstacles(item)) {
            if (overlaps(candidate, obstacle.bounds(), Math.max(item.gap(), obstacle.gap()))) {
                return false;
            }
        }
        return keepsItsFrames(item, candidate);
    }

    /**
     * The ids of the other items that {@code item} at {@code at} overlaps, each where {@code placed} says it is: what
     * still needs separating. Walls and frames do not count (a part may touch a frame's border).
     */
    List<ElementId> overlappedItems(Item item, Bounds at, Map<ElementId, Bounds> placed) {
        List<ElementId> overlapped = new ArrayList<>();
        for (Item other : items) {
            if (!other.id().equals(item.id()) && overlaps(at, placed.getOrDefault(other.id(), other.bounds()),
                    Math.max(item.gap(), other.gap()))) {
                overlapped.add(other.id());
            }
        }
        return overlapped;
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
     * True when, moved by the delta, a body of {@code members} would have a wire of {@code document} run through its
     * inside without ending on one of the members' pins (where they are now: the wires on them follow the move).
     */
    static boolean crossedByForeignWire(CircuitDocument document, ComponentTypeRegistry registry,
                                        Collection<ElementId> members, int deltaX, int deltaY) {
        Set<GridPoint> pins = new HashSet<>();
        List<Bounds> bodies = new ArrayList<>();
        for (ElementId id : members) {
            Optional<Component> component = document.component(id);
            Optional<ComponentType> type = component.flatMap(registry::findTypeOf);
            if (type.isEmpty()) {
                continue;
            }
            pins.addAll(type.get().pinLocations(component.get()));
            bodies.add(type.get().bounds(component.get()).translate(deltaX, deltaY));
        }
        for (Wire wire : document.wires()) {
            if (pins.contains(wire.start()) || pins.contains(wire.end())) {
                continue;
            }
            for (Bounds body : bodies) {
                if (runsInside(wire, body)) {
                    return true;
                }
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
        List<ElementId> members = new ArrayList<>();
        for (Component component : cluster) {
            Bounds body = registry.typeOf(component).bounds(component);
            Bounds withLabel = texts.componentLabel(component).map(body::union).orElse(body);
            bounds = bounds == null ? withLabel : bounds.union(withLabel);
            members.add(component.id());
        }
        return item(members, bounds, BODY_GAP, scope, frames, cluster.size(), 0);
    }

    private static Item textItem(ElementId id, Bounds bounds, Set<ElementId> scope, List<Frame> frames) {
        return item(List.of(id), bounds, TEXT_GAP, scope, frames, 0, 1);
    }

    private static Item item(List<ElementId> members, Bounds bounds, int gap, Set<ElementId> scope,
                             List<Frame> frames, int parts, int texts) {
        boolean inScope = scope.containsAll(members);
        boolean partlyInScope = members.stream().anyMatch(scope::contains);
        boolean straddles = frames.stream().anyMatch(frame -> straddles(frame, bounds));
        Optional<ElementId> region = straddles ? Optional.empty() : innermostAround(frames, bounds);
        return new Item(members.get(0), members, bounds, gap, inScope && !straddles, partlyInScope, region, parts,
                texts);
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
     * A line for every wire that runs through a body it does not end on, one unit shorter at each end that is a pin
     * (so the parts at its ends, one unit away, are not pushed by it).
     */
    private static List<LayoutBox> wireObstacles(CircuitDocument document, ComponentTypeRegistry registry) {
        Map<ElementId, Set<GridPoint>> pinsByComponent = new LinkedHashMap<>();
        Map<ElementId, Bounds> bodies = new LinkedHashMap<>();
        Set<GridPoint> allPins = new HashSet<>();
        for (Component component : document.components()) {
            registry.findTypeOf(component).ifPresent(type -> {
                Set<GridPoint> pins = new HashSet<>(type.pinLocations(component));
                pinsByComponent.put(component.id(), pins);
                bodies.put(component.id(), type.bounds(component));
                allPins.addAll(pins);
            });
        }
        List<LayoutBox> obstacles = new ArrayList<>();
        for (Wire wire : document.wires()) {
            boolean crossesABody = bodies.entrySet().stream().anyMatch(body -> runsInside(wire, body.getValue())
                    && !pinsByComponent.get(body.getKey()).contains(wire.start())
                    && !pinsByComponent.get(body.getKey()).contains(wire.end()));
            if (crossesABody) {
                obstacles.add(fixed(WIRE_OBSTACLE_PREFIX + wire.id().value(), shortened(wire, allPins)));
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
