package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Removes the stretch of a wire between the nearest points of interest around {@code near}.
 *
 * <p>Points of interest are the wire's endpoints, the endpoints of other wires on it and the pins on it. A wire
 * that only crosses it is not one. {@code near} is clamped to the wire. When it falls exactly on an interior
 * point of interest, the stretch after that point goes.
 *
 * <p>The remaining piece before the cut keeps the wire's id (or the piece after it, when there is none before).
 * A second remaining piece gets a fresh id on the first apply, reused on redo.
 */
public final class RemoveWireSegmentCommand implements Command {

    private final ElementId wireId;
    private final GridPoint near;
    private final ComponentTypeRegistry registry;
    private Wire original;
    private long originalOrder;
    private Wire keptPiece;
    private Wire addedPiece;
    private ElementId addedPieceId;

    public RemoveWireSegmentCommand(ElementId wireId, GridPoint near, ComponentTypeRegistry registry) {
        this.wireId = Objects.requireNonNull(wireId, "wireId");
        this.near = Objects.requireNonNull(near, "near");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void apply(CircuitDocument document) {
        Wire wire = document.wire(wireId)
                .orElseThrow(() -> new IllegalArgumentException("no wire with id " + wireId));
        TreeSet<Integer> stops = stops(document, wire);
        int first = stops.first();
        int last = stops.last();
        int position = Math.max(first, Math.min(last, along(wire, near)));
        Integer next = stops.higher(position);
        int lower = next == null ? stops.lower(position) : stops.floor(position);
        int upper = next == null ? position : next;
        GridPoint cutStart = at(wire, lower);
        GridPoint cutEnd = at(wire, upper);
        boolean keepsBefore = lower > first;
        boolean keepsAfter = upper < last;

        original = wire;
        originalOrder = document.orderOf(wireId);
        keptPiece = keepsBefore ? wire.withEndpoints(wire.start(), cutStart)
                : keepsAfter ? wire.withEndpoints(cutEnd, wire.end())
                : null;
        if (keepsBefore && keepsAfter) {
            if (addedPieceId == null) {
                addedPieceId = document.newWireId();
            }
            addedPiece = new Wire(addedPieceId, cutEnd, wire.end());
        } else {
            addedPiece = null;
        }
        document.batch(() -> {
            if (keptPiece == null) {
                document.remove(wireId);
            } else {
                document.replace(keptPiece);
            }
            if (addedPiece != null) {
                document.add(addedPiece);
            }
        });
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            if (addedPiece != null) {
                document.remove(addedPiece.id());
            }
            if (keptPiece == null) {
                document.restore(original, originalOrder);
            } else {
                document.replace(original);
            }
        });
    }

    @Override
    public String description() {
        return "command.removeSegment";
    }

    /** Coordinates along the wire's axis of its points of interest. */
    private TreeSet<Integer> stops(CircuitDocument document, Wire wire) {
        TreeSet<Integer> stops = new TreeSet<>(List.of(along(wire, wire.start()), along(wire, wire.end())));
        for (Wire other : document.wires()) {
            if (other.id().equals(wire.id())) {
                continue;
            }
            for (GridPoint endpoint : List.of(other.start(), other.end())) {
                if (wire.contains(endpoint)) {
                    stops.add(along(wire, endpoint));
                }
            }
        }
        for (Component component : document.components()) {
            ComponentType type = registry.require(component.typeId());
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                GridPoint pin = type.pinLocation(component, pinIndex);
                if (wire.contains(pin)) {
                    stops.add(along(wire, pin));
                }
            }
        }
        return stops;
    }

    private static int along(Wire wire, GridPoint point) {
        return wire.isHorizontal() ? point.x() : point.y();
    }

    private static GridPoint at(Wire wire, int coordinate) {
        return wire.isHorizontal() ? new GridPoint(coordinate, wire.start().y())
                : new GridPoint(wire.start().x(), coordinate);
    }
}
