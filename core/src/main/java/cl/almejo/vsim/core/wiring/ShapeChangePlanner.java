package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plans all changed pins together and checks the resulting electrical partition, including probes and tunnels.
 * A piece that would touch a foreign net retains its complete old geometry; no partial pin fallback is accepted.
 */
public final class ShapeChangePlanner {
    private ShapeChangePlanner() {
    }

    /** Registries resolve the original and proposed definitions respectively; neither document is mutated. */
    public static ShapeChangePlan plan(CircuitDocument document, ComponentTypeRegistry beforeRegistry,
                                       ComponentTypeRegistry afterRegistry, Map<ElementId, Component> instancesAfter) {
        Map<ElementId, Component> instances = new LinkedHashMap<>(instancesAfter);
        Map<ElementId, ForeignContact> retained = new LinkedHashMap<>();
        Connectivity before = Connectivity.of(document.wires(), document, beforeRegistry);
        while (true) {
            Map<PinRef, GridPoint> moves = moves(document, beforeRegistry, afterRegistry, instances);
            FollowPlan wires = WireFollow.plan(document, beforeRegistry, new PinMoves(moves), document::newWireId);
            Set<ElementId> blocked = blockedInstances(afterRegistry, instances, wires.newContacts());
            blocked.removeAll(retained.keySet());
            if (!blocked.isEmpty()) {
                retainOld(document, beforeRegistry, instances, retained, blocked, wires.newContacts());
                continue;
            }
            CircuitDocument candidate = after(document, instances, wires);
            Connectivity resulting = Connectivity.of(candidate.wires(), candidate, afterRegistry);
            List<PinRef> disconnected = disconnected(before, resulting, moves.keySet());
            if (without(before, disconnected).equals(without(resulting, disconnected))) {
                return new ShapeChangePlan(wires, instances, disconnected, new ArrayList<>(retained.values()));
            }
            Set<ElementId> remaining = new LinkedHashSet<>(instances.keySet());
            remaining.removeAll(retained.keySet());
            if (remaining.isEmpty()) {
                throw new IllegalStateException("shape plan cannot preserve the original connectivity");
            }
            // A failed route may split an unselected net or affect a probe. Retain every remaining piece instead
            // of treating that structural change as a permitted disconnection of just one selected pin.
            retainOld(document, beforeRegistry, instances, retained, remaining, List.of());
        }
    }

    private static Map<PinRef, GridPoint> moves(CircuitDocument document, ComponentTypeRegistry beforeRegistry,
                                               ComponentTypeRegistry afterRegistry,
                                               Map<ElementId, Component> instances) {
        Map<PinRef, GridPoint> result = new LinkedHashMap<>();
        for (Map.Entry<ElementId, Component> entry : instances.entrySet()) {
            Component before = document.component(entry.getKey()).orElseThrow();
            ComponentType original = beforeRegistry.typeOf(before);
            ComponentType replacement = afterRegistry.typeOf(entry.getValue());
            if (original.pinCount() != replacement.pinCount()) {
                throw new IllegalArgumentException("a shape edit cannot add or remove pins");
            }
            for (int index = 0; index < original.pinCount(); index++) {
                GridPoint target = replacement.pinLocation(entry.getValue(), index);
                if (!target.equals(original.pinLocation(before, index))) {
                    result.put(new PinRef(before.id(), index), target);
                }
            }
        }
        return result;
    }

    private static Set<ElementId> blockedInstances(ComponentTypeRegistry registry,
                                                    Map<ElementId, Component> instances,
                                                    List<NewContact> contacts) {
        Set<ElementId> blocked = new LinkedHashSet<>();
        for (NewContact contact : contacts) {
            for (Component instance : instances.values()) {
                ComponentType type = registry.typeOf(instance);
                for (int index = 0; index < type.pinCount(); index++) {
                    if (type.pinLocation(instance, index).equals(contact.point())) {
                        blocked.add(instance.id());
                    }
                }
            }
        }
        if (!contacts.isEmpty() && blocked.isEmpty()) {
            blocked.addAll(instances.keySet());
        }
        return blocked;
    }

    private static void retainOld(CircuitDocument document, ComponentTypeRegistry beforeRegistry,
                                   Map<ElementId, Component> instances, Map<ElementId, ForeignContact> retained,
                                   Set<ElementId> blocked, List<NewContact> contacts) {
        for (ElementId id : blocked) {
            Component original = document.component(id).orElseThrow();
            ComponentAppearance appearance = original.appearance().orElseGet(() -> ComponentAppearance.of(
                    SubcircuitTypes.definitionOf(beforeRegistry.typeOf(original)).orElseThrow()));
            instances.put(id, original.withAppearance(Optional.of(appearance)));
            String pinName = contacts.stream().map(NewContact::movingPinName).flatMap(Optional::stream)
                    .findFirst().orElse("");
            String touched = contacts.stream().map(NewContact::metPinName).flatMap(Optional::stream)
                    .findFirst().orElse("");
            retained.put(id, new ForeignContact(id, pinName, touched));
        }
    }

    private static CircuitDocument after(CircuitDocument document, Map<ElementId, Component> instances,
                                          FollowPlan wires) {
        CircuitDocument candidate = document.copy();
        instances.values().forEach(candidate::replace);
        wires.removedWireIds().forEach(candidate::remove);
        wires.replacedWires().forEach(candidate::replace);
        wires.addedWires().forEach(candidate::add);
        return candidate;
    }

    private static List<PinRef> disconnected(Connectivity before, Connectivity after, Set<PinRef> moved) {
        Map<PinRef, Set<PinRef>> original = groups(before);
        Map<PinRef, Set<PinRef>> replacement = groups(after);
        return moved.stream().filter(pin -> original.get(pin).size() > 1 && replacement.get(pin).size() == 1)
                .toList();
    }

    private static Map<PinRef, Set<PinRef>> groups(Connectivity connectivity) {
        Map<PinRef, Set<PinRef>> result = new HashMap<>();
        connectivity.pinNets().forEach(group -> group.forEach(pin -> result.put(pin, group)));
        return result;
    }

    private static Connectivity without(Connectivity original, List<PinRef> excluded) {
        Set<PinRef> removed = new HashSet<>(excluded);
        Set<Set<PinRef>> groups = new HashSet<>();
        for (Set<PinRef> group : original.pinNets()) {
            Set<PinRef> kept = new HashSet<>(group);
            kept.removeAll(removed);
            if (!kept.isEmpty()) {
                groups.add(Set.copyOf(kept));
            }
        }
        Map<String, Set<PinRef>> probePins = new HashMap<>();
        original.probePins().forEach((id, pins) -> {
            Set<PinRef> kept = new HashSet<>(pins);
            kept.removeAll(removed);
            probePins.put(id, Set.copyOf(kept));
        });
        return new Connectivity(groups, probePins, original.pinlessProbeGroups(), original.unresolvedProbes());
    }
}
