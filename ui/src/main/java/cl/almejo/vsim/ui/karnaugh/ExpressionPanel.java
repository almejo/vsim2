package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TermText;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Step 4 «Expresión» (spec §3): each output's minimized expression with its terms in their groups' colours, the
 * options (gates, form, sharing) and a live cost line. Once the student practised in step 3 (spec §2), «Usar mis
 * grupos» shows: on (by default) while every practised output's groups cover the table, the expressions, colours and
 * cost (and step 5's «Crear») use {@link KarnaughModel#effectiveMinimization()}; otherwise it is off with the reason.
 */
final class ExpressionPanel extends JPanel implements WizardStep {

    private final KarnaughModel model;
    private final KarnaughCost cost;
    private final JPanel expressions = new JPanel(new MigLayout("insets 0, gap 0 6, wrap 1"));
    private final List<ColoredExpression> lines = new ArrayList<>();
    private final Map<WizardOptions.GateChoice, JRadioButton> gateButtons =
            new EnumMap<>(WizardOptions.GateChoice.class);
    private final Map<Minimization.Form, JRadioButton> formButtons = new EnumMap<>(Minimization.Form.class);
    private final JCheckBox share = new JCheckBox(Messages.text("karnaugh.expression.share"));
    private final JCheckBox useMine = new JCheckBox(Messages.text("karnaugh.practice.useMine"));
    private final JLabel useMineReason = new JLabel() {
        @Override
        public Color getForeground() {
            return WizardColors.warning();
        }
    };
    private final JLabel costLabel = new JLabel();

    ExpressionPanel(KarnaughModel model, KarnaughCost cost) {
        super(new MigLayout("insets 12, fillx, wrap 2, hidemode 3", "[][grow, fill]", "[][]12[][][][][]12[]"));
        this.model = model;
        this.cost = cost;

        ButtonGroup gates = new ButtonGroup();
        JPanel gatesPanel = new JPanel(new MigLayout("insets 0"));
        addGate(gates, gatesPanel, WizardOptions.GateChoice.AND_OR_NOT, "karnaugh.expression.gates.andOrNot");
        addGate(gates, gatesPanel, WizardOptions.GateChoice.NAND_ONLY, "karnaugh.expression.gates.nandOnly");
        addGate(gates, gatesPanel, WizardOptions.GateChoice.NOR_ONLY, "karnaugh.expression.gates.norOnly");
        ButtonGroup forms = new ButtonGroup();
        JPanel formsPanel = new JPanel(new MigLayout("insets 0"));
        addForm(forms, formsPanel, Minimization.Form.SUM_OF_PRODUCTS, "karnaugh.expression.form.sop");
        addForm(forms, formsPanel, Minimization.Form.PRODUCT_OF_SUMS, "karnaugh.expression.form.pos");
        share.addActionListener(event -> model.setOptions(model.options().withShareTerms(share.isSelected())));
        useMine.addActionListener(event -> model.setUseStudentGroups(useMine.isSelected()));
        costLabel.setFont(costLabel.getFont().deriveFont(Font.BOLD));

        add(new Paragraph(Messages.text("karnaugh.expression.help")), "span 2, growx, wmin 100");
        add(expressions, "span 2");
        add(new JLabel(Messages.text("karnaugh.expression.gates")));
        add(gatesPanel);
        add(new JLabel(Messages.text("karnaugh.expression.form")));
        add(formsPanel);
        add(share, "skip 1");
        add(useMine, "skip 1");
        add(useMineReason, "skip 1, gapleft 24");
        add(new JLabel(Messages.text("karnaugh.expression.cost")));
        add(costLabel);
    }

    private void addGate(ButtonGroup group, JPanel panel, WizardOptions.GateChoice choice, String key) {
        JRadioButton button = new JRadioButton(Messages.text(key));
        button.addActionListener(event -> model.setOptions(model.options().withGates(choice)));
        group.add(button);
        panel.add(button);
        gateButtons.put(choice, button);
    }

    private void addForm(ButtonGroup group, JPanel panel, Minimization.Form form, String key) {
        JRadioButton button = new JRadioButton(Messages.text(key));
        button.addActionListener(event -> model.setOptions(model.options().withForm(form)));
        group.add(button);
        panel.add(button);
        formButtons.put(form, button);
    }

    @Override
    public void refresh() {
        if (model.problem().isPresent()) {
            return;
        }
        WizardOptions options = model.options();
        gateButtons.get(options.gates()).setSelected(true);
        formButtons.get(options.form()).setSelected(true);
        share.setSelected(options.shareTerms());
        refreshUseMine();
        Minimization minimization = model.effectiveMinimization();
        expressions.removeAll();
        lines.clear();
        for (int output = 0; output < minimization.outputs().size(); output++) {
            ColoredExpression line = new ColoredExpression(runs(minimization, output));
            lines.add(line);
            expressions.add(line);
        }
        expressions.revalidate();
        expressions.repaint();
        costLabel.setText(CostLine.text(cost.gatesByType(minimization, options)));
    }

    /** «Usar mis grupos»: only after some practice; off with the reason while the student's groups miss something. */
    private void refreshUseMine() {
        boolean practised = model.anyPractised();
        Optional<String> problem = model.studentGroupsProblem();
        useMine.setVisible(practised);
        useMine.setEnabled(problem.isEmpty());
        useMine.setSelected(model.usingStudentGroups());
        useMine.setToolTipText(problem.orElse(null));
        useMineReason.setText(problem.orElse(""));
        useMineReason.setVisible(practised && problem.isPresent());
    }

    /** «F = » plain, then each term in its group's colour; constants and the joining signs plain. */
    private List<ColoredExpression.Run> runs(Minimization minimization, int output) {
        TruthTableSpec spec = minimization.spec();
        List<Term> terms = minimization.outputs().get(output).terms();
        boolean sumOfProducts = minimization.form() == Minimization.Form.SUM_OF_PRODUCTS;
        List<ColoredExpression.Run> runs = new ArrayList<>();
        String outputName = spec.outputNames().get(output);
        if (terms.isEmpty() || terms.getFirst().mask() == 0) {
            runs.add(new ColoredExpression.Run(TermText.expression(minimization, output), -1));
            return runs;
        }
        runs.add(new ColoredExpression.Run(outputName + " = ", -1));
        for (int index = 0; index < terms.size(); index++) {
            Term term = terms.get(index);
            String text;
            if (sumOfProducts) {
                if (index > 0) {
                    runs.add(new ColoredExpression.Run(" + ", -1));
                }
                text = TermText.product(term, spec.inputNames());
            } else if (terms.size() == 1) {
                // A product of one sum goes without parentheses, as TermText.expression writes it.
                text = TermText.expression(outputName, minimization.form(), terms, spec.inputNames())
                        .substring(outputName.length() + 3);
            } else {
                if (index > 0) {
                    String separator = TermText.sumSeparator(terms.get(index - 1), term, spec.inputNames());
                    if (!separator.isEmpty()) {
                        runs.add(new ColoredExpression.Run(separator, -1));
                    }
                }
                text = TermText.sum(term, spec.inputNames());
            }
            runs.add(new ColoredExpression.Run(text, model.colorIndex(minimization, output, index)));
        }
        return runs;
    }

    List<String> expressionTexts() {
        return lines.stream().map(ColoredExpression::text).toList();
    }

    ColoredExpression line(int output) {
        return lines.get(output);
    }

    String costText() {
        return costLabel.getText();
    }

    AbstractButton shareCheckBox() {
        return share;
    }

    AbstractButton useMineCheckBox() {
        return useMine;
    }

    String useMineReasonText() {
        return useMineReason.isVisible() ? useMineReason.getText() : "";
    }

    AbstractButton gateButton(WizardOptions.GateChoice choice) {
        return gateButtons.get(choice);
    }

    AbstractButton formButton(Minimization.Form form) {
        return formButtons.get(form);
    }
}
