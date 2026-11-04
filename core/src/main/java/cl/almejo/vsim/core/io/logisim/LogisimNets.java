package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.UnionFind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the nets of a Logisim circuit and what VSim needs from them (gap analysis §2.2):
 * <ul>
 *   <li>Wires join where they share an end, and ends join whatever is at their point, as Logisim does. An end lying in the middle of a wire does not join it: Logisim's editor
 *   splits a wire where another one meets it, so its files never rely on that.</li>
 *   <li>Tunnels with the same label join (a blank label joins nothing).</li>
 *   <li>Bit by bit, a part's outputs drive their net, splitters and tunnels only pass bits on. A splitter whose bus
 *   is driven from outside splits, one whose ends are driven joins; driven from both sides it joins, noted. Tunnels
 *   get no direction (they become VSim tunnels «Sin dirección»), so which of them is driven does not matter.</li>
 *   <li>A one-bit splitter is only a junction between its bus and the end of its bit.</li>
 *   <li>A connected end the VSim part has no pin for is noted (an unconnected one is not).</li>
 * </ul>
 */
public final class LogisimNets {

    private static final String SPLITTER = LogisimPartRules.key(LogisimLibraries.WIRING, "Splitter");
    private static final String TUNNEL = LogisimPartRules.key(LogisimLibraries.WIRING, "Tunnel");

    private final LogisimCircuit circuit;
    private final List<LogisimComponent> components;
    private final List<List<LogisimEnd>> ends = new ArrayList<>();
    private final Set<Integer> junctions = new HashSet<>();
    private final List<ImportNote> notes = new ArrayList<>();

    private LogisimNets(LogisimCircuit circuit, LogisimProject project) {
        this.circuit = circuit;
        this.components = circuit.components();
        for (LogisimComponent component : components) {
            ends.add(LogisimEnds.of(component, project));
        }
        for (int index = 0; index < components.size(); index++) {
            if (isSplitter(index) && attributes(index).integer("incoming") == 1 && ends.get(index).size() > 1) {
                junctions.add(index);
            }
        }
    }

    /** The nets of {@code circuit}, one of {@code project}'s (its subcircuit instances need their circuits). */
    public static LogisimNetAnalysis analyze(LogisimCircuit circuit, LogisimProject project) {
        Objects.requireNonNull(circuit, "circuit");
        Objects.requireNonNull(project, "project");
        return new LogisimNets(circuit, project).analysis();
    }

    private LogisimNetAnalysis analysis() {
        UnionFind<LogisimPoint> drawn = points(false);
        UnionFind<LogisimPoint> joined = points(true);
        Map<Integer, SplitterDirection> directions = splitterDirections(new BitGraph(joined));
        Set<LogisimPoint> connected = connectedNets(drawn);
        noteLackingEnds(drawn, connected);
        Map<LogisimPoint, Integer> ids = netIds(drawn);
        Set<Integer> connectedIds = new HashSet<>();
        ids.forEach((point, id) -> {
            if (connected.contains(drawn.find(point))) {
                connectedIds.add(id);
            }
        });
        return new LogisimNetAnalysis(circuit, ends, directions, junctions, ids, connectedIds, notes);
    }

    /** The nets (union-find roots) with a wire or more than one end: what an end there is connected to. */
    private Set<LogisimPoint> connectedNets(UnionFind<LogisimPoint> drawn) {
        Map<LogisimPoint, Integer> endsPerNet = new HashMap<>();
        ends.forEach(list -> list.forEach(end -> endsPerNet.merge(drawn.find(end.point()), 1, Integer::sum)));
        Set<LogisimPoint> connected = new HashSet<>();
        circuit.wires().forEach(wire -> connected.add(drawn.find(wire.from())));
        endsPerNet.forEach((net, count) -> {
            if (count > 1) {
                connected.add(net);
            }
        });
        return connected;
    }

    // --- Points ---

