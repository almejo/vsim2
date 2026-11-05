package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;

import java.util.List;
import java.util.Optional;

/**
 * Where a problem of the circuit is shown (spec §6.7): the part it names, by its simulation id ({@code c5/c2}), and the
 * place that part is in — the circuit itself, or the body of the definition of the instance above it, with that
 * instance as the values shown.
 */
final class ProblemPlaces {

    /**
     * Part {@code part} of the circuit ({@code definitionId} empty) or of the body of {@code definitionId} with the
     * values of the instance at {@code contextPath}.
     */
    record Target(Optional<String> definitionId, Optional<String> contextPath, ElementId part) {
    }

    private ProblemPlaces() {
    }

    /** The simulation id of the part {@code problem} is about first; empty when it names none. */
    static Optional<String> focusOf(Problem problem) {
        return switch (problem) {
            case ShortCircuit shortCircuit -> shortCircuit.drivers().stream().findFirst()
                    .map(driver -> driver.componentId());
            case WidthMismatch mismatch -> mismatch.pins().stream().findFirst().map(pin -> pin.componentId());
            case DisplayBusOverride display -> Optional.of(display.componentId());
            case TunnelProblem tunnel -> tunnel.tunnels().stream().findFirst();
            case ConnectivityProblem connectivity -> Optional.of(connectivity.elementId());
            case LinkedFileProblem linkedFile -> Optional.of(linkedFile.elementId());
        };
    }

    /**
     * Where the part {@code simulationId} of {@code circuit} is: on the circuit when it is a top-level id, else in the
     * body of the definition of the instance above it ({@code c5/c3/c2} → the definition of {@code c5/c3}, with
     * {@code c5/c3} as the values, part {@code c2}). Empty when a level is not in the circuit any more.
     */
    static Optional<Target> targetOf(String simulationId, CircuitDocument circuit) {
        List<String> segments = InstancePath.segments(simulationId);
        ElementId part = new ElementId(segments.getLast());
        if (segments.size() == 1) {
            return Optional.of(new Target(Optional.empty(), Optional.empty(), part));
        }
        List<Component> level = circuit.components();
        Optional<ComponentDefinition> definition = Optional.empty();
        for (String segment : segments.subList(0, segments.size() - 1)) {
            Optional<Component> instance = level.stream()
                    .filter(component -> component.id().value().equals(segment)).findFirst();
            definition = instance.flatMap(found -> SubcircuitTypes.definitionIdOf(found.typeId()))
                    .flatMap(circuit::definition);
            if (definition.isEmpty()) {
                return Optional.empty();
            }
            level = definition.get().body().components();
        }
        String instancePath = simulationId.substring(0, simulationId.lastIndexOf(InstancePath.SEPARATOR));
        return Optional.of(new Target(definition.map(ComponentDefinition::id), Optional.of(instancePath), part));
    }
}
