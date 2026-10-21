package cl.almejo.vsim.core.truthtable;

import java.util.List;
import java.util.Objects;

/**
 * What {@link TruthTableEvaluator#evaluateWithin} computed: {@code table} holds the first rows (all of them when
 * neither limit was reached), {@code totalRows} how many the full table has, {@code truncated} whether the row limit
 * left some out and {@code budgetExceeded} whether the event budget cut something: rows it never reached (left out of
 * {@code table}) or rows that ran out of events before settling, kept as unsettled and listed in {@code cutShortRows}
 * (their indexes in {@code table}, ascending) — what such a row would have given is not known.
 */
public record BudgetedTruthTable(TruthTable table, int totalRows, long eventsProcessed, boolean truncated,
                                 boolean budgetExceeded, List<Integer> cutShortRows) {

    public BudgetedTruthTable {
        Objects.requireNonNull(table, "table");
        cutShortRows = List.copyOf(cutShortRows);
    }
}
