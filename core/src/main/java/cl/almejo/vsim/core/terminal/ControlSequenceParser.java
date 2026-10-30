package cl.almejo.vsim.core.terminal;

import java.util.Arrays;

/**
 * The escape-sequence state machine of VT100-xterm mode (spec §6), fed decoded characters one at a time. It keeps its
 * state between writes, because a CPU sends a sequence one byte per clock. It only recognises and returns sequences;
 * {@link TerminalScreen} decides what they do. Unknown sequences, OSC and the other control strings (until BEL or ST)
 * are consumed; CAN and SUB cancel; a sequence longer than {@link #MAXIMUM_SEQUENCE_LENGTH} is abandoned and what
 * follows prints as text. A C0 control inside a sequence is executed and the sequence goes on, as in xterm.
 */
final class ControlSequenceParser {

    /** What one character fed to the parser produced. */
    sealed interface Action {
    }

    /** Nothing to do yet: the character is part of a sequence, or ended one that does nothing. */
    record Pending() implements Action {
    }

    /** A C0 control byte that arrived inside a sequence; the screen runs it as if it came alone. */
    record Execute(int control) implements Action {
    }

    /** {@code ESC} followed by a final character, such as {@code ESC 7} (DECSC) or {@code ESC c} (RIS). */
    record Escape(int finalCharacter) implements Action {
    }

    /**
     * A complete {@code ESC [} sequence.
     *
     * @param privateMarker {@code ?}, {@code <}, {@code =} or {@code >} right after {@code [}, or
     *                      {@link #NO_PRIVATE_MARKER}
     * @param parameters    the numbers between {@code ;}, {@link #ABSENT} where one was left empty (copied)
     * @param finalCharacter the letter that names the sequence ({@code H}, {@code m}, …)
     */
    record ControlSequence(int privateMarker, int[] parameters, int finalCharacter) implements Action {

        static final int NO_PRIVATE_MARKER = 0;
        static final int ABSENT = -1;

        ControlSequence {
            parameters = parameters.clone();
        }

        int parameterCount() {
            return parameters.length;
        }

        /** The parameter at {@code index}, or {@code defaultValue} when it is missing or empty. */
        int parameter(int index, int defaultValue) {
            if (index >= parameters.length || parameters[index] == ABSENT) {
                return defaultValue;
            }
            return parameters[index];
        }

        @Override
        public int[] parameters() {
            return parameters.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ControlSequence sequence && privateMarker == sequence.privateMarker
                    && finalCharacter == sequence.finalCharacter && Arrays.equals(parameters, sequence.parameters);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * privateMarker + finalCharacter) + Arrays.hashCode(parameters);
        }

