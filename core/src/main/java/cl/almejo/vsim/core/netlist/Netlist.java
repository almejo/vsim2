package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.document.ElementId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Netlist(List<Net> nets, Map<NetId, Net> byId, Map<PinRef, Net> byPin, Map<ElementId, Net> byWire) {

    public static final Netlist EMPTY = of(List.of());

    public static Netlist of(List<Net> nets) {
        Map<NetId, Net> byId = new HashMap<>();
        Map<PinRef, Net> byPin = new HashMap<>();
        Map<ElementId, Net> byWire = new HashMap<>();
        for (Net net : nets) {
            byId.put(net.id(), net);
            net.pins().forEach(pin -> byPin.put(pin, net));
            net.wires().forEach(wire -> byWire.put(wire, net));
        }
        return new Netlist(List.copyOf(nets), Map.copyOf(byId), Map.copyOf(byPin), Map.copyOf(byWire));
    }

    public Optional<Net> net(NetId id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Net> netForPin(PinRef pin) {
        return Optional.ofNullable(byPin.get(pin));
    }

    public Optional<Net> netForWire(ElementId wireId) {
        return Optional.ofNullable(byWire.get(wireId));
    }
}
