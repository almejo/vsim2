package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.FocusEvents;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;

/** One line: its text field and ✕, its problem, the merged-letters warning with its ✕, and its preview. */
final class ExpressionLineRow extends JPanel {

    private static final float LINE_FONT_FACTOR = 1.2f;

    final JTextField field = new JTextField(30) {
        /** Monospaced and larger than the theme's text font, derived again when the theme or scale changes. */
        @Override
        public void updateUI() {
            super.updateUI();
            Font base = UIManager.getFont("TextField.font");
            if (base != null) {
                // A UI resource, so the next theme replaces it and this derives it again.
                setFont(new FontUIResource(Font.MONOSPACED, Font.PLAIN,
                        Math.round(base.getSize2D() * LINE_FONT_FACTOR)));
            }
        }
    };
    final JButton remove = new JButton("✕");
    final JLabel problem = WizardColors.errorLabel(" ");
    final JPanel warningRow = new JPanel(new MigLayout("insets 0", "[]4[]"));
    final JLabel warning = WizardColors.label(" ", WizardColors::warning);
    final JButton dismiss = new JButton("✕");
    final JLabel preview = new JLabel(" ");
    String mergedName;
    private final ExpressionsPanel owner;

    ExpressionLineRow(ExpressionsPanel owner) {
        super(new MigLayout("insets 0, fillx, wrap 2, gapy 2", "[grow, fill][]", ""));
        this.owner = owner;
        // Its columns are only a preferred width: the field fills the line's width and shrinks with it.
        remove.setToolTipText(Messages.text("expressions.removeLine"));
        dismiss.setToolTipText(Messages.text("expressions.merged.dismiss"));
        dismiss.putClientProperty("JButton.buttonType", "borderless");
        warningRow.add(warning);
        warningRow.add(dismiss);
        add(field, "wmin 0");
        add(remove);
        add(problem, "span 2, hidemode 3");
        add(warningRow, "span 2, hidemode 3");
        add(preview, "span 2, hidemode 3");
        owner.onEdit(field, this::lineEdited);
        field.addFocusListener(FocusEvents.onGained(this::focused));
        remove.addActionListener(event -> removeLine());
        field.addActionListener(event -> goToNextLine());
        dismiss.addActionListener(event -> dismissMerged());
    }

    private void focused() {
        int index = owner.rows.indexOf(this);
        if (index >= 0) {
            owner.lastFocused = index;
        }
    }

    private void lineEdited() {
        int index = owner.rows.indexOf(this);
        if (index >= 0) {
            owner.model.setLine(index, field.getText());
        }
    }

    private void removeLine() {
        int index = owner.rows.indexOf(this);
        if (index >= 0 && owner.model.canRemoveLine()) {
            // This row goes, so the others keep their fields; the legend follows the line it typed into.
            owner.rows.remove(index);
            owner.linesPanel.remove(this);
            if (index < owner.lastFocused) {
                owner.lastFocused--;
            }
            owner.model.removeLine(index);
            owner.linesPanel.revalidate();
            owner.linesPanel.repaint();
        }
    }

    private void goToNextLine() {
        int index = owner.rows.indexOf(this);
        if (index >= 0) {
            owner.nextLine(index);
        }
    }

    private void dismissMerged() {
        if (mergedName != null) {
            owner.model.dismissMerged(mergedName);
        }
    }
}
