package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableText;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.OptionalInt;

/** A computed truth table as Swing table data: inputs first, then outputs, every cell as text. Read-only. */
public final class TruthTableModel extends AbstractTableModel {

    private final TruthTable table;
    private final List<String> names;
    private OptionalInt currentRow = OptionalInt.empty();

    public TruthTableModel(TruthTable table) {
        this.table = table;
        this.names = TruthTableText.header(table);
    }

    public TruthTable table() {
        return table;
    }

    @Override
    public int getRowCount() {
        return table.rows().size();
    }

    @Override
    public int getColumnCount() {
        return names.size();
    }

    @Override
    public String getColumnName(int column) {
        return names.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return String.class;
    }

    @Override
    public String getValueAt(int row, int column) {
        return TruthTableText.cells(table.rows().get(row)).get(column);
    }

    /** The first output column; the window draws the thicker line on its left. */
    public int firstOutputColumn() {
        return table.inputs().size();
    }

    public boolean isSettled(int row) {
        return table.rows().get(row).settled();
    }

    /** The row of the live input values (spec §5.4), drawn with the selection color; empty for none. */
    public OptionalInt currentRow() {
        return currentRow;
    }

    public boolean isCurrent(int row) {
        return currentRow.isPresent() && currentRow.getAsInt() == row;
    }

    /** Moves the highlight to {@code row}, repainting the row it leaves and the row it reaches. */
    public void setCurrentRow(OptionalInt row) {
        if (row.equals(currentRow)) {
            return;
        }
        OptionalInt previous = currentRow;
        currentRow = row;
        previous.ifPresent(index -> fireTableRowsUpdated(index, index));
        row.ifPresent(index -> fireTableRowsUpdated(index, index));
    }
}
