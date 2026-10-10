package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What to mark on the canvas for the short circuits of a snapshot (spec §6.12). A short reported for a net merged with
 * other top-level nets through a template marks the wires of all of them ({@link TopLevelNets}).
 */
public record ShortCircuitHighlight(Set<ElementId> wires, Set<ElementId> components, Set<GridPoint> driverPins) {

    public static final ShortCircuitHighlight NONE = new ShortCircuitHighlight(Set.of(), Set.of(), Set.of());

    public ShortCircuitHighlight {
        wires = Set.copyOf(wires);
        components = Set.copyOf(components);
        driverPins = Set.copyOf(driverPins);
    }

    /** {@code merged} is asked only when the snapshot has short circuits for the shown netlist. */
    public static ShortCircuitHighlight of(SimulationSnapshot snapshot, Netlist netlist, long netlistToken,
                                           CircuitDocument document, ComponentTypeRegistry registry,
                                           Supplier<TopLevelNets> merged) {
        if (snapshot.token() != netlistToken || snapshot.shortCircuits().isEmpty()) {
            return NONE;
        }
        TopLevelNets groups = merged.get();
        Set<ElementId> wires = new HashSet<>();
        Set<ElementId> components = new HashSet<>();
        Set<GridPoint> driverPins = new HashSet<>();
        for (ShortCircuit shortCircuit : snapshot.shortCircuits()) {
            for (String member : groups.membersOf(shortCircuit.netId())) {
                netlist.net(new NetId(member)).ifPresent(net -> wires.addAll(net.wires()));
            }
            for (DriverRef driver : shortCircuit.drivers()) {
                boolean nested = InstancePath.isNested(driver.componentId());
                ElementId componentId = new ElementId(InstancePath.topLevel(driver.componentId()));
                document.component(componentId).ifPresent(component -> {
                    components.add(componentId);
                    if (!nested) {
                        driverPins.add(registry.typeOf(component).pinLocation(component, driver.pinIndex()));
                    }
                });
            }
        }
        return new ShortCircuitHighlight(wires, components, driverPins);
    }
}
