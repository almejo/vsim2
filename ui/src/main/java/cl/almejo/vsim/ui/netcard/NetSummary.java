package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.NetId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the signal card and the halo show about one net: its wires and junction points (both sides of a tunnel,
 * which joins them into one net), who drives it, where it goes, the tunnels it passes through, its value, its problem
 * and the «Mostrar como» of the part that drives it ({@code partFormat}, a {@link cl.almejo.vsim.core.document.Probe}
 * format: a fixed-point part adds its reading, as on the bus balloon). Built on the EDT from the netlist and the
 * latest snapshot, never from the engine ({@link NetSummaries}).
 */
public record NetSummary(NetId netId, int width, Set<ElementId> wires, Set<GridPoint> points,
                         List<NetEnd> drivers, List<NetEnd> readers, List<String> tunnelNames, NetValue value,
                         Optional<NetProblem> problem, Optional<String> partFormat) {

    public NetSummary {
        Objects.requireNonNull(netId, "netId");
        wires = Set.copyOf(wires);
        points = Set.copyOf(points);
        drivers = List.copyOf(drivers);
        readers = List.copyOf(readers);
        tunnelNames = List.copyOf(tunnelNames);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(partFormat, "partFormat");
    }

    /** A net whose parts have no «Mostrar como» of their own. */
    public NetSummary(NetId netId, int width, Set<ElementId> wires, Set<GridPoint> points, List<NetEnd> drivers,
                      List<NetEnd> readers, List<String> tunnelNames, NetValue value, Optional<NetProblem> problem) {
        this(netId, width, wires, points, drivers, readers, tunnelNames, value, problem, Optional.empty());
    }
}
