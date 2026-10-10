package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;

/**
 * The «Atajos de teclado» tab of Archivo ▸ Preferencias… (spec §5.2): search field, the grouped table, the conflict
 * question with «Reasignar» / «Cancelar», and «Cambiar…» (or a double click), «Quitar atajo», «Restaurar»,
 * «Restaurar todos». Every change is stored at once and every window applies it.
 */
public final class ShortcutsPanel extends JPanel {

    private final ShortcutsTableModel model;
    private final JTable table;
    private final KeyCapture capture;
    private final JTextField searchField = new JTextField(20);
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton reassignButton = new JButton(Messages.text("shortcuts.reassign"));
    private final JButton cancelButton = new JButton(Messages.text("shortcuts.cancel"));
    private final JButton changeButton = new JButton(Messages.text("shortcuts.change"));
    private final JButton clearButton = new JButton(Messages.text("shortcuts.clear"));
    private final JButton resetButton = new JButton(Messages.text("shortcuts.reset"));
    private final JButton resetAllButton = new JButton(Messages.text("shortcuts.resetAll"));

    public ShortcutsPanel(KeyBindings bindings) {
        super(new MigLayout("insets dialog, fill, wrap 1", "[grow, fill]", "[][grow, fill][][]"));
        model = new ShortcutsTableModel(bindings);
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new MenuRowRenderer());
        capture = new KeyCapture(this::captured, this::captureCancelled);
        table.addKeyListener(capture);
        table.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (!event.isTemporary()) {
                    abandonCapture();
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(event -> {
            abandonCapture();
            refreshButtons();
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    startCapture();
                }
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filterChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filterChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filterChanged();
            }
        });
        changeButton.addActionListener(event -> startCapture());
        clearButton.addActionListener(event -> selectedEntry().ifPresent(entry -> stopCaptureAnd(
                () -> model.clear(entry.id()))));
        resetButton.addActionListener(event -> selectedEntry().ifPresent(entry -> stopCaptureAnd(
                () -> model.reset(entry.id()))));
        resetAllButton.addActionListener(event -> stopCaptureAnd(model::resetAll));
        reassignButton.addActionListener(event -> {
            model.confirmReassign();
            showMessage();
        });
        cancelButton.addActionListener(event -> {
            model.cancelReassign();
            showMessage();
        });

        add(new JLabel(Messages.text("shortcuts.search")), "split 2, growx 0");
        add(searchField, "growx");
        add(new JScrollPane(table), "grow, width 460lp, height 260lp");
        add(messageLabel, "split 3, growx");
        add(reassignButton, "growx 0");
        add(cancelButton, "growx 0");
        add(changeButton, "split 4, growx 0");
        add(clearButton, "growx 0");
        add(resetButton, "growx 0");
        add(resetAllButton, "growx 0");
        showMessage();
    }

    ShortcutsTableModel model() { return model; }
    JTable table() { return table; }
    KeyCapture capture() { return capture; }
    JTextField searchField() { return searchField; }
    JLabel messageLabel() { return messageLabel; }
    JButton changeButton() { return changeButton; }
    JButton clearButton() { return clearButton; }
    JButton resetButton() { return resetButton; }
    JButton resetAllButton() { return resetAllButton; }
    JButton reassignButton() { return reassignButton; }
    JButton cancelButton() { return cancelButton; }

    private Optional<ShortcutEntry> selectedEntry() {
        return model.entryAt(table.getSelectedRow());
    }

    private void startCapture() {
        if (model.pendingConflict().isPresent()) {
            return;
        }
        selectedEntry().ifPresent(entry -> {
            model.beginCapture(entry.id());
            showMessage();
            capture.start();
            table.requestFocusInWindow();
        });
    }

    private void captured(KeyStroke stroke) {
        model.offer(stroke);
        showMessage();
    }

    private void captureCancelled() {
        model.cancelCapture();
        showMessage();
    }

    /** Focus loss, a new selection, a search and the Quitar / Restaurar buttons abandon a capture in progress. */
    private void abandonCapture() {
        capture.stop();
        if (model.isCapturing()) {
            model.cancelCapture();
        }
    }

    private void filterChanged() {
        capture.stop();
        model.setFilter(searchField.getText());
        refreshButtons();
    }

    /** A row button: the key capture stops, the model changes, and the message under the table follows. */
    private void stopCaptureAnd(Runnable change) {
        capture.stop();
        change.run();
        showMessage();
    }

    private void showMessage() {
        messageLabel.setText(model.message().isEmpty() ? " " : model.message());
        refreshButtons();
    }

    /** While a conflict waits for an answer, only «Reasignar» and «Cancelar» act. */
    private void refreshButtons() {
        boolean question = model.pendingConflict().isPresent();
        boolean entrySelected = selectedEntry().isPresent();
        changeButton.setEnabled(entrySelected && !question);
        clearButton.setEnabled(entrySelected && !question);
        resetButton.setEnabled(entrySelected && !question);
        resetAllButton.setEnabled(!question);
        reassignButton.setVisible(question);
        cancelButton.setVisible(question);
    }

    /** Menu rows in bold, like a heading. */
    private final class MenuRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
            setFont(model.isGroupRow(row) ? owner.getFont().deriveFont(Font.BOLD) : owner.getFont());
            return this;
        }
    }
}
