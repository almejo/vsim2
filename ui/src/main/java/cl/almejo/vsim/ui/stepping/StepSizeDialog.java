package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.text.ParseException;
import java.util.OptionalLong;

/** The dialog of «Otro…» and «Tamaño del avance…» (M6.1-B): a number field from 1 to 1 000 000 000. Never in tests. */
final class StepSizeDialog implements StepSizeChooser {

    static final StepSizeDialog INSTANCE = new StepSizeDialog();

    private StepSizeDialog() {
    }

    @Override
    public OptionalLong choose(Component parent, long current) {
        JSpinner field = new JSpinner(new SpinnerNumberModel(Long.valueOf(current), Long.valueOf(1),
                Long.valueOf(AppPreferences.MAXIMUM_STEP_UNITS), Long.valueOf(1)));
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.add(new JLabel(Messages.text("stepSize.prompt")), BorderLayout.NORTH);
        content.add(field, BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(parent, content, Messages.text("stepSize.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return OptionalLong.empty();
        }
        try {
            field.commitEdit();
        } catch (ParseException exception) {
            // text out of range or not a number: the field keeps its last valid value
        }
        return OptionalLong.of(((Number) field.getValue()).longValue());
    }
}
