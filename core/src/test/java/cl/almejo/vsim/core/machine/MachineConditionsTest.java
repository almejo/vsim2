package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.core.machine.MachineConditions.ParsedCondition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineConditionsTest {

    @Test
    void anEmptyConditionIsOtherwise() {
        ParsedCondition condition = MachineConditions.parse("   ");
        assertThat(condition.isOtherwise()).isTrue();
        assertThat(condition.expression()).isEmpty();
        assertThat(condition.problem()).isEmpty();
        assertThat(condition.names()).isEmpty();
    }

    @Test
    void aPrimeNegates() {
        ParsedCondition condition = MachineConditions.parse("x'");
        assertThat(condition.holds(Map.of("x", false))).isTrue();
        assertThat(condition.holds(Map.of("x", true))).isFalse();
        assertThat(condition.names()).containsExactly("x");
    }

    @Test
    void aDotAndASpaceAreAnd() {
        for (String text : List.of("a·b", "a b")) {
            ParsedCondition condition = MachineConditions.parse(text);
            assertThat(condition.names()).as(text).containsExactly("a", "b");
            assertThat(condition.holds(Map.of("a", true, "b", true))).as(text).isTrue();
            assertThat(condition.holds(Map.of("a", true, "b", false))).as(text).isFalse();
        }
    }

    @Test
    void lettersWrittenTogetherAreOneName() {
        assertThat(MachineConditions.parse("ab").names()).containsExactly("ab");
    }

    @Test
    void plusIsOrAndCaretIsXor() {
        ParsedCondition or = MachineConditions.parse("a + b");
        assertThat(or.holds(Map.of("a", false, "b", true))).isTrue();
        assertThat(or.holds(Map.of("a", false, "b", false))).isFalse();
        ParsedCondition xor = MachineConditions.parse("a ^ b");
        assertThat(xor.holds(Map.of("a", true, "b", true))).isFalse();
        assertThat(xor.holds(Map.of("a", true, "b", false))).isTrue();
    }

    @Test
    void aNameWithoutAValueCountsAsZero() {
        assertThat(MachineConditions.parse("x'").holds(Map.of())).isTrue();
        assertThat(MachineConditions.parse("x").holds(Map.of())).isFalse();
    }

    @Test
    void aBadConditionReportsItsColumnAndNeverHolds() {
        ParsedCondition condition = MachineConditions.parse("x + + y");
        assertThat(condition.isOtherwise()).isFalse();
        assertThat(condition.expression()).isEmpty();
        ExpressionProblem problem = condition.problem().orElseThrow();
        assertThat(problem.messageKey()).isEqualTo(ExpressionProblem.MISSING_OPERAND);
        assertThat(problem.column()).isEqualTo(4);
        assertThat(condition.holds(Map.of("x", true, "y", true))).isFalse();
        assertThat(condition.names()).isEmpty();
    }

    @Test
    void columnsCountFromTheTextAsTyped() {
        assertThat(MachineConditions.parse("  x $").problem().orElseThrow().column()).isEqualTo(4);
    }

    @Test
    void renamingANameInAConditionKeepsTheUsersSpacing() {
        assertThat(MachineConditions.renamed("x'  +  xy·x", "x", "a")).isEqualTo("a'  +  xy·a");
        assertThat(MachineConditions.renamed("(x)(y)", "y", "go")).isEqualTo("(x)(go)");
        assertThat(MachineConditions.renamed("", "x", "a")).isEmpty();
    }
}
