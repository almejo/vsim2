package cl.almejo.vsim.core.simulation;

import java.util.List;

/**
 * What the engine simulates: components and nets with no geometry and no hierarchy.
 * {@code token} identifies the document revision it was built from (echoed by {@link SimulationSnapshot#token()}).
 * {@code tunnelProblems} were found in the wiring while flattening; the engine publishes them with its own problems.
 */
public record FlatCircuit(long token, List<FlatComponent> components, List<FlatNet> nets,
                          List<TunnelProblem> tunnelProblems) {

    public static final FlatCircuit EMPTY = new FlatCircuit(List.of(), List.of());

    public FlatCircuit {
        components = List.copyOf(components);
        nets = List.copyOf(nets);
        tunnelProblems = List.copyOf(tunnelProblems);
    }

    public FlatCircuit(long token, List<FlatComponent> components, List<FlatNet> nets) {
        this(token, components, nets, List.of());
    }

    public FlatCircuit(List<FlatComponent> components, List<FlatNet> nets) {
        this(0, components, nets);
    }
}