        @Override
        public String toString() {
            return "ControlSequence[" + (char) privateMarker + Arrays.toString(parameters) + (char) finalCharacter
                    + "]";
        }
    }

    /** The one {@link Pending} action, so feeding a sequence allocates nothing per byte. */
    static final Pending PENDING = new Pending();
    /** Longer sequences are abandoned (spec §6), counting the ESC. */
    static final int MAXIMUM_SEQUENCE_LENGTH = 64;
    static final int ESCAPE = 0x1B;
    private static final int BELL = 0x07;
    private static final int CANCEL = 0x18;
    private static final int SUBSTITUTE = 0x1A;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int DELETE = 0x7F;
    private static final int LAST_INTERMEDIATE = 0x2F;
    private static final int FIRST_FINAL = 0x30;
    private static final int FIRST_CONTROL_SEQUENCE_FINAL = 0x40;
    private static final int LAST_FINAL = 0x7E;
    /** Enough parameters for {@code 38;2;r;g;b;48;2;r;g;b}; more are ignored. */
    private static final int MAXIMUM_PARAMETERS = 16;
    /** Parameters stop growing here, so a long run of digits never overflows. */
    private static final int MAXIMUM_PARAMETER_VALUE = 99_999;
    private static final int DECIMAL = 10;

    private enum State { GROUND, ESCAPE, ESCAPE_INTERMEDIATE, CONTROL_SEQUENCE, CONTROL_STRING }

    private State state = State.GROUND;
    private int length;
    private final int[] parameters = new int[MAXIMUM_PARAMETERS];
    private int parameterCount;
    private int privateMarker;
    private boolean unsupported;

    /** Whether no sequence is in progress; then printable characters and controls go straight to the screen. */
    boolean inGround() {
        return state == State.GROUND;
    }

    /** Leaves any sequence in progress. */
    void reset() {
        state = State.GROUND;
    }

    /** Feeds one decoded character; {@code ESC} starts a sequence, other characters only matter inside one. */
    Action feed(int codePoint) {
        if (codePoint == CANCEL || codePoint == SUBSTITUTE) {
            state = State.GROUND;
            return PENDING;
        }
        if (codePoint == ESCAPE) {
            startEscape();
            return PENDING;
        }
        if (state == State.GROUND) {
            return PENDING;
        }
        if (++length > MAXIMUM_SEQUENCE_LENGTH) {
            state = State.GROUND;
            return PENDING;
        }
        if (state == State.CONTROL_STRING) {
            return controlString(codePoint);
        }
        if (codePoint < FIRST_PRINTABLE) {
            return new Execute(codePoint);
        }
        if (codePoint == DELETE) {
            return PENDING;
        }
        return switch (state) {
            case ESCAPE -> escape(codePoint);
            case ESCAPE_INTERMEDIATE -> escapeIntermediate(codePoint);
            default -> controlSequence(codePoint);
        };
    }

    private void startEscape() {
        state = State.ESCAPE;
        length = 1;
    }

    private Action controlString(int codePoint) {
        if (codePoint == BELL) {
            state = State.GROUND;
        }
        return PENDING;
    }

    private Action escape(int codePoint) {
        switch (codePoint) {
            case '[' -> {
                state = State.CONTROL_SEQUENCE;
                parameterCount = 0;
                privateMarker = ControlSequence.NO_PRIVATE_MARKER;
                unsupported = false;
                return PENDING;
            }
            case ']', 'P', 'X', '^', '_' -> {
                state = State.CONTROL_STRING;
                return PENDING;
            }
            default -> {
                if (codePoint <= LAST_INTERMEDIATE) {
                    state = State.ESCAPE_INTERMEDIATE;
                    return PENDING;
                }
                state = State.GROUND;
                return codePoint >= FIRST_FINAL && codePoint <= LAST_FINAL ? new Escape(codePoint) : PENDING;
            }
        }
    }

    private Action escapeIntermediate(int codePoint) {
        if (codePoint > LAST_INTERMEDIATE) {
            state = State.GROUND;
        }
        return PENDING;
    }

    private Action controlSequence(int codePoint) {
        if (codePoint >= FIRST_CONTROL_SEQUENCE_FINAL && codePoint <= LAST_FINAL) {
            state = State.GROUND;
            return unsupported ? PENDING : new ControlSequence(privateMarker, Arrays.copyOf(parameters,
                    parameterCount), codePoint);
        }
        if (codePoint >= '0' && codePoint <= '9') {
            addDigit(codePoint - '0');
        } else if (codePoint == ';') {
            nextParameter();
        } else if (isPrivateMarker(codePoint) && length == 3) {
            privateMarker = codePoint;
        } else {
            unsupported = true;
        }
        return PENDING;
    }

    private static boolean isPrivateMarker(int codePoint) {
        return codePoint == '?' || codePoint == '<' || codePoint == '=' || codePoint == '>';
    }

    private void addDigit(int digit) {
        if (parameterCount == 0) {
            parameters[parameterCount++] = ControlSequence.ABSENT;
        }
        int index = parameterCount - 1;
        int current = parameters[index] == ControlSequence.ABSENT ? 0 : parameters[index];
        parameters[index] = Math.min(MAXIMUM_PARAMETER_VALUE, current * DECIMAL + digit);
    }

    private void nextParameter() {
        if (parameterCount == 0) { // a leading ';' closes an empty first parameter

            parameters[parameterCount++] = ControlSequence.ABSENT;
        }
        if (parameterCount < MAXIMUM_PARAMETERS) {
            parameters[parameterCount++] = ControlSequence.ABSENT;
        }
    }
}
