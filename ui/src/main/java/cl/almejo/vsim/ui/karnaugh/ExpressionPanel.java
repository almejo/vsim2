package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TermText;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Step 4 «Expresión» (spec §3): each output's minimized expression with its terms in their groups' colours, the
 * options (gates, form, sharing) and a live cost line. In the practice wizard (spec §2) each output shows the
 * student's expression until step 3 revealed VSim's («Revisar» or «Me rindo»), see
 * {@link KarnaughModel#shownMinimization()}; and once the student drew groups «Usar mis grupos» shows: on (by default)
 * while every practised output's groups cover the table, the expressions, colours and cost (and step 5's «Crear») use
 * {@link KarnaughModel#effectiveMinimization()}; otherwise it is off with the reason. The cost line is always what
 * «Crear» builds; in practice it waits (with a line saying so) while that would count VSim's terms for an output not
 * revealed, see {@link KarnaughModel#showsWhatCrearBuilds()}.
 */
final class ExpressionPanel extends JPanel implements WizardStep {

    private final KarnaughModel model;
    private final KarnaughCost cost;
    private final JPanel expressions = new JPanel(new MigLayout("insets 0, gap 0 6, wrap 1"));
    private final List<ColoredExpression> lines = new ArrayList<>();
    private final OptionControls optionControls;
    private final JCheckBox useMine = new JCheckBox(Messages.text("karnaugh.practice.useMine"));
    private final JLabel useMineReason = WizardColors.label("", WizardColors::warning);
    private final JLabel costLabel = new JLabel();

    ExpressionPanel(KarnaughModel model, KarnaughCost cost) {
        super(new MigLayout("insets 12, fillx, wrap 2, hidemode 3", "[][grow, fill]", "[][]12[][][][][]12[]"));
        this.model = model;
        this.cost = cost;

        optionControls = new OptionControls(model::options, model::setOptions);
        useMine.addActionListener(event -> model.setUseStudentGroups(useMine.isSelected()));
        costLabel.setFont(costLabel.getFont().deriveFont(Font.BOLD));

        add(new Paragraph(Messages.text("karnaugh.expression.help")), "span 2, growx, wmin 100");
        add(expressions, "span 2");
        add(new JLabel(Messages.text("karnaugh.expression.gates")));
        add(optionControls.gatesPanel());
        add(new JLabel(Messages.text("karnaugh.expression.form")));
        add(optionControls.formsPanel());
        add(optionControls.shareBox(), "skip 1");
        add(useMine, "skip 1");
        add(useMineReason, "skip 1, gapleft 24");
        add(new JLabel(Messages.text("karnaugh.expression.cost")));
        add(costLabel);
    }

    @Override
    public void refresh() {
        if (model.problem().isPresent()) {
            return;
        }
        WizardOptions options = model.options();
        optionControls.sync(options);
        refreshUseMine();
        Minimization minimization = model.shownMinimization();
        expressions.removeAll();
        lines.clear();
        for (int output = 0; output < minimization.outputs().size(); output++) {
            ColoredExpression line = new ColoredExpression(runs(minimization, output));
            lines.add(line);
            expressions.add(line);
        }
        expressions.revalidate();
        expressions.repaint();
        boolean costShown = model.showsWhatCrearBuilds();
        costLabel.setText(costShown ? CostLine.text(cost.gatesByType(model.effectiveMinimization(), options))
                : Messages.text("karnaugh.practice.costHidden"));
        costLabel.setFont(costLabel.getFont().deriveFont(costShown ? Font.BOLD : Font.PLAIN));
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
        if (model.waitingForGroups(output)) {
            runs.add(new ColoredExpression.Run(Messages.format("karnaugh.practice.noGroups", outputName), -1));
            return runs;
        }
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
        return optionControls.shareBox();
    }

    AbstractButton useMineCheckBox() {
        return useMine;
    }

    String useMineReasonText() {
        return useMineReason.isVisible() ? useMineReason.getText() : "";
    }

    AbstractButton gateButton(WizardOptions.GateChoice choice) {
        return optionControls.gateButton(choice);
    }

    AbstractButton formButton(Minimization.Form form) {
        return optionControls.formButton(form);
    }
}
