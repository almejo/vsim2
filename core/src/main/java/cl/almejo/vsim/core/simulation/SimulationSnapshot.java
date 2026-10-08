package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Map;

/** Immutable picture of the simulation, safe to read from any thread. */
public record SimulationSnapshot(long revision, long time, boolean running, Map<String, Value> netValues,
                                 Map<String, VisibleState> componentStates, List<Problem> problems) {

    public static final SimulationSnapshot EMPTY = new SimulationSnapshot(0, 0, false, Map.of(), Map.of(), List.of());

    public SimulationSnapshot {
        netValues = Map.copyOf(netValues);
        componentStates = Map.copyOf(componentStates);
        problems = List.copyOf(problems);
    }

    public SimulationSnapshot withRunning(boolean isRunning) {
        return new SimulationSnapshot(revision, time, isRunning, netValues, componentStates, problems);
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
}
