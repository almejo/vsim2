package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The accidental contacts of a move, a copy or a ghost (alignment guides spec §3.7): the points where, after
 * translating {@code movedElements} by the delta, a pin or a wire end meets things of a net it is not joined to
 * today. One rule with {@link WireFollow}, which reports its contacts through {@link #contactsAt} too: a pin or a wire
 * end at a point joins every wire through it (end or interior) and every pin there, as {@code NetlistCompiler} does;
 * crossing interiors do not join.
 *
 * <p>A moved element whose id is in {@code excludedIds} is the document element itself being moved: it keeps the net
 * it has in the document, and the document copy is left out. Any other moved element (a Ctrl copy of a document
 * element, a pasted or placed ghost) is new: its nets are those of the moved elements among themselves, never joined
 * to the document, so a copy touches its originals. Only the document elements within the moved points' box grown by
 * one unit are looked at ({@link #lastVisitedCount()}); the nets of today come from the whole document, built once per
 * probe and only when a contact has to be decided.
 *
 * <p>Pure: it reads the document and changes nothing. A probe is for one revision of the document.
 */
public final class ContactProbe {

    private static final String PLACEHOLDER_PREFIX = "contact-probe-";

    private final CircuitDocument document;
    private final ComponentTypeRegistry registry;
    private final List<Component> documentComponents;
    private final List<Wire> documentWires;
    private final Map<ElementId, List<GridPoint>> documentPinPoints = new HashMap<>();
    private PointNets documentNets;
    private int lastVisitedCount;

    private ContactProbe(CircuitDocument document, ComponentTypeRegistry registry) {
        this.document = document;
        this.registry = registry;
        this.documentComponents = document.components();
        this.documentWires = document.wires();
        for (Component component : documentComponents) {
            documentPinPoints.put(component.id(), pinPoints(registry, component));
        }
    }

    /** A probe over the document as it is now; keep it while the document's revision does not change. */
    public static ContactProbe of(CircuitDocument document, ComponentTypeRegistry registry) {
        return new ContactProbe(document, registry);
    }

    /** {@link #newContacts(Collection, int, int, Set)} with a probe made for this one call. */
    public static List<NewContact> newContacts(CircuitDocument document, ComponentTypeRegistry registry,
                                               Collection<? extends DocumentElement> movedElements, int deltaX,
                                               int deltaY, Set<ElementId> excludedIds) {
        return of(document, registry).newContacts(movedElements, deltaX, deltaY, excludedIds);
    }

    /**
     * The new contacts of {@code movedElements} translated by the delta, sorted by point; labels and frames make none.
     * {@code excludedIds} are the document elements being moved (empty for a copy or a ghost). A contact names the
     * pin there, preferring one that stays; a moved pin is named by the id of the moved element.
     */
    public List<NewContact> newContacts(Collection<? extends DocumentElement> movedElements, int deltaX, int deltaY,
                                        Set<ElementId> excludedIds) {
        lastVisitedCount = 0;
        Moved moved = new Moved(movedElements, deltaX, deltaY, excludedIds);
        if (moved.points.isEmpty()) {
            return List.of();
        }
        Bounds box = moved.box();
        Map<GridPoint, List<PinRef>> pinsAtPoint = new HashMap<>();
        Set<ElementId> visited = new HashSet<>();
        for (Component component : documentComponents) {
            if (excludedIds.contains(component.id())) {
                continue;
            }
            List<GridPoint> points = documentPinPoints.get(component.id());
            for (int pinIndex = 0; pinIndex < points.size(); pinIndex++) {
                GridPoint point = points.get(pinIndex);
                if (box.contains(point)) {
                    visited.add(component.id());
                    pinsAtPoint.computeIfAbsent(point, key -> new ArrayList<>())
                            .add(new PinRef(component.id(), pinIndex));
                }
            }
        }
        List<Wire> wires = new ArrayList<>();
        for (Wire wire : documentWires) {
            if (!excludedIds.contains(wire.id()) && box.intersects(Bounds.of(wire.start(), wire.end()))) {
                visited.add(wire.id());
                wires.add(wire);
            }
        }
        lastVisitedCount = visited.size();
        if (visited.isEmpty()) {
            return List.of();
        }
        moved.pinPoints.forEach((pin, point) -> pinsAtPoint.computeIfAbsent(point, key -> new ArrayList<>()).add(pin));
        wires.addAll(moved.wires);
        WireGraph after = WireGraph.of(wires, pinsAtPoint);
        SortedSet<GridPoint> candidates = new TreeSet<>(moved.pinPoints.values());
        for (Wire wire : moved.wires) {
            candidates.addAll(after.connectionPointsOn(wire));
        }
        return contactsAt(candidates, after, moved);
    }

    /** How many document elements the last {@link #newContacts} looked at: those within the moved box. */
    public int lastVisitedCount() {
        return lastVisitedCount;
    }

    private PointNets documentNets() {
        if (documentNets == null) {
            Map<PinRef, GridPoint> pins = new LinkedHashMap<>();
            for (Component component : documentComponents) {
                List<GridPoint> points = documentPinPoints.get(component.id());
                for (int pinIndex = 0; pinIndex < points.size(); pinIndex++) {
                    pins.put(new PinRef(component.id(), pinIndex), points.get(pinIndex));
                }
            }
            documentNets = new PointNets(pins, documentWires);
        }
        return documentNets;
    }

    private static List<GridPoint> pinPoints(ComponentTypeRegistry registry, Component component) {
        ComponentType type = registry.typeOf(component);
        List<GridPoint> points = new ArrayList<>(type.pinCount());
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            points.add(type.pinLocation(component, pinIndex));
        }
        return points;
    }

    // --- the shared rule ---

    /** What {@link #contactsAt} needs to know about the things of one layout after a move. */
    interface Layout {

        /** The net of today of a wire of the layout; equal keys mean joined today. */
        Object netOfWire(ElementId wireId);

        /** The net of today of a pin of the layout. */
        Object netOfPin(PinRef pin);

        boolean isMoved(PinRef pin);

        /** Orders pins of the same kind (moved or not) when choosing the one a contact names. */
        long orderOf(ElementId componentId);

        /** The id the contact reports for the component of {@code pin}. */
        ElementId reportedComponentId(ElementId componentId);

        String pinName(PinRef pin);
    }

    /**
     * The contacts at {@code candidates}, in their order: points with a pin or a wire end where things of more than one
     * net of today meet.
     */
    static List<NewContact> contactsAt(Collection<GridPoint> candidates, WireGraph after, Layout layout) {
        List<NewContact> contacts = new ArrayList<>();
        for (GridPoint point : candidates) {
            List<PinRef> pins = after.pinsAt(point);
            List<Wire> through = after.wiresThrough(point);
            boolean anEndIsHere = !pins.isEmpty() || through.stream()
                    .anyMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
            if (!anEndIsHere) {
                continue;
            }
            Set<Object> netsHere = new HashSet<>();
            for (Wire wire : through) {
                netsHere.add(layout.netOfWire(wire.id()));
            }
            for (PinRef pin : pins) {
                netsHere.add(layout.netOfPin(pin));
            }
            if (netsHere.size() > 1) {
                contacts.add(contactAt(point, pins, through, layout));
            }
        }
        return contacts;
    }

    /** Names the pin touched by something of another net, preferring a pin that stays over one that moved. */
    private static NewContact contactAt(GridPoint point, List<PinRef> pins, List<Wire> through, Layout layout) {
        Set<Object> wireNets = new HashSet<>();
        through.forEach(wire -> wireNets.add(layout.netOfWire(wire.id())));
        Optional<PinRef> named = pins.stream()
                .sorted(Comparator.comparing(layout::isMoved)
                        .thenComparing(pin -> !wireNets.isEmpty() && wireNets.contains(layout.netOfPin(pin)))
                        .thenComparing(pin -> layout.orderOf(pin.componentId()))
                        .thenComparingInt(PinRef::pinIndex))
                .findFirst();
        return new NewContact(point, named.map(pin -> layout.reportedComponentId(pin.componentId())),
                named.map(layout::pinName));
    }

    // --- the moved elements ---

    /** A net of today, of the document or of the new moved elements among themselves. */
    private record DocumentNet(GridPoint root) {
    }

    private record NewNet(GridPoint root) {
    }

    /**
     * The moved elements translated, under placeholder ids (a copy or a ghost may share ids with the document), with
     * what {@link #contactsAt} needs about them and about the document elements of the layout.
     */
    private final class Moved implements Layout {

        private final Map<PinRef, GridPoint> pinPoints = new LinkedHashMap<>();
        private final List<Wire> wires = new ArrayList<>();
        private final Set<GridPoint> points = new LinkedHashSet<>();
        private final Map<ElementId, ElementId> originalIds = new HashMap<>();
        /** The document element each kept placeholder stands for (moved itself, not copied). */
        private final Map<ElementId, DocumentElement> keptOriginals = new HashMap<>();
        private final Map<ElementId, Long> orders = new HashMap<>();
        private final Map<PinRef, String> pinNames = new HashMap<>();
        private final PointNets newNets;

        Moved(Collection<? extends DocumentElement> movedElements, int deltaX, int deltaY, Set<ElementId> excludedIds) {
            Map<PinRef, GridPoint> newPins = new HashMap<>();
            List<Wire> newWires = new ArrayList<>();
            for (DocumentElement element : movedElements) {
                if (!(element instanceof Component) && !(element instanceof Wire)) {
                    continue;
                }
                ElementId placeholder = new ElementId(PLACEHOLDER_PREFIX + originalIds.size());
                originalIds.put(placeholder, element.id());
                orders.put(placeholder, (long) orders.size());
                Optional<DocumentElement> original = excludedIds.contains(element.id())
                        ? document.element(element.id()) : Optional.empty();
                original.ifPresent(kept -> keptOriginals.put(placeholder, kept));
                if (element instanceof Component component) {
                    ComponentType type = registry.typeOf(component);
                    Component placed = component.translate(deltaX, deltaY);
                    for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                        PinRef pin = new PinRef(placeholder, pinIndex);
                        GridPoint point = type.pinLocation(placed, pinIndex);
                        pinPoints.put(pin, point);
                        pinNames.put(pin, type.pin(pinIndex).name());
                        points.add(point);
                        if (original.isEmpty()) {
                            newPins.put(pin, point);
                        }
                    }
                } else if (element instanceof Wire wire) {
                    Wire placed = new Wire(placeholder, wire.start(), wire.end()).translate(deltaX, deltaY);
                    wires.add(placed);
                    points.add(placed.start());
                    points.add(placed.end());
                    if (original.isEmpty()) {
                        newWires.add(placed);
                    }
                }
            }
            newNets = new PointNets(newPins, newWires);
        }

        Bounds box() {
            int minimumX = Integer.MAX_VALUE;
            int minimumY = Integer.MAX_VALUE;
            int maximumX = Integer.MIN_VALUE;
            int maximumY = Integer.MIN_VALUE;
            for (GridPoint point : points) {
                minimumX = Math.min(minimumX, point.x());
                minimumY = Math.min(minimumY, point.y());
                maximumX = Math.max(maximumX, point.x());
                maximumY = Math.max(maximumY, point.y());
            }
            return new Bounds(minimumX - 1, minimumY - 1, maximumX - minimumX + 2, maximumY - minimumY + 2);
        }

        private boolean isPlaceholder(ElementId id) {
            return originalIds.containsKey(id);
        }

        @Override
        public Object netOfWire(ElementId wireId) {
            if (!isPlaceholder(wireId)) {
                return new DocumentNet(documentNets().find(document.wire(wireId).orElseThrow().start()));
            }
            DocumentElement kept = keptOriginals.get(wireId);
            if (kept instanceof Wire original) {
                return new DocumentNet(documentNets().find(original.start()));
            }
            return new NewNet(newNets.find(newNets.wireStart(wireId)));
        }

        @Override
        public Object netOfPin(PinRef pin) {
            if (!isPlaceholder(pin.componentId())) {
                return new DocumentNet(documentNets().find(documentPinPoints.get(pin.componentId())
                        .get(pin.pinIndex())));
            }
            DocumentElement kept = keptOriginals.get(pin.componentId());
            if (kept instanceof Component original) {
                return new DocumentNet(documentNets().find(documentPinPoints.get(original.id())
                        .get(pin.pinIndex())));
            }
            return new NewNet(newNets.find(newNets.pinPoint(pin)));
        }

        @Override
        public boolean isMoved(PinRef pin) {
            return isPlaceholder(pin.componentId());
        }

        @Override
        public long orderOf(ElementId componentId) {
            return isPlaceholder(componentId) ? orders.get(componentId) : document.orderOf(componentId);
        }

        @Override
        public ElementId reportedComponentId(ElementId componentId) {
            return originalIds.getOrDefault(componentId, componentId);
        }

        @Override
        public String pinName(PinRef pin) {
            if (isPlaceholder(pin.componentId())) {
                return pinNames.get(pin);
            }
            return registry.typeOf(document.component(pin.componentId()).orElseThrow()).pin(pin.pinIndex()).name();
        }
    }
}
