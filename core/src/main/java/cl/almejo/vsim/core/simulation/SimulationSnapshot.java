package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable picture of the simulation, safe to read from any thread.
 * {@code token} is the token of the last {@link FlatCircuit} applied; {@code failure} describes why the
 * simulation stopped, if it did.
 */
public record SimulationSnapshot(long revision, long token, long time, boolean running, Map<String, Value> netValues,
                                 Map<String, VisibleState> componentStates, List<Problem> problems,
                                 Optional<String> failure) {

    public static final SimulationSnapshot EMPTY =
            new SimulationSnapshot(0, 0, 0, false, Map.of(), Map.of(), List.of(), Optional.empty());

    public SimulationSnapshot {
        netValues = Map.copyOf(netValues);
        componentStates = Map.copyOf(componentStates);
        problems = List.copyOf(problems);
        Objects.requireNonNull(failure, "failure");
    }

    public SimulationSnapshot withRunning(boolean isRunning) {
        return new SimulationSnapshot(revision, token, time, isRunning, netValues, componentStates, problems, failure);
    }

    public SimulationSnapshot withFailure(Optional<String> newFailure) {
        return new SimulationSnapshot(revision, token, time, running, netValues, componentStates, problems, newFailure);
    }

    /** The same values at {@code newTime}: for a running simulation whose values did not change since. */
    public SimulationSnapshot withTime(long newTime) {
        return new SimulationSnapshot(revision, token, newTime, running, netValues, componentStates, problems, failure);
    }

    public Value netValue(String netId) {
        return netValues.getOrDefault(netId, Value.HIGH_IMPEDANCE);
    }

    public VisibleState componentState(String componentId) {
        return componentStates.getOrDefault(componentId, VisibleState.NONE);
    }

    public List<ShortCircuit> shortCircuits() {
        return problems.stream()
                .filter(ShortCircuit.class::isInstance)
                .map(ShortCircuit.class::cast)
                .toList();
    }

    public List<WidthMismatch> widthMismatches() {
        return problems.stream()
                .filter(WidthMismatch.class::isInstance)
                .map(WidthMismatch.class::cast)
                .toList();
    }
}
