package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyProgress;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.BorderLayout;

/** The small window of a tidy that takes long: «Ordenando circuito…», a bar, the stage with its count and «Cancelar». */
public interface TidyProgress {

    /** Shows the stage being computed and how far it is ({@code total} 0 when unknown). */
    void stage(CircuitTidyProgress.Stage stage, int done, int total);

    /** The text of a stage: «Separando lo que se tapa… ronda 3 de 10», «Moviendo partes… 2 de 8», «Ordenando cables… pasada 2». */
    static String describe(CircuitTidyProgress.Stage stage, int done, int total) {
        return switch (stage) {
            case SEPARATING -> total > 0 ? Messages.format("tidy.progress.separating", done, total)
                    : Messages.format("tidy.progress.separating.round", done);
            case MOVING -> total > 0 ? Messages.format("tidy.progress.moving", done, total)
                    : Messages.text("tidy.progress.working");
            case WIRES -> Messages.format("tidy.progress.round", done);
        };
    }

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
                    public void stage(CircuitTidyProgress.Stage stage, int done, int total) {
                        label.setText(describe(stage, done, total));
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
