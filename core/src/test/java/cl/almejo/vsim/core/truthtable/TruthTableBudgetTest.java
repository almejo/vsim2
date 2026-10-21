package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.document.ElementId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link TruthTableEvaluator#evaluateWithin}: a table for the AI, with a row limit and an event budget (IA.1). */
class TruthTableBudgetTest {

    private static Preparation.Ready ready(TruthTableCircuits circuits) {
        return (Preparation.Ready) TruthTableScope.prepare(circuits.document, Set.<ElementId>of(), circuits.registry);
    }

    @Test
    void withinTheBudgetEveryRowIsTheSameAsTheUsersTable() {
        Preparation.Ready ready = ready(TruthTableCircuits.fullAdder());

        BudgetedTruthTable result = TruthTableEvaluator.evaluateWithin(ready, 64, 100_000);

        assertThat(result.budgetExceeded()).isFalse();
        assertThat(result.truncated()).isFalse();
        assertThat(result.totalRows()).isEqualTo(8);
        assertThat(result.eventsProcessed()).isPositive();
        assertThat(result.table()).isEqualTo(TruthTableEvaluator.evaluate(ready, percent -> { }, () -> false)
                .orElseThrow());
    }

    @Test
    void theRowLimitKeepsTheFirstRows() {
        Preparation.Ready ready = ready(TruthTableCircuits.fullAdder());

        BudgetedTruthTable result = TruthTableEvaluator.evaluateWithin(ready, 3, 100_000);

        assertThat(result.truncated()).isTrue();
        assertThat(result.table().rows()).hasSize(3);
        assertThat(result.totalRows()).isEqualTo(8);
    }

    @Test
    void anOscillatingLoopStopsAtTheBudgetWithItsRowCutShort() {
        Preparation.Ready ready = ready(TruthTableCircuits.gatedRing());

        BudgetedTruthTable result = TruthTableEvaluator.evaluateWithin(ready, 64, 5_000);

        assertThat(result.budgetExceeded()).isTrue();
        assertThat(result.eventsProcessed()).isLessThanOrEqualTo(5_000);
        assertThat(result.cutShortRows()).containsExactly(1);
        assertThat(result.table().rows().get(1).settled()).isFalse();
        assertThat(result.table().rows().getFirst().settled()).isTrue();
    }

    @Test
    void rowsTheBudgetNeverReachesAreLeftOut() {
        Preparation.Ready ready = ready(TruthTableCircuits.fullAdder());

        BudgetedTruthTable result = TruthTableEvaluator.evaluateWithin(ready, 64, 10, 10);

        assertThat(result.budgetExceeded()).isTrue();
        assertThat(result.table().rows().size()).isLessThan(result.totalRows());
        assertThat(result.eventsProcessed()).isLessThanOrEqualTo(10);
    }

    @Test
    void aLatchRowThatHoldsItsStateIsCutShortAndTheRowsAfterItAreStillEvaluated() {
        Preparation.Ready ready = ready(TruthTableCircuits.norLatch());
        TruthTable users = TruthTableEvaluator.evaluate(ready, percent -> { }, () -> false).orElseThrow();

        BudgetedTruthTable result = TruthTableEvaluator.evaluateWithin(ready, 64, 100_000);

        assertThat(result.table().rows()).hasSize(4);
        assertThat(result.cutShortRows()).containsExactly(0);
        assertThat(result.table().rows().getFirst().settled()).isFalse();
        assertThat(result.table().rows().subList(1, 4)).isEqualTo(users.rows().subList(1, 4));
        assertThat(result.budgetExceeded()).isTrue();
        assertThat(result.eventsProcessed()).isLessThanOrEqualTo(100_000);
    }

    @Test
    void aRowThatNeedsMoreThanItsShareGetsWhatIsLeftAfterTheOthers() {
        Preparation.Ready ready = ready(TruthTableCircuits.fullAdder());
        long allTheTable = TruthTableEvaluator.evaluateWithin(ready, 64, 100_000).eventsProcessed();

        BudgetedTruthTable result = TruthTableEvaluator.evaluateWithin(ready, 64, 3 * allTheTable, 1);

        assertThat(result.cutShortRows()).isEmpty();
        assertThat(result.budgetExceeded()).isFalse();
        assertThat(result.table()).isEqualTo(TruthTableEvaluator.evaluate(ready, percent -> { }, () -> false)
                .orElseThrow());
    }

    @Test
    void theLimitsMustBePositive() {
        Preparation.Ready ready = ready(TruthTableCircuits.halfAdder());

        assertThatThrownBy(() -> TruthTableEvaluator.evaluateWithin(ready, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TruthTableEvaluator.evaluateWithin(ready, 4, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
