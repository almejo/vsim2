package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** The net {@code target} watches, or empty when its component, pin or wire does not exist. */
    public static Optional<String> netOf(ProbeTarget target, CircuitDocument document, Netlist netlist) {
        Optional<Net> net = switch (target) {
            case ProbeTarget.PinTarget pin -> document.component(pin.componentId()).isEmpty() ? Optional.empty()
                    : netlist.netForPin(new PinRef(pin.componentId(), pin.pinIndex()));
            case ProbeTarget.WireTarget wire -> document.wire(wire.wireId()).isEmpty() ? Optional.empty()
                    : netlist.netForWire(wire.wireId());
        };
        return net.map(found -> found.id().value());
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
        Comparator<PinRef> order = Comparator.comparingLong((PinRef pin) -> document.orderOf(pin.componentId()))
                .thenComparingInt(PinRef::pinIndex);
        List<PinRef> pins = net.get().pins().stream()
                .filter(pin -> document.component(pin.componentId()).isPresent())
                .sorted(order)
                .toList();
        Optional<PinRef> chosen = pins.stream()
                .filter(pin -> isDriver(pin, document, registry))
                .findFirst()
                .or(() -> pins.stream().findFirst());
        return Optional.of(chosen.<ProbeTarget>map(pin -> new ProbeTarget.PinTarget(pin.componentId(), pin.pinIndex()))
                .orElseGet(() -> new ProbeTarget.WireTarget(wireId)));
    }

    private static boolean isDriver(PinRef pin, CircuitDocument document, ComponentTypeRegistry registry) {
        return document.component(pin.componentId())
                .flatMap(registry::findTypeOf)
                .filter(type -> pin.pinIndex() < type.pinCount())
                .map(type -> type.pin(pin.pinIndex()).isDriver())
                .orElse(false);
    }
}
