package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.TextFonts;
import net.miginfocom.swing.MigLayout;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The content of «Exportar a PDF» (spec §3.3, §3.5, §3.6): the options on the left in three short sections («Qué
 * exportar», «Papel y escala», «Apariencia»), the {@link PagePreview} on the right with the sheet count and the
 * legibility warning under it, «Actualizar», the note on printing and «Guardar PDF…» / «Cancelar».
 *
 * <p>Everything is painted from a frozen {@link PrintInput}; «Actualizar» takes a new one. The counts follow every
 * change at once; the preview is rebuilt on a short coalescing timer, so only the last option set is painted. Saving
 * confirms above {@link #CONFIRM_ABOVE_PAGES} sheets, is off above {@link #REFUSE_ABOVE_PAGES}, asks for the file,
 * writes off the EDT with a progress bar («Cancelar» stops it) and remembers the options only when the PDF was
 * written. EDT only; tests build it without showing it.
 */
public final class PdfExportDialog extends JPanel {

    public static final int CONFIRM_ABOVE_PAGES = PdfExport.CONFIRM_ABOVE_PAGES;
    public static final int REFUSE_ABOVE_PAGES = PdfExport.REFUSE_ABOVE_PAGES;
    /** Text on paper under this many points is hard to read (spec §3.3). */
    static final double MINIMUM_LEGIBLE_POINTS = 6;
    static final int PREVIEW_DELAY_MILLISECONDS = 150;

    /**
     * What the dialog needs from outside, all replaceable in tests: the PDF writer, the file chooser, a yes/no
     * question, the error sink, the factory of the preview's coalescing timer and where to run work on the EDT (the
     * exporter reports from its own thread).
     */
    public record Collaborators(PdfExporter exporter, PdfFileChooser chooser, BiPredicate<Component, String> confirm,
                                BiConsumer<Component, String> messages, Function<Runnable, Timer> timerFactory,
                                Executor edt) {

        public Collaborators {
            Objects.requireNonNull(exporter, "exporter");
            Objects.requireNonNull(chooser, "chooser");
            Objects.requireNonNull(confirm, "confirm");
            Objects.requireNonNull(messages, "messages");
            Objects.requireNonNull(timerFactory, "timerFactory");
            Objects.requireNonNull(edt, "edt");
        }

        /** The real ones: Swing dialogs, a one-shot timer of {@link #PREVIEW_DELAY_MILLISECONDS}, the EDT. */
        public static Collaborators swing(PdfExporter exporter) {
            return new Collaborators(exporter, PdfFileChooser.swing(),
                    (parent, message) -> JOptionPane.showConfirmDialog(parent, message,
                            Messages.text("print.title"), JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION,
                    (parent, message) -> JOptionPane.showMessageDialog(parent, message, null,
                            JOptionPane.ERROR_MESSAGE),
                    action -> {
                        Timer timer = new Timer(PREVIEW_DELAY_MILLISECONDS, event -> action.run());
                        timer.setRepeats(false);
                        return timer;
                    },
                    SwingUtilities::invokeLater);
        }
    }

    /** Shows the dialog around a {@link PdfExportDialog}; tests keep it instead of showing it. */
    @FunctionalInterface
    public interface Presenter {

        void present(Component parent, PdfExportDialog content);

        /** A modal {@link JDialog}: Enter saves, Escape and the close box cancel. */
        static Presenter modal() {
            return (parent, content) -> {
                JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), Messages.text("print.title"),
                        JDialog.DEFAULT_MODALITY_TYPE);
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                dialog.add(content, BorderLayout.CENTER);
                dialog.getRootPane().setDefaultButton(content.saveButton());
                dialog.getRootPane().registerKeyboardAction(event -> content.cancel(),
                        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
                dialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent event) {
                        content.initialFocus().requestFocusInWindow();
                    }

                    @Override
                    public void windowClosing(WindowEvent event) {
                        content.cancel();
                    }
                });
                content.onClose(dialog::dispose);
                dialog.pack();
                dialog.setLocationRelativeTo(parent);
                dialog.setVisible(true);
                content.dispose();
            };
        }
    }

    /** The scale menu: fitted to one sheet, the usual percentages, or any percentage in the spinner. */
    enum ScaleChoice {
        FIT(0), HUNDRED(100), SEVENTY_FIVE(75), FIFTY(50), OTHER(0);

        private final int percent;

        ScaleChoice(int percent) {
            this.percent = percent;
        }

        @Override
        public String toString() {
            return switch (this) {
                case FIT -> Messages.text("print.scale.fit");
                case OTHER -> Messages.text("print.scale.other");
                default -> Messages.format("print.zoom.percent", percent);
            };
        }

        static ScaleChoice of(ScaleMode mode) {
            if (mode instanceof ScaleMode.Percentage percentage) {
                for (ScaleChoice choice : List.of(HUNDRED, SEVENTY_FIVE, FIFTY)) {
                    if (choice.percent == percentage.percent()) {
                        return choice;
                    }
                }
                return OTHER;
            }
            return FIT;
        }
    }

    /** The options, their area and their layout, computed together. */
    private record Plan(PrintOptions options, PrintArea area, PageLayout layout) {
    }

    private final Supplier<PrintInput> recapture;
    private final AppPreferences preferences;
    private final Path startDirectory;
    private final String suggestedName;
    private final Collaborators collaborators;
    private final Timer previewTimer;
    private final Map<PrintScope, Optional<PrintArea>> areas = new HashMap<>();
    private PrintInput input;
    private Optional<Plan> plan = Optional.empty();
    private Runnable close = () -> { };
    private boolean updating;
    private boolean exporting;
    private volatile boolean cancelRequested;
    private boolean disposed;
    private String valuesProblemText = "";

    private final JRadioButton scopeWhole = new JRadioButton(Messages.text("print.scope.whole"));
    private final JRadioButton scopeSelection = new JRadioButton(Messages.text("print.scope.selection"));
    private final JRadioButton scopeFrame = new JRadioButton(Messages.text("print.scope.frame"));
    private final JComboBox<FrameEntry> frameChoice = new JComboBox<>();
    private final JComboBox<Paper> paperChoice = new JComboBox<>(Paper.values());
    private final JRadioButton portrait = new JRadioButton(Messages.text("print.orientation.portrait"));
    private final JRadioButton landscape = new JRadioButton(Messages.text("print.orientation.landscape"));
    private final JComboBox<ScaleChoice> scaleChoice = new JComboBox<>(ScaleChoice.values());
    private final JSpinner percentSpinner = new JSpinner(new SpinnerNumberModel(100, ScaleMode.MINIMUM_PERCENT,
            ScaleMode.MAXIMUM_PERCENT, 5));
    private final JCheckBox withValues = new JCheckBox(Messages.text("print.withValues"));
    private final JLabel valuesProblem = new JLabel();
    private final JCheckBox blackAndWhite = new JCheckBox(Messages.text("print.blackAndWhite"));
    private final JCheckBox headerFooter = new JCheckBox(Messages.text("print.headerFooter"));
    private final JTextField titleField = new JTextField(18);
    private final JButton refresh = new JButton(Messages.text("print.refresh"));
    private final PagePreview preview = new PagePreview();
    private final JLabel status = new JLabel();
    private final JLabel warning = new JLabel();
    private final JButton split = new JButton(Messages.text("print.split"));
    private final JLabel hint = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final JButton save = new JButton(Messages.text("print.save"));
    private final JButton cancel = new JButton(Messages.text("print.cancel"));

    /**
     * @param input          the circuit frozen when the dialog opened
     * @param recapture      takes a new copy («Actualizar»)
     * @param startDirectory where the file chooser starts
     * @param suggestedName  the file name offered, without «.pdf»
     */
    public PdfExportDialog(PrintInput input, Supplier<PrintInput> recapture, AppPreferences preferences,
                           Path startDirectory, String suggestedName, Collaborators collaborators) {
        super(new MigLayout("insets dialog, gap 8", "[][grow, fill]", "[grow, fill][][]"));
        this.input = Objects.requireNonNull(input, "input");
        this.recapture = Objects.requireNonNull(recapture, "recapture");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.startDirectory = Objects.requireNonNull(startDirectory, "startDirectory");
        this.suggestedName = Objects.requireNonNull(suggestedName, "suggestedName");
        this.collaborators = Objects.requireNonNull(collaborators, "collaborators");
        this.previewTimer = collaborators.timerFactory().apply(this::refreshPreview);
        build();
        updating = true;
        try {
            applyRemembered(preferences.printOptions(input.circuitName()));
            fillFrames();
        } finally {
            updating = false;
        }
        listen();
        update();
    }

    private void build() {
        JPanel options = new JPanel(new MigLayout("wrap 2, insets 0, gap 6 4", "[][grow, fill]"));
        options.add(section("print.section.scope"), "span 2, gaptop 0");
        ButtonGroup scopes = new ButtonGroup();
        scopes.add(scopeWhole);
        scopes.add(scopeSelection);
        scopes.add(scopeFrame);
        options.add(scopeWhole, "span 2");
        options.add(scopeSelection, "span 2");
        options.add(scopeFrame);
        options.add(frameChoice);
        frameChoice.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                Component shown = super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof FrameEntry entry) {
                    setText("  ".repeat(index < 0 ? 0 : entry.depth()) + entry.displayTitle());
                }
                return shown;
            }
        });
        frameChoice.getAccessibleContext().setAccessibleName(Messages.text("print.scope.frame"));

        options.add(section("print.section.paper"), "span 2, gaptop 12");
        JLabel paperLabel = new JLabel(Messages.text("print.paper"));
        paperLabel.setLabelFor(paperChoice);
        options.add(paperLabel);
        options.add(paperChoice);
        paperChoice.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                Component shown = super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Paper paper) {
                    setText(Messages.text(paper.messageKey()));
                }
                return shown;
            }
        });
        ButtonGroup orientations = new ButtonGroup();
        orientations.add(portrait);
        orientations.add(landscape);
        options.add(new JLabel(Messages.text("print.orientation")));
        JPanel orientationRow = new JPanel(new MigLayout("insets 0", "[][]"));
        orientationRow.add(portrait);
        orientationRow.add(landscape);
        options.add(orientationRow);
        JLabel scaleLabel = new JLabel(Messages.text("print.scale"));
        scaleLabel.setLabelFor(scaleChoice);
        options.add(scaleLabel);
        JPanel scaleRow = new JPanel(new MigLayout("insets 0", "[grow, fill][]"));
        scaleRow.add(scaleChoice);
        scaleRow.add(percentSpinner);
        percentSpinner.getAccessibleContext().setAccessibleName(Messages.text("print.scale.other"));
        options.add(scaleRow);

        options.add(section("print.section.appearance"), "span 2, gaptop 12");
        options.add(withValues, "span 2");
        valuesProblem.setForeground(hintColor());
        options.add(valuesProblem, "span 2, gapleft 20, hidemode 3");
        options.add(blackAndWhite, "span 2");
        options.add(headerFooter, "span 2");
        JLabel titleLabel = new JLabel(Messages.text("print.headerTitle"));
        titleLabel.setLabelFor(titleField);
        options.add(titleLabel, "gapleft 20");
        options.add(titleField);
        options.add(refresh, "span 2, gaptop 12, growx 0");
        refresh.setToolTipText(Messages.text("print.refresh.tip"));
        add(options, "aligny top");

        JPanel right = new JPanel(new MigLayout("wrap 1, insets 0, gap 4", "[grow, fill]", "[grow, fill][][][]"));
        right.add(preview);
        right.add(status);
        JPanel warningRow = new JPanel(new MigLayout("insets 0", "[grow, fill][]"));
        warningRow.add(warning);
        warningRow.add(split);
        right.add(warningRow, "hidemode 3");
        hint.setForeground(hintColor());
        right.add(hint, "hidemode 3");
        add(right, "wrap");

        progress.setStringPainted(true);
        progress.setVisible(false);
        add(progress, "span 2, growx, hidemode 3, wrap");
        add(new JSeparator(), "span 2, growx, wrap");
        JLabel note = new JLabel(Messages.text("print.note"));
        note.setForeground(hintColor());
        add(note, "span 2, split 3, growx");
        add(save, "tag ok");
        add(cancel, "tag cancel");
        warningRow.setVisible(false);
    }

    private static JLabel section(String key) {
        JLabel label = new JLabel(Messages.text(key));
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        return label;
    }

    private static Color hintColor() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }

    private void applyRemembered(PrintOptions remembered) {
        scopeWhole.setSelected(true);
        paperChoice.setSelectedItem(remembered.paper());
        portrait.setSelected(remembered.orientation() == PageOrientation.PORTRAIT);
        landscape.setSelected(remembered.orientation() == PageOrientation.LANDSCAPE);
        scaleChoice.setSelectedItem(ScaleChoice.of(remembered.scaleMode()));
        if (remembered.scaleMode() instanceof ScaleMode.Percentage percentage) {
            percentSpinner.setValue(percentage.percent());
        }
        withValues.setSelected(remembered.withValues());
        blackAndWhite.setSelected(remembered.blackAndWhite());
        headerFooter.setSelected(remembered.headerFooter());
        titleField.setText(remembered.title());
    }

    private void fillFrames() {
        Object previous = frameChoice.getSelectedItem();
        frameChoice.removeAllItems();
        for (FrameEntry entry : input.frames()) {
            frameChoice.addItem(entry);
        }
        if (previous instanceof FrameEntry previousEntry) {
            input.frames().stream().filter(entry -> entry.id().equals(previousEntry.id())).findFirst()
                    .ifPresent(frameChoice::setSelectedItem);
        }
        if (scopeSelection.isSelected() && input.selection().isEmpty()
                || scopeFrame.isSelected() && input.frames().isEmpty()) {
            scopeWhole.setSelected(true);
        }
    }

    private void listen() {
        List.of(scopeWhole, scopeSelection, scopeFrame, portrait, landscape, withValues, blackAndWhite,
                headerFooter).forEach(button -> button.addActionListener(event -> update()));
        frameChoice.addActionListener(event -> update());
        paperChoice.addActionListener(event -> update());
        scaleChoice.addActionListener(event -> update());
        percentSpinner.addChangeListener(event -> update());
        titleField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                update();
            }
        });
        split.addActionListener(event -> scaleChoice.setSelectedItem(ScaleChoice.HUNDRED));
        refresh.addActionListener(event -> refresh());
        save.addActionListener(event -> save());
        cancel.addActionListener(event -> cancel());
    }

    /** What the controls say now. */
    PrintOptions options() {
        PrintScope scope = new PrintScope.WholeCircuit();
        if (scopeSelection.isSelected() && !input.selection().isEmpty()) {
            scope = new PrintScope.Selection(input.selection());
        } else if (scopeFrame.isSelected() && frameChoice.getSelectedItem() instanceof FrameEntry entry) {
            scope = new PrintScope.Frame(entry.id());
        }
        ScaleChoice choice = (ScaleChoice) scaleChoice.getSelectedItem();
        ScaleMode mode = switch (Objects.requireNonNull(choice)) {
            case FIT -> new ScaleMode.FitToOnePage();
            case OTHER -> new ScaleMode.Percentage((Integer) percentSpinner.getValue());
            default -> new ScaleMode.Percentage(choice.percent);
        };
        return new PrintOptions(scope, (Paper) paperChoice.getSelectedItem(),
                landscape.isSelected() ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT, mode,
                withValues.isSelected(), blackAndWhite.isSelected(), headerFooter.isSelected(), titleField.getText());
    }

    /** The area of {@code scope}, measured with the frozen font (labels change the bounds). */
    private Optional<PrintArea> area(PrintScope scope) {
        return areas.computeIfAbsent(scope, key -> TextFonts.withFont(input.textFont(),
                () -> PrintScopes.area(input, key)));
    }

    private void update() {
        if (updating || disposed) {
            return;
        }
        boolean idle = !exporting;
        scopeSelection.setEnabled(idle && !input.selection().isEmpty());
        scopeFrame.setEnabled(idle && !input.frames().isEmpty());
        frameChoice.setEnabled(idle && scopeFrame.isSelected() && !input.frames().isEmpty());
        percentSpinner.setEnabled(idle && scaleChoice.getSelectedItem() == ScaleChoice.OTHER);
        titleField.setEnabled(idle && headerFooter.isSelected());
        List.<JComponent>of(scopeWhole, paperChoice, portrait, landscape, scaleChoice, withValues, blackAndWhite,
                headerFooter, refresh, split).forEach(control -> control.setEnabled(idle));
        Optional<String> problem = withValues.isSelected() ? input.valuesProblem() : Optional.empty();
        valuesProblem.setText(problem.map(text -> "<html><body style='width: 240px'>" + escape(text)
                + "</body></html>").orElse(""));
        valuesProblem.setVisible(problem.isPresent());
        valuesProblemText = problem.orElse("");

        PrintOptions options = options();
        Optional<PrintArea> area = area(options.scope());
        if (area.isEmpty()) {
            plan = Optional.empty();
            show("", "", Messages.text("print.nothing"));
            save.setEnabled(false);
            preview.clear();
            return;
        }
        PageLayout layout = PageLayout.of(area.get().gridArea(), PageGeometry.standard(options.paper(),
                options.orientation(), options.headerFooter()), options.scaleMode());
        plan = Optional.of(new Plan(options, area.get(), layout));
        if (layout.problem().isPresent()) {
            show("", "", Messages.text(layout.problem().get()));
            save.setEnabled(false);
            previewTimer.restart();
            return;
        }
        String statusText = layout.pageCountExact() == 1
                ? Messages.format("print.status.one", layout.scale() * 100)
                : Messages.format("print.status.many", layout.columns(), layout.rows(), layout.pageCountExact(),
                layout.scale() * 100);
        double smallest = layout.smallestTextPoints(input.textFont().size());
        String warningText = options.scaleMode() instanceof ScaleMode.FitToOnePage
                && smallest < MINIMUM_LEGIBLE_POINTS ? Messages.format("print.warning.smallText", smallest) : "";
        boolean tooMany = layout.pageCountExact() > REFUSE_ABOVE_PAGES;
        show(statusText, warningText, tooMany ? Messages.format("print.tooManyPages", REFUSE_ABOVE_PAGES) : "");
        save.setEnabled(idle && !tooMany);
        previewTimer.restart();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void show(String statusText, String warningText, String hintText) {
        status.setText(statusText);
        warning.setText(warningText);
        warning.getParent().setVisible(!warningText.isEmpty());
        hint.setText(hintText);
        hint.setVisible(!hintText.isEmpty());
        revalidate();
    }

    /** The coalescing timer fired: paint the last option set, whatever came before it. */
    private void refreshPreview() {
        if (disposed) {
            return;
        }
        Optional<Plan> current = plan;
        if (current.isEmpty() || current.get().layout().problem().isPresent()) {
            preview.clear();
            return;
        }
        preview.show(painterOf(current.get()), current.get().layout());
    }

    private PagePainter painterOf(Plan chosen) {
        return new PagePainter(input, chosen.area(), chosen.options(), chosen.layout());
    }

    /** «Actualizar»: a new copy of the circuit as it is now. */
    void refresh() {
        if (exporting || disposed) {
            return;
        }
        input = recapture.get();
        areas.clear();
        updating = true;
        try {
            fillFrames();
        } finally {
            updating = false;
        }
        update();
    }

    /** «Guardar PDF…». */
    void save() {
        if (exporting || disposed || plan.isEmpty() || !save.isEnabled()) {
            return;
        }
        Plan chosen = plan.get();
        long count = chosen.layout().pageCountExact();
        if (count > REFUSE_ABOVE_PAGES) {
            return;
        }
        if (count > CONFIRM_ABOVE_PAGES
                && !collaborators.confirm().test(this, Messages.format("print.confirm.manyPages", count))) {
            return;
        }
        Optional<Path> target = collaborators.chooser().choose(this, startDirectory, suggestedName);
        if (target.isEmpty()) {
            return;
        }
        PagePainter painter = painterOf(chosen);
        int total = painter.pages().size();
        exporting = true;
        cancelRequested = false;
        progress.setMinimum(0);
        progress.setMaximum(total);
        progress.setValue(0);
        progress.setString(Messages.format("print.progress", 0, total));
        progress.setVisible(true);
        update();
        PdfExporter.ProgressListener listener = new PdfExporter.ProgressListener() {
            @Override
            public void pageDone(int done, int pages) {
                collaborators.edt().execute(() -> {
                    if (!cancelRequested) {
                        progress.setValue(done);
                        progress.setString(Messages.format("print.progress", done, pages));
                    }
                });
            }

            @Override
            public boolean cancelled() {
                return cancelRequested;
            }
        };
        Path file = target.get();
        collaborators.exporter().export(painter, file, listener).whenComplete((ignored, failure) ->
                collaborators.edt().execute(() -> finished(chosen.options(), file, failure)));
    }

    private void finished(PrintOptions options, Path file, Throwable failure) {
        exporting = false;
        progress.setVisible(false);
        cancel.setEnabled(true);
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause == null) {
            preferences.setPrintOptions(options);
            Path folder = file.toAbsolutePath().getParent();
            if (folder != null) {
                preferences.setPrintDirectory(folder);
            }
            close.run();
            return;
        }
        if (!(cause instanceof CancellationException)) {
            String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            collaborators.messages().accept(this, Messages.format("export.failed", file.getFileName(), reason));
        }
        update();
    }

    /** «Cancelar», Escape or the close box: stops a running export, otherwise closes without saving anything. */
    void cancel() {
        if (exporting) {
            cancelRequested = true;
            cancel.setEnabled(false);
            progress.setString(Messages.text("print.cancelling"));
            return;
        }
        close.run();
    }

    /** What closes the window around this content. */
    public void onClose(Runnable closer) {
        this.close = Objects.requireNonNull(closer, "closer");
    }

    /** The first option, focused when the dialog opens. */
    JComponent initialFocus() {
        return scopeWhole;
    }

    /** Stops the preview timer; an export still running keeps going to its end. */
    public void dispose() {
        disposed = true;
        previewTimer.stop();
    }

    // --- for tests ---

    Optional<PageLayout> pageLayout() {
        return plan.map(Plan::layout);
    }

    boolean exporting() {
        return exporting;
    }

    String statusText() {
        return status.getText();
    }

    boolean warningVisible() {
        return warning.getParent().isVisible();
    }

    String warningText() {
        return warning.getText();
    }

    String hintText() {
        return hint.isVisible() ? hint.getText() : "";
    }

    String valuesProblemText() {
        return valuesProblem.isVisible() ? valuesProblemText : "";
    }

    PagePreview preview() {
        return preview;
    }

    JRadioButton scopeWhole() {
        return scopeWhole;
    }

    JRadioButton scopeSelection() {
        return scopeSelection;
    }

    JRadioButton scopeFrame() {
        return scopeFrame;
    }

    JComboBox<FrameEntry> frameChoice() {
        return frameChoice;
    }

    JComboBox<Paper> paperChoice() {
        return paperChoice;
    }

    JRadioButton landscape() {
        return landscape;
    }

    JComboBox<ScaleChoice> scaleChoice() {
        return scaleChoice;
    }

    JSpinner percentSpinner() {
        return percentSpinner;
    }

    JCheckBox withValues() {
        return withValues;
    }

    JCheckBox blackAndWhite() {
        return blackAndWhite;
    }

    JCheckBox headerFooter() {
        return headerFooter;
    }

    JTextField titleField() {
        return titleField;
    }

    JButton splitButton() {
        return split;
    }

    JButton refreshButton() {
        return refresh;
    }

    JProgressBar progressBar() {
        return progress;
    }

    JButton saveButton() {
        return save;
    }

    JButton cancelButton() {
        return cancel;
    }
}