    /** Every wire end and component end, joined by wires and junctions, and by tunnel names when asked. */
    private UnionFind<LogisimPoint> points(boolean joinTunnels) {
        UnionFind<LogisimPoint> points = new UnionFind<>();
        for (LogisimWire wire : circuit.wires()) {
            points.add(wire.from());
            points.add(wire.to());
            points.union(wire.from(), wire.to());
        }
        ends.forEach(list -> list.forEach(end -> points.add(end.point())));
        for (int junction : junctions) {
            points.union(ends.get(junction).getFirst().point(), junctionEnd(junction));
        }
        if (joinTunnels) {
            Map<String, LogisimPoint> firstOfLabel = new HashMap<>();
            for (int index = 0; index < components.size(); index++) {
                String label = tunnelLabel(index);
                if (!label.isEmpty()) {
                    LogisimPoint point = components.get(index).location();
                    LogisimPoint first = firstOfLabel.putIfAbsent(label, point);
                    if (first != null) {
                        points.union(first, point);
                    }
                }
            }
        }
        return points;
    }

    /** The end a junction's one bit goes to (its bus is end 0). */
    private LogisimPoint junctionEnd(int junction) {
        String end = attributes(junction).text("bit0").strip();
        int target = end.equals(LogisimDefaults.NO_END) ? 0 : Integer.parseInt(end);
        return ends.get(junction).get(target + 1).point();
    }

    /** Net numbers in the order of first appearance (wires first, then ends). */
    private Map<LogisimPoint, Integer> netIds(UnionFind<LogisimPoint> drawn) {
        Map<LogisimPoint, Integer> rootIds = new HashMap<>();
        Map<LogisimPoint, Integer> ids = new LinkedHashMap<>();
        List<LogisimPoint> points = new ArrayList<>();
        circuit.wires().forEach(wire -> {
            points.add(wire.from());
            points.add(wire.to());
        });
        ends.forEach(list -> list.forEach(end -> points.add(end.point())));
        for (LogisimPoint point : points) {
            int id = rootIds.computeIfAbsent(drawn.find(point), root -> rootIds.size());
            ids.put(point, id);
        }
        return ids;
    }

    // --- Splitters ---

    private Map<Integer, SplitterDirection> splitterDirections(BitGraph graph) {
        Map<Integer, SplitterDirection> directions = new HashMap<>();
        for (int index = 0; index < components.size(); index++) {
            if (isSplitter(index) && !junctions.contains(index)) {
                directions.put(index, direction(index, graph));
            }
        }
        return directions;
    }

    /** The direction most of the splitter's bits go; a bit driven from both sides makes it join, noted. */
    private SplitterDirection direction(int splitter, BitGraph graph) {
        int splitting = 0;
        int joining = 0;
        boolean bothSides = false;
        for (BitLink link : graph.linksOf(splitter)) {
            boolean busDriven = graph.isDriven(link.bus(), splitter);
            boolean endDriven = graph.isDriven(link.end(), splitter);
            if (busDriven && endDriven) {
                bothSides = true;
            } else if (busDriven) {
                splitting++;
            } else if (endDriven) {
                joining++;
            }
        }
        if (bothSides) {
            notes.add(ImportNote.at(ImportNote.Kind.APPROXIMATED, circuit.name(), components.get(splitter).location(),
                    LogisimNotes.SPLITTER_BOTH_SIDES));
            return SplitterDirection.JOIN;
        }
        return joining > splitting ? SplitterDirection.JOIN : SplitterDirection.SPLIT;
    }

    // --- Ends VSim lacks ---

    private void noteLackingEnds(UnionFind<LogisimPoint> drawn, Set<LogisimPoint> connected) {
        for (int index = 0; index < components.size(); index++) {
            for (LogisimEnd end : ends.get(index)) {
                if (!end.inVsim() && connected.contains(drawn.find(end.point()))) {
                    notes.add(ImportNote.at(ImportNote.Kind.UNSUPPORTED, circuit.name(),
                            components.get(index).location(), LogisimNotes.MISSING_PIN,
                            components.get(index).name(), end.name()));
                }
            }
        }
    }

