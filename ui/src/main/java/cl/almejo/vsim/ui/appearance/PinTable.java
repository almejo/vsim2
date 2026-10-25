package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The «Pines» table of the shape editor (mockup screens 2, 3, 9 and 12): Nombre / Sentido / Bits, no grid, one row
 * per pin. A pin with a problem gets the problem's icon in its name cell and, when it blocks «Aceptar», the reason in
 * place of its direction («⛔ A · mismo punto que B»); a pin that cannot move shows a padlock. Never colour alone.
 */
@SuppressWarnings("serial")
final class PinTable extends JTable {
    private static final int LOCK_ICON_SIZE = 12;
    private static final int NAME_COLUMN = 0;
    private static final int DIRECTION_COLUMN = 1;
    private static final int WIDTH_COLUMN = 2;
    /** The margin of the rest of the side panel: the last column ends this far from its right edge. */
    static final int RIGHT_MARGIN = 10;

    /** One pin as the table shows it; {@code problem} is empty when the pin has none. */
    record Row(String name, String direction, int width, ToneLabel.Tone tone, String problem, boolean locked) {
        Row {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(tone, "tone");
            Objects.requireNonNull(problem, "problem");
        }

        boolean blocking() {
            return tone == ToneLabel.Tone.BLOCKING;
        }
    }

    private final Model model = new Model();

    PinTable() {
        setModel(model);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowSelectionAllowed(true);
        setFocusable(true);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(false);
        setDefaultRenderer(Object.class, new Renderer());
        getTableHeader().setDefaultRenderer(new HeaderRenderer());
        // Name : direction : bits as in the mockups; the direction column also holds «mismo punto que B».
        getColumnModel().getColumn(NAME_COLUMN).setPreferredWidth(80);
        getColumnModel().getColumn(DIRECTION_COLUMN).setPreferredWidth(140);
        getColumnModel().getColumn(WIDTH_COLUMN).setPreferredWidth(RIGHT_MARGIN + 40);
        getColumnModel().getColumn(WIDTH_COLUMN).setMaxWidth(RIGHT_MARGIN + 48);
    }

    /** Shows {@code rows}, keeping the selected row when the pins did not change. */
    void show(List<Row> rows) {
        if (rows.equals(model.rows)) {
            return;
        }
        int selected = getSelectedRow();
        model.rows = List.copyOf(rows);
        model.fireTableDataChanged();
        if (selected >= 0 && selected < rows.size()) {
            setRowSelectionInterval(selected, selected);
        }
    }

    Row row(int index) {
        return model.rows.get(index);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(super.getPreferredScrollableViewportSize().width, getPreferredSize().height);
    }

    private static final class Model extends AbstractTableModel {
        private List<Row> rows = List.of();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return Messages.text(switch (column) {
                case NAME_COLUMN -> "shape.pins.name";
                case DIRECTION_COLUMN -> "shape.pins.direction";
                default -> "shape.pins.width";
            });
        }

        @Override
        public Object getValueAt(int rowIndex, int column) {
            Row row = rows.get(rowIndex);
            return switch (column) {
                case NAME_COLUMN -> row.name();
                case DIRECTION_COLUMN -> row.blocking() ? row.problem() : row.direction();
                default -> row.blocking() ? "" : Integer.toString(row.width());
            };
        }
    }

    /** The column titles as in the mockups: small grey text at the left over a thin line. */
    private static final class HeaderRenderer implements TableCellRenderer {
        private final ToneLabel title = new ToneLabel(ToneLabel.Tone.INFO);

        HeaderRenderer() {
            title.putClientProperty("FlatLaf.style", "font: -1");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                 boolean focused, int rowIndex, int column) {
            title.setText(String.valueOf(value));
            boolean last = column == WIDTH_COLUMN;
            title.setHorizontalAlignment(last ? SwingConstants.RIGHT : SwingConstants.LEADING);
            title.setBorder(new EdgeBorder(Set.of(EdgeBorder.Side.BOTTOM),
                    new Insets(2, 10, 3, last ? RIGHT_MARGIN : 6)));
            return title;
        }
    }

    /** Icons and colours per row, read from the theme at each paint. */
    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                 boolean focused, int rowIndex, int column) {
            JLabel cell = (JLabel) super.getTableCellRendererComponent(table, value, selected, false, rowIndex, column);
            Row row = model.rows.get(rowIndex);
            cell.setIcon(column == NAME_COLUMN ? icon(row) : null);
            // The warning mark follows the name as in mockup 3 («F ⚠»); the padlock and ⛔ come first.
            cell.setHorizontalTextPosition(row.tone() == ToneLabel.Tone.WARNING ? SwingConstants.LEADING
                    : SwingConstants.TRAILING);
            cell.setToolTipText(row.problem().isEmpty() ? null : row.problem());
            boolean last = column == WIDTH_COLUMN;
            cell.setHorizontalAlignment(last ? SwingConstants.RIGHT : SwingConstants.LEADING);
            cell.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, last ? RIGHT_MARGIN : 6));
            if (!selected) {
                cell.setForeground(row.blocking() ? ReadableColors.errorText()
                        : row.locked() ? ReadableColors.mutedText() : table.getForeground());
            }
            return cell;
        }

        private Icon icon(Row row) {
            if (row.tone() != ToneLabel.Tone.PLAIN) {
                return ProblemIcon.of(row.tone()).orElse(null);
            }
            return row.locked() ? Icons.get("lock-closed", LOCK_ICON_SIZE) : null;
        }
    }
}
