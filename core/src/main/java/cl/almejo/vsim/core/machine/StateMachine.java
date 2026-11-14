package cl.almejo.vsim.core.machine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * The inside of a «Máquina de estados» user component: its states and arrows, the initial state, the names of its
 * inputs and outputs in pin order (a name stays after it is no longer used: «sin usar»), whether they show as a bus,
 * the clock edge it reacts to and its saved tests. Immutable; ids are stable and never shown.
 * Design: docs/interno/superpowers/specs/2026-11-14-maquinas-de-estado-design.md §2.1.
 */
public record StateMachine(List<MachineState> states, List<MachineTransition> transitions,
                           Optional<String> initialStateId, List<String> inputNames, List<String> outputNames,
                           boolean inputsAsBus, boolean outputsAsBus, String edge, List<MachineTest> tests) {

    /** The values of {@code StandardParameters.EDGE}; a machine reacts to the falling edge by default. */
    public static final String EDGE_FALLING = "falling";
    public static final String EDGE_RISING = "rising";

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public StateMachine {
        states = List.copyOf(states);
        transitions = List.copyOf(transitions);
        Objects.requireNonNull(initialStateId, "initialStateId");
        inputNames = List.copyOf(inputNames);
        outputNames = List.copyOf(outputNames);
        Objects.requireNonNull(edge, "edge");
        if (!edge.equals(EDGE_FALLING) && !edge.equals(EDGE_RISING)) {
            throw new IllegalArgumentException("unknown machine edge: " + edge);
        }
        tests = List.copyOf(tests);
        Set<String> stateIds = new HashSet<>();
        for (MachineState state : states) {
            if (!stateIds.add(state.id())) {
                throw new IllegalArgumentException("state id used twice: " + state.id());
            }
        }
        Set<String> transitionIds = new HashSet<>();
        for (MachineTransition transition : transitions) {
            if (!transitionIds.add(transition.id())) {
                throw new IllegalArgumentException("transition id used twice: " + transition.id());
            }
            if (!stateIds.contains(transition.fromId()) || !stateIds.contains(transition.toId())) {
                throw new IllegalArgumentException("transition " + transition.id() + " joins a missing state");
            }
        }
        initialStateId.ifPresent(initial -> {
            if (!stateIds.contains(initial)) {
                throw new IllegalArgumentException("the initial state " + initial + " does not exist");
            }
        });
        requireUniqueNames(inputNames, outputNames);
    }

    /** A new machine: one state {@code S0}, initial, at the origin; no arrows, no pins, falling edge. */
    public static StateMachine initial() {
        MachineState first = new MachineState("s0", "S0", DiagramPoint.ORIGIN, List.of());
        return new StateMachine(List.of(first), List.of(), Optional.of(first.id()), List.of(), List.of(), false,
                false, EDGE_FALLING, List.of());
    }

    static void requireId(String id, String what) {
        Objects.requireNonNull(id, what + " id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(what + " id must be a slug: " + id);
        }
    }

    private static void requireUniqueNames(List<String> inputs, List<String> outputs) {
        Set<String> seen = new HashSet<>();
        List<String> all = new ArrayList<>(inputs);
        all.addAll(outputs);
        for (String name : all) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("a machine pin needs a name");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("machine pin name used twice: " + name);
            }
        }
    }

    public Optional<MachineState> state(String stateId) {
        return states.stream().filter(state -> state.id().equals(stateId)).findFirst();
    }

    public Optional<MachineTransition> transition(String transitionId) {
        return transitions.stream().filter(transition -> transition.id().equals(transitionId)).findFirst();
    }

    /** The arrows that leave {@code stateId}, in model order (self loops included). */
    public List<MachineTransition> transitionsFrom(String stateId) {
        return transitions.stream().filter(transition -> transition.fromId().equals(stateId)).toList();
    }

    public StateMachine withStates(List<MachineState> newStates) {
        return new StateMachine(newStates, transitions, initialStateId, inputNames, outputNames, inputsAsBus,
                outputsAsBus, edge, tests);
    }

    public StateMachine withTransitions(List<MachineTransition> newTransitions) {
        return new StateMachine(states, newTransitions, initialStateId, inputNames, outputNames, inputsAsBus,
                outputsAsBus, edge, tests);
    }

    public StateMachine withInitialStateId(Optional<String> newInitialStateId) {
        return new StateMachine(states, transitions, newInitialStateId, inputNames, outputNames, inputsAsBus,
                outputsAsBus, edge, tests);
    }

    public StateMachine withPinNames(List<String> newInputNames, List<String> newOutputNames) {
        return new StateMachine(states, transitions, initialStateId, newInputNames, newOutputNames, inputsAsBus,
                outputsAsBus, edge, tests);
    }

    public StateMachine withBus(boolean newInputsAsBus, boolean newOutputsAsBus) {
        return new StateMachine(states, transitions, initialStateId, inputNames, outputNames, newInputsAsBus,
                newOutputsAsBus, edge, tests);
    }

    public StateMachine withEdge(String newEdge) {
        return new StateMachine(states, transitions, initialStateId, inputNames, outputNames, inputsAsBus,
                outputsAsBus, newEdge, tests);
    }

    public StateMachine withTests(List<MachineTest> newTests) {
        return new StateMachine(states, transitions, initialStateId, inputNames, outputNames, inputsAsBus,
                outputsAsBus, edge, newTests);
    }

    /** This machine with the state {@code stateId} replaced by what {@code change} gives. */
    public StateMachine withState(String stateId, UnaryOperator<MachineState> change) {
        return withStates(states.stream().map(state -> state.id().equals(stateId) ? change.apply(state) : state)
                .toList());
    }

    /** This machine with the transition {@code transitionId} replaced by what {@code change} gives. */
    public StateMachine withTransition(String transitionId, UnaryOperator<MachineTransition> change) {
        return withTransitions(transitions.stream()
                .map(transition -> transition.id().equals(transitionId) ? change.apply(transition) : transition)
                .toList());
    }
}
