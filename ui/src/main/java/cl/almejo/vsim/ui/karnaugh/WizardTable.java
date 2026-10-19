package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The truth table of the wizard: row number, inputs, outputs. Step 1 edits it (a click on an output cycles
 * 0 → 1 → X; keys 0, 1, X set the selected output cell and space cycles it); step 2 shows it read-only. Both light the
 * row under the mouse and the row the map points at, through {@link HoverState}.
 */
final class WizardTable extends JTable {

    private static final int ROW_COLUMN_WIDTH = 44;
    private static final int VALUE_COLUMN_WIDTH = 48;

    private final KarnaughModel model;
    private final HoverState hover;
    private final TableModel tableModel;
    private int shownInputs;
    private int shownOutputs;

    WizardTable(KarnaughModel model, HoverState hover, boolean editable) {
        this.model = model;
        this.hover = hover;
        this.tableModel = new TableModel();
        this.shownInputs = model.inputCount();
        this.shownOutputs = model.outputCount();
        setModel(tableModel);
        setAutoResizeMode(AUTO_RESIZE_OFF);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setCellSelectionEnabled(editable);
        setRowSelectionAllowed(editable);
        setFocusable(editable);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(false);
        setDefaultRenderer(Object.class, new Renderer());
        sizeColumns();
        model.addListener(this::modelChanged);
        hover.addListener(this::repaint);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                hover.setRow(rowAtPoint(event.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover.setRow(-1);
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (editable && event.getButton() == MouseEvent.BUTTON1) {
                    int row = rowAtPoint(event.getPoint());
                    int output = outputOf(columnAtPoint(event.getPoint()));
                    if (row >= 0 && output >= 0) {
                        model.cycle(output, row);
                    }
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        if (editable) {
            bindKey("karnaugh.zero", (output, row) -> model.setValue(output, row, LogicValue.ZERO),
                    KeyEvent.VK_0, KeyEvent.VK_NUMPAD0);
            bindKey("karnaugh.one", (output, row) -> model.setValue(output, row, LogicValue.ONE),
                    KeyEvent.VK_1, KeyEvent.VK_NUMPAD1);
            bindKey("karnaugh.dontCare", (output, row) -> model.setValue(output, row, LogicValue.DONT_CARE),
                    KeyEvent.VK_X);
            bindKey("karnaugh.cycle", model::cycle, KeyEvent.VK_SPACE);
        }
    }

    /** Which output a view column shows, or -1 for the row number and the inputs. */
    int outputOf(int column) {
        int output = column - 1 - model.inputCount();
        return column >= 0 && output >= 0 && output < model.outputCount() ? output : -1;
    }

    private interface CellEdit {
        void apply(int output, int row);
    }

    private void bindKey(String name, CellEdit edit, int... keyCodes) {
        for (int keyCode : keyCodes) {
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        }
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int row = getSelectedRow();
                int output = outputOf(getSelectedColumn());
                if (row >= 0 && output >= 0) {
                    edit.apply(output, row);
                }
            }
        });
    }

    /** A new shape rebuilds the columns; values and names keep the selection (for keyboard editing). */
    private void modelChanged() {
        if (model.inputCount() != shownInputs || model.outputCount() != shownOutputs) {
            shownInputs = model.inputCount();
            shownOutputs = model.outputCount();
            tableModel.fireTableStructureChanged();
            sizeColumns();
            return;
        }
        for (int column = 0; column < getColumnModel().getColumnCount(); column++) {
            getColumnModel().getColumn(column).setHeaderValue(tableModel.getColumnName(column));
        }
        getTableHeader().repaint();
        if (tableModel.getRowCount() > 0) {
            tableModel.fireTableRowsUpdated(0, tableModel.getRowCount() - 1);
        }
    }

    private void sizeColumns() {
        for (int column = 0; column < getColumnModel().getColumnCount(); column++) {
            int width = column == 0 ? ROW_COLUMN_WIDTH : VALUE_COLUMN_WIDTH;
            getColumnModel().getColumn(column).setPreferredWidth(width);
            getColumnModel().getColumn(column).setMinWidth(width);
        }
    }

    private final class TableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return model.rowCount();
        }

        @Override
        public int getColumnCount() {
            return 1 + model.inputCount() + model.outputCount();
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) {
                return Messages.text("karnaugh.table.row");
            }
            if (column <= model.inputCount()) {
                return model.inputNames().get(column - 1);
            }
            return model.outputNames().get(column - 1 - model.inputCount());
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (column == 0) {
                return String.valueOf(row);
            }
            int inputCount = model.inputCount();
            if (column <= inputCount) {
                return (row >> (inputCount - column) & 1) == 1 ? "1" : "0";
            }
            return KarnaughMapView.valueText(model.value(column - 1 - inputCount, row));
        }
    }

    /** Centred values; outputs in bold, the row number muted, the hovered row lit. Colours read at paint time. */
    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            boolean output = outputOf(column) >= 0;
            setFont(table.getFont().deriveFont(output ? Font.BOLD : Font.PLAIN));
            if (!selected) {
                setForeground(column == 0 ? WizardColors.muted() : WizardColors.foreground());
                setBackground(row == hover.row()
                        ? WizardColors.blend(WizardColors.highlight(), table.getBackground(), 0.3)
                        : table.getBackground());
            }
            return this;
        }
    }
}
