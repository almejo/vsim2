package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What to mark on the canvas for nets that join pins of different widths (spec §5.5): their wires, and at each pin
 * point the widths of the pins there. Mismatches come from the netlist (shown even before the simulation reports
 * them) and from a snapshot of the shown netlist.
 */
public record WidthMismatchHighlight(Set<ElementId> wires, Map<GridPoint, List<Integer>> pinWidths) {

    public static final WidthMismatchHighlight NONE = new WidthMismatchHighlight(Set.of(), Map.of());

    public WidthMismatchHighlight {
        wires = Set.copyOf(wires);
        Map<GridPoint, List<Integer>> copy = new HashMap<>();
        pinWidths.forEach((point, widths) -> copy.put(point, List.copyOf(widths)));
        pinWidths = Map.copyOf(copy);
    }

    public static WidthMismatchHighlight of(SimulationSnapshot snapshot, Netlist netlist, long netlistToken,
                                            CircuitDocument document, ComponentTypeRegistry registry) {
        Set<NetId> nets = new LinkedHashSet<>();
        netlist.nets().stream().filter(Net::widthMismatch).forEach(net -> nets.add(net.id()));
        if (snapshot.token() == netlistToken) {
            for (WidthMismatch mismatch : snapshot.widthMismatches()) {
                nets.add(new NetId(mismatch.netId()));
            }
        }
        Set<ElementId> wires = new HashSet<>();
        Map<GridPoint, TreeSet<Integer>> widths = new HashMap<>();
        for (NetId id : nets) {
            netlist.net(id).ifPresent(net -> {
                wires.addAll(net.wires());
                for (PinRef pin : net.pins()) {
                    document.component(pin.componentId()).ifPresent(component -> {
                        ComponentType type = registry.typeOf(component);
                        widths.computeIfAbsent(type.pinLocation(component, pin.pinIndex()), point -> new TreeSet<>())
                                .add(type.pin(pin.pinIndex()).width());
                    });
                }
            });
        }
        if (wires.isEmpty() && widths.isEmpty()) {
            return NONE;
        }
        Map<GridPoint, List<Integer>> pinWidths = new HashMap<>();
        widths.forEach((point, sorted) -> pinWidths.put(point, List.copyOf(sorted)));
        return new WidthMismatchHighlight(wires, pinWidths);
    }
}
