package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.wiring.ShapeChangePlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pieces and wires of a {@link ShapeChangePlan}, with what they were when the plan was made, so the change can
 * be applied and reverted exactly (wire ids and paint order included). Shared by the shape and update commands; it
 * never touches definitions and runs inside the caller's batch.
 */
final class PlannedShapeChange {
    private final ShapeChangePlan plan;
    private final Map<ElementId, Component> previousInstances = new LinkedHashMap<>();
    private final Map<ElementId, Wire> previousWires = new LinkedHashMap<>();
    private final Map<ElementId, Long> removedOrders = new LinkedHashMap<>();

    /** Records the current state in {@code document} of every piece and wire {@code plan} touches. */
    PlannedShapeChange(CircuitDocument document, ShapeChangePlan plan) {
        this.plan = plan;
        plan.instances().keySet().forEach(id -> previousInstances.put(id, document.component(id).orElseThrow()));
        plan.wires().replacedWires().forEach(wire -> previousWires.put(wire.id(),
                document.wire(wire.id()).orElseThrow()));
        plan.wires().removedWireIds().forEach(id -> {
            previousWires.put(id, document.wire(id).orElseThrow());
            removedOrders.put(id, document.orderOf(id));
        });
    }

    ShapeChangePlan plan() {
        return plan;
    }

    /** True when every piece stays as it was and no wire changes. */
    boolean changesNothing() {
        return plan.wires().isEmpty() && previousInstances.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(plan.instances().get(entry.getKey())));
    }

    void apply(CircuitDocument document) {
        plan.instances().values().forEach(document::replace);
        plan.wires().removedWireIds().forEach(document::remove);
        plan.wires().replacedWires().forEach(document::replace);
        plan.wires().addedWires().forEach(document::add);
    }

    void revert(CircuitDocument document) {
        plan.wires().addedWires().forEach(wire -> document.remove(wire.id()));
        previousInstances.values().forEach(document::replace);
        previousWires.forEach((id, wire) -> {
            if (removedOrders.containsKey(id)) {
                document.restore(wire, removedOrders.get(id));
            } else {
                document.replace(wire);
            }
        });
    }
}
