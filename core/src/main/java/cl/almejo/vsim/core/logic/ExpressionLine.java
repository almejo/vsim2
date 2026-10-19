package cl.almejo.vsim.core.logic;

import java.util.Objects;

/**
 * One line of «Circuito desde expresiones»: {@code outputName = expression}.
 *
 * @param outputName the output pin's name, stripped
 * @param expression what the output gives
 */
public record ExpressionLine(String outputName, Expression expression) {

    public ExpressionLine {
        Objects.requireNonNull(outputName, "outputName");
        Objects.requireNonNull(expression, "expression");
    }
}
