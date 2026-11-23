package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.Expression;
import cl.almejo.vsim.core.logic.ExpressionParser;
import cl.almejo.vsim.core.logic.ExpressionProblem;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the condition of a machine's arrow in the notation of «Circuito desde expresiones» ({@code x}, {@code x'},
 * {@code a·b}, {@code a b}, {@code a + b}, {@code ^}, {@code 0}/{@code 1}; letters written together are one name).
 * An empty condition means «otherwise».
 */
public final class MachineConditions {

    private MachineConditions() {
    }

    /**
     * A read condition: the expression (empty for «otherwise» and for a bad text), the first problem of a bad text
     * (its column counts from the text as typed) and the names it uses, in order of appearance (empty when bad).
     */
    public record ParsedCondition(Optional<Expression> expression, Optional<ExpressionProblem> problem,
                                  Set<String> names) {

        public ParsedCondition {
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(problem, "problem");
            names = Collections.unmodifiableSet(new LinkedHashSet<>(names));
        }

        /** An arrow without a condition: taken when no conditional arrow of its state holds. */
        public boolean isOtherwise() {
            return expression.isEmpty() && problem.isEmpty();
        }

        /**
         * Whether the condition holds for {@code inputs}; a name without a value counts as 0. «Otherwise» and a bad
         * condition never hold (the evaluator treats «otherwise» apart).
         */
        public boolean holds(Map<String, Boolean> inputs) {
            if (expression.isEmpty()) {
                return false;
            }
            Map<String, Boolean> values = new HashMap<>();
            for (String name : names) {
                values.put(name, inputs.getOrDefault(name, false));
            }
            return expression.get().evaluate(values);
        }
    }

    /**
     * {@code condition} with every whole name {@code from} written {@code to}, the rest as typed ({@code x' + xy·x},
     * {@code x} → {@code a}: {@code a' + xy·a}). A condition that does not parse is renamed too, from its tokens, so
     * the user's half-typed text follows the pin it was about.
     */
    public static String renamed(String condition, String from, String to) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        StringBuilder result = new StringBuilder(condition);
        List<ExpressionParser.NameSpan> spans = ExpressionParser.nameSpans(condition);
        for (int index = spans.size() - 1; index >= 0; index--) {
            ExpressionParser.NameSpan span = spans.get(index);
            if (span.name().equals(from)) {
                result.replace(span.start(), span.end(), to);
            }
        }
        return result.toString();
    }

    /** Reads {@code text}; blank is «otherwise». */
    public static ParsedCondition parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            return new ParsedCondition(Optional.empty(), Optional.empty(), Set.of());
        }
        ExpressionParser.ExpressionResult result = ExpressionParser.parseExpression(text);
        if (result.problem().isPresent()) {
            return new ParsedCondition(Optional.empty(), result.problem(), Set.of());
        }
        Expression expression = result.expression().orElseThrow();
        return new ParsedCondition(Optional.of(expression), Optional.empty(), expression.variables());
    }
}
