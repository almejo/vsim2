package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.Optional;

/**
 * Modal "Configure…" dialog. An OK with invalid values keeps it open and shows the error under the form.
 * The dialog needs a screen; its decision logic is {@link #acceptChoice}, which is unit-tested.
 */
public final class ConfigureDialog {

    private ConfigureDialog() {
    }

    public static Optional<Map<String, Object>> show(java.awt.Component owner, ComponentType type,
                                                     Component component) {
        ParameterForm form = new ParameterForm(type, component.parameters());
        JLabel error = new JLabel(" ");
        Color errorColor = UIManager.getColor("Component.error.focusedBorderColor");
        error.setForeground(errorColor != null ? errorColor : Color.RED);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(form, BorderLayout.CENTER);
        content.add(error, BorderLayout.SOUTH);

        JOptionPane pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, Messages.format("dialog.configure.title",
                Messages.componentName(type)), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(pane);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                pane.setValue(JOptionPane.CLOSED_OPTION);
            }
        });
        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, event -> {
            Object choice = pane.getValue();
            if (choice == JOptionPane.UNINITIALIZED_VALUE) {
                return;
            }
            if (acceptChoice(choice, form, error)) {
                dialog.setVisible(false);
            } else {
                pane.setValue(JOptionPane.UNINITIALIZED_VALUE);
                dialog.pack();
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.dispose();
        return isOk(pane.getValue()) && form.hasChanges() ? Optional.of(form.rawValues()) : Optional.empty();
    }

    /**
     * What a button press or close does: true closes the dialog. An OK with invalid values shows the error in
     * {@code error} and returns false; a valid OK clears it.
     */
    static boolean acceptChoice(Object choice, ParameterForm form, JLabel error) {
        if (!isOk(choice)) {
            return true;
        }
        Optional<String> problem = form.validationError();
        error.setText(problem.orElse(" "));
        return problem.isEmpty();
    }

    private static boolean isOk(Object choice) {
        return choice instanceof Integer option && option == JOptionPane.OK_OPTION;
    }
}
