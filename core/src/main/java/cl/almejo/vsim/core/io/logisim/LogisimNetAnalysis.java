package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What {@link LogisimNets} found in one circuit: where each component's ends are, which nets the wires make (as drawn,
 * without joining tunnels by name), which way each splitter goes, which
 * one-bit splitters are only junctions, and the notes about it. Components are those of the circuit, asked by value.
 */
public final class LogisimNetAnalysis {

    private final LogisimCircuit circuit;
    private final Map<LogisimComponent, Integer> indexes = new HashMap<>();
    private final List<List<LogisimEnd>> ends;
    private final Map<Integer, SplitterDirection> splitterDirections;
    private final Set<Integer> junctions;
    private final Map<LogisimPoint, Integer> nets;
    private final Set<Integer> connectedNets;
    private final List<ImportNote> notes;

    LogisimNetAnalysis(LogisimCircuit circuit, List<List<LogisimEnd>> ends,
                       Map<Integer, SplitterDirection> splitterDirections, Set<Integer> junctions, Map<LogisimPoint, Integer> nets, Set<Integer> connectedNets,
                       List<ImportNote> notes) {
        this.circuit = circuit;
        for (int index = circuit.components().size() - 1; index >= 0; index--) {
            indexes.put(circuit.components().get(index), index);
        }
        this.ends = List.copyOf(ends);
        this.splitterDirections = Map.copyOf(splitterDirections);
        this.junctions = Set.copyOf(junctions);
        this.nets = Map.copyOf(nets);
        this.connectedNets = Set.copyOf(connectedNets);
        this.notes = List.copyOf(notes);
    }

    /** The circuit analysed. */
    public LogisimCircuit circuit() {
        return circuit;
    }

    /** The ends of {@code component} ({@link LogisimEnds}), in Logisim's order. */
    public List<LogisimEnd> ends(LogisimComponent component) {
        return ends.get(indexOf(component));
    }

    /** Where end {@code endIndex} of {@code component} is. */
    public LogisimPoint endAt(LogisimComponent component, int endIndex) {
        return ends(component).get(endIndex).point();
    }

    /**
     * Which way {@code splitter}'s signals go: SPLIT when its bus is driven from outside, JOIN when its ends are (or
     * both are, noted). Any other component, and a splitter nothing drives, splits.
     */
    public SplitterDirection splitterDirection(LogisimComponent splitter) {
        return splitterDirections.getOrDefault(indexOf(splitter), SplitterDirection.SPLIT);
    }

    /** Whether {@code component} is a one-bit splitter, which only joins its bus to the end its bit goes to. */
    public boolean isJunction(LogisimComponent component) {
        return junctions.contains(indexOf(component));
    }

    /**
     * The net {@code point} is on, as drawn: wires join where they share an end, ends join the wires and ends at
     * their point, a junction joins its two ends; tunnels are not joined by name. {@code -1} when no wire ends there
     * and no component has an end there (Logisim joins nothing in the middle of a wire either).
     */
    public int netOf(LogisimPoint point) {
        return nets.getOrDefault(Objects.requireNonNull(point, "point"), -1);
    }

    /** Whether something is connected at {@code point}: its net has a wire or another end. */
    public boolean isConnected(LogisimPoint point) {
        return connectedNets.contains(netOf(point));
    }

    /** The context to map {@code component} with: this circuit, {@code registry}, a splitter's direction. */
    public MappingContext mappingContext(LogisimComponent component, ComponentTypeRegistry registry) {
        return MappingContext.of(circuit.name(), registry)
                .withSplitterDirection(splitterDirection(component));
    }

    /** The notes about the circuit's connections (splitters, ends VSim lacks). */
    public List<ImportNote> notes() {
        return notes;
    }

    private int indexOf(LogisimComponent component) {
        Integer index = indexes.get(Objects.requireNonNull(component, "component"));
        if (index == null) {
            throw new IllegalArgumentException("not a component of «" + circuit.name() + "»: " + component);
        }
        return index;
    }
}
