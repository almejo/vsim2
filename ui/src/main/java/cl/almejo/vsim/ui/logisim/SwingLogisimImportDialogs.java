package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.ui.app.FileProblemDialog;
import cl.almejo.vsim.ui.app.SwingFileProblemDialog;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** The real dialogs: the file problem as everywhere else, a scrollable resizable report, a small progress window. */
final class SwingLogisimImportDialogs implements LogisimImportDialogs {

    private static final Dimension REPORT_SIZE = new Dimension(760, 520);
    private static final Dimension MINIMUM_REPORT_SIZE = new Dimension(360, 240);

    private final FileProblemDialog problemDialog = new SwingFileProblemDialog();

    @Override
    public void problem(Component parent, String title, String message, String details) {
        problemDialog.show(parent, title, message, details);
    }

    @Override
    public void report(Component parent, String title, String summary, ImportReport report, ReportHost host) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(true);
        JButton close = new JButton(Messages.text(LogisimImportTexts.CLOSE));
        close.addActionListener(action -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        buttons.add(close);
        dialog.add(new ImportReportPanel(summary, report, host), BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setMinimumSize(MINIMUM_REPORT_SIZE);
        dialog.setSize(host.size().orElse(REPORT_SIZE));
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                host.rememberSize(dialog.getSize());
            }
        });
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    @Override
    public LogisimImportJob.Progress progress(Component parent) {
        return new LogisimImportJob.Progress() {
            private JDialog dialog;

            @Override
            public void show(Runnable cancel) {
                dialog = new JDialog(SwingUtilities.getWindowAncestor(parent),
                        Messages.text(LogisimImportTexts.PROGRESS_TITLE));
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                JProgressBar bar = new JProgressBar();
                bar.setIndeterminate(true);
                JButton cancelButton = new JButton(Messages.text(LogisimImportTexts.PROGRESS_CANCEL));
                cancelButton.addActionListener(action -> cancel.run());
                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                buttons.add(cancelButton);
                JPanel content = new JPanel(new BorderLayout(0, 10));
                content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
                content.add(new JLabel(Messages.text(LogisimImportTexts.PROGRESS_TEXT)), BorderLayout.NORTH);
                content.add(bar, BorderLayout.CENTER);
                content.add(buttons, BorderLayout.SOUTH);
                dialog.setContentPane(content);
                dialog.pack();
                dialog.setLocationRelativeTo(parent);
                dialog.setVisible(true); // not modal: the editor stays responsive and the import goes on
            }

            @Override
            public void hide() {
                if (dialog != null) {
                    dialog.dispose();
                    dialog = null;
                }
            }
        };
    }
}
