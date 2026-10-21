package cl.almejo.vsim.core.truthtable;

import java.util.Objects;

/**
 * What {@link TruthTableEvaluator#evaluateWithin} computed: {@code table} holds the first rows (all of them when
 * neither limit was reached), {@code totalRows} how many the full table has, {@code truncated} whether the row limit
 * left some out and {@code budgetExceeded} whether the event budget ran out first (the row it was on is left out).
 */
public record BudgetedTruthTable(TruthTable table, int totalRows, long eventsProcessed, boolean truncated,
                                 boolean budgetExceeded) {

    public BudgetedTruthTable {
        Objects.requireNonNull(table, "table");
    }
}
