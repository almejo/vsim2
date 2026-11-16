package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /** A state's name as a new state gets it ({@code S3}): its copy takes the next free one too. */
    private static final Pattern NUMBERED_NAME = Pattern.compile(STATE_PREFIX + "\\d+");
    /** A name already numbered as a copy ({@code AMARILLO (2)}): its copy counts on from the name before it. */
    private static final Pattern COPY_NUMBER = Pattern.compile("(.*) \\((\\d+)\\)");
    /** How far a paste lands from what it would cover, each way, in diagram units (a state's radius). */
    static final double PASTE_STEP = MachineDiagramGeometry.STATE_RADIUS;
    /** A paste moves on at most this many steps looking for a free place. */
    private static final int PASTE_TRIES = 64;

    /** What a paste added: the machine after it and the ids of the new states and arrows, to select them. */
    record Pasted(StateMachine machine, Set<String> stateIds, Set<String> transitionIds) {
        Pasted {
            stateIds = Set.copyOf(stateIds);
            transitionIds = Set.copyOf(transitionIds);
        }
    }

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

    /** {@code machine} with {@code outputs} as the Mealy outputs of the arrow {@code transitionId}. */
    static StateMachine withMealyOutputs(StateMachine machine, String transitionId, List<OutputAssignment> outputs) {
        return machine.withTransition(transitionId, transition -> transition.withOutputs(outputs));
    }

    /**
     * {@code machine} with {@code condition} and {@code outputs} on the arrow {@code transitionId} at once (the
     * inline editor's whole label).
     */
    static StateMachine withLabel(StateMachine machine, String transitionId, String condition,
                                  List<OutputAssignment> outputs) {
        return machine.withTransition(transitionId,
                transition -> transition.withCondition(condition).withOutputs(outputs));
    }

    /** {@code machine} with {@code condition} as the arrow's condition, kept as written even when it is wrong. */
    static StateMachine withCondition(StateMachine machine, String transitionId, String condition) {
        return machine.withTransition(transitionId, transition -> transition.withCondition(condition));
    }

    /**
     * What Copiar takes from {@code machine}: the states {@code stateIds} with the arrows that join two of them (an
     * arrow to a state left behind stays behind), without an initial state, pins or tests; empty without a state.
     */
    static Optional<StateMachine> pieceOf(StateMachine machine, Set<String> stateIds) {
        List<MachineState> states = machine.states().stream().filter(state -> stateIds.contains(state.id())).toList();
        if (states.isEmpty()) {
            return Optional.empty();
        }
        List<MachineTransition> transitions = machine.transitions().stream()
                .filter(transition -> stateIds.contains(transition.fromId()) && stateIds.contains(transition.toId()))
                .toList();
        return Optional.of(new StateMachine(states, transitions, Optional.empty(), List.of(), List.of(), false, false,
                machine.edge(), List.of()));
    }

    /** Whether {@code piece} still fits in {@code machine} ({@link MachineLimits#MAXIMUM_STATES}). */
    static boolean fits(StateMachine machine, StateMachine piece) {
        return machine.states().size() + piece.states().size() <= MachineLimits.MAXIMUM_STATES;
    }

    /**
     * {@code machine} with {@code piece} added after its states and arrows: new ids, a free name for each state (the
     * name itself when free; else the next free {@code Sn} for a numbered one, or {@code AMARILLO (2)}, {@code (3)}…),
     * moved {@link #PASTE_STEP} down and right as many times as needed so that no state lands on another one (where
     * it was when nothing is in the way, as in another machine). The names in it become pins as any new name does.
     */
    static Pasted withPasted(StateMachine machine, StateMachine piece) {
        double shift = freeShift(machine, piece);
        List<MachineState> states = new ArrayList<>(machine.states());
        Map<String, String> newIds = new HashMap<>();
        Set<String> stateIds = new LinkedHashSet<>();
        for (MachineState copied : piece.states()) {
            List<MachineState> sofar = List.copyOf(states);
            String id = firstFree(STATE_ID_PREFIX, 0, candidate -> sofar.stream()
                    .anyMatch(state -> state.id().equals(candidate)));
            Set<String> names = new HashSet<>();
            sofar.forEach(state -> names.add(state.name()));
            states.add(new MachineState(id, freeName(copied.name(), names),
                    copied.position().translated(shift, shift), copied.outputs()));
            newIds.put(copied.id(), id);
            stateIds.add(id);
        }
        List<MachineTransition> transitions = new ArrayList<>(machine.transitions());
        Set<String> transitionIds = new LinkedHashSet<>();
        for (MachineTransition copied : piece.transitions()) {
            List<MachineTransition> sofar = List.copyOf(transitions);
            String id = firstFree(TRANSITION_ID_PREFIX, 1, candidate -> sofar.stream()
                    .anyMatch(transition -> transition.id().equals(candidate)));
            transitions.add(new MachineTransition(id, newIds.get(copied.fromId()), newIds.get(copied.toId()),
                    copied.condition(), copied.outputs()));
            transitionIds.add(id);
        }
        StateMachine pasted = machine.withStates(states).withTransitions(transitions);
        if (machine.states().isEmpty()) {
            pasted = pasted.withInitialStateId(Optional.of(states.get(0).id()));
        }
        return new Pasted(pasted, stateIds, transitionIds);
    }

    /** How far {@code piece} moves (both ways) so that none of its states lands on one of {@code machine}'s. */
    private static double freeShift(StateMachine machine, StateMachine piece) {
        double shift = 0;
        for (int attempt = 0; attempt < PASTE_TRIES && covers(machine, piece, shift); attempt++) {
            shift += PASTE_STEP;
        }
        return shift;
    }

    private static boolean covers(StateMachine machine, StateMachine piece, double shift) {
        return piece.states().stream().anyMatch(copied -> machine.states().stream().anyMatch(state ->
                Math.hypot(copied.position().x() + shift - state.position().x(),
                        copied.position().y() + shift - state.position().y()) < PASTE_STEP / 2));
    }

    /** {@code name} when no state has it; else the next free {@code Sn}, or {@code name (2)}, {@code (3)}… */
    private static String freeName(String name, Set<String> taken) {
        if (!taken.contains(name)) {
            return name;
        }
        if (NUMBERED_NAME.matcher(name).matches()) {
            return firstFree(STATE_PREFIX, 0, taken::contains);
        }
        Matcher numbered = COPY_NUMBER.matcher(name);
        String base = numbered.matches() ? numbered.group(1) : name;
        int number = 2;
        while (taken.contains(base + " (" + number + ")")) {
            number++;
        }
        return base + " (" + number + ")";
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
