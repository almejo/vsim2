package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.HashSet;
import java.util.Set;

/** What to mark on the canvas for the short circuits of a snapshot (spec §6.12). */
public record ShortCircuitHighlight(Set<ElementId> wires, Set<ElementId> components, Set<GridPoint> driverPins) {

    public static final ShortCircuitHighlight NONE = new ShortCircuitHighlight(Set.of(), Set.of(), Set.of());

    public ShortCircuitHighlight {
        wires = Set.copyOf(wires);
        components = Set.copyOf(components);
        driverPins = Set.copyOf(driverPins);
    }

    public static ShortCircuitHighlight of(SimulationSnapshot snapshot, Netlist netlist, long netlistToken,
                                           CircuitDocument document, ComponentTypeRegistry registry) {
        if (snapshot.token() != netlistToken || snapshot.shortCircuits().isEmpty()) {
            return NONE;
        }
        Set<ElementId> wires = new HashSet<>();
        Set<ElementId> components = new HashSet<>();
        Set<GridPoint> driverPins = new HashSet<>();
        for (ShortCircuit shortCircuit : snapshot.shortCircuits()) {
            netlist.net(new NetId(shortCircuit.netId())).ifPresent(net -> wires.addAll(net.wires()));
            for (DriverRef driver : shortCircuit.drivers()) {
                ElementId componentId = new ElementId(driver.componentId());
                document.component(componentId).ifPresent(component -> {
                    components.add(componentId);
                    driverPins.add(registry.require(component.typeId()).pinLocation(component, driver.pinIndex()));
                });
            }
        }
        return new ShortCircuitHighlight(wires, components, driverPins);
    }
}
