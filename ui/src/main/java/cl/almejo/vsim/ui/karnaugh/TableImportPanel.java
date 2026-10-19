package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.GateCost;
import cl.almejo.vsim.core.logic.ImportProblem;
import cl.almejo.vsim.core.logic.ImportedGrid;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.TableImportException;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Archivo ▸ Importar ▸ «Tabla de verdad desde Excel/CSV…» (spec §6): «Abrir archivo…» or «Pegar» reads a table, the
 * user sets how many leading columns are inputs and the title, the problems are listed cell by cell, then the options
 * of step 4 (gates, form, sharing), a summary («8 entradas, 8 salidas → 23 términos, 31 compuertas») and the
 * {@link CreationControls} of step 5. Reading, the summary and «Crear» run on the {@link Background}; the EDT only
 * shows their results. A panel; {@link #showDialog} puts it in a modal dialog.
 */
public final class TableImportPanel extends JPanel {

    /** Shows the panel to the user; the real one opens {@link #showDialog}. */
    @FunctionalInterface
    public interface Opener {
        void open(Component parent, TableImportPanel panel);

        static Opener dialog() {
            return TableImportPanel::showDialog;
        }
    }

    private static final Dimension DIALOG_SIZE = new Dimension(760, 640);

    private final TableImportModel model = new TableImportModel();
    private final TableSources sources;
    private final Background background;
    private final CreationForm form;
    private final CreationControls controls;
    private final JButton openFile = new JButton(Messages.text("tableImport.openFile"));
    private final JButton paste = new JButton(Messages.text("tableImport.paste"));
    private final JLabel sourceLabel = new JLabel(Messages.text("tableImport.noTable"));
    private final JTextField title = new JTextField(20);
    private final JSpinner inputCount = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
    private final JLabel columns = new JLabel(" ");
    private final Paragraph problems = new Paragraph("") {
        @Override
        public Color getForeground() {
            return WizardColors.error();
        }
    };
    private final Paragraph notice = new Paragraph("");
    private final Map<WizardOptions.GateChoice, JRadioButton> gateButtons =
            new EnumMap<>(WizardOptions.GateChoice.class);
    private final Map<Minimization.Form, JRadioButton> formButtons = new EnumMap<>(Minimization.Form.class);
    private final JCheckBox share = new JCheckBox(Messages.text("karnaugh.expression.share"));
    private final JLabel summary = new JLabel(" ");
    private final JButton close = new JButton(Messages.text("karnaugh.close"));
    private Runnable onClose = () -> { };
    private boolean reading;
    private boolean syncing;
    /** Counts the summaries asked for: only the latest one is shown. */
    private long summaryRequest;
    /** Counts the files read: a file whose number is not the latest (the dialog was closed) is dropped. */
    private long readRequest;

    public TableImportPanel(TableSources sources, CreationTarget target, Background background) {
        super(new BorderLayout());
        this.sources = sources;
        this.background = background;
        this.form = new CreationForm(target);
        this.controls = new CreationControls(form, new CircuitCreator(target, background), model::spec,
                model::options, () -> onClose.run(), this::refreshButtons);

        JPanel content = new JPanel(new MigLayout("insets 16, fillx, wrap 2", "[][grow, fill]",
                "[][]8[][][]12[][][]12[]12[]"));
        JPanel sourceRow = new JPanel(new MigLayout("insets 0", "[][]12[grow]"));
        sourceRow.add(openFile);
        sourceRow.add(paste);
        sourceRow.add(sourceLabel);
        content.add(sourceRow, "span 2");
        content.add(new Paragraph(Messages.text("tableImport.help")), "span 2, growx, wmin 100");
        content.add(new JLabel(Messages.text("karnaugh.table.titleLabel")));
        content.add(title, "growx 0, wmin 200");
        content.add(new JLabel(Messages.text("tableImport.inputCount")));
        JPanel inputsRow = new JPanel(new MigLayout("insets 0", "[]12[grow]"));
        inputsRow.add(inputCount);
        inputsRow.add(columns);
        content.add(inputsRow);
        content.add(problems, "span 2, growx, wmin 100, hidemode 3");
        content.add(notice, "span 2, growx, wmin 100, hidemode 3");

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
        content.add(share, "span 2");
        summary.setFont(summary.getFont().deriveFont(Font.BOLD));
        content.add(summary, "span 2");
        content.add(controls, "span 2, growx");

        JPanel buttons = new JPanel(new MigLayout("insets 6 12 10 12, fillx", "[grow][]"));
        buttons.add(close, "skip 1");
        add(new JScrollPane(content), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        openFile.addActionListener(event -> openFile());
        paste.addActionListener(event -> paste());
        close.addActionListener(event -> requestClose());
        title.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                titleTyped();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                titleTyped();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                titleTyped();
            }
        });
        inputCount.addChangeListener(event -> {
            if (!syncing) {
                model.setInputCount((Integer) inputCount.getValue());
            }
        });
        model.addListener(this::modelChanged);
        modelChanged();
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

    private void titleTyped() {
        if (!syncing) {
            model.setTitle(title.getText());
        }
    }

    /**
     * Every way of closing the dialog («Cerrar», which reads «Cancelar» while busy, Escape and the close box) comes
     * here: a file being read is dropped and a creation that is running is cancelled, then the dialog closes.
     */
    public void requestClose() {
        if (reading) {
            readRequest++;
            reading = false;
        }
        summaryRequest++;
        controls.cancel();
        onClose.run();
    }

    /** What closing (and an «Insertar») does once the work is cancelled (the dialog disposes itself). */
    public void setOnClose(Runnable action) {
        onClose = action;
    }

    /** «Abrir archivo…»: the file is read off the EDT. */
    void openFile() {
        sources.chooser().choose(this).ifPresent(file -> read(() -> TableSources.readFile(file),
                file.getFileName().toString(), TableSources.titleOf(file)));
    }

    /** «Pegar»: the clipboard's text, tab separated as spreadsheets copy it. */
    void paste() {
        Optional<String> text = sources.clipboard().get();
        if (text.isEmpty() || text.get().isBlank()) {
            model.loadFailed(ImportProblem.general("tableImport.clipboardEmpty"), Messages.text("tableImport.clipboard"));
            return;
        }
        read(() -> TableSources.readPasted(text.get()), Messages.text("tableImport.clipboard"),
                Messages.text("karnaugh.table.defaultTitle"));
    }

    private void read(Callable<ImportedGrid> reader, String source, String proposedTitle) {
        reading = true;
        long request = ++readRequest;
        refreshButtons();
        background.run(reader, grid -> {
            if (request == readRequest) {
                reading = false;
                model.load(grid, source, proposedTitle);
            }
        }, failure -> {
            if (request == readRequest) {
                reading = false;
                model.loadFailed(failure instanceof TableImportException unreadable ? unreadable.problem()
                        : ImportProblem.general("tableImport.readFailed", readFailure(failure)), source);
            }
        });
    }

    private void modelChanged() {
        syncing = true;
        try {
            if (!title.getText().equals(model.title())) {
                title.setText(model.title());
            }
            SpinnerNumberModel spinner = (SpinnerNumberModel) inputCount.getModel();
            spinner.setMaximum(Math.max(1, model.columnCount() - 1));
            spinner.setValue(model.inputCount());
        } finally {
            syncing = false;
        }
        sourceLabel.setText(model.source().isEmpty() ? Messages.text("tableImport.noTable") : model.source());
        columns.setText(model.hasTable() ? Messages.format("tableImport.columns",
                String.join(", ", model.inputNames()), String.join(", ", model.outputNames())) : " ");
        List<String> lines = model.problemLines();
        problems.setText(String.join("\n", lines));
        problems.setVisible(!lines.isEmpty());
        Optional<String> missing = model.missingNotice();
        notice.setText(missing.orElse(""));
        notice.setVisible(missing.isPresent());
        for (Map.Entry<WizardOptions.GateChoice, JRadioButton> entry : gateButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == model.options().gates());
        }
        for (Map.Entry<Minimization.Form, JRadioButton> entry : formButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == model.options().form());
        }
        share.setSelected(model.options().shareTerms());
        form.proposeName(model.title());
        refreshButtons();
        summarize();
    }

    /** The summary of the table as it is now, computed off the EDT; an older one arriving late is dropped. */
    private void summarize() {
        long request = ++summaryRequest;
        Optional<TruthTableSpec> spec = model.spec();
        if (spec.isEmpty()) {
            summary.setText(" ");
            return;
        }
        summary.setText(Messages.text("tableImport.summarizing"));
        WizardOptions options = model.options();
        background.run(() -> summaryOf(spec.get(), options), text -> {
            if (request == summaryRequest) {
                summary.setText(text);
            }
        }, failure -> {
            if (request == summaryRequest) {
                summary.setText(" ");
            }
        });
    }

    /** «8 entradas, 8 salidas → 23 términos, 31 compuertas»: terms are counted once when they are shared. */
    static String summaryOf(TruthTableSpec spec, WizardOptions options) {
        Minimization minimization = Minimizer.minimize(spec, options.form());
        GateCost cost = CircuitGenerator.cost(minimization, options.generatorOptions());
        int terms = 0;
        Set<Term> distinct = new HashSet<>();
        for (Minimization.OutputExpression output : minimization.outputs()) {
            for (Term term : output.terms()) {
                if (term.mask() != 0 && (!options.shareTerms() || distinct.add(term))) {
                    terms++;
                }
            }
        }
        // The choices pick the wording by number; the counts themselves go as text (no thousands separator).
        return Messages.format("tableImport.summary", spec.inputCount(), spec.outputCount(), terms,
                cost.gateCount(), String.valueOf(terms), String.valueOf(cost.gateCount()));
    }

    /**
     * Why a file could not be read, for the user: the system's message for an input/output error (it names the file
     * and the cause), a fixed sentence for anything else (a bug, or a file too large to read).
     */
    private static String readFailure(Throwable failure) {
        if (failure instanceof IOException && failure.getMessage() != null) {
            return failure.getMessage();
        }
        return Messages.text("tableImport.readFailedUnexpected");
    }

    /**
     * While a file is read or the circuit is built, the buttons that would change the table are off, and «Cerrar»
     * reads «Cancelar».
     */
    private void refreshButtons() {
        boolean busy = reading || controls.isBusy();
        openFile.setEnabled(!busy);
        paste.setEnabled(!busy);
        close.setText(Messages.text(busy ? "karnaugh.cancel" : "karnaugh.close"));
        title.setEnabled(!busy && model.hasTable());
        inputCount.setEnabled(!busy && model.hasTable());
        List<AbstractButton> optionButtons = new ArrayList<>(gateButtons.values());
        optionButtons.addAll(formButtons.values());
        optionButtons.add(share);
        optionButtons.forEach(button -> button.setEnabled(!busy));
        controls.refresh();
    }

    TableImportModel model() {
        return model;
    }

    CreationControls controls() {
        return controls;
    }

    String problemsText() {
        return problems.isVisible() ? problems.getText() : "";
    }

    String noticeText() {
        return notice.isVisible() ? notice.getText() : "";
    }

    String summaryText() {
        return summary.getText();
    }

    String columnsText() {
        return columns.getText();
    }

    JSpinner inputCountSpinner() {
        return inputCount;
    }

    JTextField titleField() {
        return title;
    }

    JButton openFileButton() {
        return openFile;
    }

    JButton pasteButton() {
        return paste;
    }

    JButton closeButton() {
        return close;
    }

    AbstractButton gateButton(WizardOptions.GateChoice choice) {
        return gateButtons.get(choice);
    }

    /**
     * Shows {@code panel} in a dialog over {@code parent}; «Cerrar», Escape and the close box all go through
     * {@link #requestClose()}. Like the wizard's, it blocks only its own window.
     */
    public static void showDialog(Component parent, TableImportPanel panel) {
        JDialog dialog = WizardDialogs.create(parent, Messages.text("tableImport.title"), panel,
                panel::requestClose);
        panel.setOnClose(dialog::dispose);
        dialog.setSize(DIALOG_SIZE);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
