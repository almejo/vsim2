package cl.almejo.vsim.core.machine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One problem of a «Máquina de estados» ({@link MachineChecks}): what kind, the states and arrows it is about (by id,
 * for the glows and the navigation), the names it is about (pins, outputs), the inputs that show it ({@code witness},
 * in the order the conditions name them: «con x = 1, y = 0») and the column of a bad condition ({@code -1} for every
 * other kind). Core says what; the editor words it.
 */
public record MachineFinding(Kind kind, List<String> stateIds, List<String> transitionIds, List<String> names,
                             Map<String, Boolean> witness, int column) {

    /** No column: every kind but {@link Kind#BAD_CONDITION}. */
    public static final int NO_COLUMN = -1;

    /** The kinds, in the order {@link MachineChecks#check} lists them. */
    public enum Kind {
        /** The machine has no state at all. */
        NO_STATES,
        /** No state is the initial one: the machine holds no state and does nothing. */
        NO_INITIAL_STATE,
        /** An arrow's condition cannot be read; the arrow is never taken. */
        BAD_CONDITION,
        /** A state has two arrows without condition. */
        TWO_OTHERWISE,
        /** Two arrows of a state hold at once and go to different places or give different outputs. */
        NONDETERMINISTIC,
        /** A condition reads an output, {@code CLK} or {@code RST}, which it reads as 0. */
        READS_OUTPUT_OR_RESERVED,
        /** A name beyond the 16 inputs or 16 outputs a machine can have: not a pin, read as 0. */
        TOO_MANY_NAMES,
        /**
         * More states than {@link MachineLimits#MAXIMUM_STATES}, which only a hand-made file can hold: all kept, none
         * can be added. The same for the next three limits.
         */
        TOO_MANY_STATES,
        /** More input pins than {@link MachineLimits#MAXIMUM_INPUTS} (a hand-made file). */
        TOO_MANY_INPUTS,
        /** More output pins than {@link MachineLimits#MAXIMUM_OUTPUTS} (a hand-made file). */
        TOO_MANY_OUTPUTS,
        /** More tests than {@link MachineTests#MAXIMUM_TESTS} (a hand-made file). */
        TOO_MANY_TESTS,
        /** No arrow leads to the state from the initial one. */
        UNREACHABLE_STATE,
        /** A state with conditional arrows and no «otherwise» one stays for some inputs. */
        STAYS_FOR_SOME_INPUT,
        /** An output pin no state or arrow gives a value: it is always 0. */
        OUTPUT_NEVER_ASSIGNED,
        /** An input pin no condition reads. */
        UNUSED_INPUT,
        /** An arrow gives an output another value than its state does: the arrow wins. */
        MOORE_MEALY_CLASH,
        /** At the last clock edge an input the conditions read was Z or in conflict: it was read as 0. */
        INPUT_UNDEFINED_AT_EDGE,
        /** {@code CLK} is Z or in conflict now: the machine does not advance. */
        CLOCK_UNDEFINED;

        /** Warnings are only listed; errors are counted on the red badge too. */
        public boolean isWarning() {
            return switch (this) {
                case NO_STATES, NO_INITIAL_STATE, BAD_CONDITION, TWO_OTHERWISE, NONDETERMINISTIC,
                     READS_OUTPUT_OR_RESERVED, TOO_MANY_NAMES, TOO_MANY_STATES, TOO_MANY_INPUTS, TOO_MANY_OUTPUTS,
                     TOO_MANY_TESTS -> false;
                case UNREACHABLE_STATE, STAYS_FOR_SOME_INPUT, OUTPUT_NEVER_ASSIGNED, UNUSED_INPUT, MOORE_MEALY_CLASH,
                     INPUT_UNDEFINED_AT_EDGE, CLOCK_UNDEFINED -> true;
            };
        }
    }

    public MachineFinding {
        Objects.requireNonNull(kind, "kind");
        stateIds = List.copyOf(stateIds);
        transitionIds = List.copyOf(transitionIds);
        names = List.copyOf(names);
        witness = Collections.unmodifiableMap(new LinkedHashMap<>(witness));
    }

    /** A finding about states and arrows only. */
    static MachineFinding about(Kind kind, List<String> stateIds, List<String> transitionIds) {
        return new MachineFinding(kind, stateIds, transitionIds, List.of(), Map.of(), NO_COLUMN);
    }

    /** A finding about names (pins, outputs), with the states and arrows that use them. */
    static MachineFinding aboutNames(Kind kind, List<String> stateIds, List<String> transitionIds,
                                     List<String> names) {
        return new MachineFinding(kind, stateIds, transitionIds, names, Map.of(), NO_COLUMN);
    }

    /** {@code true} for a warning ({@link Kind#isWarning()}). */
    public boolean isWarning() {
        return kind.isWarning();
    }
}
