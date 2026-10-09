package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Map;
import java.util.Optional;

/** Modal "Configure…" dialog. Not unit-tested (needs a screen); the logic lives in {@link ParameterForm}. */
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
        String title = Messages.format("dialog.configure.title", Messages.componentName(type.id()));
        while (true) {
            int choice = JOptionPane.showConfirmDialog(owner, content, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            Optional<String> problem = form.validationError();
            if (problem.isEmpty()) {
                return form.hasChanges() ? Optional.of(form.rawValues()) : Optional.empty();
            }
            error.setText(problem.get());
        }
    }
}
