package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.Expression.And;
import cl.almejo.vsim.core.logic.Expression.Constant;
import cl.almejo.vsim.core.logic.Expression.Not;
import cl.almejo.vsim.core.logic.Expression.Or;
import cl.almejo.vsim.core.logic.Expression.Variable;
import cl.almejo.vsim.core.logic.Expression.Xor;
import cl.almejo.vsim.core.logic.ExpressionParser.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionParserTest {

    private static final Variable A = new Variable("A");
    private static final Variable B = new Variable("B");
    private static final Variable C = new Variable("C");
    private static final Variable D = new Variable("D");

    // --- notations ---

    @Test
    void everyAndNotationGivesTheSameProduct() {
        Expression expected = and(A, B);
        for (String text : List.of("A*B", "A·B", "A&B", "A∧B", "A B", "A  *  B", "A ∧ B")) {
            assertThat(expression(text)).as(text).isEqualTo(expected);
        }
    }

    @Test
    void everyOrNotationGivesTheSameSum() {
        for (String text : List.of("A+B", "A|B", "A∨B", "A + B", " A ∨ B ")) {
            assertThat(expression(text)).as(text).isEqualTo(or(A, B));
        }
    }

    @Test
    void everyXorNotationGivesTheSameExclusiveOr() {
        for (String text : List.of("A^B", "A⊕B", "A ^ B")) {
            assertThat(expression(text)).as(text).isEqualTo(xor(A, B));
        }
    }

    @Test
    void everyNotNotationGivesTheSameComplement() {
        for (String text : List.of("A'", "¬A", "!A", "~A", "¬ A", "A '", "A’", "A′", "Aʹ")) {
            assertThat(expression(text)).as(text).isEqualTo(new Not(A));
        }
    }

    @Test
    void constantsAreZeroAndOne() {
        assertThat(expression("0")).isEqualTo(new Constant(false));
        assertThat(expression("1")).isEqualTo(new Constant(true));
        assertThat(expression("1'")).isEqualTo(new Not(new Constant(true)));
    }

    // --- names and implicit AND (Review Focus 1) ---

    @Test
    void lettersWrittenTogetherAreOneName() {
        assertThat(expression("AB")).isEqualTo(new Variable("AB"));
        assertThat(expression("A1B")).isEqualTo(new Variable("A1B"));
        assertThat(expression("Cin")).isEqualTo(new Variable("Cin"));
        assertThat(expression("carry_in")).isEqualTo(new Variable("carry_in"));
        assertThat(expression("SEL1")).isEqualTo(new Variable("SEL1"));
        assertThat(expression("ñandú")).isEqualTo(new Variable("ñandú"));
    }

    @Test
    void juxtapositionWhereANameCannotContinueIsAnd() {
        assertThat(expression("A'B")).isEqualTo(and(new Not(A), B));
        assertThat(expression("A' B'")).isEqualTo(and(new Not(A), new Not(B)));
        assertThat(expression("Cin A")).isEqualTo(and(new Variable("Cin"), A));
        assertThat(expression("A(B+C)")).isEqualTo(and(A, or(B, C)));
        assertThat(expression("(A)(B)")).isEqualTo(and(A, B));
        assertThat(expression("A 1")).isEqualTo(and(A, new Constant(true)));
        assertThat(expression("1A")).isEqualTo(and(new Constant(true), A));
        assertThat(expression("¬A B")).isEqualTo(and(new Not(A), B));
        assertThat(expression("A¬B")).isEqualTo(and(A, new Not(B)));
        assertThat(expression("A!B")).isEqualTo(and(A, new Not(B)));
        assertThat(expression("10")).isEqualTo(and(new Constant(true), new Constant(false)));
    }

    @Test
    void aDigitAfterANameBelongsToIt() {
        assertThat(expression("A1")).isEqualTo(new Variable("A1"));
        assertThat(expression("A1 B")).isEqualTo(and(new Variable("A1"), B));
    }

    // --- precedence and associativity (Review Focus 2) ---

    @Test
    void notBindsTighterThanAndTighterThanXorTighterThanOr() {
        assertThat(expression("A + B C ^ D")).isEqualTo(or(A, xor(and(B, C), D)));
        assertThat(expression("A ^ B + C")).isEqualTo(or(xor(A, B), C));
        assertThat(expression("A B' + C")).isEqualTo(or(and(A, new Not(B)), C));
    }

    @Test
    void chainsOfOneOperatorAreFlattened() {
        assertThat(expression("A ^ B ^ C")).isEqualTo(xor(A, B, C));
        assertThat(expression("A B C D")).isEqualTo(and(A, B, C, D));
        assertThat(expression("(A B) C")).isEqualTo(and(A, B, C));
        assertThat(expression("A + (B + C) + D")).isEqualTo(or(A, B, C, D));
        assertThat(expression("A ^ (B ^ C)")).isEqualTo(xor(A, B, C));
        assertThat(expression("((A))")).isEqualTo(A);
    }

    @Test
    void notsStackAndApplyToParentheses() {
        assertThat(expression("!A'")).isEqualTo(new Not(new Not(A)));
        assertThat(expression("A''")).isEqualTo(new Not(new Not(A)));
        assertThat(expression("(A+B)'C")).isEqualTo(and(new Not(or(A, B)), C));
        assertThat(expression("¬(A B)")).isEqualTo(new Not(and(A, B)));
        assertThat(expression("(A B)' C")).isEqualTo(and(new Not(and(A, B)), C));
    }

    // --- the line ---

    @Test
    void theOutputNameIsLeftOfTheEquals() {
        ParseResult result = ExpressionParser.parseLine("  Sum = A ^ B ^ Cin ");
        assertThat(result.problem()).isEmpty();
        assertThat(result.line()).contains(new ExpressionLine("Sum", xor(A, B, new Variable("Cin"))));
    }

    // --- errors (Review Focus 3) ---

    @Test
    void problemsCarryTheirKeyAndColumn() {
        assertProblem("", ExpressionProblem.EMPTY_LINE, -1);
        assertProblem("   ", ExpressionProblem.EMPTY_LINE, -1);
        assertProblem("A + B", ExpressionProblem.MISSING_EQUALS, -1);
        assertProblem(" = A", ExpressionProblem.BLANK_OUTPUT_NAME, 1);
        assertProblem("1F = A", ExpressionProblem.INVALID_OUTPUT_NAME, 0);
        assertProblem("  F G = A", ExpressionProblem.INVALID_OUTPUT_NAME, 2);
        assertProblem("F =   ", ExpressionProblem.EMPTY_EXPRESSION, 3);
        assertProblem("F = A + + B", ExpressionProblem.MISSING_OPERAND, 8);
        assertProblem("F = A +", ExpressionProblem.MISSING_OPERAND, 7);
        assertProblem("F = * A", ExpressionProblem.MISSING_OPERAND, 4);
        assertProblem("F = 'A", ExpressionProblem.MISSING_OPERAND, 4);
        assertProblem("F = ()", ExpressionProblem.MISSING_OPERAND, 5);
        assertProblem("F = ¬", ExpressionProblem.MISSING_OPERAND, 5);
        assertProblem("F = A (B + C", ExpressionProblem.UNCLOSED_PARENTHESIS, 6);
        assertProblem("F = ((A) + B", ExpressionProblem.UNCLOSED_PARENTHESIS, 4);
        assertProblem("F = A + B)", ExpressionProblem.UNEXPECTED_CLOSING_PARENTHESIS, 9);
        assertProblem("F = A ? B", ExpressionProblem.UNEXPECTED_CHARACTER, 6);
        assertProblem("F = A = B", ExpressionProblem.UNEXPECTED_CHARACTER, 6);
        assertProblem("F = A 2", ExpressionProblem.UNEXPECTED_CHARACTER, 6);
        assertProblem("F = _A", ExpressionProblem.UNEXPECTED_CHARACTER, 4);
    }

    @Test
    void anUnexpectedCharacterNamesItself() {
        ParseResult result = ExpressionParser.parseLine("F = A $ B");
        assertThat(result.problem().orElseThrow().arguments()).containsExactly("$");
        assertThat(result.line()).isEmpty();
    }

    @Test
    void aCharacterOutsideTheBasicPlaneIsNamedWhole() {
        ParseResult result = ExpressionParser.parseLine("F = A 😀 B");
        assertThat(result.problem().orElseThrow().arguments()).containsExactly("😀");
        assertThat(result.problem().orElseThrow().column()).isEqualTo(6);
    }

    @Test
    void anInvalidOutputNameNamesItself() {
        assertThat(ExpressionParser.parseLine("F' = A").problem().orElseThrow().arguments()).containsExactly("F'");
    }

    @Test
    void theLeftmostProblemIsReported() {
        assertProblem("F = A + + B ?", ExpressionProblem.MISSING_OPERAND, 8);
        assertProblem("F = ? + + B", ExpressionProblem.UNEXPECTED_CHARACTER, 4);
    }

    // --- merged letters ---

    @Test
    void namesOfTwoToFourCapitalsAreReportedAsMerged() {
        assertThat(ExpressionParser.parseLine("F = AB + C").mergedNames()).containsExactly("AB");
        assertThat(ExpressionParser.parseLine("F = ABC + ABCD + ABCDE + AB").mergedNames())
                .containsExactly("ABC", "ABCD", "AB");
        assertThat(ExpressionParser.parseLine("F = Cin + SEL1 + A + carry").mergedNames()).isEmpty();
        assertThat(ExpressionParser.parseLine("F = A +").mergedNames()).isEmpty();
    }

    // --- the tree ---

    @Test
    void evaluateFollowsTheOperators() {
        Expression expression = expression("A B' + (C ^ D)");
        assertThat(expression.evaluate(Map.of("A", true, "B", false, "C", false, "D", false))).isTrue();
        assertThat(expression.evaluate(Map.of("A", false, "B", false, "C", true, "D", true))).isFalse();
        assertThat(expression.evaluate(Map.of("A", false, "B", false, "C", true, "D", false))).isTrue();
        assertThat(expression("A ^ B ^ C").evaluate(Map.of("A", true, "B", true, "C", true))).isTrue();
        assertThat(expression.variables()).containsExactlyInAnyOrder("A", "B", "C", "D");
        assertThatThrownBy(() -> expression.evaluate(Map.of("A", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operationsNeedTwoOperands() {
        assertThatThrownBy(() -> new And(List.of(A))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Or(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Xor(List.of(A))).isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private static Expression expression(String text) {
        ParseResult result = ExpressionParser.parseLine("F = " + text);
        assertThat(result.problem()).as(text).isEmpty();
        return result.line().orElseThrow().expression();
    }

    private static void assertProblem(String text, String key, int column) {
        ParseResult result = ExpressionParser.parseLine(text);
        assertThat(result.line()).as(text).isEmpty();
        ExpressionProblem problem = result.problem().orElseThrow(() -> new AssertionError("no problem: " + text));
        assertThat(problem.messageKey()).as(text).isEqualTo(key);
        assertThat(problem.column()).as(text).isEqualTo(column);
    }

    private static Expression and(Expression... operands) {
        return new And(List.of(operands));
    }

    private static Expression or(Expression... operands) {
        return new Or(List.of(operands));
    }

    private static Expression xor(Expression... operands) {
        return new Xor(List.of(operands));
    }
}
