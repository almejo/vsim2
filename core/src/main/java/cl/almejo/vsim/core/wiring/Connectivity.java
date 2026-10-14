package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.netlist.ProbeResolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a set of wires means electrically, as seen by pins and probes: the partition of the top-level pins into
 * nets, what every probe watches (the pin set of its net, or, for a net without pins, which probes share it) and
 * which probes watch nothing. Two sets of wires with an equal signature are interchangeable for the simulation.
 */
public record Connectivity(Set<Set<PinRef>> pinNets, Map<String, Set<PinRef>> probePins,
                           Set<Set<String>> pinlessProbeGroups, Set<String> unresolvedProbes) {

    /** The signature of {@code wires} placed in {@code document} (its components and probes; its wires are ignored). */
    public static Connectivity of(List<Wire> wires, CircuitDocument document, ComponentTypeRegistry registry) {
        return of(wires, document, registry, Map.of());
    }

    /**
     * Like {@link #of(List, CircuitDocument, ComponentTypeRegistry)} with every probe on a wire id of
     * {@code probeRetargets} moved to the wire id it maps to.
     */
    public static Connectivity of(List<Wire> wires, CircuitDocument document, ComponentTypeRegistry registry,
                                  Map<ElementId, ElementId> probeRetargets) {
        CircuitDocument scratch = scratchOf(wires, document);
        Netlist netlist = new NetlistCompiler(registry).compile(scratch);
        Set<Set<PinRef>> pinNets = new HashSet<>();
        for (Net net : netlist.nets()) {
            if (!net.pins().isEmpty()) {
                pinNets.add(Set.copyOf(net.pins()));
            }
        }
        Map<String, Set<PinRef>> probePins = new HashMap<>();
        Map<String, Set<String>> pinlessGroups = new HashMap<>();
        Set<String> unresolved = new HashSet<>();
        for (Probe probe : document.probes()) {
            Optional<String> netId = ProbeResolver.netOf(retarget(probe.target(), probeRetargets), scratch, netlist);
            if (netId.isEmpty()) {
                unresolved.add(probe.id());
                continue;
            }
            Net net = netlist.net(new NetId(netId.get())).orElseThrow();
            if (net.pins().isEmpty()) {
                pinlessGroups.computeIfAbsent(netId.get(), key -> new HashSet<>()).add(probe.id());
            } else {
                probePins.put(probe.id(), Set.copyOf(net.pins()));
            }
        }
        return new Connectivity(pinNets, probePins, Set.copyOf(pinlessGroups.values()), unresolved);
    }

    private static CircuitDocument scratchOf(List<Wire> wires, CircuitDocument document) {
        CircuitDocument scratch = new CircuitDocument();
        document.components().forEach(scratch::add);
        wires.forEach(scratch::add);
        return scratch;
    }

    private static ProbeTarget retarget(ProbeTarget target, Map<ElementId, ElementId> retargets) {
        if (target instanceof ProbeTarget.WireTarget wireTarget && retargets.containsKey(wireTarget.wireId())) {
            return new ProbeTarget.WireTarget(retargets.get(wireTarget.wireId()));
        }
        return target;
    }
}
