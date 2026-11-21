package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.DialogButtons;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Editar ▸ «Ordenar circuito…»: the modal dialog around a {@link TidyWiresPanel}. Never shown by tests. */
public final class TidyWiresDialog {

    private TidyWiresDialog() {
    }

    /** Shows the dialog; empty when the user cancels. */
    public static Optional<TidyChoice> choose(Component parent, boolean hasSelection, CircuitTidyOptions initial,
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
        DialogButtons.assemble(dialog, panel, apply, cancel);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        panel.dispose();
        return Optional.ofNullable(result.get());
    }
}
