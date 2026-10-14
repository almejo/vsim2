package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Editar ▸ «Ordenar cables…»: the modal dialog around a {@link TidyWiresPanel}. Never shown by tests. */
public final class TidyWiresDialog {

    private TidyWiresDialog() {
    }

    /** Shows the dialog; empty when the user cancels. */
    public static Optional<TidyChoice> choose(Component parent, boolean hasSelection, TidyOptions initial,
                                              TidyPlanner planner) {
        TidyWiresPanel panel = new TidyWiresPanel(hasSelection, initial, planner);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), Messages.text("tidy.title"),
                JDialog.DEFAULT_MODALITY_TYPE);
        JButton apply = new JButton(Messages.text("tidy.apply"));
        JButton cancel = new JButton(Messages.text("tidy.cancel"));
        AtomicReference<TidyChoice> result = new AtomicReference<>();
        apply.addActionListener(event -> {
            result.set(panel.choice());
            dialog.dispose();
        });
        cancel.addActionListener(event -> dialog.dispose());
        panel.onChange(() -> apply.setEnabled(panel.canApply()));
        apply.setEnabled(panel.canApply());
        javax.swing.JPanel buttons = new javax.swing.JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(apply);
        buttons.add(cancel);
        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(apply);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        panel.dispose();
        return Optional.ofNullable(result.get());
    }
}
