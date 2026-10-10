package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.List;

/** The recovery dialog shown at start (spec §4.1) over a {@link RecoveryChoices}; closing it means «Decidir después». */
public final class RecoveryPrompt {

    private static final int RECOVER_OPTION = 0;
    private static final int DISCARD_OPTION = 1;

    private RecoveryPrompt() {
    }

    public static <C> RecoveryChoices.Decision ask(Component parent, RecoveryChoices<C> choices) {
        JPanel panel = new JPanel(new MigLayout("insets 0, wrap 1", "[grow, fill]"));
        panel.add(new JLabel(Messages.text("recovery.message")), "gapbottom 8");
        List<RecoveryChoices.Row<C>> rows = choices.rows();
        for (int index = 0; index < rows.size(); index++) {
            RecoveryChoices.Row<C> row = rows.get(index);
            int rowIndex = index;
            JCheckBox box = new JCheckBox(row.text(), choices.isSelected(index));
            box.addActionListener(event -> choices.setSelected(rowIndex, box.isSelected()));
            panel.add(box);
            row.note().ifPresent(note -> panel.add(new JLabel(note), "gapleft 28"));
        }
        Object[] options = {Messages.text("recovery.recover"), Messages.text("recovery.discard"),
                Messages.text("recovery.later")};
        int answer = JOptionPane.showOptionDialog(parent, panel, Messages.text("recovery.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[RECOVER_OPTION]);
        return switch (answer) {
            case RECOVER_OPTION -> RecoveryChoices.Decision.RECOVER;
            case DISCARD_OPTION -> RecoveryChoices.Decision.DISCARD;
            default -> RecoveryChoices.Decision.LATER;
        };
    }
}
