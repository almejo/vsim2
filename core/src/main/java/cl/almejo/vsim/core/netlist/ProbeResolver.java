package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.StateMachineTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Maps waveform probes to the top-level nets of a netlist compiled from the same document. */
public final class ProbeResolver {

    private ProbeResolver() {
    }

    /** Probe id → top-level net id for every probe that resolves now, in probe order; the others are absent. */
    public static Map<String, String> resolve(List<Probe> probes, CircuitDocument document, Netlist netlist) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Probe probe : probes) {
            netOf(probe.target(), document, netlist).ifPresent(netId -> resolved.put(probe.id(), netId));
        }
        return Collections.unmodifiableMap(resolved);
    }

    /**
     * The net {@code target} watches, or empty when its component, pin or wire does not exist. A machine piece's state
     * is no net of {@code netlist}: it is the state net the flattener gives that copy, which the simulation records
     * like any other.
     */
    public static Optional<String> netOf(ProbeTarget target, CircuitDocument document, Netlist netlist) {
        return switch (target) {
            case ProbeTarget.PinTarget pin -> document.component(pin.componentId()).isEmpty() ? Optional.empty()
                    : netlist.netForPin(new PinRef(pin.componentId(), pin.pinIndex())).map(net -> net.id().value());
            case ProbeTarget.WireTarget wire -> document.wire(wire.wireId()).isEmpty() ? Optional.empty()
                    : netlist.netForWire(wire.wireId()).map(net -> net.id().value());
            case ProbeTarget.MachineStateTarget state -> stateNetOf(state.componentId(), document);
        };
    }

    /**
     * The state net of the machine piece {@code componentId} ({@link StateMachineTypes#stateNetId}); empty when the
     * piece is gone or is not a machine.
     */
    private static Optional<String> stateNetOf(ElementId componentId, CircuitDocument document) {
        return document.component(componentId)
                .flatMap(component -> SubcircuitTypes.definitionIdOf(component.typeId()))
                .flatMap(document::definition)
                .filter(ComponentDefinition::isMachine)
                .map(definition -> StateMachineTypes.stateNetId(componentId.value()));
    }

    /**
     * What a probe added on {@code wireId} stores: a pin of its net when the net has one (a driver first, else the
     * first pin by component document order, then pin order), else the wire itself. Empty when the wire is not in
     * {@code netlist}.
     */
    public static Optional<ProbeTarget> anchor(ElementId wireId, CircuitDocument document, Netlist netlist,
                                               ComponentTypeRegistry registry) {
        Optional<Net> net = netlist.netForWire(wireId);
        if (net.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(chosenPin(net.get(), pin -> true, document, registry)
                .orElseGet(() -> new ProbeTarget.WireTarget(wireId)));
    }

    /**
     * The probes as they are once {@code removedIds} are deleted from {@code document}, computed before the deletion
     * with {@code netlist} compiled from it and {@code netlistAfter} compiled from the document without them. A probe
     * whose component or wire is deleted moves to what survives of its net and is still connected: a pin or wire of the
     * old net that, after the deletion, shares its net with at least one other element (a pin left floating alone, or a
     * wire touching nothing, does not count). Among those it takes, by the rule of {@link #anchor}, a driver pin first,
     * else the first pin by document order, else the first wire by document order, keeping its id, name and format;
     * a probe with an empty name stores the one {@code naming} gives its old target before the deletion, the name its
     * row showed. With nothing connected left of its net (or a net it never had) it is removed. Every other probe
     * stays as it is, in its place.
     */
    public static List<Probe> afterRemoval(List<Probe> probes, Set<ElementId> removedIds, CircuitDocument document,
                                           Netlist netlist, Netlist netlistAfter, ComponentTypeRegistry registry,
                                           ProbeNaming naming) {
        List<Probe> after = new ArrayList<>(probes.size());
        for (Probe probe : probes) {
            if (!watchesRemoved(probe.target(), removedIds, document)) {
                after.add(probe);
                continue;
            }
            Optional<Net> net = netOfTarget(probe.target(), netlist);
            net.flatMap(found -> survivingTarget(found, removedIds, document, netlistAfter, registry))
                    .ifPresent(target -> after.add(keptName(probe, document, netlist, registry, naming)
                            .withTarget(target)));
        }
        return List.copyOf(after);
    }

    /** {@code probe} with the name its row shows now stored, when it has no name of its own. */
    private static Probe keptName(Probe probe, CircuitDocument document, Netlist netlist,
                                  ComponentTypeRegistry registry, ProbeNaming naming) {
        return probe.name().isBlank()
                ? probe.withName(naming.defaultName(probe.target(), document, netlist, registry))
                : probe;
    }

    /** Whether {@code target}'s component or wire exists in {@code document} and is among {@code removedIds}. */
    public static boolean watchesRemoved(ProbeTarget target, Set<ElementId> removedIds, CircuitDocument document) {
        ElementId watched = switch (target) {
            case ProbeTarget.PinTarget pin -> pin.componentId();
            case ProbeTarget.MachineStateTarget machine -> machine.componentId();
            case ProbeTarget.WireTarget wire -> wire.wireId();
        };
        return removedIds.contains(watched) && document.element(watched).isPresent();
    }

    private static Optional<Net> netOfTarget(ProbeTarget target, Netlist netlist) {
        return switch (target) {
            case ProbeTarget.PinTarget pin -> netlist.netForPin(new PinRef(pin.componentId(), pin.pinIndex()));
            // The state of a deleted machine piece has no net that survives it: its channel goes with the piece.
            case ProbeTarget.MachineStateTarget machine -> Optional.empty();
            case ProbeTarget.WireTarget wire -> netlist.netForWire(wire.wireId());
        };
    }

    private static Optional<ProbeTarget> survivingTarget(Net net, Set<ElementId> removedIds, CircuitDocument document,
                                                         Netlist netlistAfter, ComponentTypeRegistry registry) {
        Predicate<PinRef> connectedPin = pin -> !removedIds.contains(pin.componentId())
                && stillConnected(netlistAfter.netForPin(pin));
        return chosenPin(net, connectedPin, document, registry).or(() -> net.wires().stream()
                .filter(wireId -> !removedIds.contains(wireId) && document.wire(wireId).isPresent())
                .filter(wireId -> stillConnected(netlistAfter.netForWire(wireId)))
                .min(Comparator.comparingLong(document::orderOf))
                .map(ProbeTarget.WireTarget::new));
    }

    /** Whether a net of the netlist after the deletion joins at least two elements (pins and wires). */
    private static boolean stillConnected(Optional<Net> net) {
        return net.map(found -> found.pins().size() + found.wires().size() >= 2).orElse(false);
    }

    /** A pin of {@code net} that {@code eligible} accepts: a driver first, else the first by document then pin order. */
    private static Optional<ProbeTarget> chosenPin(Net net, Predicate<PinRef> eligible, CircuitDocument document,
                                                   ComponentTypeRegistry registry) {
        Comparator<PinRef> order = Comparator.comparingLong((PinRef pin) -> document.orderOf(pin.componentId()))
                .thenComparingInt(PinRef::pinIndex);
        List<PinRef> pins = net.pins().stream()
                .filter(pin -> document.component(pin.componentId()).isPresent())
                .filter(eligible)
                .sorted(order)
                .toList();
        return pins.stream()
                .filter(pin -> isDriver(pin, document, registry))
                .findFirst()
                .or(() -> pins.stream().findFirst())
                .map(pin -> new ProbeTarget.PinTarget(pin.componentId(), pin.pinIndex()));
    }

    private static boolean isDriver(PinRef pin, CircuitDocument document, ComponentTypeRegistry registry) {
        return document.component(pin.componentId())
                .flatMap(registry::findTypeOf)
                .filter(type -> pin.pinIndex() < type.pinCount())
                .map(type -> type.pin(pin.pinIndex()).isDriver())
                .orElse(false);
    }
}
