package cl.almejo.vsim.core.simulation;

import java.util.List;
import java.util.Optional;

/**
 * What the engine simulates: components and nets with no geometry and no hierarchy.
 * {@code token} identifies the document revision it was built from (echoed by {@link SimulationSnapshot#token()}).
 * {@code tunnelProblems} and {@code connectivityProblems} were found in the wiring while flattening; the engine
 * publishes them with its own problems.
 */
public record FlatCircuit(long token, List<FlatComponent> components, List<FlatNet> nets,
                          List<TunnelProblem> tunnelProblems, List<ConnectivityProblem> connectivityProblems) {

    public static final FlatCircuit EMPTY = new FlatCircuit(List.of(), List.of());

    public FlatCircuit {
        components = List.copyOf(components);
        nets = List.copyOf(nets);
        tunnelProblems = List.copyOf(tunnelProblems);
        connectivityProblems = List.copyOf(connectivityProblems);
    }

    public FlatCircuit(long token, List<FlatComponent> components, List<FlatNet> nets,
                       List<TunnelProblem> tunnelProblems) {
        this(token, components, nets, tunnelProblems, List.of());
    }

    public FlatCircuit(long token, List<FlatComponent> components, List<FlatNet> nets) {
        this(token, components, nets, List.of());
    }

    public FlatCircuit(List<FlatComponent> components, List<FlatNet> nets) {
        this(0, components, nets);
    }

    /**
     * The id of the net pin {@code pinIndex} of component {@code componentId} is in (the root id, aliases already
     * merged); empty when the pin is in no net. A scan of every net: for one pin, not for many.
     */
    public Optional<String> netOfPin(String componentId, int pinIndex) {
        FlatPinRef pin = new FlatPinRef(componentId, pinIndex);
        return nets.stream().filter(net -> net.pins().contains(pin)).map(FlatNet::id).findFirst();
    }
}
