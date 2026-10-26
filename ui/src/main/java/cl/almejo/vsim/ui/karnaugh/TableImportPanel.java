package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.GateCost;
import cl.almejo.vsim.core.logic.ImportProblem;
import cl.almejo.vsim.core.logic.ImportedGrid;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.TableImport;
import cl.almejo.vsim.core.logic.TableImportException;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.panels.TextChanges;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Archivo ▸ Importar ▸ «Tabla de verdad desde Excel/CSV…» (spec §6), in two pages. «1 Datos»: «Abrir archivo…» or
 * «Pegar» reads a table, the user sets how many leading columns are inputs, the problems are listed cell by cell and
 * a preview shows how every cell was read ({@link TablePreview}); «Siguiente» goes on when the data has no problem.
 * «2 Crear»: the title, the options of the wizard's step 4 (gates, form, sharing), a summary («8 entradas, 8 salidas →
 * 23 términos, 31 compuertas») and the {@link CreationControls} of its step 5, with «Atrás», «Abrir en el asistente»
 * and «Crear». Reading, the summary and «Crear» run on the {@link Background}; the EDT only shows their results. A
 * panel; {@link #showDialog} puts it in a modal dialog.
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

    /**
     * «Abrir en el asistente»: opens the Karnaugh wizard on {@code spec} (2..4 inputs, up to 4 outputs) with the
     * dialog's {@code options}; the import dialog is already closed (the table moves into the wizard).
     */
    @FunctionalInterface
    public interface WizardOpener {
        void open(TruthTableSpec spec, WizardOptions options);
    }

    /** The two pages. */
    enum Page {
        DATA("tableImport.step.data"),
        CREATE("tableImport.step.create");

        private final String titleKey;

        Page(String titleKey) {
            this.titleKey = titleKey;
        }

        String title() {
            return Messages.text(titleKey);
        }
    }

    /** The icon of page «Datos»'s suggestion of the wizard (a light bulb). */
    public static final String HINT_ICON = "hint";

    /** The dialog is never smaller than this when it fits itself to its content (page «Crear» needs about that). */
    static final Dimension MINIMUM_DIALOG_SIZE = new Dimension(640, 440);

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
    /** How the file must look, and the limits («Hasta 10 entradas y 16 salidas.»). */
    private final Paragraph help = new Paragraph(Messages.text("tableImport.help") + " "
            + Messages.format("tableImport.limits", String.valueOf(TruthTableSpec.MAXIMUM_INPUTS),
            String.valueOf(TruthTableSpec.MAXIMUM_OUTPUTS)));
    private final Paragraph problems = Paragraph.error();
    private final Paragraph titleProblem = Paragraph.error();
    private final JButton hintButton = new JButton(Messages.text("tableImport.openInWizard"));
    private final Paragraph hintText = new Paragraph(Messages.text("tableImport.wizardHint"));
    /** Page «Datos»'s suggestion of the Karnaugh wizard, over the preview, for a table that fits it. */
    private final JPanel wizardHint = new JPanel(new MigLayout("insets 6 10 6 8", "[]8[grow, fill]12[]", "[center]")) {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(WizardColors.hint());
                graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                graphics2D.setColor(WizardColors.hintEdge());
                graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            } finally {
                graphics2D.dispose();
            }
        }
    };
    private final TablePreviewModel previewModel = new TablePreviewModel();
    private final TablePreview preview = new TablePreview(previewModel);
    private final CardLayout pages = new CardLayout();
    private final JPanel pageCards = new JPanel(pages);
    /** «Paso 1 de 2 · Datos»: two pages need no tabs, «Siguiente» and «Atrás» move between them. */
    private final JLabel heading = new JLabel(" ");
    private final JButton back = new JButton(Messages.text("karnaugh.back"));
    private final JButton next = new JButton(Messages.text("karnaugh.next"));
    private Page page = Page.DATA;
    private final Paragraph notice = new Paragraph("");
    private final Map<WizardOptions.GateChoice, JRadioButton> gateButtons =
            new EnumMap<>(WizardOptions.GateChoice.class);
    private final Map<Minimization.Form, JRadioButton> formButtons = new EnumMap<>(Minimization.Form.class);
    private final JCheckBox share = new JCheckBox(Messages.text("karnaugh.expression.share"));
    private final JLabel summary = new JLabel(" ");
    /** «El circuito tendría 2 229 compuertas; el máximo es 2 000.» under the summary, when «Crear» is off for it. */
    private final Paragraph gateLimit = Paragraph.error();
    private final JButton close = new JButton(Messages.text("karnaugh.close"));
    private final JButton wizard = new JButton(Messages.text("tableImport.openInWizard"));
    private final WizardOpener openInWizard;
    private final JPanel buttonBar;
    private Runnable onClose = () -> { };
    private boolean reading;
    private boolean syncing;
    /** Counts the summaries asked for: only the latest one is shown. */
    private long summaryRequest;
    /** What the summary shown (or being computed) is of: the table without its title, and the options. */
    private List<Object> summarized;
    /** The summary of {@link #summarized}; null while it is computed. */
    private String summarizedText;
    /** The gates of {@link #summarized}'s circuit; null while computed, -1 when they could not be counted. */
    private Integer summarizedGates;
    /** Counts the files read: a file whose number is not the latest (the dialog was closed) is dropped. */
    private long readRequest;
    /** The size the dialog was last given by {@link #fitWindow}; another size means the user resized it. */
    private Dimension fittedSize;
    /** The size the user resized the dialog to, or null: fitting never makes it smaller than that. */
    private Dimension userSize;

    /**
     * @param openInWizard «Abrir en el asistente»: opens the wizard on the table once this dialog is closed
     */
    public TableImportPanel(TableSources sources, CreationTarget target, Background background,
                            WizardOpener openInWizard) {
        super(new BorderLayout());
        this.sources = sources;
        this.openInWizard = openInWizard;
        this.background = background;
        this.form = new CreationForm(target);
        this.controls = new CreationControls(form, new CircuitCreator(target, background), model::spec,
                model::options, () -> onClose.run(), this::refreshButtons,
                () -> !reading && page == Page.CREATE && sizeKnownAndAllowed());

        JPanel data = new JPanel(new MigLayout("insets 12 16 8 16, fill, wrap 1, hidemode 3", "[grow, fill]",
                "[][]8[][][][][grow]"));
        JPanel sourceRow = new JPanel(new MigLayout("insets 0", "[][]12[grow]"));
        sourceRow.add(openFile);
        sourceRow.add(paste);
        sourceRow.add(sourceLabel);
        data.add(sourceRow);
        // The format help in a soft box, so it reads as instructions and not as part of the form.
        JPanel helpBox = new JPanel(new MigLayout("insets 8 12 8 12, fill", "[grow, fill]")) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics2D.setColor(WizardColors.sidePanel());
                    graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    graphics2D.setColor(WizardColors.sidePanelEdge());
                    graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                } finally {
                    graphics2D.dispose();
                }
            }
        };
        helpBox.setOpaque(false);
        helpBox.add(help, "wmin 100");
        data.add(helpBox, "wmin 100");
        JPanel inputsRow = new JPanel(new MigLayout("insets 0", "[][]12[grow]"));
        inputsRow.add(new JLabel(Messages.text("tableImport.inputCount")));
        inputsRow.add(inputCount);
        inputsRow.add(columns);
        data.add(inputsRow);
        data.add(problems, "wmin 100");
        data.add(notice, "wmin 100");
        wizardHint.setOpaque(false);
        // A drawn bulb: the fonts Java finds rarely have the emoji 💡, which would show as an empty box or nothing.
        wizardHint.add(new JLabel(Icons.get(HINT_ICON, Icons.MENU_SIZE)));
        wizardHint.add(hintText, "wmin 100");
        wizardHint.add(hintButton);
        data.add(wizardHint, "wmin 100");
        data.add(preview, "grow, push, align left top");

        JPanel content = new JPanel(new MigLayout("insets 16, fillx, wrap 2", "[][grow, fill]",
                "[][]12[][][]12[]12[]"));
        content.add(new JLabel(Messages.text("karnaugh.table.titleLabel")));
        content.add(title, "growx 0, wmin 200");
        content.add(titleProblem, "skip 1, growx, wmin 100, hidemode 3");

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
        content.add(gateLimit, "span 2, growx, wmin 100, hidemode 3");
        content.add(controls, "span 2, growx");

        pageCards.add(data, Page.DATA.name());
        pageCards.add(new JScrollPane(content), Page.CREATE.name());

        JPanel headingBar = new JPanel(new MigLayout("insets 12 16 0 16"));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        headingBar.add(heading);

        buttonBar = new JPanel(new MigLayout("insets 6 12 10 12, fillx, hidemode 3", "[][grow][][][][]"));
        buttonBar.add(wizard, "cell 0 0");
        buttonBar.add(back, "cell 2 0");
        buttonBar.add(next, "cell 3 0");
        buttonBar.add(controls.createButton(), "cell 4 0");
        buttonBar.add(close, "cell 5 0, gapleft 12");
        add(headingBar, BorderLayout.NORTH);
        add(pageCards, BorderLayout.CENTER);
        add(buttonBar, BorderLayout.SOUTH);
        controls.addRefreshListener(this::updateDefaultButton);

        openFile.addActionListener(event -> openFile());
        paste.addActionListener(event -> paste());
        close.addActionListener(event -> requestClose());
        wizard.addActionListener(event -> openWizard(Page.CREATE));
        hintButton.addActionListener(event -> openWizard(Page.DATA));
        back.addActionListener(event -> goTo(Page.DATA));
        next.addActionListener(event -> goTo(Page.CREATE));
        TextChanges.onChange(title, this::titleTyped);
        inputCount.addChangeListener(event -> {
            if (!syncing) {
                model.setInputCount((Integer) inputCount.getValue());
            }
        });
        model.addListener(this::modelChanged);
        modelChanged();
        display(Page.DATA);
    }

    /** The page shown. */
    Page page() {
        return page;
    }

    /**
     * Shows {@code target}; «2 Crear» is refused (false) while the data has a problem or none was read, and nothing
     * changes page while a file is read or the circuit is built.
     */
    boolean goTo(Page target) {
        if (reading || controls.isBusy() || target == Page.CREATE && !model.dataReady()) {
            return false;
        }
        display(target);
        return true;
    }

    private void display(Page target) {
        page = target;
        pages.show(pageCards, target.name());
        heading.setText(Messages.format("tableImport.page.heading", target.ordinal() + 1, Page.values().length,
                target.title()));
        refreshButtons();
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

    /**
     * «Abrir en el asistente» (on page «Crear», or in page «Datos»'s suggestion, {@code from}): this dialog closes
     * without asking (nothing is lost: the table, its title and the options move into the wizard, missing rows as X)
     * and the wizard opens on its map.
     */
    private void openWizard(Page from) {
        Optional<TruthTableSpec> spec = model.spec();
        if (page != from || !model.canOpenInWizard() || spec.isEmpty() || reading || controls.isBusy()) {
            return;
        }
        WizardOptions options = model.options();
        requestClose();
        openInWizard.open(spec.get(), options);
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
                fitWindow();
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
        Optional<String> blankTitle = model.titleProblem();
        titleProblem.setText(blankTitle.orElse(""));
        titleProblem.setVisible(blankTitle.isPresent());
        if (model.hasTable()) {
            previewModel.show(model.grid(), model.tableColumns(), model.inputCount(), model.tableProblems());
        } else if (!previewModel.isEmpty()) {
            previewModel.clear();
        }
        preview.refresh();
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
        summarize();
        refreshButtons();
    }

    /**
     * The summary of the table as it is now, computed off the EDT; an older one arriving late is dropped. The title
     * does not change it, so typing one does not compute it again.
     */
    private void summarize() {
        Optional<TruthTableSpec> spec = model.spec();
        if (spec.isEmpty()) {
            summary.setText(" ");
            showGateLimit();
            return;
        }
        WizardOptions options = model.options();
        TruthTableSpec table = spec.get();
        List<Object> key = List.of(table.inputNames(), table.outputNames(), table.outputs(), options);
        if (key.equals(summarized)) {
            summary.setText(summarizedText == null ? Messages.text("tableImport.summarizing") : summarizedText);
            showGateLimit();
            return;
        }
        summarized = key;
        summarizedText = null;
        summarizedGates = null;
        long request = ++summaryRequest;
        summary.setText(Messages.text("tableImport.summarizing"));
        showGateLimit();
        background.run(() -> summaryOf(table, options), done -> summarized(request, done),
                failure -> summarized(request, new Summary(" ", -1)));
    }

    /** A summary arrived: kept when it is the latest, and shown unless the table has a problem now. */
    private void summarized(long request, Summary done) {
        if (request == summaryRequest) {
            summarizedText = done.text();
            summarizedGates = done.gateCount();
            if (model.spec().isPresent()) {
                summary.setText(done.text());
            }
            showGateLimit();
            refreshButtons();
        }
    }

    /**
     * Whether the circuit's size allows «Crear»: its gates were counted and are at most {@value
     * TableImport#MAXIMUM_GATES} (or could not be counted: the creation then says what went wrong).
     */
    private boolean sizeKnownAndAllowed() {
        return summarizedGates != null && summarizedGates <= TableImport.MAXIMUM_GATES;
    }

    /** Says under the summary, and on «Crear», why a circuit too large cannot be created. */
    private void showGateLimit() {
        boolean tooLarge = model.spec().isPresent() && summarizedGates != null
                && summarizedGates > TableImport.MAXIMUM_GATES;
        String text = tooLarge ? Messages.format("tableImport.tooManyGates", grouped(summarizedGates),
                grouped(TableImport.MAXIMUM_GATES)) : "";
        gateLimit.setText(text);
        gateLimit.setVisible(tooLarge);
        controls.createButton().setToolTipText(tooLarge ? text : null);
    }

    /** «2 229»: thousands apart by a space that does not break the line. */
    static String grouped(int number) {
        return String.format(Locale.ROOT, "%,d", number).replace(',', '\u00A0');
    }

    /** What the summary line says, and the gates it counts. */
    record Summary(String text, int gateCount) {
    }

    /** «8 entradas, 8 salidas → 23 términos, 31 compuertas»: terms are counted once when they are shared. */
    static Summary summaryOf(TruthTableSpec spec, WizardOptions options) {
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
        // The choices pick the wording by number; the counts themselves go as text, grouped like the gate limit.
        return new Summary(Messages.format("tableImport.summary", spec.inputCount(), spec.outputCount(), terms,
                cost.gateCount(), grouped(terms), grouped(cost.gateCount())), cost.gateCount());
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
        wizard.setEnabled(!busy && model.canOpenInWizard());
        wizardHint.setVisible(model.dataReady() && model.splitFitsTheWizard());
        hintButton.setEnabled(!busy && model.canOpenInWizard());
        boolean outOfRange = model.hasTable() && !model.splitFitsTheWizard();
        wizard.setToolTipText(outOfRange ? Messages.text("tableImport.openInWizard.range") : null);
        boolean onData = page == Page.DATA;
        next.setEnabled(!busy && model.dataReady());
        next.setToolTipText(model.dataReady() ? null : Messages.text(model.problemLines().isEmpty()
                ? "tableImport.next.noTable" : "tableImport.next.problems"));
        back.setEnabled(!busy);
        next.setVisible(onData);
        back.setVisible(!onData);
        wizard.setVisible(!onData);
        controls.createButton().setVisible(!onData);
        controls.refresh();
        updateDefaultButton();
    }

    /**
     * Enter: «Siguiente» on page «Datos», «Crear» on page «Crear», while it is on; otherwise Enter does nothing.
     */
    private void updateDefaultButton() {
        JRootPane root = getRootPane();
        if (root != null && buttonBar != null) {
            JButton button = page == Page.DATA ? next : controls.createButton();
            root.setDefaultButton(button.isEnabled() ? button : null);
        }
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

    /** «Escribe un título para la tabla.» under the title on page «Crear», or empty. */
    String titleProblemText() {
        return titleProblem.isVisible() ? titleProblem.getText() : "";
    }

    TablePreviewModel previewModel() {
        return previewModel;
    }

    TablePreview preview() {
        return preview;
    }

    String pageHeading() {
        return heading.getText();
    }

    JButton nextButton() {
        return next;
    }

    JButton backButton() {
        return back;
    }

    /** «Siguiente»'s reason when it is off (its tooltip), or empty. */
    String nextReasonText() {
        String reason = next.getToolTipText();
        return reason == null ? "" : reason;
    }

    String noticeText() {
        return notice.isVisible() ? notice.getText() : "";
    }

    String summaryText() {
        return summary.getText();
    }

    /** Why «Crear» is off for the circuit's size, or empty. */
    String gateLimitText() {
        return gateLimit.isVisible() ? gateLimit.getText() : "";
    }

    /** Whether page «Datos» shows the suggestion of the Karnaugh wizard. */
    boolean wizardHintShown() {
        return wizardHint.isVisible() && page == Page.DATA;
    }

    String wizardHintText() {
        return hintText.getText();
    }

    JButton wizardHintButton() {
        return hintButton;
    }

    String helpText() {
        return help.getText();
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

    JButton createButton() {
        return controls.createButton();
    }

    List<String> buttonBarTexts() {
        List<String> texts = new ArrayList<>();
        for (Component each : buttonBar.getComponents()) {
            if (each instanceof AbstractButton button && button.isVisible()) {
                texts.add(button.getText());
            }
        }
        return texts;
    }

    JButton wizardButton() {
        return wizard;
    }

    /** Why «Abrir en el asistente» is off (its tooltip), or empty. */
    String wizardReasonText() {
        String reason = wizard.getToolTipText();
        return reason == null ? "" : reason;
    }

    JButton closeButton() {
        return close;
    }

    AbstractButton gateButton(WizardOptions.GateChoice choice) {
        return gateButtons.get(choice);
    }

    /**
     * Sizes the dialog to its content: the larger of the two pages (so changing page does not make it jump), with the
     * preview as tall as its rows up to {@value TablePreview#VISIBLE_ROWS}. Called when it opens and each time a
     * table is read; never smaller than {@link #MINIMUM_DIALOG_SIZE} or than the size the user gave it, never larger
     * than the screen. Nothing happens while the panel is not in a dialog.
     */
    void fitWindow() {
        if (!(SwingUtilities.getWindowAncestor(this) instanceof JDialog dialog)) {
            return;
        }
        Dimension current = dialog.getSize();
        if (fittedSize != null && !current.equals(fittedSize)) {
            userSize = current;
        }
        Rectangle screen = usableScreen(dialog);
        Dimension size = fittedSize(dialog.getPreferredSize(), userSize, screen.getSize());
        fittedSize = size;
        dialog.setSize(size);
        int x = Math.clamp(dialog.getX(), screen.x, Math.max(screen.x, screen.x + screen.width - size.width));
        int y = Math.clamp(dialog.getY(), screen.y, Math.max(screen.y, screen.y + screen.height - size.height));
        if (x != dialog.getX() || y != dialog.getY()) {
            dialog.setLocation(x, y);
        }
    }

    /**
     * The dialog's size for content that asks for {@code preferred}: at least {@link #MINIMUM_DIALOG_SIZE} and the
     * size the user resized it to ({@code user}, null when they did not), at most {@code screen}.
     */
    static Dimension fittedSize(Dimension preferred, Dimension user, Dimension screen) {
        int width = Math.max(preferred.width, MINIMUM_DIALOG_SIZE.width);
        int height = Math.max(preferred.height, MINIMUM_DIALOG_SIZE.height);
        if (user != null) {
            width = Math.max(width, user.width);
            height = Math.max(height, user.height);
        }
        return new Dimension(Math.min(width, screen.width), Math.min(height, screen.height));
    }

    /** The screen the dialog is on, without the task bars. */
    private static Rectangle usableScreen(Window window) {
        GraphicsConfiguration configuration = window.getGraphicsConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top, bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }

    /**
     * Shows {@code panel} in a dialog over {@code parent}; «Cerrar», Escape and the close box all go through
     * {@link #requestClose()}. Like the wizard's, it blocks only its own window.
     */
    public static void showDialog(Component parent, TableImportPanel panel) {
        JDialog dialog = WizardDialogs.create(parent, Messages.text("tableImport.title"), panel,
                panel::requestClose);
        panel.setOnClose(dialog::dispose);
        panel.updateDefaultButton();
        dialog.pack();
        panel.fitWindow();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
