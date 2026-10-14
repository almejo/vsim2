package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.BorderLayout;

/** The small window of a tidy that takes long: «Ordenando cables…», a bar, the round and «Cancelar». */
public interface TidyProgress {

    /** Shows the number of the round being computed. */
    void round(int round);

    /** Closes the window. */
    void close();

    /** Opens a progress window; {@code onCancel} runs when the user presses «Cancelar» or closes it. */
    @FunctionalInterface
    interface Factory {
        TidyProgress open(Component parent, Runnable onCancel);

        /** A modal-less {@link JDialog}. */
        static Factory dialog() {
            return (parent, onCancel) -> {
                JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent),
                        Messages.text("tidy.progress.title"), JDialog.ModalityType.MODELESS);
                JLabel label = new JLabel(Messages.text("tidy.progress.working"));
                JProgressBar bar = new JProgressBar();
                bar.setIndeterminate(true);
                JButton cancel = new JButton(Messages.text("tidy.cancel"));
                cancel.addActionListener(event -> onCancel.run());
                javax.swing.JPanel content = new javax.swing.JPanel(
                        new net.miginfocom.swing.MigLayout("wrap 1, insets 12", "[grow, fill, 260::]"));
                content.add(label);
                content.add(bar);
                javax.swing.JPanel buttons = new javax.swing.JPanel(new FlowLayout(FlowLayout.RIGHT));
                buttons.add(cancel);
                dialog.add(content, BorderLayout.CENTER);
                dialog.add(buttons, BorderLayout.SOUTH);
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent event) {
                        onCancel.run();
                    }
                });
                dialog.pack();
                dialog.setLocationRelativeTo(parent);
                dialog.setVisible(true);
                return new TidyProgress() {
                    @Override
                    public void round(int round) {
                        label.setText(Messages.format("tidy.progress.round", round));
                    }

                    @Override
                    public void close() {
                        dialog.dispose();
                    }
                };
            };
        }
    }
}
