package cl.almejo.vsim.core.machine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a machine assigns its outputs: in its states (Moore), on its arrows (Mealy), both, or nowhere yet. The names
 * are distinct and in the machine's output pin order (names not on a pin come last, in the order they appear).
 */
public record MachineKind(boolean moore, boolean mealy, List<String> outputsInStates, List<String> outputsOnArrows) {

    public MachineKind {
        outputsInStates = List.copyOf(outputsInStates);
        outputsOnArrows = List.copyOf(outputsOnArrows);
    }

    public static MachineKind of(StateMachine machine) {
        Set<String> inStates = new LinkedHashSet<>();
        machine.states().forEach(state -> state.outputs().forEach(assignment -> inStates.add(assignment.output())));
        Set<String> onArrows = new LinkedHashSet<>();
        machine.transitions()
                .forEach(transition -> transition.outputs().forEach(assignment -> onArrows.add(assignment.output())));
        return new MachineKind(!inStates.isEmpty(), !onArrows.isEmpty(), inPinOrder(machine, inStates),
                inPinOrder(machine, onArrows));
    }

    private static List<String> inPinOrder(StateMachine machine, Set<String> names) {
        List<String> ordered = new ArrayList<>();
        machine.outputNames().stream().filter(names::contains).forEach(ordered::add);
        names.stream().filter(name -> !ordered.contains(name)).forEach(ordered::add);
        return ordered;
    }

    /** Both kinds at once. */
    public boolean mixed() {
        return moore && mealy;
    }

    public boolean hasOutputs() {
        return moore || mealy;
    }
}
