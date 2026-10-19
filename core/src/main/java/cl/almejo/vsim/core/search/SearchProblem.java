package cl.almejo.vsim.core.search;

import java.util.List;
import java.util.Objects;

/** Why a query matches nothing on purpose: a message key of the UI bundle and its arguments. */
public record SearchProblem(String messageKey, List<Object> arguments) {

    /** Any other invalid regular expression; the argument is Java's own description (English). */
    public static final String INVALID_REGEX = "search.invalidRegex";
    public static final String UNCLOSED_GROUP = "search.invalidRegex.unclosedGroup";
    public static final String UNCLOSED_CHARACTER_CLASS = "search.invalidRegex.unclosedCharacterClass";
    public static final String UNMATCHED_CLOSING_PARENTHESIS = "search.invalidRegex.unmatchedClosingParenthesis";
    /** A {@code *}, {@code +} or {@code ?} with nothing before it; the argument is that character. */
    public static final String NOTHING_TO_REPEAT = "search.invalidRegex.nothingToRepeat";
    public static final String BAD_REPETITION = "search.invalidRegex.badRepetition";
    public static final String BAD_ESCAPE = "search.invalidRegex.badEscape";
    public static final String BAD_RANGE = "search.invalidRegex.badRange";
    /** The regular expression ran out of its step budget (see {@link CompiledQuery}). */
    public static final String TOO_EXPENSIVE = "search.regexTooExpensive";

    public static final List<String> MESSAGE_KEYS = List.of(INVALID_REGEX, UNCLOSED_GROUP, UNCLOSED_CHARACTER_CLASS,
            UNMATCHED_CLOSING_PARENTHESIS, NOTHING_TO_REPEAT, BAD_REPETITION, BAD_ESCAPE, BAD_RANGE, TOO_EXPENSIVE);

    public SearchProblem {
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(arguments);
    }
}
