package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.core.truthtable.BudgetedTruthTable;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Objects;

/**
 * A truth table for the AI: the columns (name as the editor's table shows it, the element id in the evaluated circuit —
 * for one instance, inside its definition — and the width), the rows in binary counting order with each cell written
 * as {@link ValueText#cell}, whether each row settled, and why rows may be missing: {@code truncated} (the row limit)
 * or {@code budgetExceeded} (the event budget; the tools answer it as BUDGET_EXCEEDED). {@code hasFeedback}: the
 * circuit has a loop without flip-flops. {@code version}: the circuit version the copy was taken from.
 */
public record TruthTableView(DocumentId document, DocumentVersion version, List<ColumnView> inputs,
                             List<ColumnView> outputs, List<RowView> rows, int totalRows, boolean truncated,
                             boolean budgetExceeded, long eventsProcessed, int eventBudget, boolean hasFeedback) {

    public record ColumnView(String name, String elementId, int width) {
    }

    public record RowView(List<String> inputs, List<String> outputs, boolean settled) {
        public RowView {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
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
                             int eventBudget) {
        TruthTable table = evaluated.table();
        return new TruthTableView(document, version, columns(table.inputs()), columns(table.outputs()),
                table.rows().stream().map(TruthTableView::rowOf).toList(), evaluated.totalRows(),
                evaluated.truncated(), evaluated.budgetExceeded(), evaluated.eventsProcessed(), eventBudget,
                table.hasFeedback());
    }

    private static List<ColumnView> columns(List<Column> columns) {
        return columns.stream().map(column -> new ColumnView(column.name(), column.componentId(), column.width()))
                .toList();
    }

    private static RowView rowOf(Row row) {
        return new RowView(cells(row.inputs()), cells(row.outputs()), row.settled());
    }

    private static List<String> cells(List<Value> values) {
        return values.stream().map(ValueText::cell).toList();
    }
}
