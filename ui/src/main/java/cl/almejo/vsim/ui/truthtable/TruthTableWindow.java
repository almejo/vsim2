package cl.almejo.vsim.ui.truthtable;

import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableText;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The non-modal «Tabla de verdad — …» window (spec §5.1): feedback banner, summary, progress with «Cancelar», the
 * table (monospaced, a thicker line before the outputs, unsettled rows tinted with a tooltip) and «Copiar»,
 * «Exportar CSV…», «Recalcular», «Cerrar». Never created in tests: the feature talks to {@link TruthTableDisplay}.
 */
final class TruthTableWindow extends JDialog implements TruthTableDisplay {

    private final EditorView view;
    private final Controls controls;
    private final JLabel banner = new JLabel(TruthTableMessages.feedbackBanner());
    private final JLabel summary = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JButton cancel = new JButton(Messages.text("truthTable.cancel"));
    private final JTable grid = new JTable();
    private final JButton copy = new JButton(Messages.text("truthTable.copy"));
    private final JButton export = new JButton(Messages.text("truthTable.exportCsv"));
    private TruthTable shown;
    private boolean closing;

    private TruthTableWindow(EditorView view, String title, Controls controls) {
        super(SwingUtilities.getWindowAncestor(view), title, ModalityType.MODELESS);
        this.view = view;
        this.controls = controls;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new MigLayout("insets dialog, fill, wrap 1", "[grow, fill]",
                "[][][][grow, fill][]"));
        banner.setForeground(new Color(0xB0, 0x60, 0x00));
        banner.setVisible(false);
        content.add(banner, "hidemode 3");
        content.add(summary);
        JPanel progress = new JPanel(new MigLayout("insets 0", "[grow, fill][]"));
        progress.add(status);
        progress.add(cancel);
        content.add(progress);
        grid.setFont(new Font(Font.MONOSPACED, Font.PLAIN, grid.getFont().getSize()));
        grid.setDefaultRenderer(String.class, new CellRenderer());
        grid.getTableHeader().setReorderingAllowed(false);
        grid.setFillsViewportHeight(true);
        content.add(new JScrollPane(grid), "width 240:420:, height 160:360:");
        JPanel buttons = new JPanel(new MigLayout("insets 0", "[][]push[][]"));
        JButton recalculate = new JButton(Messages.text("truthTable.recalculate"));
        JButton close = new JButton(Messages.text("truthTable.close"));
        buttons.add(copy);
        buttons.add(export);
        buttons.add(recalculate);
        buttons.add(close);
        content.add(buttons);
        setContentPane(content);
        copy.setEnabled(false);
        export.setEnabled(false);
        cancel.setVisible(false);
        cancel.addActionListener(event -> controls.cancel());
        copy.addActionListener(event -> copyTable());
        export.addActionListener(event -> exportTable());
        recalculate.addActionListener(event -> controls.recalculate());
        close.addActionListener(event -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (!closing) {
                    closing = true;
                    controls.closed();
                }
            }
        });
    }

    /** The {@link TruthTableDisplay.Factory} of the application. */
    static TruthTableDisplay open(EditorView view, String title, Controls controls) {
        TruthTableWindow window = new TruthTableWindow(view, title, controls);
        window.pack();
        window.setLocationRelativeTo(view);
        window.setVisible(true);
        return window;
    }

    @Override
    public void showProgress(int percent) {
        status.setText(Messages.format("truthTable.progress", percent));
        cancel.setVisible(percent < 100);
    }

    @Override
    public void showTable(TruthTable table) {
        shown = table;
        TruthTableModel model = new TruthTableModel(table);
        grid.setModel(model);
        banner.setVisible(table.hasFeedback());
        summary.setText(TruthTableMessages.summary(table));
        status.setText(" ");
        cancel.setVisible(false);
        copy.setEnabled(true);
        export.setEnabled(true);
        pack();
    }

    @Override
    public void showMessage(String message) {
        status.setText(message);
        cancel.setVisible(false);
    }

    @Override
    public void close() {
        closing = true;
        dispose();
    }

    private void copyTable() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(TruthTableText.tabSeparated(shown)), null);
    }

    private void exportTable() {
        Path directory = view.openDocument().path().map(Path::getParent)
                .orElseGet(() -> Path.of(System.getProperty("user.home")));
        TruthTableExport.choose(this, directory, view.openDocument().displayName()).ifPresent(target -> {
            try {
                TruthTableExport.write(target, shown);
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(this, Messages.format("truthTable.exportFailed", target.getFileName(),
                        exception.getMessage()), getTitle(), JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /** Centered monospaced cells; a thicker line before the first output; unsettled rows tinted, with a tooltip. */
    private static final class CellRenderer extends DefaultTableCellRenderer {

        CellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JComponent cell = (JComponent) super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                    row, column);
            TruthTableModel model = (TruthTableModel) table.getModel();
            boolean settled = model.isSettled(row);
            if (!isSelected) {
                cell.setBackground(settled ? table.getBackground() : mix(table.getBackground(), Color.ORANGE));
            }
            cell.setToolTipText(settled ? null : TruthTableMessages.unsettledTooltip());
            cell.setBorder(column == model.firstOutputColumn()
                    ? BorderFactory.createMatteBorder(0, 2, 0, 0, table.getGridColor())
                    : BorderFactory.createEmptyBorder(0, 2, 0, 0));
            return cell;
        }

        private static Color mix(Color base, Color tint) {
            return new Color((base.getRed() * 3 + tint.getRed()) / 4, (base.getGreen() * 3 + tint.getGreen()) / 4,
                    (base.getBlue() * 3 + tint.getBlue()) / 4);
        }
    }
}
