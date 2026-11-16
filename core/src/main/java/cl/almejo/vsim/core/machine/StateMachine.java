package cl.almejo.vsim.core.machine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * The inside of a «Máquina de estados» user component: its states and arrows, the initial state, its input and
 * output pins in pin order (a pin stays after its name is no longer used: «sin usar»), whether they show as a bus,
 * the clock edge it reacts to, its saved tests and the notes stuck on its diagram. Immutable; ids are stable and
 * never shown (a pin's id survives a rename, so the wires on it stay).
 * Design: docs/interno/superpowers/specs/2026-11-14-maquinas-de-estado-design.md §2.1.
 */
public record StateMachine(List<MachineState> states, List<MachineTransition> transitions,
                           Optional<String> initialStateId, List<MachinePin> inputs, List<MachinePin> outputs,
                           boolean inputsAsBus, boolean outputsAsBus, String edge, List<MachineTest> tests,
                           List<MachineNote> notes) {

    /** The values of {@code StandardParameters.EDGE}; a machine reacts to the falling edge by default. */
    public static final String EDGE_FALLING = "falling";
    public static final String EDGE_RISING = "rising";

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public StateMachine {
        states = List.copyOf(states);
        transitions = List.copyOf(transitions);
        Objects.requireNonNull(initialStateId, "initialStateId");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        Objects.requireNonNull(edge, "edge");
        if (!edge.equals(EDGE_FALLING) && !edge.equals(EDGE_RISING)) {
            throw new IllegalArgumentException("unknown machine edge: " + edge);
        }
        tests = List.copyOf(tests);
        notes = List.copyOf(notes);
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
        requireUniqueNames(names(inputs), names(outputs));
        requireUniqueIds(inputs, "input");
        requireUniqueIds(outputs, "output");
        Set<String> noteIds = new HashSet<>();
        for (MachineNote note : notes) {
            if (!noteIds.add(note.id())) {
                throw new IllegalArgumentException("note id used twice: " + note.id());
            }
        }
    }

    /**
     * A machine whose pins are born from {@code inputNames} and {@code outputNames} (each id equal to its name),
     * without notes: how the machines of hitos 1–2 were made, and what most tests need.
     */
    public StateMachine(List<MachineState> states, List<MachineTransition> transitions,
                        Optional<String> initialStateId, List<String> inputNames, List<String> outputNames,
                        boolean inputsAsBus, boolean outputsAsBus, String edge, List<MachineTest> tests) {
        this(states, transitions, initialStateId, named(inputNames), named(outputNames), inputsAsBus, outputsAsBus,
                edge, tests, List.of());
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

    private static List<MachinePin> named(List<String> names) {
        return names.stream().map(MachinePin::named).toList();
    }

    private static List<String> names(List<MachinePin> pins) {
        return pins.stream().map(MachinePin::name).toList();
    }

    private static void requireUniqueIds(List<MachinePin> pins, String side) {
        Set<String> seen = new HashSet<>();
        for (MachinePin pin : pins) {
            if (!seen.add(pin.id())) {
                throw new IllegalArgumentException(side + " pin id used twice: " + pin.id());
            }
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
            if (MachineLimits.isReserved(name)) {
                throw new IllegalArgumentException("every machine has the pin " + name + " already");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("machine pin name used twice: " + name);
            }
        }
    }

    /** The names of the input pins, in pin order. */
    public List<String> inputNames() {
        return names(inputs);
    }

    /** The names of the output pins, in pin order. */
    public List<String> outputNames() {
        return names(outputs);
    }

    /** The pins of {@code side}, in pin order. */
    public List<MachinePin> pins(MachinePinEdits.Side side) {
        return side == MachinePinEdits.Side.INPUT ? inputs : outputs;
    }

    /** The pin called {@code name} on {@code side}. */
    public Optional<MachinePin> pin(MachinePinEdits.Side side, String name) {
        return pins(side).stream().filter(pin -> pin.name().equals(name)).findFirst();
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

    /**
     * The names the conditions read, in order of first appearance over the arrows (bad conditions add none), without
     * {@code CLK}, {@code RST} and the names of {@link #outputNamesUsed() outputs} (an output is never an input).
     */
    public List<String> inputNamesUsed() {
        Set<String> outputNames = new HashSet<>(outputNamesUsed());
        outputNames.addAll(outputNames());
        Set<String> names = new LinkedHashSet<>();
        for (MachineTransition transition : transitions) {
            for (String name : MachineConditions.parse(transition.condition()).names()) {
                if (!MachineLimits.isReserved(name) && !outputNames.contains(name)) {
                    names.add(name);
                }
            }
        }
        return List.copyOf(names);
    }

    /**
     * The names given a value by a state (Moore) or an arrow (Mealy), in order of first appearance (states first),
     * without {@code CLK} and {@code RST}.
     */
    public List<String> outputNamesUsed() {
        Set<String> names = new LinkedHashSet<>();
        for (MachineState state : states) {
            addOutputNames(names, state.outputs());
        }
        for (MachineTransition transition : transitions) {
            addOutputNames(names, transition.outputs());
        }
        return List.copyOf(names);
    }

    private static void addOutputNames(Set<String> names, List<OutputAssignment> assignments) {
        for (OutputAssignment assignment : assignments) {
            if (!MachineLimits.isReserved(assignment.output())) {
                names.add(assignment.output());
            }
        }
    }

    /**
     * This machine with every name in use that is not a pin yet added after its pins (inputs and outputs), up to
     * {@link MachineLimits}: «a new name in a condition creates an input». Pins no longer used stay («sin usar»).
     */
    public StateMachine withUsedNamesAdded() {
        List<String> newOutputNames = withAdded(outputNames(), outputNamesUsed(), MachineLimits.MAXIMUM_OUTPUTS,
                new HashSet<>(inputNames()));
        List<String> newInputNames = withAdded(inputNames(), inputNamesUsed(), MachineLimits.MAXIMUM_INPUTS,
                new HashSet<>(newOutputNames));
        if (newInputNames.equals(inputNames()) && newOutputNames.equals(outputNames())) {
            return this;
        }
        return withPinNames(newInputNames, newOutputNames);
    }

    private static List<String> withAdded(List<String> pins, List<String> used, int limit, Set<String> taken) {
        List<String> result = new ArrayList<>(pins);
        for (String name : used) {
            if (result.size() >= limit) {
                break;
            }
            if (!result.contains(name) && !taken.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    public StateMachine withStates(List<MachineState> newStates) {
        return new StateMachine(newStates, transitions, initialStateId, inputs, outputs, inputsAsBus, outputsAsBus,
                edge, tests, notes);
    }

    public StateMachine withTransitions(List<MachineTransition> newTransitions) {
        return new StateMachine(states, newTransitions, initialStateId, inputs, outputs, inputsAsBus, outputsAsBus,
                edge, tests, notes);
    }

    public StateMachine withInitialStateId(Optional<String> newInitialStateId) {
        return new StateMachine(states, transitions, newInitialStateId, inputs, outputs, inputsAsBus, outputsAsBus,
                edge, tests, notes);
    }

    public StateMachine withPins(List<MachinePin> newInputs, List<MachinePin> newOutputs) {
        return new StateMachine(states, transitions, initialStateId, newInputs, newOutputs, inputsAsBus,
                outputsAsBus, edge, tests, notes);
    }

    /**
     * This machine with these pin names in this order: a name that is already a pin of its side keeps that pin (and
     * its id, so its wires); a new name gets a new pin ({@link MachinePin#minted}).
     */
    public StateMachine withPinNames(List<String> newInputNames, List<String> newOutputNames) {
        return withPins(pinsNamed(inputs, newInputNames), pinsNamed(outputs, newOutputNames));
    }

    private static List<MachinePin> pinsNamed(List<MachinePin> current, List<String> names) {
        List<Optional<MachinePin>> kept = names.stream()
                .map(name -> current.stream().filter(pin -> pin.name().equals(name)).findFirst()).toList();
        Set<String> takenIds = new HashSet<>();
        kept.forEach(pin -> pin.ifPresent(present -> takenIds.add(present.id())));
        List<MachinePin> result = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            MachinePin pin = kept.get(index).orElseGet(() -> MachinePin.minted(name, takenIds));
            takenIds.add(pin.id());
            result.add(pin);
        }
        return result;
    }

    public StateMachine withBus(boolean newInputsAsBus, boolean newOutputsAsBus) {
        return new StateMachine(states, transitions, initialStateId, inputs, outputs, newInputsAsBus,
                newOutputsAsBus, edge, tests, notes);
    }

    public StateMachine withEdge(String newEdge) {
        return new StateMachine(states, transitions, initialStateId, inputs, outputs, inputsAsBus, outputsAsBus,
                newEdge, tests, notes);
    }

    public StateMachine withTests(List<MachineTest> newTests) {
        return new StateMachine(states, transitions, initialStateId, inputs, outputs, inputsAsBus, outputsAsBus,
                edge, newTests, notes);
    }

    public StateMachine withNotes(List<MachineNote> newNotes) {
        return new StateMachine(states, transitions, initialStateId, inputs, outputs, inputsAsBus, outputsAsBus,
                edge, tests, newNotes);
    }

    /** This machine with the note {@code noteId} replaced by what {@code change} gives. */
    public StateMachine withNote(String noteId, UnaryOperator<MachineNote> change) {
        return withNotes(notes.stream().map(note -> note.id().equals(noteId) ? change.apply(note) : note).toList());
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