    private boolean isSplitter(int index) {
        LogisimComponent component = components.get(index);
        return LogisimPartRules.key(component.library(), component.name()).equals(SPLITTER);
    }

    /** A tunnel's label, trimmed as Logisim does; empty for a tunnel without one and for any other component. */
    private String tunnelLabel(int index) {
        LogisimComponent component = components.get(index);
        if (!LogisimPartRules.key(component.library(), component.name()).equals(TUNNEL)) {
            return "";
        }
        return component.attributes().textOr("label", "").strip();
    }

    private LogisimAttributes attributes(int index) {
        return components.get(index).attributes();
    }

    /** One bit of one net (a union-find root). */
    private record BitNode(LogisimPoint net, int bit) {
    }

    /** A splitter's bit: the bus's bit and the bit of the end it goes to. */
    private record BitLink(int splitter, BitNode bus, BitNode end) {
    }

    /**
     * The bits of the nets of one union-find, the splitters' links between them and how wide each net is driven
     * (a part's driving end of width w drives bits 0..w-1 of its net).
     */
    private final class BitGraph {

        private final UnionFind<LogisimPoint> points;
        private final Map<LogisimPoint, Integer> drivenWidth = new HashMap<>();
        private final Map<BitNode, List<BitLink>> links = new HashMap<>();
        private final Map<Integer, List<BitLink>> linksOfSplitter = new HashMap<>();

        BitGraph(UnionFind<LogisimPoint> points) {
            this.points = points;
            for (List<LogisimEnd> list : ends) {
                for (LogisimEnd end : list) {
                    if (end.role().drives()) {
                        drivenWidth.merge(points.find(end.point()), end.width(), Math::max);
                    }
                }
            }
            for (int index = 0; index < components.size(); index++) {
                if (isSplitter(index) && !junctions.contains(index)) {
                    link(index);
                }
            }
        }

        BitNode node(LogisimPoint point, int bit) {
            return new BitNode(points.find(point), bit);
        }

        List<BitLink> linksOf(int splitter) {
            return linksOfSplitter.getOrDefault(splitter, List.of());
        }

        /** Each bit of the bus to its place in the end it goes to (bits keep their order inside an end). */
        private void link(int splitter) {
            LogisimAttributes attributes = attributes(splitter);
            List<LogisimEnd> splitterEnds = ends.get(splitter);
            Map<Integer, Integer> nextBitOfEnd = new HashMap<>();
            for (int bit = 0; bit < attributes.integer("incoming"); bit++) {
                String end = attributes.text("bit" + bit).strip();
                if (end.equals(LogisimDefaults.NO_END)) {
                    continue;
                }
                int target = Integer.parseInt(end);
                int local = nextBitOfEnd.merge(target, 1, Integer::sum) - 1;
                BitLink link = new BitLink(splitter, node(splitterEnds.getFirst().point(), bit),
                        node(splitterEnds.get(target + 1).point(), local));
                links.computeIfAbsent(link.bus(), key -> new ArrayList<>()).add(link);
                links.computeIfAbsent(link.end(), key -> new ArrayList<>()).add(link);
                linksOfSplitter.computeIfAbsent(splitter, key -> new ArrayList<>()).add(link);
            }
        }

        /** Whether a part drives {@code start}'s bit, going through every splitter but {@code excluded}. */
        boolean isDriven(BitNode start, int excluded) {
            Set<BitNode> seen = new HashSet<>();
            Deque<BitNode> pending = new ArrayDeque<>();
            seen.add(start);
            pending.push(start);
            while (!pending.isEmpty()) {
                BitNode current = pending.pop();
                if (current.bit() < drivenWidth.getOrDefault(current.net(), 0)) {
                    return true;
                }
                for (BitLink link : links.getOrDefault(current, List.of())) {
                    BitNode other = link.bus().equals(current) ? link.end() : link.bus();
                    if (link.splitter() != excluded && seen.add(other)) {
                        pending.push(other);
                    }
                }
            }
            return false;
        }
    }
}
