package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic appearance replacements and checked wire routing, plus the user-visible exceptions to full following. */
public record ShapeChangePlan(FollowPlan wires, Map<ElementId, Component> instances, List<PinRef> disconnected,
                              List<ForeignContact> keptOldShape) {
    public ShapeChangePlan {
        Objects.requireNonNull(wires, "wires");
        instances = Map.copyOf(instances);
        disconnected = List.copyOf(disconnected);
        keptOldShape = List.copyOf(keptOldShape);
    }
}
