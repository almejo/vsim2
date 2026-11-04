package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.TunnelProblem.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds what is wrong with the tunnels of one circuit body: a name with two entradas, salidas without entrada, an
 * entrada without salidas, a salida driven on its own wire, and a tunnel without direction alone with its name. Only
 * tunnels with direction are entradas and salidas here: one without direction never gets their warnings, gives a name
 * its signal like an entrada (its salidas are fine) and takes it like a salida (its entrada is not alone). Ids in the
 * problems are the body's own.
 */
public final class TunnelChecks {

    private TunnelChecks() {
    }

    /**
     * The tunnel problems of {@code body}, whose nets (tunnels joined) are {@code netlist}; sorted by name, then by
     * kind. A salida is «driven» when its wire as drawn (before joining by name) holds a driver pin of a part that is
     * not a tunnel and no entrada of its name; it is only checked when the name has an entrada (without one, the
     * missing entrada is the error, or a tunnel without direction gives the signal). Two drivers fighting through
     * tunnels without direction are the usual short, found by the simulation.
     */
    public static List<TunnelProblem> problems(CircuitDocument body, Netlist netlist, ComponentTypeRegistry registry) {
        Map<String, Tunnels.TunnelGroup> groups = Tunnels.groups(body);
        List<TunnelProblem> problems = new ArrayList<>();
        Optional<Netlist> drawn = Optional.empty();
        for (Tunnels.TunnelGroup group : groups.values()) {
            String netId = netOf(netlist, group);
            List<ElementId> entrances = group.directedEntrances();
            List<ElementId> exits = group.directedExits();
            boolean hasUndirected = !group.undirected().isEmpty();
            if (entrances.size() > 1) {
                problems.add(problem(netId, Kind.DUPLICATE_ENTRANCE, group, entrances));
            }
            if (entrances.isEmpty()) {
                if (!exits.isEmpty() && !hasUndirected) {
                    problems.add(problem(netId, Kind.EXIT_WITHOUT_ENTRANCE, group, exits));
                } else if (exits.isEmpty() && group.undirected().size() == 1) {
                    problems.add(problem(netId, Kind.LONE_TUNNEL, group, group.undirected()));
                }
            } else if (exits.isEmpty()) {
                if (!hasUndirected) {
                    problems.add(problem(netId, Kind.ENTRANCE_WITHOUT_EXITS, group, entrances));
                }
            } else {
                if (drawn.isEmpty()) {
                    drawn = Optional.of(new NetlistCompiler(registry).compileAsDrawn(body));
                }
                for (ElementId exit : exits) {
                    if (isDrivenAsDrawn(exit, entrances, drawn.get(), body, registry)) {
                        problems.add(problem(netId, Kind.EXIT_DRIVEN, group, List.of(exit)));
                    }
                }
            }
        }
        return problems;
    }

    private static String netOf(Netlist netlist, Tunnels.TunnelGroup group) {
        ElementId anyTunnel = group.entrances().isEmpty() ? group.exits().getFirst() : group.entrances().getFirst();
        return netlist.netForPin(new PinRef(anyTunnel, 0)).orElseThrow().id().value();
    }

    private static TunnelProblem problem(String netId, Kind kind, Tunnels.TunnelGroup group, List<ElementId> tunnels) {
        return new TunnelProblem(netId, kind, group.name(), tunnels.stream().map(ElementId::value).toList());
    }

    /**
     * True when the salida's wire as drawn has a driver of a part on it, unless an entrada of its own name is on that
     * wire too: then the driver feeds that entrada and the salida only gives the same signal back.
     */
    private static boolean isDrivenAsDrawn(ElementId exit, List<ElementId> entrances, Netlist drawn,
                                           CircuitDocument body, ComponentTypeRegistry registry) {
        Optional<Net> wireSide = drawn.netForPin(new PinRef(exit, 0));
        if (wireSide.isEmpty()) {
            return false;
        }
        Set<PinRef> pins = wireSide.get().pins();
        boolean holdsItsEntrance = pins.stream().anyMatch(pin -> entrances.contains(pin.componentId()));
        return !holdsItsEntrance && pins.stream().anyMatch(pin -> isDriverOfAPart(pin, body, registry));
    }

    private static boolean isDriverOfAPart(PinRef pin, CircuitDocument body, ComponentTypeRegistry registry) {
        Component component = body.component(pin.componentId()).orElseThrow();
        return !Tunnels.isTunnel(component.typeId()) && registry.typeOf(component).pin(pin.pinIndex()).isDriver();
    }
}
