package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.ui.panels.TextChanges;

import javax.swing.DefaultCellEditor;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * One side's pins in the create/update dialog: a one-column table whose rows are the pin names, edited in place
 * (double click, F2 or typing). The name reaches the form while typing, so the row turns red with its reason and OK
 * follows at once; Escape gives the pin back the name it had before the edit. A table instead of the former list
 * because it brings in-place editing (F2, Enter, Escape, commit on focus loss) with the standard keys.
 */
final class PinNameTable extends JTable {

    private static final Color ERROR_FALLBACK = new Color(0xDB5860);

    private final Supplier<List<String>> names;
    private final BiConsumer<Integer, String> rename;
    private final IntFunction<Optional<String>> problem;
    private final NameModel model = new NameModel();
    private final JTextField editorField = new JTextField();
    private int editedRow = -1;
    private String nameBeforeEditing;
    private boolean loadingEditor;

    PinNameTable(Supplier<List<String>> names, BiConsumer<Integer, String> rename,
                 IntFunction<Optional<String>> problem) {
        this.names = names;
        this.rename = rename;
        this.problem = problem;
        setModel(model);
        setTableHeader(null);
        setShowGrid(false);
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        DefaultCellEditor editor = new DefaultCellEditor(editorField) {
            @Override
            public java.awt.Component getTableCellEditorComponent(JTable table, Object value, boolean selected,
                                                                  int row, int column) {
                loadingEditor = true;
                try {
                    return super.getTableCellEditorComponent(table, value, selected, row, column);
                } finally {
                    loadingEditor = false;
                    editedRow = row;
                    nameBeforeEditing = value == null ? "" : value.toString();
                }
            }
        };
        editor.addCellEditorListener(new CellEditorListener() {
            @Override
            public void editingStopped(ChangeEvent event) {
                editedRow = -1;
            }

            @Override
            public void editingCanceled(ChangeEvent event) {
                int row = editedRow;
                editedRow = -1;
                if (row >= 0) {
                    rename.accept(row, nameBeforeEditing);
                }
            }
        });
        TextChanges.onChange(editorField, this::typed);
        setDefaultEditor(Object.class, editor);
        setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                    boolean focused, int row, int column) {
                setForeground(null); // DefaultTableCellRenderer would otherwise keep the red of an earlier row
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                Optional<String> reason = problem.apply(row);
                if (reason.isPresent()) {
                    Color error = UIManager.getColor("Actions.Red");
                    setForeground(error == null ? ERROR_FALLBACK : error);
                }
                setToolTipText(reason.orElse(null));
                return this;
            }
        });
    }

    /** The form changed: while a name is being edited only the rows' colors change, otherwise the rows too. */
    void refresh() {
        if (isEditing()) {
            repaint();
            return;
        }
        int selected = getSelectedRow();
        model.fireTableDataChanged();
        if (selected >= 0 && selected < getRowCount()) {
            setRowSelectionInterval(selected, selected);
        }
    }

    /** Ends an edit in progress, keeping the typed name. */
    void stopEditing() {
        if (isEditing()) {
            getCellEditor().stopCellEditing();
        }
    }

    private void typed() {
        if (!loadingEditor && editedRow >= 0) {
            rename.accept(editedRow, editorField.getText());
        }
    }

    private final class NameModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return names.get().size();
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public Object getValueAt(int row, int column) {
            return names.get().get(row);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            rename.accept(row, value == null ? "" : value.toString());
        }
    }
}
