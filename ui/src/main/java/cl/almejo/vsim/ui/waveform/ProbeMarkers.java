package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.ProbeResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Where the canvas shows that a net is in the waveform panel (M6.1-E, pure). */
public final class ProbeMarkers {

    private ProbeMarkers() {
    }

    /**
     * One marker per distinct anchor, in channel order, for the probes of {@code document} that resolve in
     * {@code netlist}; a probe whose target is gone or does not resolve has none. {@code names} gives each probe the
     * name its row shows.
     */
    public static List<ProbeMarker> of(CircuitDocument document, Netlist netlist, ComponentTypeRegistry registry,
                                       Function<Probe, String> names) {
        Map<String, String> resolved = ProbeResolver.resolve(document.probes(), document, netlist);
        Map<GridPoint, List<Probe>> byAnchor = new LinkedHashMap<>();
        for (Probe probe : document.probes()) {
            if (resolved.containsKey(probe.id())) {
                anchor(probe.target(), document, registry)
                        .ifPresent(point -> byAnchor.computeIfAbsent(point, key -> new ArrayList<>()).add(probe));
            }
        }
        List<ProbeMarker> markers = new ArrayList<>();
        byAnchor.forEach((point, probes) -> markers.add(new ProbeMarker(point,
                side(probes.getFirst().target(), document, registry),
                probes.stream().map(Probe::id).toList(), probes.stream().map(names).toList())));
        return List.copyOf(markers);
    }

    /**
     * The edge of its component's box a probed pin is on, so the badge can go outward; {@code RIGHT} for a wire, a
     * missing component or a pin inside the box.
     */
    static ProbeMarker.Side side(ProbeTarget target, CircuitDocument document, ComponentTypeRegistry registry) {
        if (!(target instanceof ProbeTarget.PinTarget pin)) {
            return ProbeMarker.Side.RIGHT;
        }
        return document.component(pin.componentId())
                .flatMap(component -> registry.findTypeOf(component)
                        .filter(type -> pin.pinIndex() < type.pinCount())
                        .map(type -> edge(type.bounds(component), type.pinLocation(component, pin.pinIndex()))))
                .orElse(ProbeMarker.Side.RIGHT);
    }

    private static ProbeMarker.Side edge(Bounds box, GridPoint pin) {
        if (pin.x() <= box.x()) {
            return ProbeMarker.Side.LEFT;
        }
        if (pin.x() >= box.maximumX()) {
            return ProbeMarker.Side.RIGHT;
        }
        if (pin.y() <= box.y()) {
            return ProbeMarker.Side.TOP;
        }
        if (pin.y() >= box.maximumY()) {
            return ProbeMarker.Side.BOTTOM;
        }
        return ProbeMarker.Side.RIGHT;
    }

    /** The probed pin's location, or the grid point in the middle of the probed wire. */
    static Optional<GridPoint> anchor(ProbeTarget target, CircuitDocument document, ComponentTypeRegistry registry) {
        return switch (target) {
            case ProbeTarget.PinTarget pin -> document.component(pin.componentId())
                    .flatMap(component -> registry.findTypeOf(component)
                            .filter(type -> pin.pinIndex() < type.pinCount())
                            .map(type -> type.pinLocation(component, pin.pinIndex())));
            case ProbeTarget.WireTarget wire -> document.wire(wire.wireId())
                    .map(found -> new GridPoint(Math.floorDiv(found.start().x() + found.end().x(), 2),
                            Math.floorDiv(found.start().y() + found.end().y(), 2)));
        };
    }
}
