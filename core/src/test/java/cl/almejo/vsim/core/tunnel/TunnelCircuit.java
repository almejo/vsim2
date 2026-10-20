package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;

import java.util.HashMap;
import java.util.Map;

/** A document with the standard parts and a few helpers to place tunnels and the parts around them in tests. */
public final class TunnelCircuit {

    private static final int SETTLE_TIME = 50;

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    public TunnelCircuit() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    public CircuitDocument document() {
        return document;
    }

    public ComponentTypeRegistry registry() {
        return registry;
    }

    /** A part of {@code typeId} at {@code (x, y)}, rotation 0, with {@code parameters} on top of the defaults. */
    public Component place(String typeId, int x, int y, Map<String, Object> parameters) {
        return place(typeId, x, y, Rotation.R0, parameters);
    }

    public Component place(String typeId, int x, int y, Rotation rotation, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                rotation, parameters, "");
        document.add(component);
        return component;
    }

    /** An entrada named {@code name} whose pin is at {@code pin}. */
    public Component entrance(String name, GridPoint pin) {
        return tunnelAt(StandardComponentTypes.TUNNEL_ENTRANCE, Map.of("name", name), pin);
    }

    /** An entrada named {@code name} with colour {@code color} whose pin is at {@code pin}. */
    public Component entrance(String name, int color, GridPoint pin) {
        return tunnelAt(StandardComponentTypes.TUNNEL_ENTRANCE, Map.of("name", name, "color", color), pin);
    }

    /** A salida named {@code name} whose pin is at {@code pin}. */
    public Component exit(String name, GridPoint pin) {
        return tunnelAt(StandardComponentTypes.TUNNEL_EXIT, Map.of("name", name), pin);
    }

    private Component tunnelAt(String typeId, Map<String, Object> parameters, GridPoint pin) {
        ComponentType type = registry.require(typeId);
        Component atOrigin = type.newComponent(document.newComponentId(), GridPoint.ORIGIN, Rotation.R0,
                parameters, "");
        GridPoint offset = type.pinLocation(atOrigin, 0);
        Component placed = atOrigin.translate(pin.x() - offset.x(), pin.y() - offset.y());
        document.add(placed);
        return placed;
    }

    public GridPoint pin(Component component, int pinIndex) {
        return registry.typeOf(component).pinLocation(component, pinIndex);
    }

    public Wire wire(GridPoint start, GridPoint end) {
        Wire wire = new Wire(document.newWireId(), start, end);
        document.add(wire);
        return wire;
    }

    public Netlist netlist() {
        return new NetlistCompiler(registry).compile(document);
    }

    public Net netOf(Component component, int pinIndex) {
        return netlist().netForPin(new PinRef(component.id(), pinIndex)).orElseThrow();
    }

    public FlatCircuit flatten() {
        return new CircuitFlattener(registry).flatten(document, netlist());
    }

    /** Simulates the document until it settles. */
    public SimulationSnapshot simulate() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(flatten());
        engine.runUntil(SETTLE_TIME);
        return engine.snapshot();
    }

    /** The value on the net of {@code component}'s pin {@code pinIndex} after {@link #simulate()}. */
    public Value valueAt(SimulationSnapshot snapshot, Component component, int pinIndex) {
        return snapshot.netValue(netOf(component, pinIndex).id().value());
    }

    public static Map<String, Object> parameters(Object... keysAndValues) {
        Map<String, Object> parameters = new HashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            parameters.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return parameters;
    }

    public Component component(ElementId id) {
        return document.component(id).orElseThrow();
    }
}
