package cl.almejo.vsim.core.simulation;

import java.util.List;

/** What the engine simulates: components and nets with no geometry and no hierarchy. */
public record FlatCircuit(List<FlatComponent> components, List<FlatNet> nets) {

    public static final FlatCircuit EMPTY = new FlatCircuit(List.of(), List.of());

    public FlatCircuit {
        components = List.copyOf(components);
        nets = List.copyOf(nets);
    }
}
