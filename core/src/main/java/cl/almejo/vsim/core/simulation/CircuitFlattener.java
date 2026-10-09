package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;

import java.util.Comparator;
import java.util.List;

/** Turns a document and its netlist into a {@link FlatCircuit}. Sub-circuits are expanded here in M4. */
public final class CircuitFlattener {

    private final ComponentTypeRegistry registry;

    public CircuitFlattener(ComponentTypeRegistry registry) {
        this.registry = registry;
    }

    public FlatCircuit flatten(CircuitDocument document, Netlist netlist) {
        List<FlatComponent> components = document.components().stream()
                .map(this::flatten)
                .toList();
        List<FlatNet> nets = netlist.nets().stream()
                .map(CircuitFlattener::flatten)
                .toList();
        return new FlatCircuit(document.revision(), components, nets);
    }

    private FlatComponent flatten(Component component) {
        return new FlatComponent(component.id().value(), registry.require(component.typeId()), component.parameters());
    }

    private static FlatNet flatten(Net net) {
        List<FlatPinRef> pins = net.pins().stream()
                .map(pin -> new FlatPinRef(pin.componentId().value(), pin.pinIndex()))
                .sorted(Comparator.comparing(FlatPinRef::componentId).thenComparingInt(FlatPinRef::pinIndex))
                .toList();
        return new FlatNet(net.id().value(), pins, net.width(), net.widthMismatch());
    }
}
