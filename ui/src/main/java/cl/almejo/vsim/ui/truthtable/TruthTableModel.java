package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableText;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** A computed truth table as Swing table data: inputs first, then outputs, every cell as text. Read-only. */
public final class TruthTableModel extends AbstractTableModel {

    private final TruthTable table;
    private final List<String> names;

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
}
