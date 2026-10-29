package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Points where a wire can start: component pins and grid points on existing wires. */
public final class ConnectionPoints {

    public static final double PIN_RADIUS = 0.6;

    private final ComponentTypeRegistry registry;
    /** The last document's drawing, kept while its structure revision lasts (read and replaced whole). */
    private volatile Drawing drawing;

    public ConnectionPoints(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    /** A pin location together with the component that owns it and the pin's index in its type. */
    public record PinHit(GridPoint point, Component component, int pinIndex) {
    }

    /** The closest pin within {@link #PIN_RADIUS} grid units of {@code gridPoint}, with its owner. */
    public Optional<PinHit> pinNear(CircuitDocument document, Point2D gridPoint) {
        PinHit closest = null;
        double closestDistance = PIN_RADIUS;
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                GridPoint pin = type.pinLocation(component, pinIndex);
                double distance = gridPoint.distance(pin.x(), pin.y());
                if (distance <= closestDistance) {
                    closest = new PinHit(pin, component, pinIndex);
                    closestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(closest);
    }

    /**
     * True when {@code point} already is a connection: a component pin, a wire end or a point on a wire's interior.
     * Ajustar never moves such a point to a guide or a grid line (alignment-guides spec §4.4).
     */
    public boolean isConnection(CircuitDocument document, GridPoint point) {
        for (Wire wire : document.wires()) {
            if (wire.contains(point)) {
                return true;
            }
        }
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                if (type.pinLocation(component, pinIndex).equals(point)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The widest pin of any component located at {@code point}; 1 when there is none. A pin without a width of its
     * own (a tunnel's) counts with the width of its net in {@code netlist}, so a wire drawn from a tunnel on a bus
     * previews as a bus.
     */
    public int widthAt(CircuitDocument document, Netlist netlist, GridPoint point) {
        int widest = 1;
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                if (type.pinLocation(component, pinIndex).equals(point)) {
                    widest = Math.max(widest, widthOf(component, type, pinIndex, netlist));
                }
            }
        }
        return widest;
    }

    private static int widthOf(Component component, ComponentType type, int pinIndex, Netlist netlist) {
        if (!type.pin(pinIndex).isAdaptiveWidth()) {
            return type.pin(pinIndex).width();
        }
        return netlist.netForPin(new PinRef(component.id(), pinIndex)).map(Net::width).orElse(1);
    }

    /**
     * A pin whose net holds nothing else (no wire, no other pin), or that has no net: the red ring and the
     * «(sin conectar)» tooltip share this one rule.
     */
    public static boolean isOpen(Optional<Net> net) {
        return net.map(found -> found.pins().size() == 1 && found.wires().isEmpty()).orElse(true);
    }

    /**
     * {@link #isOpen(Optional)} for pin {@code pinIndex} of {@code component}, whose net is {@code net}, except for a
     * tunnel: its net also holds every other tunnel of its name, which says nothing about its own wire, so a tunnel's
     * pin is open when nothing is drawn at its point (no wire through it, no other pin on it).
     */
    public boolean isOpen(CircuitDocument document, Component component, int pinIndex, Optional<Net> net) {
        if (!Tunnels.isTunnel(component.typeId())) {
            return isOpen(net);
        }
        GridPoint point = registry.typeOf(component).pinLocation(component, pinIndex);
        Drawing current = drawingOf(document);
        return !current.hasWireThrough(point) && !current.hasAnotherPinAt(component.id(), point);
    }

    /** The drawing of {@code document} at its current structure revision, built when it is not the one kept. */
    private Drawing drawingOf(CircuitDocument document) {
        Drawing current = drawing;
        if (current == null || current.document != document
                || current.structureRevision != document.structureRevision()) {
            current = new Drawing(document, registry);
            drawing = current;
        }
        return current;
    }

    /** Who has a pin at a point: one owner, and whether some other component has one there too. */
    private record PinOwners(ElementId ownerId, boolean others) {
    }

    /**
     * Where the pins and the wires of a document lie, so that asking what is drawn at a point costs nothing: a frame
     * asks it once per tunnel, and a document with thousands of tunnels would otherwise walk all its pieces for each.
     */
    private static final class Drawing {

        final CircuitDocument document;
        final long structureRevision;
        private final Map<GridPoint, PinOwners> pinOwners = new HashMap<>();
        private final Map<Integer, List<Wire>> horizontalWiresByRow = new HashMap<>();
        private final Map<Integer, List<Wire>> verticalWiresByColumn = new HashMap<>();

        Drawing(CircuitDocument document, ComponentTypeRegistry registry) {
            this.document = document;
            this.structureRevision = document.structureRevision();
            for (Component component : document.components()) {
                ComponentType type = registry.typeOf(component);
                for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                    pinOwners.merge(type.pinLocation(component, pinIndex), new PinOwners(component.id(), false),
                            (present, added) -> present.others() || present.ownerId().equals(added.ownerId())
                                    ? present : new PinOwners(present.ownerId(), true));
                }
            }
            for (Wire wire : document.wires()) {
                Map<Integer, List<Wire>> byLine = wire.isHorizontal() ? horizontalWiresByRow : verticalWiresByColumn;
                int line = wire.isHorizontal() ? wire.start().y() : wire.start().x();
                byLine.computeIfAbsent(line, key -> new ArrayList<>()).add(wire);
            }
        }

        boolean hasWireThrough(GridPoint point) {
            for (Wire wire : horizontalWiresByRow.getOrDefault(point.y(), List.of())) {
                if (wire.contains(point)) {
                    return true;
                }
            }
            for (Wire wire : verticalWiresByColumn.getOrDefault(point.x(), List.of())) {
                if (wire.contains(point)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasAnotherPinAt(ElementId owner, GridPoint point) {
            PinOwners owners = pinOwners.get(point);
            return owners != null && (owners.others() || !owners.ownerId().equals(owner));
        }
    }

    /** The grid point of {@code wire} nearest to {@code gridPoint}. */
    public static GridPoint pointOnWire(Wire wire, Point2D gridPoint) {
        if (wire.isHorizontal()) {
            long x = Math.round(gridPoint.getX());
            return new GridPoint((int) Math.max(wire.start().x(), Math.min(wire.end().x(), x)), wire.start().y());
        }
        long y = Math.round(gridPoint.getY());
        return new GridPoint(wire.start().x(), (int) Math.max(wire.start().y(), Math.min(wire.end().y(), y)));
    }
}
