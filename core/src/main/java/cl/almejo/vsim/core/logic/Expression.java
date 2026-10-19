package cl.almejo.vsim.core.logic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A boolean expression as the user wrote it («Circuito desde expresiones», spec §3): variables, the constants 0 and 1,
 * NOT and the n-ary AND, OR and XOR. {@link ExpressionParser} builds the operations flattened (an And never has an And
 * directly among its operands, and so on), so equal sub-expressions are equal records.
 */
public sealed interface Expression permits Expression.Variable, Expression.Constant, Expression.Not, Expression.And,
        Expression.Or, Expression.Xor {

    /**
     * The value of the expression when each variable has the value in {@code values}.
     *
     * @throws IllegalArgumentException when a variable has no value
     */
    boolean evaluate(Map<String, Boolean> values);

    /** The names of the variables it uses, each once. */
    default Set<String> variables() {
        Set<String> names = new LinkedHashSet<>();
        collectVariables(this, names);
        return names;
    }

    private static void collectVariables(Expression expression, Set<String> names) {
        switch (expression) {
            case Variable variable -> names.add(variable.name());
            case Constant ignored -> {
            }
            case Not not -> collectVariables(not.operand(), names);
            case And and -> and.operands().forEach(operand -> collectVariables(operand, names));
            case Or or -> or.operands().forEach(operand -> collectVariables(operand, names));
            case Xor xor -> xor.operands().forEach(operand -> collectVariables(operand, names));
        }
    }

    /** An input of the table, by name (case matters). */
    record Variable(String name) implements Expression {

        public Variable {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public boolean evaluate(Map<String, Boolean> values) {
            Boolean value = values.get(name);
            if (value == null) {
                throw new IllegalArgumentException("no value for " + name);
            }
            return value;
        }
    }

    /** {@code 0} or {@code 1}. */
    record Constant(boolean value) implements Expression {

        @Override
        public boolean evaluate(Map<String, Boolean> values) {
            return value;
        }
    }

    record Not(Expression operand) implements Expression {

        public Not {
            Objects.requireNonNull(operand, "operand");
        }

        @Override
        public boolean evaluate(Map<String, Boolean> values) {
            return !operand.evaluate(values);
        }
    }

    /** The AND of two or more operands. */
    record And(List<Expression> operands) implements Expression {

        public And {
            operands = checkedOperands(operands);
        }

        @Override
        public boolean evaluate(Map<String, Boolean> values) {
            boolean result = true;
            for (Expression operand : operands) {
                result &= operand.evaluate(values);
            }
            return result;
        }
    }

    /** The OR of two or more operands. */
    record Or(List<Expression> operands) implements Expression {

        public Or {
            operands = checkedOperands(operands);
        }

        @Override
        public boolean evaluate(Map<String, Boolean> values) {
            boolean result = false;
            for (Expression operand : operands) {
                result |= operand.evaluate(values);
            }
            return result;
        }
    }

    /** The XOR (odd parity) of two or more operands. */
    record Xor(List<Expression> operands) implements Expression {

        public Xor {
            operands = checkedOperands(operands);
        }

        @Override
        public boolean evaluate(Map<String, Boolean> values) {
            boolean result = false;
            for (Expression operand : operands) {
                result ^= operand.evaluate(values);
            }
            return result;
        }
    }

    private static List<Expression> checkedOperands(List<Expression> operands) {
        List<Expression> copied = List.copyOf(operands);
        if (copied.size() < 2) {
            throw new IllegalArgumentException("an operation needs two or more operands: " + copied.size());
        }
        return copied;
    }
}
