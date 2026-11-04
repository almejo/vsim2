package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a laid-out circuit against Logisim's nets: VSim's netlist as drawn must join exactly the pins Logisim's nets
 * join. A VSim net with pins of two Logisim nets is a short; a Logisim net whose pins fall in two VSim nets is open.
 */
final class NetEquivalence {

    private NetEquivalence() {
    }

    /** What differs, one line each; empty when VSim's nets are Logisim's. */
    static List<String> mismatches(LaidOutCircuit laidOut, ComponentTypeRegistry registry) {
        Netlist netlist = new NetlistCompiler(registry).compileAsDrawn(laidOut.document());
        List<String> mismatches = new ArrayList<>();
        for (Component component : laidOut.document().components()) {
            int pins = registry.typeOf(component).forComponent(component).pinCount();
            for (int pin = 0; pin < pins; pin++) {
                if (!laidOut.pinNets().containsKey(new PinRef(component.id(), pin))) {
                    mismatches.add("pin without a Logisim net: " + component.typeId() + " " + component.id() + "#"
                            + pin);
                }
            }
        }
        Map<Integer, Set<NetId>> vsimNetsOfLogisimNet = new HashMap<>();
        for (Net net : netlist.nets()) {
            Set<Integer> logisimNets = new HashSet<>();
            for (PinRef pin : net.pins()) {
                Integer logisimNet = laidOut.pinNets().get(pin);
                if (logisimNet != null) {
                    logisimNets.add(logisimNet);
                    vsimNetsOfLogisimNet.computeIfAbsent(logisimNet, key -> new HashSet<>()).add(net.id());
                }
            }
            if (logisimNets.size() > 1) {
                mismatches.add("short: " + net.id() + " joins Logisim nets " + logisimNets + " at " + net.points());
            }
        }
        vsimNetsOfLogisimNet.forEach((logisimNet, vsimNets) -> {
            if (vsimNets.size() > 1) {
                mismatches.add("open: Logisim net " + logisimNet + " falls in " + vsimNets);
            }
        });
        return mismatches;
    }
}
