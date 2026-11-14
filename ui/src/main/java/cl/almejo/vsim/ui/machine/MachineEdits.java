package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The edits the diagram's gestures, its menu items and the right panel make on a machine (spec 2026-11-14 §1
 * «Drawing»), as pure functions of the model: each returns the machine after the edit, which the tab then puts as one
 * {@code EditMachineCommand}. New states are called {@code S0}, {@code S1}, … (the first free name, spec §4.2); ids
 * are the first free {@code s0, s1, …} for states and {@code t1, t2, …} for arrows (as the examples number them).
 */
final class MachineEdits {

    /** The prefix of a new state's name and id: {@code S3} / {@code s3}. */
    private static final String STATE_PREFIX = "S";
    private static final String STATE_ID_PREFIX = "s";
    private static final String TRANSITION_ID_PREFIX = "t";

    private MachineEdits() {
    }

    /**
     * {@code machine} with a new state centred at {@code centre}, last in the model, with the first free name; the
     * first state of a machine without states is its initial state.
     */
    static StateMachine withNewState(StateMachine machine, DiagramPoint centre) {
        String id = firstFree(STATE_ID_PREFIX, 0, candidate -> machine.state(candidate).isPresent());
        String name = firstFree(STATE_PREFIX, 0, candidate -> machine.states().stream()
                .anyMatch(state -> state.name().equals(candidate)));
        List<MachineState> states = new ArrayList<>(machine.states());
        states.add(new MachineState(id, name, centre, List.of()));
        StateMachine added = machine.withStates(states);
        return machine.states().isEmpty() ? added.withInitialStateId(Optional.of(id)) : added;
    }

    /** The state {@link #withNewState} added: the last one. */
    static MachineState lastState(StateMachine machine) {
        return machine.states().get(machine.states().size() - 1);
    }

    /** {@code machine} with a new arrow from {@code fromId} to {@code toId} (the same for a loop), «otherwise». */
    static StateMachine withNewTransition(StateMachine machine, String fromId, String toId) {
        String id = firstFree(TRANSITION_ID_PREFIX, 1, candidate -> machine.transition(candidate).isPresent());
        List<MachineTransition> transitions = new ArrayList<>(machine.transitions());
        transitions.add(new MachineTransition(id, fromId, toId, "", List.of()));
        return machine.withTransitions(transitions);
    }

    /** The arrow {@link #withNewTransition} added: the last one. */
    static MachineTransition lastTransition(StateMachine machine) {
        return machine.transitions().get(machine.transitions().size() - 1);
    }

    /**
     * {@code machine} without the states {@code stateIds} (each with every arrow that leaves or enters it) and the
     * arrows {@code transitionIds}. Deleting the initial state is allowed: the machine then has none (a problem the
     * Problems list reports).
     */
    static StateMachine without(StateMachine machine, Set<String> stateIds, Set<String> transitionIds) {
        List<MachineTransition> transitions = machine.transitions().stream()
                .filter(transition -> !transitionIds.contains(transition.id())
                        && !stateIds.contains(transition.fromId()) && !stateIds.contains(transition.toId()))
                .toList();
        Optional<String> initial = machine.initialStateId().filter(id -> !stateIds.contains(id));
        return machine.withInitialStateId(initial).withTransitions(transitions)
                .withStates(machine.states().stream().filter(state -> !stateIds.contains(state.id())).toList());
    }

    /** {@code machine} with the states {@code stateIds} moved by ({@code deltaX}, {@code deltaY}) diagram units. */
    static StateMachine withStatesMoved(StateMachine machine, Set<String> stateIds, double deltaX, double deltaY) {
        return machine.withStates(machine.states().stream()
                .map(state -> stateIds.contains(state.id())
                        ? state.withPosition(state.position().translated(deltaX, deltaY)) : state)
                .toList());
    }

    /**
     * Why {@code name} cannot be the name of the state {@code stateId} (a bundle key: blank, or another state's
     * name); empty when it can.
     */
    static Optional<String> stateNameProblem(StateMachine machine, String stateId, String name) {
        String wanted = name.strip();
        if (wanted.isEmpty()) {
            return Optional.of("machine.stateName.blank");
        }
        boolean taken = machine.states().stream()
                .anyMatch(state -> !state.id().equals(stateId) && state.name().equals(wanted));
        return taken ? Optional.of("machine.stateName.taken") : Optional.empty();
    }

    /** {@code machine} with the state {@code stateId} called {@code name} (checked by {@link #stateNameProblem}). */
    static StateMachine withStateName(StateMachine machine, String stateId, String name) {
        return machine.withState(stateId, state -> state.withName(name));
    }

    /**
     * {@code machine} with {@code stateId} as its initial state, or, when {@code initial} is false and it is the
     * initial one, with none.
     */
    static StateMachine withInitial(StateMachine machine, String stateId, boolean initial) {
        if (initial) {
            return machine.withInitialStateId(Optional.of(stateId));
        }
        return machine.initialStateId().filter(stateId::equals).isPresent()
                ? machine.withInitialStateId(Optional.empty()) : machine;
    }

    /** {@code machine} with {@code outputs} as the Moore outputs of the state {@code stateId}. */
    static StateMachine withMooreOutputs(StateMachine machine, String stateId, List<OutputAssignment> outputs) {
        return machine.withState(stateId, state -> state.withOutputs(outputs));
    }

    /** {@code machine} with {@code condition} as the arrow's condition, kept as written even when it is wrong. */
    static StateMachine withCondition(StateMachine machine, String transitionId, String condition) {
        return machine.withTransition(transitionId, transition -> transition.withCondition(condition));
    }

    /** {@code prefix} followed by the smallest number from {@code first} on whose result is not {@code taken}. */
    private static String firstFree(String prefix, int first, Predicate<String> taken) {
        int number = first;
        while (taken.test(prefix + number)) {
            number++;
        }
        return prefix + number;
    }
}
