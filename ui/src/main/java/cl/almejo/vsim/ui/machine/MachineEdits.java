package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.i18n.Messages;

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
    /**
     * How far a paste moves on each step (down and right, each way) while looking for a free place, in diagram units
     * (half a state's radius, so it stops at the nearest free place along the way).
     */
    static final double PASTE_STEP = MachineDiagramGeometry.STATE_RADIUS / 2;
    /** The least room between a pasted state's circle and any other state's circle (hand test of hito 2, round 2). */
    static final double PASTE_GAP = MachineDiagramGeometry.STATE_RADIUS / 2;
    /** A paste moves on at most this many steps looking for a free place. */
    private static final int PASTE_TRIES = 256;

    /** The prefix of a note's id: {@code n1}, {@code n2}, … */
    private static final String NOTE_ID_PREFIX = "n";
    /**
     * A note's size goes in whole steps of this many diagram units (a grid unit of the circuit's notes), so it is
     * painted exactly as a circuit note of that many units.
     */
    static final double NOTE_SIZE_STEP = MachineNotePainting.UNIT;

    /** What a paste added: the machine after it and the ids of the new states, arrows and notes, to select them. */
    record Pasted(StateMachine machine, Set<String> stateIds, Set<String> transitionIds, Set<String> noteIds) {
        Pasted {
            stateIds = Set.copyOf(stateIds);
            transitionIds = Set.copyOf(transitionIds);
            noteIds = Set.copyOf(noteIds);
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
        return without(machine, stateIds, transitionIds, Set.of());
    }

    /** {@link #without(StateMachine, Set, Set)}, and without the notes {@code noteIds} too: one Borrar. */
    static StateMachine without(StateMachine machine, Set<String> stateIds, Set<String> transitionIds,
                                Set<String> noteIds) {
        List<MachineTransition> transitions = machine.transitions().stream()
                .filter(transition -> !transitionIds.contains(transition.id())
                        && !stateIds.contains(transition.fromId()) && !stateIds.contains(transition.toId()))
                .toList();
        Optional<String> initial = machine.initialStateId().filter(id -> !stateIds.contains(id));
        return machine.withInitialStateId(initial).withTransitions(transitions)
                .withStates(machine.states().stream().filter(state -> !stateIds.contains(state.id())).toList())
                .withNotes(machine.notes().stream().filter(note -> !noteIds.contains(note.id())).toList());
    }

    /** {@code machine} with the states {@code stateIds} moved by ({@code deltaX}, {@code deltaY}) diagram units. */
    static StateMachine withStatesMoved(StateMachine machine, Set<String> stateIds, double deltaX, double deltaY) {
        return machine.withStates(machine.states().stream()
                .map(state -> stateIds.contains(state.id())
                        ? state.withPosition(state.position().translated(deltaX, deltaY)) : state)
                .toList());
    }

    /**
     * {@code machine} with the states {@code stateIds} and the notes {@code noteIds} moved by ({@code deltaX},
     * {@code deltaY}) diagram units: one drag of a selection.
     */
    static StateMachine withMoved(StateMachine machine, Set<String> stateIds, Set<String> noteIds, double deltaX,
                                  double deltaY) {
        return withStatesMoved(machine, stateIds, deltaX, deltaY).withNotes(machine.notes().stream()
                .map(note -> noteIds.contains(note.id())
                        ? note.withPosition(note.position().translated(deltaX, deltaY)) : note)
                .toList());
    }

    /**
     * {@code machine} with a new note whose top-left corner is {@code topLeft}: yellow, empty, of the default size,
     * last in the model, with the first free id {@code n1}, {@code n2}, …
     */
    static StateMachine withNewNote(StateMachine machine, DiagramPoint topLeft) {
        String id = firstFree(NOTE_ID_PREFIX, 1, candidate -> machine.notes().stream()
                .anyMatch(note -> note.id().equals(candidate)));
        List<MachineNote> notes = new ArrayList<>(machine.notes());
        notes.add(new MachineNote(id, topLeft, MachineNote.DEFAULT_WIDTH, MachineNote.DEFAULT_HEIGHT,
                StickyColor.YELLOW, ""));
        return machine.withNotes(notes);
    }

    /** The note {@link #withNewNote} added: the last one. */
    static MachineNote lastNote(StateMachine machine) {
        return machine.notes().get(machine.notes().size() - 1);
    }

    /**
     * {@code machine} with the note {@code noteId} grown by ({@code deltaWidth}, {@code deltaHeight}) from its
     * top-left corner, in whole {@link #NOTE_SIZE_STEP}s and never under the smallest note.
     */
    static StateMachine withNoteResized(StateMachine machine, String noteId, double deltaWidth, double deltaHeight) {
        return machine.withNote(noteId, note -> note.withSize(
                Math.max(MachineNote.MINIMUM_WIDTH, inSteps(note.width() + deltaWidth)),
                Math.max(MachineNote.MINIMUM_HEIGHT, inSteps(note.height() + deltaHeight))));
    }

    private static double inSteps(double length) {
        return Math.round(length / NOTE_SIZE_STEP) * NOTE_SIZE_STEP;
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
        return pieceOf(machine, stateIds, Set.of());
    }

    /**
     * {@link #pieceOf(StateMachine, Set)} with the notes {@code noteIds} too; a piece may hold notes alone. Empty when
     * it would hold nothing.
     */
    static Optional<StateMachine> pieceOf(StateMachine machine, Set<String> stateIds, Set<String> noteIds) {
        List<MachineState> states = machine.states().stream().filter(state -> stateIds.contains(state.id())).toList();
        List<MachineNote> notes = machine.notes().stream().filter(note -> noteIds.contains(note.id())).toList();
        if (states.isEmpty() && notes.isEmpty()) {
            return Optional.empty();
        }
        List<MachineTransition> transitions = machine.transitions().stream()
                .filter(transition -> stateIds.contains(transition.fromId()) && stateIds.contains(transition.toId()))
                .toList();
        return Optional.of(new StateMachine(states, transitions, Optional.empty(), List.of(), List.of(), false, false,
                machine.edge(), List.of()).withNotes(notes));
    }

    /**
     * Why {@code changed} cannot replace {@code machine}: it needs more input or output names than a machine has pins
     * for ({@link MachineLimits}), and more than {@code machine} already needed (a machine already past the limit, as
     * read from a file, can still be edited down); empty when it fits. The reason as the user reads it.
     */
    static Optional<String> pinLimitProblem(StateMachine machine, StateMachine changed) {
        if (outputsNeeded(changed) > MachineLimits.MAXIMUM_OUTPUTS
                && outputsNeeded(changed) > outputsNeeded(machine)) {
            return Optional.of(Messages.format("machine.limit.tooManyOutputs", MachineLimits.MAXIMUM_OUTPUTS));
        }
        if (inputsNeeded(changed) > MachineLimits.MAXIMUM_INPUTS && inputsNeeded(changed) > inputsNeeded(machine)) {
            return Optional.of(Messages.format("machine.limit.tooManyInputs", MachineLimits.MAXIMUM_INPUTS));
        }
        return Optional.empty();
    }

    /** The output names {@code machine} has as pins or assigns. */
    private static int outputsNeeded(StateMachine machine) {
        Set<String> names = new HashSet<>(machine.outputNames());
        names.addAll(machine.outputNamesUsed());
        return names.size();
    }

    /** The input names {@code machine} has as pins or reads. */
    private static int inputsNeeded(StateMachine machine) {
        Set<String> names = new HashSet<>(machine.inputNames());
        names.addAll(machine.inputNamesUsed());
        return names.size();
    }

    /** Whether {@code piece} still fits in {@code machine} ({@link MachineLimits#MAXIMUM_STATES}). */
    static boolean fits(StateMachine machine, StateMachine piece) {
        return machine.states().size() + piece.states().size() <= MachineLimits.MAXIMUM_STATES;
    }

    /**
     * {@code machine} with {@code piece} added after its states and arrows: new ids, a free name for each state (the
     * name itself when free; else the next free {@code Sn} for a numbered one, or {@code AMARILLO (2)}, {@code (3)}…),
     * moved {@link #PASTE_STEP} down and right as many times as needed so that each of its circles stays
     * {@link #PASTE_GAP} clear of every circle already there (where it was when nothing is in the way, as in another
     * machine). The names in it become pins as any new name does.
     */
    static Pasted withPasted(StateMachine machine, StateMachine piece) {
        List<MachineState> states = new ArrayList<>(machine.states());
        Map<String, String> newIds = new HashMap<>();
        Set<String> stateIds = new LinkedHashSet<>();
        List<MachineState> added = new ArrayList<>();
        for (MachineState copied : piece.states()) {
            List<MachineState> sofar = new ArrayList<>(states);
            sofar.addAll(added);
            String id = firstFree(STATE_ID_PREFIX, 0, candidate -> sofar.stream()
                    .anyMatch(state -> state.id().equals(candidate)));
            Set<String> names = new HashSet<>();
            sofar.forEach(state -> names.add(state.name()));
            added.add(new MachineState(id, freeName(copied.name(), names), copied.position(), copied.outputs(),
                    copied.color()));
            newIds.put(copied.id(), id);
            stateIds.add(id);
        }
        double shift = freeShift(machine, added, piece.notes());
        added.forEach(state -> states.add(state.withPosition(state.position().translated(shift, shift))));
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
        List<MachineNote> notes = new ArrayList<>(machine.notes());
        Set<String> noteIds = new LinkedHashSet<>();
        for (MachineNote copied : piece.notes()) {
            List<MachineNote> sofar = List.copyOf(notes);
            String id = firstFree(NOTE_ID_PREFIX, 1, candidate -> sofar.stream()
                    .anyMatch(note -> note.id().equals(candidate)));
            notes.add(copied.placed(id, copied.position().translated(shift, shift)));
            noteIds.add(id);
        }
        StateMachine pasted = machine.withStates(states).withTransitions(transitions).withNotes(notes);
        if (machine.states().isEmpty() && !states.isEmpty()) {
            pasted = pasted.withInitialStateId(Optional.of(states.get(0).id()));
        }
        return new Pasted(pasted, stateIds, transitionIds, noteIds);
    }

    /**
     * How far the {@code added} states and {@code addedNotes} move (both ways) so that each circle, as the diagram
     * draws it (its name may make it bigger), keeps {@link #PASTE_GAP} from every circle of {@code machine}'s states,
     * and no note lands within {@link #PASTE_GAP} of the place of one of its notes (it would hide it).
     */
    private static double freeShift(StateMachine machine, List<MachineState> added, List<MachineNote> addedNotes) {
        List<MachineState> existing = machine.states();
        Map<String, Double> radii = new HashMap<>();
        existing.forEach(state -> radii.put(state.id(), MachineDiagramGeometry.radiusOf(state)));
        added.forEach(state -> radii.put(state.id(), MachineDiagramGeometry.radiusOf(state)));
        double shift = 0;
        for (int attempt = 0; attempt < PASTE_TRIES && (touches(existing, added, radii, shift)
                || covers(machine.notes(), addedNotes, shift)); attempt++) {
            shift += PASTE_STEP;
        }
        return shift;
    }

    private static boolean covers(List<MachineNote> existing, List<MachineNote> added, double shift) {
        return added.stream().anyMatch(copied -> existing.stream().anyMatch(note ->
                Math.hypot(copied.position().x() + shift - note.position().x(),
                        copied.position().y() + shift - note.position().y()) < PASTE_GAP));
    }

    private static boolean touches(List<MachineState> existing, List<MachineState> added, Map<String, Double> radii,
                                   double shift) {
        return added.stream().anyMatch(copied -> existing.stream().anyMatch(state ->
                Math.hypot(copied.position().x() + shift - state.position().x(),
                        copied.position().y() + shift - state.position().y())
                        < radii.get(copied.id()) + radii.get(state.id()) + PASTE_GAP));
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
