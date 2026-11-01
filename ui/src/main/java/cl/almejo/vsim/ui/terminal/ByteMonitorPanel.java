package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The «Monitor de bytes» tab (spec §9, mockup §5), like Arduino's serial monitor: every byte received with its time,
 * as a «Lista» (t · hex · dec · carácter) or a «Volcado hex» like {@code hexdump -C}; the last
 * {@value ByteLog#CAPACITY} bytes, emptied on «Reiniciar». EDT only.
 */
final class ByteMonitorPanel extends JPanel {

    private static final String LIST_CARD = "list";
    private static final String DUMP_CARD = "dump";
    private static final int COLUMNS = 4;

    /** A row of the list: a byte, or a marker for the {@code lost} bytes the log dropped before the next row. */
    private record Row(ByteLog.Entry entry, long lost) {
    }

    private final Deque<Row> entries = new ArrayDeque<>();
    private final BytesModel model = new BytesModel();
    private final JTable table = new JTable(model);
    private final JTextArea dump = new JTextArea();
    private final JLabel count = new JLabel();
    private final CardLayout cards = new CardLayout();
    private final JPanel views = new JPanel(cards);
    private final JToggleButton listButton = new JToggleButton(Messages.text("terminal.monitor.list"), true);
    private final JToggleButton dumpButton = new JToggleButton(Messages.text("terminal.monitor.hexDump"));
    private List<Row> rows = List.of();
    private int byteCount;
    private boolean dumpShown;

    ByteMonitorPanel() {
        super(new BorderLayout());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        ButtonGroup group = new ButtonGroup();
        group.add(listButton);
        group.add(dumpButton);
        listButton.addActionListener(action -> showList());
        dumpButton.addActionListener(action -> showDump());
        controls.add(listButton);
        controls.add(dumpButton);
        controls.add(count);
        add(controls, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        dump.setEditable(false);
        dump.setFont(new Font(Font.MONOSPACED, Font.PLAIN, dump.getFont().getSize()));
        views.add(new JScrollPane(table), LIST_CARD);
        views.add(new JScrollPane(dump), DUMP_CARD);
        add(views, BorderLayout.CENTER);
        refreshCount();
    }

    /**
     * New bytes, after {@code lostBytes} the log dropped before they could be read (a marker row says so); a start over
     * (a «Reiniciar») empties the monitor first.
     */
    void accept(List<ByteLog.Entry> newBytes, boolean startedOver, long lostBytes) {
        if (startedOver) {
            entries.clear();
            byteCount = 0;
        }
        if (newBytes.isEmpty() && !startedOver) {
            return;
        }
        if (lostBytes > 0 && !startedOver) {
            entries.add(new Row(null, lostBytes));
        }
        newBytes.forEach(entry -> entries.add(new Row(entry, 0)));
        byteCount += newBytes.size();
        while (byteCount > ByteLog.CAPACITY) {
            if (entries.removeFirst().entry() != null) {
                byteCount--;
            }
        }
        rows = new ArrayList<>(entries);
        model.fireTableDataChanged();
        if (!rows.isEmpty()) {
            table.scrollRectToVisible(table.getCellRect(rows.size() - 1, 0, true));
        }
        if (dumpShown) {
            refreshDump();
        }
        refreshCount();
    }

    int rowCount() {
        return model.getRowCount();
    }

    /** The four columns of row {@code index}. */
    List<String> row(int index) {
        return rowTexts(rows.get(index));
    }

    String countText() {
        return count.getText();
    }

    String hexDumpText() {
        return ByteMonitorText.hexDump(rows.stream().map(Row::entry).filter(Objects::nonNull).toList());
    }

    private static List<String> rowTexts(Row row) {
        return row.entry() == null ? ByteMonitorText.lostRow(row.lost()) : ByteMonitorText.row(row.entry());
    }

    private void showList() {
        dumpShown = false;
        cards.show(views, LIST_CARD);
    }

    private void showDump() {
        dumpShown = true;
        refreshDump();
        cards.show(views, DUMP_CARD);
    }

    private void refreshDump() {
        dump.setText(hexDumpText());
        dump.setCaretPosition(dump.getDocument().getLength());
    }

    private void refreshCount() {
        count.setText(Messages.format("terminal.monitor.count", byteCount));
    }

    /** The «Lista» rows: one per byte, formatted when shown. */
    private final class BytesModel extends AbstractTableModel {

        private final String[] headers = {Messages.text("terminal.monitor.time"), Messages.text("terminal.monitor.hex"),
                Messages.text("terminal.monitor.decimal"), Messages.text("terminal.monitor.character")};

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS;
        }

        @Override
        public String getColumnName(int column) {
            return headers[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rowTexts(rows.get(rowIndex)).get(columnIndex);
        }
    }
}
