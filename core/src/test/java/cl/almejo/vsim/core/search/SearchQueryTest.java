package cl.almejo.vsim.core.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryTest {

    private static CompiledQuery plain(String text) {
        return new SearchQuery(text, false, false).compile();
    }

    @Test
    void plainTextIsASubstringThatIgnoresCaseAndAccents() {
        CompiledQuery query = plain("accion");

        assertThat(query.matches("Acción")).isTrue();
        assertThat(query.matches("REACCIONES")).isTrue();
        assertThat(query.matches("acto")).isFalse();
        assertThat(plain("ACCIÓN").matches("accion")).isTrue();
        assertThat(query.problem()).isEmpty();
    }

    @Test
    void matchCaseKeepsCaseAndAccents() {
        assertThat(new SearchQuery("Acc", true, false).compile().matches("Acción")).isTrue();
        assertThat(new SearchQuery("acc", true, false).compile().matches("Acción")).isFalse();
        assertThat(new SearchQuery("Accion", true, false).compile().matches("Acción")).isFalse();
    }

    @Test
    void aRegularExpressionIsFoundAnywhereAndIgnoresCaseOnlyWhenAsked() {
        assertThat(new SearchQuery("u[0-9]+", false, true).compile().matches("salida U12")).isTrue();
        assertThat(new SearchQuery("^u[0-9]+$", false, true).compile().matches("salida U12")).isFalse();
        assertThat(new SearchQuery("u[0-9]+", true, true).compile().matches("U12")).isFalse();
        assertThat(new SearchQuery("ñ", false, true).compile().matches("AÑO")).isTrue();
    }

    @Test
    void aRegularExpressionKeepsAccents() {
        assertThat(new SearchQuery("accion", false, true).compile().matches("Acción")).isFalse();
    }

    @Test
    void anEmptyQueryMatchesNothingAndIsNoProblem() {
        CompiledQuery query = plain("");

        assertThat(query.matches("")).isFalse();
        assertThat(query.matches("anything")).isFalse();
        assertThat(query.matchesNothing()).isTrue();
        assertThat(query.problem()).isEmpty();
        assertThat(new SearchQuery("", false, true).compile().problem()).isEmpty();
    }

    @Test
    void anInvalidRegularExpressionMatchesNothingAndSaysWhy() {
        CompiledQuery query = new SearchQuery("(ab", false, true).compile();

        assertThat(query.matches("(ab")).isFalse();
        assertThat(query.matchesNothing()).isTrue();
        assertThat(query.problem()).contains(new SearchProblem(SearchProblem.UNCLOSED_GROUP, List.of()));
    }

    @Test
    void theCommonMistakesHaveTheirOwnReason() {
        assertThat(problemOf("[ab")).isEqualTo(new SearchProblem(SearchProblem.UNCLOSED_CHARACTER_CLASS, List.of()));
        assertThat(problemOf("ab)"))
                .isEqualTo(new SearchProblem(SearchProblem.UNMATCHED_CLOSING_PARENTHESIS, List.of()));
        assertThat(problemOf("a**")).isEqualTo(new SearchProblem(SearchProblem.NOTHING_TO_REPEAT, List.of("*")));
        assertThat(problemOf("+")).isEqualTo(new SearchProblem(SearchProblem.NOTHING_TO_REPEAT, List.of("+")));
        assertThat(problemOf("a{2")).isEqualTo(new SearchProblem(SearchProblem.BAD_REPETITION, List.of()));
        assertThat(problemOf("x{3,1}")).isEqualTo(new SearchProblem(SearchProblem.BAD_REPETITION, List.of()));
        assertThat(problemOf("\\")).isEqualTo(new SearchProblem(SearchProblem.BAD_ESCAPE, List.of()));
        assertThat(problemOf("[z-a]")).isEqualTo(new SearchProblem(SearchProblem.BAD_RANGE, List.of()));
    }

    @Test
    void anyOtherMistakeKeepsTheDescriptionOfJava() {
        SearchProblem problem = problemOf("(?<n");

        assertThat(problem.messageKey()).isEqualTo(SearchProblem.INVALID_REGEX);
        assertThat(problem.arguments()).containsExactly("named capturing group is missing trailing '>'");
    }

    @Test
    void plainTextWithRegexCharactersIsLiteral() {
        CompiledQuery query = plain("(a+");

        assertThat(query.problem()).isEmpty();
        assertThat(query.matches("x(A+)")).isTrue();
        assertThat(query.matches("aaa")).isFalse();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void aCatastrophicRegularExpressionGivesUpWithinItsStepBudget() {
        // Java 9+ memoizes the classic (a+)+$; a back-reference turns that off and makes it exponential again
        // (about 4 billion character reads on 30 letters without the budget).
        CompiledQuery query = new SearchQuery("^(a+)+\\1$", false, true).compile();
        String label = "a".repeat(40) + "!";

        assertThat(query.matches(label)).isFalse();
        assertThat(query.problem()).contains(new SearchProblem(SearchProblem.TOO_EXPENSIVE, List.of()));
        // Once it gave up it stops trying, so a long document cannot add up to a freeze.
        assertThat(query.matches("aaa")).isFalse();
        assertThat(query.matchesNothing()).isTrue();
    }

    @Test
    void theStepBudgetIsSharedByEveryTextOfOneQuery() {
        // Each of these texts alone costs far less than the whole budget; together they must still make the
        // query give up, so a document full of medium-cost labels cannot add up to a freeze.
        CompiledQuery probe = new SearchQuery("^(a+)+\\1$", true, true).compile();
        String label = "a".repeat(14) + "!";
        probe.matches(label);
        long stepsForOneText = CompiledQuery.STEP_BUDGET - probe.remainingSteps();
        assertThat(probe.problem()).isEmpty();
        assertThat(stepsForOneText).isPositive().isLessThan(CompiledQuery.STEP_BUDGET / 20);

        CompiledQuery query = new SearchQuery("^(a+)+\\1$", true, true).compile();
        long textsTried = 0;
        while (!query.matchesNothing() && textsTried < 10_000) {
            query.matches(label);
            textsTried++;
        }

        assertThat(query.problem()).contains(new SearchProblem(SearchProblem.TOO_EXPENSIVE, List.of()));
        assertThat(textsTried).isLessThanOrEqualTo(CompiledQuery.STEP_BUDGET / stepsForOneText + 1);
    }

    @Test
    void eachCompiledQueryStartsWithAWholeBudget() {
        CompiledQuery spent = new SearchQuery("^(a+)+\\1$", true, true).compile();
        spent.matches("a".repeat(40) + "!");
        assertThat(spent.matchesNothing()).isTrue();

        CompiledQuery fresh = new SearchQuery("^(a+)+\\1$", true, true).compile();

        assertThat(fresh.remainingSteps()).isEqualTo(CompiledQuery.STEP_BUDGET);
        assertThat(fresh.matches("aa")).isTrue();
    }

    @Test
    void theClassicNestedRepetitionIsNoProblemForJava() {
        CompiledQuery query = new SearchQuery("(a+)+$", false, true).compile();

        assertThat(query.matches("a".repeat(300) + "!")).isFalse();
        assertThat(query.problem()).isEmpty();
        assertThat(query.matches("aaa")).isTrue();
    }

    @Test
    void aReasonableRegularExpressionOnALongTextStaysWithinTheBudget() {
        CompiledQuery query = new SearchQuery("fin$", false, true).compile();

        assertThat(query.matches("x".repeat(CompiledQuery.MAXIMUM_TEXT_LENGTH - 3) + "fin")).isTrue();
        assertThat(query.problem()).isEmpty();
    }

    @Test
    void onlyTheFirstCharactersOfAVeryLongTextAreSearched() {
        CompiledQuery query = plain("fin");

        assertThat(query.matches("x".repeat(CompiledQuery.MAXIMUM_TEXT_LENGTH) + "fin")).isFalse();
        assertThat(query.matches("fin" + "x".repeat(CompiledQuery.MAXIMUM_TEXT_LENGTH))).isTrue();
    }

    @Test
    void everyProblemKeyIsListed() {
        assertThat(SearchProblem.MESSAGE_KEYS).contains(SearchProblem.INVALID_REGEX, SearchProblem.UNCLOSED_GROUP,
                SearchProblem.UNCLOSED_CHARACTER_CLASS, SearchProblem.UNMATCHED_CLOSING_PARENTHESIS,
                SearchProblem.NOTHING_TO_REPEAT, SearchProblem.BAD_REPETITION, SearchProblem.BAD_ESCAPE,
                SearchProblem.BAD_RANGE, SearchProblem.TOO_EXPENSIVE);
    }

    private static SearchProblem problemOf(String regex) {
        return new SearchQuery(regex, false, true).compile().problem().orElseThrow();
    }
}
