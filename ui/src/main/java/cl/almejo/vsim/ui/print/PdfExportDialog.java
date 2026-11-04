package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.panels.TextChanges;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.awt.BorderLayout;
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
 * confirms above {@link PdfExport#CONFIRM_ABOVE_PAGES} sheets, is off above {@link PdfExport#REFUSE_ABOVE_PAGES},
 * asks for the file, writes off the EDT with a progress bar («Cancelar» stops it) and remembers the options only
 * when the PDF was written. EDT only; tests build it without showing it.
 *
 * <p>In {@link Mode#PRINT} (spec 2026-10-19 §4) «Imprimir…» is the default button and «Guardar PDF…» the second
 * choice. «Imprimir…» opens the printer's dialog on the paper and orientation shown; when the printer answers with
 * the same sheet (paper, orientation and a margin of 1 cm or the printer's wider hardware margin) the sheets shown go
 * to it, with the same progress and «Cancelar» as the PDF. When it answers with another one nothing is printed: the
 * controls switch to the printer's paper, the preview and the count are redone with its margin, a hint says so, and
 * the next «Imprimir…» asks again; «Guardar PDF…» then uses that same sheet. «Imprimir…» is off while the system
 * says no printer is installed.
 */
public final class PdfExportDialog extends JPanel {

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
                                Executor edt, PrinterAccess printers) {

        public Collaborators {
            Objects.requireNonNull(exporter, "exporter");
            Objects.requireNonNull(chooser, "chooser");
            Objects.requireNonNull(confirm, "confirm");
            Objects.requireNonNull(messages, "messages");
            Objects.requireNonNull(timerFactory, "timerFactory");
            Objects.requireNonNull(edt, "edt");
            Objects.requireNonNull(printers, "printers");
        }

        /** Without printers: enough for «Exportar a PDF…». */
        public Collaborators(PdfExporter exporter, PdfFileChooser chooser, BiPredicate<Component, String> confirm,
                             BiConsumer<Component, String> messages, Function<Runnable, Timer> timerFactory,
                             Executor edt) {
            this(exporter, chooser, confirm, messages, timerFactory, edt, PrinterAccess.none());
        }

        /** The real ones without printers. */
        public static Collaborators swing(PdfExporter exporter) {
            return swing(exporter, PrinterAccess.none());
        }

        /** The real ones: Swing dialogs, a one-shot timer of {@link #PREVIEW_DELAY_MILLISECONDS}, the EDT. */
        public static Collaborators swing(PdfExporter exporter, PrinterAccess printers) {
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
                    SwingUtilities::invokeLater, printers);
        }
    }

    /** What the dialog is for: «Exportar a PDF…», or «Imprimir…» with «Guardar PDF…» as the second choice. */
    public enum Mode {
        PDF, PRINT
    }

    /** Shows the dialog around a {@link PdfExportDialog}; tests keep it instead of showing it. */
    @FunctionalInterface
    public interface Presenter {

        void present(Component parent, PdfExportDialog content);

        /** A modal {@link JDialog}: Enter saves (prints, to print), Escape and the close box cancel. */
        static Presenter modal() {
            return (parent, content) -> {
                JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), content.title(),
                        JDialog.DEFAULT_MODALITY_TYPE);
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                dialog.add(content, BorderLayout.CENTER);
                dialog.getRootPane().setDefaultButton(content.defaultButton());
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
                content.onContentGrew(() -> growToFit(dialog));
                dialog.pack();
                dialog.setLocationRelativeTo(parent);
                dialog.setVisible(true);
                content.dispose();
            };
        }
    }

    /** Makes {@code dialog} as large as its content now asks, within the screen; it never shrinks. */
    private static void growToFit(JDialog dialog) {
        if (!dialog.isDisplayable()) {
            return;
        }
        java.awt.GraphicsConfiguration screen = dialog.getGraphicsConfiguration();
        java.awt.Rectangle usable = screen.getBounds();
        java.awt.Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(screen);
        usable = new java.awt.Rectangle(usable.x + insets.left, usable.y + insets.top,
                usable.width - insets.left - insets.right, usable.height - insets.top - insets.bottom);
        java.awt.Rectangle grown = WindowGrowth.grown(dialog.getBounds(), dialog.getPreferredSize(), usable);
        if (!grown.equals(dialog.getBounds())) {
            dialog.setBounds(grown);
            dialog.validate();
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

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfExportDialog.class);

    /** The options, their area and their layout, computed together. */
    private record Plan(PrintOptions options, PrintArea area, PageLayout layout) {
    }

    private final Mode mode;
    private final Supplier<PrintInput> recapture;
    private final AppPreferences preferences;
    private final Path startDirectory;
    private final String suggestedName;
    private final Collaborators collaborators;
    private final Timer previewTimer;
    private final Map<AreaKey, Optional<PrintArea>> areas = new HashMap<>();
    private PrintInput input;
    private Optional<Plan> plan = Optional.empty();
    private TextSizeKey textSizeKey;
    private double smallestText;
    private Runnable close = () -> { };
    /** Told after a notice, a warning or the status changed, so the window can grow to the content. */
    private Runnable contentGrew = () -> { };
    private boolean updating;
    private boolean exporting;
    private volatile boolean cancelRequested;
    private boolean disposed;
    private String valuesProblemText = "";
    /** The blank border of every sheet: 1 cm, or the wider hardware margin of the printer that answered last. */
    private double marginPoints = PageGeometry.CENTIMETRE_POINTS;
    /** What the printer's last answer asks the user to check; shown when no other hint is. */
    private Notice printerNotice = Notice.NONE;
    /** The paper the printer prints on unless told otherwise, once it has said so. */
    private Optional<Paper> printerDefaultPaper = Optional.empty();
    /** The paper and orientation the printer's last answer switched to, while the hint is about them. */
    private Optional<PrintSetup> printerSwitch = Optional.empty();
    /** Whether the user changed the paper by hand: then the printer's default paper is no longer ours to apply. */
    private boolean paperTouched;
    /** False once the system said no printer is installed; true until it answers. */
    private boolean printersAvailable = true;

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
    private final JCheckBox includeStickyNotes = new JCheckBox(Messages.text("print.includeStickyNotes"));
    private final JCheckBox headerFooter = new JCheckBox(Messages.text("print.headerFooter"));
    private final JTextField titleField = new JTextField(18);
    private final JCheckBox showDate = new JCheckBox(Messages.text("print.headerDate"));
    private final JCheckBox showFileName = new JCheckBox(Messages.text("print.headerFileName"));
    private final JCheckBox showSheetNumber = new JCheckBox(Messages.text("print.headerSheetNumber"));
    private final JCheckBox sheetGuides = new JCheckBox(Messages.text("print.sheetGuides"));
    private final JButton refresh = new JButton(Messages.text("print.refresh"));
    private final PagePreview preview = new PagePreview();
    private final JLabel status = new JLabel();
    private final JLabel warning = new JLabel();
    private final JButton split = new JButton(Messages.text("print.split"));
    /** «Ajustar a una hoja» next to the sheet count: the way back after «Dividir en varias hojas». */
    private final JButton fit = new JButton(Messages.text("print.scale.fit"));
    private final NoticeLabel hint = new NoticeLabel();
    private final JProgressBar progress = new JProgressBar();
    private final JButton save = new JButton(Messages.text("print.save"));
    private final JButton printButton = new JButton(Messages.text("print.print"));
    private final JButton cancel = new JButton(Messages.text("print.cancel"));

    /**
     * @param input          the circuit frozen when the dialog opened
     * @param recapture      takes a new copy («Actualizar»)
     * @param startDirectory where the file chooser starts
     * @param suggestedName  the file name offered, without «.pdf»
     */
    public PdfExportDialog(PrintInput input, Supplier<PrintInput> recapture, AppPreferences preferences,
                           Path startDirectory, String suggestedName, Collaborators collaborators) {
        this(Mode.PDF, input, recapture, preferences, startDirectory, suggestedName, collaborators);
    }

    /** Like the other constructor, for {@code mode}. */
    public PdfExportDialog(Mode mode, PrintInput input, Supplier<PrintInput> recapture, AppPreferences preferences,
                           Path startDirectory, String suggestedName, Collaborators collaborators) {
        super(new MigLayout("insets dialog, gap 8", "[][grow, fill]", "[grow, fill][][]"));
        this.mode = Objects.requireNonNull(mode, "mode");
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
        if (mode == Mode.PRINT) {
            lookUpPrinters();
            lookUpDefaultPaper();
        }
    }

    private void build() {
        JPanel options = new JPanel(new MigLayout("wrap 2, insets 0, gap 6 4", "[][grow, fill]"));
        buildScope(options);
        buildPaper(options);
        buildScale(options);
        buildOptions(options);
        add(options, "aligny top");
        buildPreviewColumn();
    }

    /** The «scope» section: whole circuit, selection or one frame. */
    private void buildScope(JPanel options) {
        options.add(section(mode == Mode.PRINT ? "print.section.scope.print" : "print.section.scope"),
                "span 2, gaptop 0");
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
    }

    /** The «paper» section: paper size and orientation. */
    private void buildPaper(JPanel options) {
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
    }

    /** The scale row of the paper section: a choice plus the custom percentage. */
    private void buildScale(JPanel options) {
        JLabel scaleLabel = new JLabel(Messages.text("print.scale"));
        scaleLabel.setLabelFor(scaleChoice);
        options.add(scaleLabel);
        JPanel scaleRow = new JPanel(new MigLayout("insets 0", "[grow, fill][]"));
        scaleRow.add(scaleChoice);
        scaleRow.add(percentSpinner);
        percentSpinner.getAccessibleContext().setAccessibleName(Messages.text("print.scale.other"));
        options.add(scaleRow);
    }

    /** The «appearance» section and the refresh button. */
    private void buildOptions(JPanel options) {
        options.add(section("print.section.appearance"), "span 2, gaptop 12");
        options.add(withValues, "span 2");
        hinted(valuesProblem);
        options.add(valuesProblem, "span 2, gapleft 20, hidemode 3");
        options.add(blackAndWhite, "span 2");
        options.add(includeStickyNotes, "span 2");
        options.add(headerFooter, "span 2");
        JLabel titleLabel = new JLabel(Messages.text("print.headerTitle"));
        titleLabel.setLabelFor(titleField);
        options.add(titleLabel, "gapleft 20");
        options.add(titleField);
        options.add(showDate, "span 2, gapleft 20, hidemode 3");
        options.add(showFileName, "span 2, gapleft 20, hidemode 3");
        options.add(showSheetNumber, "span 2, gapleft 20, hidemode 3");
        options.add(sheetGuides, "span 2, hidemode 3");
        options.add(refresh, "span 2, gaptop 12, growx 0");
        refresh.setToolTipText(Messages.text("print.refresh.tip"));
    }

    /** The preview column, the progress bar and the bottom button row. */
    private void buildPreviewColumn() {
        JPanel right = new JPanel(new MigLayout("wrap 1, insets 0, gap 4", "[grow, fill]", "[grow, fill][][][]"));
        right.add(preview);
        JPanel statusRow = new JPanel(new MigLayout("insets 0", "[grow, fill][]"));
        statusRow.add(status);
        statusRow.add(fit, "hidemode 3");
        fit.setVisible(false);
        right.add(statusRow);
        JPanel warningRow = new JPanel(new MigLayout("insets 0", "[grow, fill][]"));
        warningRow.add(warning);
        warningRow.add(split);
        right.add(warningRow, "hidemode 3");
        right.add(hint, "hidemode 3");
        add(right, "wrap");

        progress.setStringPainted(true);
        progress.setVisible(false);
        add(progress, "span 2, growx, hidemode 3, wrap");
        add(new JSeparator(), "span 2, growx, wrap");
        if (mode == Mode.PRINT) {
            add(save, "span 2, split 3, tag other");
            add(printButton, "tag ok");
        } else {
            JLabel note = new JLabel(Messages.text("print.note"));
            hinted(note);
            add(note, "span 2, split 3, growx");
            add(save, "tag ok");
        }
        add(cancel, "tag cancel");
        warningRow.setVisible(false);
    }

    private static JLabel section(String key) {
        JLabel label = new JLabel(Messages.text(key));
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        return label;
    }

    /** What the line under the preview says besides the sheet count: its look and its text. */
    private record Notice(NoticeLabel.Kind kind, String text) {
        static final Notice NONE = new Notice(NoticeLabel.Kind.HINT, "");

        static Notice hint(String text) {
            return new Notice(NoticeLabel.Kind.HINT, text);
        }

        static Notice info(String text) {
            return new Notice(NoticeLabel.Kind.INFO, text);
        }

        static Notice warning(String text) {
            return new Notice(NoticeLabel.Kind.WARNING, text);
        }
    }

    /** A hint's dimmed text colour, from the theme each time the look changes (FlatLaf resolves the style then). */
    private static void hinted(JLabel label) {
        label.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
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
        includeStickyNotes.setSelected(remembered.includeStickyNotes());
        headerFooter.setSelected(remembered.headerFooter());
        titleField.setText(remembered.title());
        sheetGuides.setSelected(remembered.sheetGuides());
        showDate.setSelected(remembered.showDate());
        showFileName.setSelected(remembered.showFileName());
        showSheetNumber.setSelected(remembered.showSheetNumber());
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
                includeStickyNotes, headerFooter, showDate, showFileName, showSheetNumber, sheetGuides)
                .forEach(button -> button.addActionListener(event -> update()));
        frameChoice.addActionListener(event -> update());
        paperChoice.addActionListener(event -> {
            paperTouched |= !updating;
            update();
        });
        scaleChoice.addActionListener(event -> update());
        percentSpinner.addChangeListener(event -> update());
        TextChanges.onChange(titleField, this::update);
        split.addActionListener(event -> scaleChoice.setSelectedItem(ScaleChoice.HUNDRED));
        fit.addActionListener(event -> scaleChoice.setSelectedItem(ScaleChoice.FIT));
        refresh.addActionListener(event -> refresh());
        save.addActionListener(event -> save());
        printButton.addActionListener(event -> print());
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
                withValues.isSelected(), blackAndWhite.isSelected(), headerFooter.isSelected(), titleField.getText(),
                includeStickyNotes.isSelected(), sheetGuides.isSelected(), showDate.isSelected(),
                showFileName.isSelected(), showSheetNumber.isSelected());
    }

    /** The area of {@code scope}, measured with the frozen font (labels change the bounds). */
    private Optional<PrintArea> area(PrintScope scope) {
        boolean includeStickyNotes = this.includeStickyNotes.isSelected();
        return areas.computeIfAbsent(new AreaKey(scope, includeStickyNotes), key -> TextFonts.withFont(
                input.textFont(), () -> PrintScopes.area(input, key.scope(), key.includeStickyNotes())));
    }

    /** What an area depends on: the scope, and whether the sticky notes count. */
    private record AreaKey(PrintScope scope, boolean includeStickyNotes) {
    }

    private void update() {
        if (updating || disposed) {
            return;
        }
        boolean idle = !exporting;
        enableControls(idle);
        showValuesProblem();

        PrintOptions options = options();
        dropPrinterHintWhenAnotherSheetIsShown(options);
        fit.setVisible(false);
        Optional<PrintArea> area = area(options.scope());
        if (area.isEmpty()) {
            plan = Optional.empty();
            show("", "", Notice.hint(Messages.text(mode == Mode.PRINT ? "print.nothing.print" : "print.nothing")));
            enableOutputs(false);
            preview.clear();
            return;
        }
        PageLayout layout = PageLayout.of(area.get().gridArea(), PageGeometry.standard(options, marginPoints),
                options.scaleMode());
        plan = Optional.of(new Plan(options, area.get(), layout));
        sheetGuides.setVisible(layout.tiled());
        if (layout.problem().isPresent()) {
            show("", "", Notice.hint(Messages.text(layout.problem().get())));
            enableOutputs(false);
            previewTimer.restart();
            return;
        }
        String statusText = layout.pageCountExact() == 1
                ? Messages.format("print.status.one", layout.scale() * 100)
                : Messages.format("print.status.many", layout.columns(), layout.rows(), layout.pageCountExact(),
                layout.scale() * 100);
        double smallest = layout.smallestTextPoints(smallestTextPixels(area.get(), options));
        String warningText = options.scaleMode() instanceof ScaleMode.FitToOnePage && layout.scale() < 1
                && smallest < MINIMUM_LEGIBLE_POINTS ? Messages.format("print.warning.smallText", smallest) : "";
        boolean tooMany = layout.pageCountExact() > PdfExport.REFUSE_ABOVE_PAGES;
        fit.setVisible(layout.pageCountExact() > 1);
        show(statusText, warningText, tooMany
                ? Notice.hint(Messages.format("print.tooManyPages", PdfExport.REFUSE_ABOVE_PAGES))
                : paperMismatch(options));
        enableOutputs(idle && !tooMany);
        previewTimer.restart();
    }

    /** «Guardar PDF…» and «Imprimir…» by whether the sheets can go out; printing also needs a printer. */
    private void enableOutputs(boolean sheetsCanGoOut) {
        save.setEnabled(sheetsCanGoOut);
        printButton.setEnabled(sheetsCanGoOut && printersAvailable);
        printButton.setToolTipText(printersAvailable ? null : Messages.text("print.noPrinters"));
    }

    /**
     * «La impresora usa Carta…» speaks of the sheet shown after the switch: once the user picks another paper or
     * orientation by hand, it no longer does.
     */
    private void dropPrinterHintWhenAnotherSheetIsShown(PrintOptions options) {
        if (printerSwitch.isPresent() && (printerSwitch.get().paper() != options.paper()
                || printerSwitch.get().orientation() != options.orientation())) {
            printerSwitch = Optional.empty();
            printerNotice = Notice.NONE;
        }
    }

    /**
     * When printing on a paper other than the printer's default: a sheet of the wrong size may come out cut, so say
     * so before the user presses «Imprimir…». Nothing while the printer has not said its paper, or to save a PDF.
     */
    private Notice paperMismatch(PrintOptions options) {
        if (mode != Mode.PRINT || printerDefaultPaper.isEmpty() || printerDefaultPaper.get() == options.paper()) {
            return Notice.NONE;
        }
        return Notice.warning(Messages.format("print.printer.paperMismatch",
                Messages.text(printerDefaultPaper.get().shortMessageKey()),
                Messages.text(options.paper().shortMessageKey())));
    }

    /** Enables each control by whether an export is running and by what the circuit and the choices allow. */
    private void enableControls(boolean idle) {
        scopeSelection.setEnabled(idle && !input.selection().isEmpty());
        scopeFrame.setEnabled(idle && !input.frames().isEmpty());
        frameChoice.setEnabled(idle && scopeFrame.isSelected() && !input.frames().isEmpty());
        percentSpinner.setEnabled(idle && scaleChoice.getSelectedItem() == ScaleChoice.OTHER);
        titleField.setEnabled(idle && headerFooter.isSelected());
        List.of(showDate, showFileName, showSheetNumber).forEach(item -> {
            item.setVisible(headerFooter.isSelected());
            item.setEnabled(idle);
        });
        List.<JComponent>of(scopeWhole, paperChoice, portrait, landscape, scaleChoice, withValues, blackAndWhite,
                includeStickyNotes, headerFooter, sheetGuides, refresh, split, fit).forEach(control -> control.setEnabled(idle));
    }

    /** Shows (or hides) why the values cannot be printed, when «with values» is on. */
    private void showValuesProblem() {
        Optional<String> problem = withValues.isSelected() ? input.valuesProblem() : Optional.empty();
        valuesProblem.setText(problem.map(text -> "<html><body style='width: 240px'>" + escape(text)
                + "</body></html>").orElse(""));
        valuesProblem.setVisible(problem.isPresent());
        valuesProblemText = problem.orElse("");
    }

    /**
     * The smallest text the circuit paints, in canvas pixels ({@link PagePainter#smallestTextPixels}), or the label
     * font's size when it paints none; remembered for the last copy, area and look, since only those change it.
     */
    private double smallestTextPixels(PrintArea area, PrintOptions options) {
        TextSizeKey key = new TextSizeKey(input, area, options.withValues(), options.blackAndWhite(),
                options.includeStickyNotes());
        if (!key.equals(textSizeKey)) {
            textSizeKey = key;
            smallestText = PagePainter.smallestTextPixels(input, area, options).orElse(input.textFont().size());
        }
        return smallestText;
    }

    private record TextSizeKey(PrintInput input, PrintArea area, boolean withValues, boolean blackAndWhite,
                               boolean includeStickyNotes) {
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void show(String statusText, String warningText, Notice notice) {
        status.setText(statusText);
        warning.setText(warningText);
        warning.getParent().setVisible(!warningText.isEmpty());
        Notice shown = !notice.text().isEmpty() ? notice : printerNotice;
        hint.notice(shown.kind(), shown.text());
        hint.setVisible(!shown.text().isEmpty());
        revalidate();
        contentGrew.run();
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
        if (!sheetCountAccepted(chosen)) {
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
        showProgress(total);
        update();
        PdfExporter.ProgressListener listener = progressListener();
        Path file = target.get();
        double laidOutMargin = marginPoints;
        collaborators.exporter().export(painter, file, listener).whenComplete((ignored, failure) ->
                collaborators.edt().execute(() -> finished(chosen.options(), laidOutMargin, file, failure)));
    }

    /**
     * Whether the sheets of {@code chosen} may go out: never above {@link PdfExport#REFUSE_ABOVE_PAGES}, and above
     * {@link PdfExport#CONFIRM_ABOVE_PAGES} only when the user says so.
     */
    private boolean sheetCountAccepted(Plan chosen) {
        long count = chosen.layout().pageCountExact();
        if (count > PdfExport.REFUSE_ABOVE_PAGES) {
            return false;
        }
        return count <= PdfExport.CONFIRM_ABOVE_PAGES
                || collaborators.confirm().test(this, Messages.format("print.confirm.manyPages", count));
    }

    /**
     * «Imprimir…»: asks the printer's dialog, then prints the sheets shown when it answers with the sheet shown, or
     * switches to the sheet it answered with and waits for the next press.
     */
    void print() {
        if (mode != Mode.PRINT || exporting || disposed || plan.isEmpty() || !printButton.isEnabled()) {
            return;
        }
        Plan chosen = plan.get();
        if (!sheetCountAccepted(chosen)) {
            return;
        }
        PrintSetup requested = new PrintSetup(chosen.options().paper(), chosen.options().orientation(),
                marginPoints);
        printerNotice = Notice.NONE;
        printerSwitch = Optional.empty();
        switch (collaborators.printers().choose(this, requested, suggestedName)) {
            case PrinterChoice.Declined declined -> update();
            case PrinterChoice.UnknownPaper unknown -> {
                printerNotice = Notice.warning(Messages.format("print.printer.unknownPaper", unknown.name()));
                update();
            }
            case PrinterChoice.Chosen accepted when keptOurPaperOnAnotherDefault(accepted, requested) ->
                    adoptPrintersPaper(accepted.printerDefault().orElseThrow());
            case PrinterChoice.Chosen accepted when accepted.effective().matches(chosen.options(), marginPoints) -> {
                accepted.printerDefault().filter(printerPaper -> printerPaper != accepted.effective().paper())
                        .filter(printerPaper -> !printerDefaultPaper.equals(Optional.of(printerPaper)))
                        .ifPresent(printerPaper -> printerNotice = Notice.warning(
                                Messages.format("print.printer.defaultDiffers",
                                        Messages.text(printerPaper.shortMessageKey()))));
                startPrinting(chosen, accepted);
            }
            case PrinterChoice.Chosen accepted -> switchTo(accepted.effective());
        }
    }

    /**
     * Java's dialog cannot say whether the user picked the paper it returns or just left the one it was given, so
     * the paper counts as «kept» when it is what we asked for and the user never chose it here: it was only our
     * guess, and the chosen printer defaults to another paper.
     */
    private boolean keptOurPaperOnAnotherDefault(PrinterChoice.Chosen accepted, PrintSetup requested) {
        return !paperTouched && accepted.effective().paper() == requested.paper()
                && accepted.printerDefault().filter(printerPaper -> printerPaper != requested.paper()).isPresent();
    }

    /** Shows {@code paper}, the printer's, in place of the dialog's, and says so; prints nothing. */
    private void adoptPrintersPaper(Paper paper) {
        updating = true;
        try {
            paperChoice.setSelectedItem(paper);
        } finally {
            updating = false;
        }
        PageOrientation orientation = landscape.isSelected() ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT;
        printerSwitch = Optional.of(new PrintSetup(paper, orientation, marginPoints));
        printerNotice = Notice.info(Messages.format("print.printer.defaultPaper",
                Messages.text(paper.shortMessageKey())));
        update();
    }

    private void lookUpDefaultPaper() {
        collaborators.printers().defaultPaper().whenComplete((printerPaper, failure) ->
                collaborators.edt().execute(() -> defaultPaperAnswered(printerPaper, failure)));
    }

    private void defaultPaperAnswered(Optional<Paper> printerPaper, Throwable failure) {
        if (failure != null) {
            LOGGER.warn("Could not look up the printer's paper", failure);
            return;
        }
        if (disposed || printerPaper == null) {
            return;
        }
        printerDefaultPaper = printerPaper;
        if (exporting || paperTouched || printerPaper.isEmpty()
                || printerPaper.get() == paperChoice.getSelectedItem()) {
            update();
            return;
        }
        adoptPrintersPaper(printerPaper.get());
    }

    /** The printer prints on another sheet: show that one, say so, print nothing yet. */
    private void switchTo(PrintSetup effective) {
        updating = true;
        try {
            paperChoice.setSelectedItem(effective.paper());
            portrait.setSelected(effective.orientation() == PageOrientation.PORTRAIT);
            landscape.setSelected(effective.orientation() == PageOrientation.LANDSCAPE);
        } finally {
            updating = false;
        }
        marginPoints = effective.marginPoints();
        String orientation = Messages.text(effective.orientation() == PageOrientation.LANDSCAPE
                ? "print.orientation.landscape" : "print.orientation.portrait").toLowerCase(Messages.locale());
        printerSwitch = Optional.of(effective);
        printerNotice = Notice.info(Messages.format("print.printer.changed",
                Messages.text(effective.paper().messageKey()), orientation,
                effective.marginPoints() / PageGeometry.CENTIMETRE_POINTS));
        update();
    }

    private void startPrinting(Plan chosen, PrinterChoice.Chosen accepted) {
        PagePainter painter = painterOf(chosen);
        exporting = true;
        cancelRequested = false;
        showProgress(painter.pages().size());
        update();
        collaborators.printers().print(accepted.job(), painter, accepted.effective(), progressListener())
                .whenComplete((ignored, failure) ->
                        collaborators.edt().execute(() -> printFinished(chosen.options(),
                                accepted.effective().marginPoints(), failure)));
    }

    /** @param effectiveMargin the printer's margin the sheets went out with, remembered for the canvas's sheets */
    private void printFinished(PrintOptions options, double effectiveMargin, Throwable failure) {
        exporting = false;
        progress.setVisible(false);
        cancel.setEnabled(true);
        Throwable cause = unwrap(failure);
        if (cause == null) {
            remember(options, effectiveMargin);
            close.run();
            return;
        }
        if (!(cause instanceof CancellationException)) {
            LOGGER.warn("Printing failed", cause);
            collaborators.messages().accept(this, Messages.text("print.failed"));
        }
        update();
    }

    /**
     * What the sheets on the canvas follow (sheets spec §0.2–§0.3): the options and the margin of a PDF that was
     * saved or a print that went out; never of a cancelled or failed one.
     */
    private void remember(PrintOptions options, double margin) {
        preferences.setPrintOptions(options);
        preferences.setPrintMarginPoints(margin);
    }

    /** Asks the system for printers off the EDT; the answer turns «Imprimir…» on or off. */
    private void lookUpPrinters() {
        collaborators.printers().hasPrinters().whenComplete((installed, failure) ->
                collaborators.edt().execute(() -> printersAnswered(installed, failure)));
    }

    private void printersAnswered(Boolean installed, Throwable failure) {
        if (failure != null) {
            // printing will say what is wrong if it really fails
            LOGGER.warn("Could not look up the printers", failure);
        }
        printersAvailable = failure != null || installed;
        update();
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    /** Shows the progress bar at zero of {@code total} pages. */
    private void showProgress(int total) {
        progress.setMinimum(0);
        progress.setMaximum(total);
        progress.setValue(0);
        progress.setString(Messages.format("print.progress", 0, total));
        progress.setVisible(true);
    }

    /** Reports each finished page on the EDT and answers whether the user asked to cancel. */
    private PdfExporter.ProgressListener progressListener() {
        return new PdfExporter.ProgressListener() {
            @Override
            public void pageDone(int done, int pages) {
                collaborators.edt().execute(() -> {
                    if (!cancelRequested) {
                        progress.setMaximum(pages);
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
    }

    /** @param laidOutMargin the margin the saved sheets were laid out with, remembered for the canvas's sheets */
    private void finished(PrintOptions options, double laidOutMargin, Path file, Throwable failure) {
        exporting = false;
        progress.setVisible(false);
        cancel.setEnabled(true);
        Throwable cause = unwrap(failure);
        if (cause == null) {
            remember(options, laidOutMargin);
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
    void onContentGrew(Runnable listener) {
        this.contentGrew = Objects.requireNonNull(listener, "listener");
    }

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
        return hint.isVisible() ? hint.plainText() : "";
    }

    /** How the line under the preview looks now. */
    NoticeLabel.Kind noticeKind() {
        return hint.kind();
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

    JRadioButton portrait() {
        return portrait;
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

    JCheckBox includeStickyNotes() {
        return includeStickyNotes;
    }

    JCheckBox blackAndWhite() {
        return blackAndWhite;
    }

    JCheckBox headerFooter() {
        return headerFooter;
    }

    JCheckBox sheetGuides() {
        return sheetGuides;
    }

    JCheckBox showDate() {
        return showDate;
    }

    JCheckBox showFileName() {
        return showFileName;
    }

    JCheckBox showSheetNumber() {
        return showSheetNumber;
    }

    JTextField titleField() {
        return titleField;
    }

    JButton splitButton() {
        return split;
    }

    JButton fitButton() {
        return fit;
    }

    JLabel hintLabel() {
        return hint;
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

    JButton printButton() {
        return printButton;
    }

    /** The button Enter presses: «Imprimir…» to print, «Guardar PDF…» otherwise. */
    JButton defaultButton() {
        return mode == Mode.PRINT ? printButton : save;
    }

    /** The window's title: «Imprimir» or «Exportar a PDF». */
    String title() {
        return Messages.text(mode == Mode.PRINT ? "print.printTitle" : "print.title");
    }
}
