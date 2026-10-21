package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.core.truthtable.BudgetedTruthTable;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A truth table for the AI: the columns (name as the editor's table shows it, the element id in the evaluated circuit —
 * for one instance, inside its definition — and the width), the rows in binary counting order with each cell written
 * as {@link ValueText#cell}, whether each row settled, and why rows may be missing or unknown: {@code truncated} (the
 * row limit), {@code budgetExceeded} (the event budget cut something), {@code rowsLeftOut} (rows it never reached;
 * the tools answer it as BUDGET_EXCEEDED) and each row's {@code cutShort} (it ran out of its share of the events
 * before settling — a latch's hold row on the fresh copy, an oscillation — so what it gives is not known).
 * {@code hasFeedback}: the circuit has a loop without flip-flops. {@code version}: the circuit version the copy was
 * taken from.
 */
public record TruthTableView(DocumentId document, DocumentVersion version, List<ColumnView> inputs,
                             List<ColumnView> outputs, List<RowView> rows, int totalRows, boolean truncated,
                             boolean budgetExceeded, boolean rowsLeftOut, long eventsProcessed, int eventBudget,
                             boolean hasFeedback) {

    public record ColumnView(String name, String elementId, int width) {
    }

    public record RowView(List<String> inputs, List<String> outputs, boolean settled, boolean cutShort) {
        public RowView {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }

        /** A row that ran to its end (settled or not). */
        public RowView(List<String> inputs, List<String> outputs, boolean settled) {
            this(inputs, outputs, settled, false);
        }
    }

    public TruthTableView {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        rows = List.copyOf(rows);
    }

    static TruthTableView of(DocumentId document, DocumentVersion version, BudgetedTruthTable evaluated,
                             int maximumRows, int eventBudget) {
        TruthTable table = evaluated.table();
        List<RowView> rows = new ArrayList<>();
        for (int index = 0; index < table.rows().size(); index++) {
            Row row = table.rows().get(index);
            rows.add(new RowView(cells(row.inputs()), cells(row.outputs()), row.settled(),
                    evaluated.cutShortRows().contains(index)));
        }
        boolean rowsLeftOut = rows.size() < Math.min(evaluated.totalRows(), maximumRows);
        return new TruthTableView(document, version, columns(table.inputs()), columns(table.outputs()), rows,
                evaluated.totalRows(), evaluated.truncated(), evaluated.budgetExceeded(), rowsLeftOut,
                evaluated.eventsProcessed(), eventBudget, table.hasFeedback());
    }

    private static List<ColumnView> columns(List<Column> columns) {
        return columns.stream().map(column -> new ColumnView(column.name(), column.componentId(), column.width()))
                .toList();
    }

    private static List<String> cells(List<Value> values) {
        return values.stream().map(ValueText::cell).toList();
    }
}
