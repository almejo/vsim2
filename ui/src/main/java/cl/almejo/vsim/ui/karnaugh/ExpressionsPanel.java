package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.ExpressionLine;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Herramientas ▸ «Circuito desde expresiones…» (spec §3): one {@code NOMBRE = expresión} line per output, each read
 * as it is typed with its problem under it; the inputs found; «Simplificar» (remembered in the preferences) with, under
 * each line, its simplified expression computed on the {@link Background} (the latest request wins); the step-4
 * options and the cost line; «Ver en Karnaugh…»; and the {@link CreationControls} of the import dialog. Without
 * «Simplificar» the circuit is built as written, one gate per operator. The {@link ExpressionLegend} on the side
 * explains the notation. A panel; {@link #showDialog} puts it in a dialog.
 */
public final class ExpressionsPanel extends JPanel {

    /** Shows the panel to the user; the real one opens {@link #showDialog}. */
    @FunctionalInterface
    public interface Opener {
        void open(Component parent, ExpressionsPanel panel);

        static Opener dialog() {
            return ExpressionsPanel::showDialog;
        }
    }

    /** What is under the lines once computed: one text per line (empty without «Simplificar») and the cost line. */
    record Preview(List<String> lineTexts, String cost) {
        Preview {
            lineTexts = List.copyOf(lineTexts);
        }
    }

    private static final Dimension DIALOG_SIZE = new Dimension(1040, 700);
    private static final Dimension LEGEND_SIZE = new Dimension(380, 0);
    private static final float LINE_FONT_FACTOR = 1.2f;

    private final ExpressionsModel model;
    private final Background background;
    private final AppPreferences preferences;
    private final KarnaughCost cost;
    private final Consumer<TruthTableSpec> openInWizard;
    private final CreationForm form;
    private final CreationControls controls;
    private final JTextField title = new JTextField(20);
    private final JPanel linesPanel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow, fill]", ""));
    private final List<LineRow> rows = new ArrayList<>();
    private final JButton addLine = new JButton(Messages.text("expressions.addLine"));
    private final JLabel inputs = new JLabel(" ");
    private final Paragraph tableProblems = new Paragraph("") {
        @Override
        public Color getForeground() {
            return WizardColors.errorText();
        }
    };
    private final JCheckBox simplify = new JCheckBox(Messages.text("expressions.simplify"));
    private final JButton wizardLink = new JButton(Messages.text("expressions.openInWizard"));
    private final JLabel wizardReason = new JLabel(" ") {
        @Override
        public Color getForeground() {
            return WizardColors.muted();
        }
    };
    private final Map<WizardOptions.GateChoice, JRadioButton> gateButtons =
            new EnumMap<>(WizardOptions.GateChoice.class);
    private final Map<Minimization.Form, JRadioButton> formButtons = new EnumMap<>(Minimization.Form.class);
    private final JLabel formReason = new JLabel(Messages.text("expressions.asWrittenReason")) {
        @Override
        public Color getForeground() {
            return WizardColors.muted();
        }
    };
    private final JCheckBox share = new JCheckBox(Messages.text("karnaugh.expression.share"));
    private final JLabel costLine = new JLabel(" ");
    private final JButton close = new JButton(Messages.text("karnaugh.close"));
    private final ExpressionLegend legend;
    private final JPanel buttonBar;
    private Runnable onClose = () -> { };
    private KarnaughWizard.DiscardConfirmation discardConfirmation = discardDialog();
    /** The lines as the dialog opened, or as «Crear» last created them: closing asks only when they differ. */
    private ExpressionsModel.Contents unchanged;
    /** The line the legend types into: the last one that had the focus. */
    private int lastFocused;
    private boolean syncing;
    /** Counts the previews asked for: only the latest one is shown. */
    private long previewRequest;
    /** What the preview shown (or being computed) is of; null when there is none. */
    private List<Object> previewed;
    /** The preview of {@link #previewed}; null while it is computed. */
    private Preview preview;

    /**
     * @param target       where «Crear» puts the circuit (and the component)
     * @param background   where the previews are computed and the circuit is built
     * @param preferences  where «Simplificar» is remembered
     * @param cost         the gates of a minimized circuit, for the cost line
     * @param openInWizard «Ver en Karnaugh…»: opens the wizard on the table (this dialog closes first)
     */
    public ExpressionsPanel(CreationTarget target, Background background, AppPreferences preferences,
                            KarnaughCost cost, Consumer<TruthTableSpec> openInWizard) {
        super(new BorderLayout());
        this.model = new ExpressionsModel(preferences.simplifyExpressions());
        this.background = background;
        this.preferences = preferences;
        this.cost = cost;
        this.openInWizard = openInWizard;
        this.form = new CreationForm(target);
        this.controls = new CreationControls(form, new CircuitCreator(target, background), model::spec,
                model::options, () -> onClose.run(), this::refreshButtons, () -> true);
        controls.setAsWritten(() -> model.simplify() ? Optional.empty() : model.expressionLines());
        controls.setOnCreated(() -> unchanged = model.contents());
        this.unchanged = model.contents();
        this.legend = new ExpressionLegend(this::insert);

        JPanel content = new JPanel(new MigLayout("insets 16, fillx, wrap 2", "[][grow, fill]", ""));
        content.add(new JLabel(Messages.text("karnaugh.table.titleLabel")));
        content.add(title, "growx 0, wmin 200");
        content.add(linesPanel, "span 2, growx, gaptop 8");
        content.add(addLine, "span 2, growx 0");
        content.add(inputs, "span 2, gaptop 8");
        content.add(tableProblems, "span 2, growx, wmin 100, hidemode 3");

        JPanel simplifyRow = new JPanel(new MigLayout("insets 0", "[]16[][]"));
        simplifyRow.add(simplify);
        wizardLink.putClientProperty("JButton.buttonType", "borderless");
        simplifyRow.add(wizardLink, "hidemode 3");
        simplifyRow.add(wizardReason, "hidemode 3");
        content.add(simplifyRow, "span 2, gaptop 12");

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
        content.add(new JLabel(Messages.text("karnaugh.expression.gates")));
        content.add(gatesPanel);
        content.add(new JLabel(Messages.text("karnaugh.expression.form")));
        content.add(formsPanel);
        content.add(formReason, "span 2, hidemode 3");
        content.add(share, "span 2");
        JPanel costRow = new JPanel(new MigLayout("insets 0", "[]8[]"));
        costRow.add(new JLabel(Messages.text("karnaugh.expression.cost")));
        costLine.setFont(costLine.getFont().deriveFont(Font.BOLD));
        costRow.add(costLine);
        content.add(costRow, "span 2");
        content.add(controls, "span 2, growx, gaptop 12");

        JScrollPane legendScroll = new JScrollPane(legend);
        legendScroll.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 16));
        legendScroll.setPreferredSize(LEGEND_SIZE);
        legend.setOnToggle(() -> {
            // Collapsed, the legend takes only the width of its «Notación» button.
            legendScroll.setPreferredSize(legend.isExpanded() ? LEGEND_SIZE : null);
            revalidate();
        });

        buttonBar = new JPanel(new MigLayout("insets 6 12 10 12, fillx", "[grow][][]"));
        buttonBar.add(controls.createButton(), "skip 1");
        buttonBar.add(close, "gapleft 12");
        add(new JScrollPane(content), BorderLayout.CENTER);
        add(legendScroll, BorderLayout.EAST);
        add(buttonBar, BorderLayout.SOUTH);
        controls.addRefreshListener(this::updateDefaultButton);

        addLine.addActionListener(event -> addLine());
        close.addActionListener(event -> requestClose());
        wizardLink.addActionListener(event -> openWizard());
        simplify.addActionListener(event -> {
            preferences.setSimplifyExpressions(simplify.isSelected());
            model.setSimplify(simplify.isSelected());
        });
        title.setText(model.title());
        title.getDocument().addDocumentListener(onEdit(() -> model.setTitle(title.getText())));
        model.addListener(this::modelChanged);
        modelChanged();
    }

    private DocumentListener onEdit(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                edited();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                edited();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                edited();
            }

            private void edited() {
                if (!syncing) {
                    action.run();
                }
            }
        };
    }

    private void addGate(ButtonGroup group, JPanel panel, WizardOptions.GateChoice choice, String key) {
        JRadioButton button = new JRadioButton(Messages.text(key));
        button.addActionListener(event -> model.setOptions(model.options().withGates(choice)));
        group.add(button);
        panel.add(button);
        gateButtons.put(choice, button);
    }

    private void addForm(ButtonGroup group, JPanel panel, Minimization.Form choice, String key) {
        JRadioButton button = new JRadioButton(Messages.text(key));
        button.addActionListener(event -> model.setOptions(model.options().withForm(choice)));
        group.add(button);
        panel.add(button);
        formButtons.put(choice, button);
    }

    /** «+ Agregar salida»: a new line with the next free name, ready to type in. */
    private void addLine() {
        if (model.addLine()) {
            lastFocused = model.lineCount() - 1;
            JTextField field = rows.get(lastFocused).field;
            field.setCaretPosition(field.getText().length());
            field.requestFocusInWindow();
        }
    }

    /** A legend row: its operator goes in at the caret of the last focused line ({@code ()} with the caret inside). */
    private void insert(String text) {
        JTextField field = rows.get(Math.min(lastFocused, rows.size() - 1)).field;
        if (!field.isEnabled()) {
            return;
        }
        int start = field.getSelectionStart();
        field.replaceSelection(text);
        // Set explicitly: off the EDT the caret does not follow the insertion by itself.
        field.setCaretPosition(start + text.length() - (text.equals("()") ? 1 : 0));
        field.requestFocusInWindow();
    }

    /**
     * Every way of closing the dialog («Cerrar», which reads «Cancelar» while «Crear» is building, Escape and the
     * close box) comes here: a creation that is running is cancelled; then, when the lines were edited and not created
     * since, the user is asked before they are discarded.
     */
    public void requestClose() {
        controls.cancel();
        if (!model.contents().equals(unchanged) && !discardConfirmation.discard(this)) {
            return;
        }
        previewRequest++;
        onClose.run();
    }

    /** What asks before discarding the lines; the real one shows a dialog, tests answer it. */
    public void setDiscardConfirmation(KarnaughWizard.DiscardConfirmation confirmation) {
        discardConfirmation = confirmation;
    }

    /** What closing (and an «Insertar», and «Ver en Karnaugh…») does (the dialog disposes itself). */
    public void setOnClose(Runnable action) {
        onClose = action;
    }

    /** «Descartar» / «Seguir editando», with «Seguir editando» as the default answer. */
    static KarnaughWizard.DiscardConfirmation discardDialog() {
        return parent -> {
            String discard = Messages.text("karnaugh.discard.discard");
            String keep = Messages.text("karnaugh.discard.keep");
            int answer = JOptionPane.showOptionDialog(parent, Messages.text("expressions.discard.message"),
                    Messages.text("expressions.discard.title"), JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, new Object[] {discard, keep}, keep);
            return answer == 0;
        };
    }

    /** «Ver en Karnaugh…»: this dialog closes (the table goes with the wizard) and the wizard opens on it. */
    private void openWizard() {
        Optional<TruthTableSpec> spec = model.spec();
        if (!model.canOpenInWizard() || spec.isEmpty() || controls.isBusy()) {
            return;
        }
        previewRequest++;
        onClose.run();
        openInWizard.accept(spec.get());
    }

    private void modelChanged() {
        syncing = true;
        try {
            if (!title.getText().equals(model.title())) {
                title.setText(model.title());
            }
            if (rows.size() != model.lineCount()) {
                rebuildRows();
            }
            for (int index = 0; index < rows.size(); index++) {
                JTextField field = rows.get(index).field;
                if (!field.getText().equals(model.lines().get(index))) {
                    field.setText(model.lines().get(index));
                }
            }
        } finally {
            syncing = false;
        }
        for (int index = 0; index < rows.size(); index++) {
            LineRow row = rows.get(index);
            Optional<String> problem = model.lineProblem(index);
            row.problem.setText(problem.orElse(""));
            row.problem.setVisible(problem.isPresent());
            Optional<String> merged = model.mergedWarning(index);
            row.warning.setText(merged.map(ExpressionsModel::mergedText).orElse(""));
            row.mergedName = merged.orElse(null);
            row.warningRow.setVisible(merged.isPresent());
        }
        inputs.setText(model.inputsText());
        List<String> problems = model.tableProblems();
        tableProblems.setText(String.join("\n", problems));
        tableProblems.setVisible(!problems.isEmpty());
        simplify.setSelected(model.simplify());
        for (Map.Entry<WizardOptions.GateChoice, JRadioButton> entry : gateButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == model.options().gates());
        }
        for (Map.Entry<Minimization.Form, JRadioButton> entry : formButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == model.options().form());
        }
        share.setSelected(model.options().shareTerms());
        form.proposeName(model.spec().map(TruthTableSpec::title).orElse(model.title()));
        refreshButtons();
        refreshPreview();
    }

    private void rebuildRows() {
        linesPanel.removeAll();
        rows.clear();
        for (int index = 0; index < model.lineCount(); index++) {
            LineRow row = new LineRow();
            rows.add(row);
            linesPanel.add(row);
        }
        lastFocused = Math.min(lastFocused, rows.size() - 1);
        linesPanel.revalidate();
        linesPanel.repaint();
    }

    /**
     * The previews and the cost of the lines as they are now, computed off the EDT; an older one arriving late is
     * dropped. The title does not change them, so typing one does not compute them again.
     */
    private void refreshPreview() {
        Optional<TruthTableSpec> spec = model.spec();
        Optional<List<ExpressionLine>> lines = model.expressionLines();
        if (spec.isEmpty() || lines.isEmpty()) {
            previewed = null;
            preview = null;
            previewRequest++;
            showPreview();
            return;
        }
        TruthTableSpec table = spec.get();
        boolean simplifying = model.simplify();
        WizardOptions options = model.options();
        List<Object> key = List.of(table.inputNames(), table.outputNames(), table.outputs(), lines.get(),
                simplifying, options);
        if (key.equals(previewed)) {
            showPreview();
            return;
        }
        previewed = key;
        preview = null;
        long request = ++previewRequest;
        showPreview();
        List<ExpressionLine> written = lines.get();
        background.run(() -> previewOf(table, written, simplifying, options, cost), result -> {
            if (request == previewRequest) {
                preview = result;
                showPreview();
            }
        }, failure -> {
            if (request == previewRequest) {
                preview = new Preview(List.of(), " ");
                showPreview();
            }
        });
    }

    /** The worker's part: the minimized expressions and the cost, or only the cost of the circuit as written. */
    static Preview previewOf(TruthTableSpec spec, List<ExpressionLine> lines, boolean simplify,
                             WizardOptions options, KarnaughCost cost) {
        if (!simplify) {
            return new Preview(List.of(), CostLine.text(
                    CircuitGenerator.costAsWritten(spec, lines, options.shareTerms()).gatesByType()));
        }
        Minimization minimization = Minimizer.minimize(spec, options.form());
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            texts.add(ExpressionsModel.previewText(lines.get(index), minimization, index));
        }
        return new Preview(texts, CostLine.text(cost.gatesByType(minimization, options)));
    }

    private void showPreview() {
        String computing = Messages.text("expressions.preview.computing");
        boolean computed = preview != null;
        for (int index = 0; index < rows.size(); index++) {
            String text = "";
            if (previewed != null && model.simplify()) {
                text = !computed ? computing
                        : index < preview.lineTexts().size() ? preview.lineTexts().get(index) : "";
            }
            rows.get(index).preview.setText(text);
            rows.get(index).preview.setVisible(!text.isEmpty());
        }
        costLine.setText(previewed == null ? " " : computed ? preview.cost() : computing);
    }

    /**
     * While «Crear» is building, everything that would change the lines is off and «Cerrar» reads «Cancelar»; without
     * «Simplificar» the gates and the form are off (the circuit uses AND, OR, NOT and XOR as written).
     */
    private void refreshButtons() {
        boolean busy = controls.isBusy();
        boolean simplifying = model.simplify();
        close.setText(Messages.text(busy ? "karnaugh.cancel" : "karnaugh.close"));
        title.setEnabled(!busy);
        for (LineRow row : rows) {
            row.field.setEnabled(!busy);
            row.remove.setEnabled(!busy && model.canRemoveLine());
            row.dismiss.setEnabled(!busy);
        }
        addLine.setEnabled(!busy && model.canAddLine());
        simplify.setEnabled(!busy);
        List<AbstractButton> minimizing = new ArrayList<>(gateButtons.values());
        minimizing.addAll(formButtons.values());
        minimizing.forEach(button -> button.setEnabled(!busy && simplifying));
        formReason.setVisible(!simplifying);
        share.setEnabled(!busy);
        wizardLink.setVisible(simplifying);
        wizardLink.setEnabled(!busy && model.canOpenInWizard());
        boolean outOfRange = simplifying && model.spec().isPresent() && !model.canOpenInWizard();
        wizardReason.setText(outOfRange ? Messages.text("expressions.openInWizard.range") : "");
        wizardReason.setVisible(outOfRange);
        controls.refresh();
    }

    /** «Crear» is the default button (Enter) while it is on; otherwise Enter does nothing. */
    private void updateDefaultButton() {
        JRootPane root = getRootPane();
        if (root != null && buttonBar != null) {
            JButton create = controls.createButton();
            root.setDefaultButton(create.isEnabled() ? create : null);
        }
    }

    /** One line: its text field and ✕, its problem, the merged-letters warning with its ✕, and its preview. */
    private final class LineRow extends JPanel {

        final JTextField field = new JTextField(30);
        final JButton remove = new JButton("✕");
        final JLabel problem = new JLabel(" ") {
            @Override
            public Color getForeground() {
                return WizardColors.errorText();
            }
        };
        final JPanel warningRow = new JPanel(new MigLayout("insets 0", "[]4[]"));
        final JLabel warning = new JLabel(" ") {
            @Override
            public Color getForeground() {
                return WizardColors.warning();
            }
        };
        final JButton dismiss = new JButton("✕");
        final JLabel preview = new JLabel(" ");
        String mergedName;

        LineRow() {
            super(new MigLayout("insets 0, fillx, wrap 2, gapy 2", "[grow, fill][]", ""));
            Font base = field.getFont();
            field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(base.getSize2D() * LINE_FONT_FACTOR)));
            remove.setToolTipText(Messages.text("expressions.removeLine"));
            dismiss.setToolTipText(Messages.text("expressions.merged.dismiss"));
            dismiss.putClientProperty("JButton.buttonType", "borderless");
            warningRow.add(warning);
            warningRow.add(dismiss);
            add(field);
            add(remove);
            add(problem, "span 2, hidemode 3");
            add(warningRow, "span 2, hidemode 3");
            add(preview, "span 2, hidemode 3");
            field.getDocument().addDocumentListener(onEdit(() -> {
                int index = rows.indexOf(this);
                if (index >= 0) {
                    model.setLine(index, field.getText());
                }
            }));
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    int index = rows.indexOf(LineRow.this);
                    if (index >= 0) {
                        lastFocused = index;
                    }
                }
            });
            remove.addActionListener(event -> {
                int index = rows.indexOf(this);
                if (index >= 0) {
                    model.removeLine(index);
                }
            });
            dismiss.addActionListener(event -> {
                if (mergedName != null) {
                    model.dismissMerged(mergedName);
                }
            });
        }
    }

    ExpressionsModel model() {
        return model;
    }

    int lineCount() {
        return rows.size();
    }

    JTextField lineField(int index) {
        return rows.get(index).field;
    }

    AbstractButton removeLineButton(int index) {
        return rows.get(index).remove;
    }

    AbstractButton dismissWarningButton(int index) {
        return rows.get(index).dismiss;
    }

    String problemText(int index) {
        LineRow row = rows.get(index);
        return row.problem.isVisible() ? row.problem.getText() : "";
    }

    String warningText(int index) {
        LineRow row = rows.get(index);
        return row.warningRow.isVisible() ? row.warning.getText() : "";
    }

    String previewText(int index) {
        LineRow row = rows.get(index);
        return row.preview.isVisible() ? row.preview.getText() : "";
    }

    String inputsText() {
        return inputs.getText();
    }

    String tableProblemsText() {
        return tableProblems.isVisible() ? tableProblems.getText() : "";
    }

    String costText() {
        return costLine.getText();
    }

    String wizardReasonText() {
        return wizardReason.isVisible() ? wizardReason.getText() : "";
    }

    String resultText() {
        return controls.resultText();
    }

    boolean formReasonShown() {
        return formReason.isVisible();
    }

    AbstractButton addLineButton() {
        return addLine;
    }

    AbstractButton simplifyBox() {
        return simplify;
    }

    AbstractButton shareBox() {
        return share;
    }

    AbstractButton gateButton(WizardOptions.GateChoice choice) {
        return gateButtons.get(choice);
    }

    AbstractButton wizardLink() {
        return wizardLink;
    }

    JTextField titleField() {
        return title;
    }

    JButton createButton() {
        return controls.createButton();
    }

    ExpressionLegend legend() {
        return legend;
    }

    /**
     * Shows {@code panel} in a dialog over {@code parent}; «Cerrar», Escape and the close box all go through
     * {@link #requestClose()}. Like the wizard's, it blocks only its own window.
     */
    public static void showDialog(Component parent, ExpressionsPanel panel) {
        JDialog dialog = WizardDialogs.create(parent, Messages.text("expressions.title"), panel,
                panel::requestClose);
        panel.setOnClose(dialog::dispose);
        panel.updateDefaultButton();
        dialog.setSize(DIALOG_SIZE);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
