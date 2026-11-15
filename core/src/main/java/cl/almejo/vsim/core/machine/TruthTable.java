package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.Expression;

import java.util.List;

/**
 * The truth table of one condition over a list of variables, as bits: row {@code r} gives the first variable bit
 * {@code n-1} of {@code r} and the last one bit 0, so the rows go 00…0, 00…1, … and the first row found is the one with
 * the fewest leading ones («x = 1, y = 0» before «x = 1, y = 1»). A name of the condition that is not among the
 * variables reads 0, as in the simulation. At most {@link MachineLimits#MAXIMUM_INPUTS} variables: 2^16 rows, 1024
 * words, so {@link MachineChecks} can compare every pair of arrows of a 64-state machine at once.
 */
final class TruthTable {

    private static final int WORD_BITS = Long.SIZE;
    private static final int WORD_BITS_LOG = 6;

    private final long[] words;

    private TruthTable(long[] words) {
        this.words = words;
    }

    static TruthTable of(Expression expression, List<String> variables) {
        if (variables.size() > MachineLimits.MAXIMUM_INPUTS) {
            throw new IllegalArgumentException("too many variables: " + variables.size());
        }
        int rows = 1 << variables.size();
        return new TruthTable(evaluate(expression, variables, rows));
    }

    /** The first row where this table and {@code other} both hold; -1 when they never do together. */
    int firstRowAlsoIn(TruthTable other) {
        for (int word = 0; word < words.length; word++) {
            long both = words[word] & other.words[word];
            if (both != 0) {
                return word * WORD_BITS + Long.numberOfTrailingZeros(both);
            }
        }
        return -1;
    }

    /** The first row of {@code variableCount} variables where none of {@code tables} holds; -1 when one always does. */
    static int firstRowInNone(List<TruthTable> tables, int variableCount) {
        int rows = 1 << variableCount;
        long[] none = constant(true, rows);
        for (TruthTable table : tables) {
            for (int word = 0; word < none.length; word++) {
                none[word] &= ~table.words[word];
            }
        }
        for (int word = 0; word < none.length; word++) {
            if (none[word] != 0) {
                return word * WORD_BITS + Long.numberOfTrailingZeros(none[word]);
            }
        }
        return -1;
    }

    private static long[] evaluate(Expression expression, List<String> variables, int rows) {
        return switch (expression) {
            case Expression.Variable variable -> column(variables.indexOf(variable.name()), variables.size(), rows);
            case Expression.Constant constant -> constant(constant.value(), rows);
            case Expression.Not not -> inverted(evaluate(not.operand(), variables, rows), rows);
            case Expression.And and -> combined(and.operands(), variables, rows, Operation.AND);
            case Expression.Or or -> combined(or.operands(), variables, rows, Operation.OR);
            case Expression.Xor xor -> combined(xor.operands(), variables, rows, Operation.XOR);
        };
    }

    private enum Operation { AND, OR, XOR }

    private static long[] combined(List<Expression> operands, List<String> variables, int rows,
                                   Operation operation) {
        long[] result = evaluate(operands.getFirst(), variables, rows);
        for (Expression operand : operands.subList(1, operands.size())) {
            long[] next = evaluate(operand, variables, rows);
            for (int word = 0; word < result.length; word++) {
                result[word] = switch (operation) {
                    case AND -> result[word] & next[word];
                    case OR -> result[word] | next[word];
                    case XOR -> result[word] ^ next[word];
                };
            }
        }
        return result;
    }

    /** The column of variable {@code index} (0 the highest bit); a name that is no variable ({@code -1}) reads 0. */
    private static long[] column(int index, int variableCount, int rows) {
        long[] words = new long[wordCount(rows)];
        if (index < 0) {
            return words;
        }
        int bit = variableCount - 1 - index;
        if (bit < WORD_BITS_LOG) {
            long pattern = 0;
            for (int position = 0; position < WORD_BITS; position++) {
                if ((position >> bit & 1) == 1) {
                    pattern |= 1L << position;
                }
            }
            for (int word = 0; word < words.length; word++) {
                words[word] = pattern & mask(rows);
            }
        } else {
            for (int word = 0; word < words.length; word++) {
                words[word] = (word >> (bit - WORD_BITS_LOG) & 1) == 1 ? -1L : 0L;
            }
        }
        return words;
    }

    private static long[] constant(boolean value, int rows) {
        long[] words = new long[wordCount(rows)];
        if (value) {
            for (int word = 0; word < words.length; word++) {
                words[word] = mask(rows);
            }
        }
        return words;
    }

    private static long[] inverted(long[] words, int rows) {
        for (int word = 0; word < words.length; word++) {
            words[word] = ~words[word] & mask(rows);
        }
        return words;
    }

    private static int wordCount(int rows) {
        return Math.max(1, rows / WORD_BITS);
    }

    /** The bits of a word that are rows: all of them but in a table shorter than one word. */
    private static long mask(int rows) {
        return rows >= WORD_BITS ? -1L : (1L << rows) - 1;
    }
}
