package cl.almejo.vsim.core.logic;

import java.util.List;

/**
 * Something wrong in the lines of «Circuito desde expresiones». The message key lives in the UI bundles and is
 * formatted with {@code arguments}; the UI adds the position itself.
 *
 * @param messageKey the bundle key, one of the constants of this record
 * @param arguments  the values for the message's placeholders
 * @param column     0-based offset in the line's text where the problem is, -1 when it is not about one place
 */
public record ExpressionProblem(String messageKey, List<Object> arguments, int column) {

    /** The line is blank. */
    public static final String EMPTY_LINE = "expressions.problem.emptyLine";
    /** The line has no {@code =}. */
    public static final String MISSING_EQUALS = "expressions.problem.missingEquals";
    /** Nothing left of the {@code =}. */
    public static final String BLANK_OUTPUT_NAME = "expressions.problem.blankOutputName";
    /** The output's name is not a name (a letter, then letters, digits or _). Arguments: the name. */
    public static final String INVALID_OUTPUT_NAME = "expressions.problem.invalidOutputName";
    /** Nothing right of the {@code =}. */
    public static final String EMPTY_EXPRESSION = "expressions.problem.emptyExpression";
    /** A character that is not part of the notation. Arguments: the character. */
    public static final String UNEXPECTED_CHARACTER = "expressions.problem.unexpectedCharacter";
    /** An operator without its operand (or an empty pair of parentheses, or the end of the line too soon). */
    public static final String MISSING_OPERAND = "expressions.problem.missingOperand";
    /** A {@code (} that is never closed; the column is the parenthesis'. */
    public static final String UNCLOSED_PARENTHESIS = "expressions.problem.unclosedParenthesis";
    /** A {@code )} without its {@code (}. */
    public static final String UNEXPECTED_CLOSING_PARENTHESIS = "expressions.problem.unexpectedClosingParenthesis";
    /** Two lines with the same output (ignoring case). Arguments: the second name. */
    public static final String REPEATED_OUTPUT = "expressions.problem.repeatedOutput";
    /** An output's name is used as a variable (ignoring case). Arguments: the name as used. */
    public static final String OUTPUT_USED_AS_INPUT = "expressions.problem.outputUsedAsInput";
    /** Two variables whose names differ only in case (pins must differ ignoring case). Arguments: both names. */
    public static final String INPUTS_DIFFER_ONLY_IN_CASE = "expressions.problem.inputsDifferOnlyInCase";
    /** No line uses a variable. */
    public static final String NO_INPUTS = "expressions.problem.noInputs";
    /** More inputs than allowed. Arguments: the count, the limit. */
    public static final String TOO_MANY_INPUTS = "expressions.problem.tooManyInputs";
    /** More lines than allowed outputs. Arguments: the count, the limit. */
    public static final String TOO_MANY_OUTPUTS = "expressions.problem.tooManyOutputs";
    /** No line at all. */
    public static final String NO_OUTPUTS = "expressions.problem.noOutputs";

    /** Every message key, for the bundle completeness test. */
    public static final List<String> MESSAGE_KEYS = List.of(EMPTY_LINE, MISSING_EQUALS, BLANK_OUTPUT_NAME,
            INVALID_OUTPUT_NAME, EMPTY_EXPRESSION, UNEXPECTED_CHARACTER, MISSING_OPERAND, UNCLOSED_PARENTHESIS,
            UNEXPECTED_CLOSING_PARENTHESIS, REPEATED_OUTPUT, OUTPUT_USED_AS_INPUT, INPUTS_DIFFER_ONLY_IN_CASE,
            NO_INPUTS, TOO_MANY_INPUTS, TOO_MANY_OUTPUTS, NO_OUTPUTS);

    public ExpressionProblem {
        arguments = List.copyOf(arguments);
    }

    /** A problem at {@code column} of the line. */
    static ExpressionProblem at(int column, String messageKey, Object... arguments) {
        return new ExpressionProblem(messageKey, List.of(arguments), column);
    }

    /** A problem about the whole set of lines or a whole line. */
    static ExpressionProblem general(String messageKey, Object... arguments) {
        return new ExpressionProblem(messageKey, List.of(arguments), -1);
    }
}
