package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.VisibleState.MachineVisibleState;
import cl.almejo.vsim.core.machine.MachineConditions.ParsedCondition;
import cl.almejo.vsim.core.machine.MachineFinding.Kind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The problems of a «Máquina de estados» (hito 2), found from its model alone ({@link #check}) or from what one copy
 * read while it ran ({@link #runtime}). Pure: core says which states, arrows and names, the editor words it.
 * <p>
 * A bad condition is reported once and then left out of every other check (it never holds, as in the simulation).
 * Two conditional arrows of one state that hold at once are found with each arrow's truth table over the inputs its
 * state's arrows read (at most 16, so 2^16 rows, as bits): the first row where both hold is the witness. Names that are
 * not input pins (an output, {@code CLK}, {@code RST}, a name beyond the 16 inputs) read 0, as in the simulation.
 * <p>
 * {@link #check} runs on every Problemas refresh and on paint paths (the circuit's overlay, the diagram's glows), so
 * its result is kept per machine (machines are immutable values; the cache holds them weakly). The last few machines
 * looked up are first found by identity, which skips the record's deep hash (every state and arrow) on each call.
 */
public final class MachineChecks {

    private static final Map<StateMachine, List<MachineFinding>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** How many recent machines {@link #check} finds by identity. */
    private static final int RECENT_SIZE = 16;
    /** The recent machines and their findings, by slot; written round-robin under the class's lock. */
    private static final Recent[] RECENT = new Recent[RECENT_SIZE];
    private static int nextRecentSlot;

    private record Recent(StateMachine machine, List<MachineFinding> findings) {
    }

    private MachineChecks() {
    }

    /** Every problem of {@code machine}'s model, in {@link Kind} order and, within a kind, in model order. */
    public static List<MachineFinding> check(StateMachine machine) {
        Objects.requireNonNull(machine, "machine");
        List<MachineFinding> recent = recentFindings(machine);
        if (recent != null) {
            return recent;
        }
        List<MachineFinding> findings = CACHE.get(machine);
        if (findings == null) {
            findings = new Checker(machine).findings();
            CACHE.put(machine, findings);
        }
        remember(machine, findings);
        return findings;
    }

    private static synchronized List<MachineFinding> recentFindings(StateMachine machine) {
        for (Recent recent : RECENT) {
            if (recent != null && recent.machine() == machine) {
                return recent.findings();
            }
        }
        return null;
    }

    private static synchronized void remember(StateMachine machine, List<MachineFinding> findings) {
        RECENT[nextRecentSlot] = new Recent(machine, findings);
        nextRecentSlot = (nextRecentSlot + 1) % RECENT_SIZE;
    }

    /**
     * What one running copy read as 0 without wanting to: inputs in Z or in conflict at the last clock edge, and a
     * clock in Z or in conflict now (the machine does not advance).
     */
    public static List<MachineFinding> runtime(MachineVisibleState state) {
        Objects.requireNonNull(state, "state");
        List<MachineFinding> findings = new ArrayList<>();
        if (!state.undefinedInputsAtLastEdge().isEmpty()) {
            findings.add(MachineFinding.aboutNames(Kind.INPUT_UNDEFINED_AT_EDGE, List.of(), List.of(),
                    state.undefinedInputsAtLastEdge()));
        }
        if (state.clockUndefined()) {
            findings.add(MachineFinding.about(Kind.CLOCK_UNDEFINED, List.of(), List.of()));
        }
        return List.copyOf(findings);
    }

    /** One run of the checks over one machine, its conditions read once. */
    private static final class Checker {

        private final StateMachine machine;
        private final Map<String, ParsedCondition> conditions = new HashMap<>();
        private final Set<String> inputPins;
        /** Every output name: the pins and the names states and arrows assign. */
        private final Set<String> outputs;
        private final List<MachineFinding> findings = new ArrayList<>();

        Checker(StateMachine machine) {
            this.machine = machine;
            machine.transitions().forEach(transition ->
                    conditions.put(transition.id(), MachineConditions.parse(transition.condition())));
            inputPins = new HashSet<>(machine.inputNames());
            outputs = new HashSet<>(machine.outputNames());
            outputs.addAll(machine.outputNamesUsed());
        }

        List<MachineFinding> findings() {
            if (machine.states().isEmpty()) {
                findings.add(MachineFinding.about(Kind.NO_STATES, List.of(), List.of()));
            } else if (machine.initialStateId().isEmpty()) {
                findings.add(MachineFinding.about(Kind.NO_INITIAL_STATE, List.of(), List.of()));
            }
            badConditions();
            twoOtherwise();
            List<MachineFinding> stays = new ArrayList<>();
            for (MachineState state : machine.states()) {
                StateTables tables = new StateTables(state.id());
                tables.nondeterministic();
                tables.stays().ifPresent(stays::add);
            }
            readsOutputOrReserved();
            tooManyNames();
            beyondTheLimits();
            unreachable();
            findings.addAll(stays);
            outputsNeverAssigned();
            unusedInputs();
            mooreMealyClashes();
            return List.copyOf(findings);
        }

        /**
         * Each limit a hand-made file passes (the editor never does): everything is kept and works, and nothing more
         * of that kind can be added.
         */
        private void beyondTheLimits() {
            if (machine.states().size() > MachineLimits.MAXIMUM_STATES) {
                findings.add(MachineFinding.about(Kind.TOO_MANY_STATES, List.of(), List.of()));
            }
            if (machine.inputs().size() > MachineLimits.MAXIMUM_INPUTS) {
                findings.add(MachineFinding.about(Kind.TOO_MANY_INPUTS, List.of(), List.of()));
            }
            if (machine.outputs().size() > MachineLimits.MAXIMUM_OUTPUTS) {
                findings.add(MachineFinding.about(Kind.TOO_MANY_OUTPUTS, List.of(), List.of()));
            }
            if (machine.tests().size() > MachineTests.MAXIMUM_TESTS) {
                findings.add(MachineFinding.about(Kind.TOO_MANY_TESTS, List.of(), List.of()));
            }
        }

        private void badConditions() {
            for (MachineTransition transition : machine.transitions()) {
                conditions.get(transition.id()).problem().ifPresent(problem -> findings.add(new MachineFinding(
                        Kind.BAD_CONDITION, List.of(), List.of(transition.id()), List.of(), Map.of(),
                        problem.column())));
            }
        }

        private void twoOtherwise() {
            for (MachineState state : machine.states()) {
                List<String> otherwise = machine.transitionsFrom(state.id()).stream()
                        .filter(transition -> conditions.get(transition.id()).isOtherwise())
                        .map(MachineTransition::id).toList();
                if (otherwise.size() > 1) {
                    findings.add(MachineFinding.about(Kind.TWO_OTHERWISE, List.of(state.id()), otherwise));
                }
            }
        }

        private void readsOutputOrReserved() {
            for (MachineTransition transition : machine.transitions()) {
                List<String> read = conditions.get(transition.id()).names().stream()
                        .filter(name -> MachineLimits.isReserved(name) || outputs.contains(name)).toList();
                if (!read.isEmpty()) {
                    findings.add(MachineFinding.aboutNames(Kind.READS_OUTPUT_OR_RESERVED, List.of(),
                            List.of(transition.id()), read));
                }
            }
        }

        /** Names in use that did not fit as pins: inputs a condition reads, then outputs a state or arrow gives. */
        private void tooManyNames() {
            Map<String, List<String>> readBy = new LinkedHashMap<>();
            for (MachineTransition transition : machine.transitions()) {
                for (String name : conditions.get(transition.id()).names()) {
                    if (!MachineLimits.isReserved(name) && !outputs.contains(name) && !inputPins.contains(name)) {
                        readBy.computeIfAbsent(name, key -> new ArrayList<>()).add(transition.id());
                    }
                }
            }
            readBy.forEach((name, transitionIds) -> findings.add(MachineFinding.aboutNames(Kind.TOO_MANY_NAMES,
                    List.of(), transitionIds, List.of(name))));
            for (String name : machine.outputNamesUsed()) {
                if (!machine.outputNames().contains(name)) {
                    findings.add(MachineFinding.aboutNames(Kind.TOO_MANY_NAMES, statesAssigning(name),
                            transitionsAssigning(name), List.of(name)));
                }
            }
        }

        private List<String> statesAssigning(String output) {
            return machine.states().stream().filter(state -> assigns(state.outputs(), output))
                    .map(MachineState::id).toList();
        }

        private List<String> transitionsAssigning(String output) {
            return machine.transitions().stream().filter(transition -> assigns(transition.outputs(), output))
                    .map(MachineTransition::id).toList();
        }

        private static boolean assigns(List<OutputAssignment> assignments, String output) {
            return assignments.stream().anyMatch(assignment -> assignment.output().equals(output));
        }

        /** The states no arrow leads to from the initial one (arrows with a bad condition are never taken). */
        private void unreachable() {
            if (machine.initialStateId().isEmpty()) {
                return;
            }
            Set<String> reached = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add(machine.initialStateId().get());
            reached.add(machine.initialStateId().get());
            while (!pending.isEmpty()) {
                String stateId = pending.poll();
                for (MachineTransition transition : machine.transitionsFrom(stateId)) {
                    boolean taken = conditions.get(transition.id()).problem().isEmpty();
                    if (taken && reached.add(transition.toId())) {
                        pending.add(transition.toId());
                    }
                }
            }
            for (MachineState state : machine.states()) {
                if (!reached.contains(state.id())) {
                    findings.add(MachineFinding.about(Kind.UNREACHABLE_STATE, List.of(state.id()), List.of()));
                }
            }
        }

        private void outputsNeverAssigned() {
            Set<String> assigned = new HashSet<>(machine.outputNamesUsed());
            for (String output : machine.outputNames()) {
                if (!assigned.contains(output)) {
                    findings.add(MachineFinding.aboutNames(Kind.OUTPUT_NEVER_ASSIGNED, List.of(), List.of(),
                            List.of(output)));
                }
            }
        }

        private void unusedInputs() {
            Set<String> read = new HashSet<>(machine.inputNamesUsed());
            for (String input : machine.inputNames()) {
                if (!read.contains(input)) {
                    findings.add(MachineFinding.aboutNames(Kind.UNUSED_INPUT, List.of(), List.of(), List.of(input)));
                }
            }
        }

        /** An arrow giving an output another value than the state it leaves: the arrow wins, once per output. */
        private void mooreMealyClashes() {
            for (MachineTransition transition : machine.transitions()) {
                Optional<MachineState> from = machine.state(transition.fromId());
                if (from.isEmpty()) {
                    continue;
                }
                Map<String, Boolean> moore = valuesOf(from.get().outputs());
                for (OutputAssignment mealy : transition.outputs()) {
                    Boolean stateValue = moore.get(mealy.output());
                    if (stateValue != null && stateValue != mealy.value()) {
                        findings.add(MachineFinding.aboutNames(Kind.MOORE_MEALY_CLASH, List.of(from.get().id()),
                                List.of(transition.id()), List.of(mealy.output())));
                    }
                }
            }
        }

        private static Map<String, Boolean> valuesOf(List<OutputAssignment> assignments) {
            Map<String, Boolean> values = new HashMap<>();
            assignments.forEach(assignment -> values.put(assignment.output(), assignment.value()));
            return values;
        }

        /** The truth tables of one state's readable conditional arrows, over the input pins they read. */
        private final class StateTables {

            private final String stateId;
            private final List<MachineTransition> conditional = new ArrayList<>();
            private final boolean hasOtherwise;
            /** The input pins the arrows read, in order of appearance: row bits, the first one the highest. */
            private final List<String> variables;
            private final List<TruthTable> tables = new ArrayList<>();

            StateTables(String stateId) {
                this.stateId = stateId;
                boolean otherwise = false;
                Set<String> names = new LinkedHashSet<>();
                for (MachineTransition transition : machine.transitionsFrom(stateId)) {
                    ParsedCondition condition = conditions.get(transition.id());
                    if (condition.isOtherwise()) {
                        otherwise = true;
                    } else if (condition.expression().isPresent()) {
                        conditional.add(transition);
                        condition.names().stream().filter(inputPins::contains).forEach(names::add);
                    }
                }
                hasOtherwise = otherwise;
                variables = List.copyOf(names);
                if (variables.size() > MachineLimits.MAXIMUM_INPUTS) {
                    // more inputs than a table can hold, only from a hand-made file: TOO_MANY_INPUTS says so, and
                    // this state's arrows are not compared (2^17 rows and more)
                    conditional.clear();
                }
                for (MachineTransition transition : conditional) {
                    tables.add(TruthTable.of(conditions.get(transition.id()).expression().orElseThrow(), variables));
                }
            }

            /** Every pair of arrows that hold at once and lead to different places or give different outputs. */
            void nondeterministic() {
                for (int first = 0; first < conditional.size(); first++) {
                    for (int second = first + 1; second < conditional.size(); second++) {
                        MachineTransition one = conditional.get(first);
                        MachineTransition other = conditional.get(second);
                        if (sameEffect(one, other)) {
                            continue;
                        }
                        int row = tables.get(first).firstRowAlsoIn(tables.get(second));
                        if (row >= 0) {
                            findings.add(new MachineFinding(Kind.NONDETERMINISTIC, List.of(stateId),
                                    List.of(one.id(), other.id()), List.of(),
                                    witness(row, namesOf(one, other)), MachineFinding.NO_COLUMN));
                        }
                    }
                }
            }

            /** With conditional arrows and no «otherwise» one, the first inputs for which none holds. */
            Optional<MachineFinding> stays() {
                if (hasOtherwise || conditional.isEmpty()) {
                    return Optional.empty();
                }
                int row = TruthTable.firstRowInNone(tables, variables.size());
                if (row < 0) {
                    return Optional.empty();
                }
                return Optional.of(new MachineFinding(Kind.STAYS_FOR_SOME_INPUT, List.of(stateId),
                        conditional.stream().map(MachineTransition::id).toList(), List.of(),
                        witness(row, variables), MachineFinding.NO_COLUMN));
            }

            private boolean sameEffect(MachineTransition one, MachineTransition other) {
                return one.toId().equals(other.toId()) && valuesOf(one.outputs()).equals(valuesOf(other.outputs()));
            }

            /** The input pins the two arrows read, the first arrow's first. */
            private List<String> namesOf(MachineTransition one, MachineTransition other) {
                Set<String> names = new LinkedHashSet<>(conditions.get(one.id()).names());
                names.addAll(conditions.get(other.id()).names());
                return names.stream().filter(inputPins::contains).toList();
            }

            private Map<String, Boolean> witness(int row, List<String> names) {
                Map<String, Boolean> values = new LinkedHashMap<>();
                for (String name : names) {
                    int bit = variables.size() - 1 - variables.indexOf(name);
                    values.put(name, (row >> bit & 1) == 1);
                }
                return values;
            }
        }
    }
}
