package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportDialogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final FakeExporter exporter = new FakeExporter();
    private final List<String> messages = new ArrayList<>();
    private final List<String> confirmations = new ArrayList<>();
    private final List<Runnable> timerActions = new ArrayList<>();
    private final List<Path> chooserDirectories = new ArrayList<>();
    private boolean confirmAnswer = true;
    private Optional<Path> chosenFile = Optional.empty();
    private Set<ElementId> viewSelection = Set.of();
    private int closes;
    private PdfExportDialog dialog;

    /** Records what it was asked to write and lets the test finish it. */
    static final class FakeExporter implements PdfExporter {
        final List<PagePainter> painters = new ArrayList<>();
        final List<Path> targets = new ArrayList<>();
        ProgressListener progress;
        CompletableFuture<Void> future = new CompletableFuture<>();

        @Override
        public CompletableFuture<Void> export(PagePainter pages, Path target, ProgressListener progress) {
            painters.add(pages);
            targets.add(target);
            this.progress = progress;
            return future;
        }
    }

    @AfterEach
    void close() {
        if (dialog != null) {
            dialog.dispose();
        }
        openDocument.close();
    }

    private PrintInput capture() {
        return PrintInput.capture(openDocument, viewSelection, ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
    }

    private PdfExportDialog open() {
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(exporter,
                (parent, startDirectory, suggestedName) -> {
                    chooserDirectories.add(startDirectory);
                    return chosenFile;
                },
                (parent, message) -> {
                    confirmations.add(message);
                    return confirmAnswer;
                },
                (parent, message) -> messages.add(message),
                action -> {
                    timerActions.add(action);
                    return new Timer(Integer.MAX_VALUE, event -> action.run());
                },
                Runnable::run);
        dialog = new PdfExportDialog(capture(), this::capture, preferences, directory, "circuito", collaborators);
        dialog.onClose(() -> closes++);
        return dialog;
    }

    private Component place(int x, int y) {
        return UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, x, y, Map.of());
    }

    private Frame addFrame(int x, int y, int width, int height, String title) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(x, y), width, height, title,
                FrameTint.BLUE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        return frame;
    }

    private void firePreviewTimer() {
        timerActions.forEach(Runnable::run);
    }

    private static BufferedImage paint(PagePainter painter) {
        PdfPages.PageSize size = painter.pages().get(0).size();
        BufferedImage image = new BufferedImage((int) size.widthPoints(), (int) size.heightPoints(),
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        painter.paint(graphics, 0);
        graphics.dispose();
        return image;
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return false;
        }
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void theOptionsStartAsTheDefaultsWithTheCircuitNameAsTitle() {
        place(10, 10);
        open();
        assertThat(dialog.options()).isEqualTo(PrintOptions.defaults(openDocument.displayName()));
        assertThat(dialog.titleField().isEnabled()).as("the title only matters with a header").isFalse();
        assertThat(dialog.saveButton().isEnabled()).isTrue();
    }

    @Test
    void theOptionsStartAsTheRememberedOnes() {
        place(10, 10);
        preferences.setPrintOptions(new PrintOptions(new PrintScope.WholeCircuit(), Paper.A3,
                PageOrientation.LANDSCAPE, new ScaleMode.Percentage(75), false, true, true, "ignored"));
        open();
        assertThat(dialog.options()).isEqualTo(new PrintOptions(new PrintScope.WholeCircuit(), Paper.A3,
                PageOrientation.LANDSCAPE, new ScaleMode.Percentage(75), false, true, true,
                openDocument.displayName()));
        assertThat(dialog.scaleChoice().getSelectedItem()).isEqualTo(PdfExportDialog.ScaleChoice.SEVENTY_FIVE);
        preferences.setPrintOptions(new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4,
                PageOrientation.PORTRAIT, new ScaleMode.Percentage(130), true, false, false, ""));
        dialog.dispose();
        open();
        assertThat(dialog.scaleChoice().getSelectedItem()).isEqualTo(PdfExportDialog.ScaleChoice.OTHER);
        assertThat(dialog.percentSpinner().getValue()).isEqualTo(130);
        assertThat(dialog.percentSpinner().isEnabled()).isTrue();
    }

    @Test
    void selectionIsOffWithoutASelectionAndFrameIsOffWithoutFrames() {
        place(10, 10);
        open();
        assertThat(dialog.scopeSelection().isEnabled()).isFalse();
        assertThat(dialog.scopeFrame().isEnabled()).isFalse();
        assertThat(dialog.frameChoice().isEnabled()).isFalse();
    }

    @Test
    void selectionAndFrameScopesExportWhatTheyName() {
        Component inside = place(10, 10);
        Component outside = place(60, 60);
        Frame frame = addFrame(5, 5, 20, 20, "Entrada");
        viewSelection = Set.of(outside.id());
        open();
        assertThat(dialog.scopeSelection().isEnabled()).isTrue();
        assertThat(dialog.scopeFrame().isEnabled()).isTrue();
        dialog.scopeSelection().doClick();
        assertThat(dialog.options().scope()).isEqualTo(new PrintScope.Selection(Set.of(outside.id())));
        dialog.scopeFrame().doClick();
        assertThat(dialog.frameChoice().isEnabled()).isTrue();
        assertThat(dialog.options().scope()).isEqualTo(new PrintScope.Frame(frame.id()));
        assertThat(((FrameEntry) dialog.frameChoice().getSelectedItem()).displayTitle()).isEqualTo("Entrada");
        assertThat(inside.id()).isNotEqualTo(outside.id());
    }

    @Test
    void theStatusLineCountsTheSheetsAndTheScale() {
        place(0, 0);
        place(200, 0);
        open();
        assertThat(dialog.statusText()).startsWith("1 hoja · escala ");
        dialog.scaleChoice().setSelectedItem(PdfExportDialog.ScaleChoice.HUNDRED);
        PageLayout layout = dialog.pageLayout().orElseThrow();
        assertThat(layout.columns()).isGreaterThan(1);
        assertThat(layout.rows()).isEqualTo(1);
        assertThat(dialog.statusText()).isEqualTo(layout.columns() + " × 1 hojas · " + layout.pageCount()
                + " en total · escala 100 %");
    }

    @Test
    void tinyTextWarnsAndSplittingSwitchesToOneHundredPercent() {
        place(0, 0);
        place(900, 600);
        open();
        assertThat(dialog.warningVisible()).isTrue();
        assertThat(dialog.warningText()).startsWith("El texto quedará muy pequeño (≈ ");
        dialog.splitButton().doClick();
        assertThat(dialog.options().scaleMode()).isEqualTo(new ScaleMode.Percentage(100));
        assertThat(dialog.warningVisible()).isFalse();
    }

    @Test
    void theWarningCountsABlocksPinNamesNotOnlyTheLabels() {
        UiTestSupport.place(openDocument, StandardComponentTypes.ADDER, 0, 0, Map.of());
        place(85, 0);
        open();
        PageLayout layout = dialog.pageLayout().orElseThrow();
        assertThat(layout.smallestTextPoints(TextFont.DEFAULT.size())).as("the labels alone read fine")
                .isGreaterThanOrEqualTo(PdfExportDialog.MINIMUM_LEGIBLE_POINTS);
        assertThat(dialog.warningVisible()).as("the pin names do not").isTrue();
    }

    @Test
    void theHintsTakeTheirDimColourFromTheThemeWhenItChanges() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            place(0, 0);
            open();
            SwingUtilities.updateComponentTreeUI(dialog);
            assertThat(dialog.hintLabel().getForeground())
                    .isEqualTo(UIManager.getColor("Label.disabledForeground"));
            UIManager.setLookAndFeel(new FlatDarkLaf());
            SwingUtilities.updateComponentTreeUI(dialog);
            assertThat(dialog.hintLabel().getForeground())
                    .isEqualTo(UIManager.getColor("Label.disabledForeground"));
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void aSmallCircuitDoesNotWarn() {
        place(10, 10);
        open();
        assertThat(dialog.warningVisible()).isFalse();
    }

    @Test
    void theHeaderSwitchEnablesTheTitle() {
        place(10, 10);
        open();
        dialog.headerFooter().doClick();
        assertThat(dialog.titleField().isEnabled()).isTrue();
        dialog.titleField().setText("Práctica ñ");
        assertThat(dialog.options().title()).isEqualTo("Práctica ñ");
        assertThat(dialog.options().headerFooter()).isTrue();
    }

    @Test
    void thePreviewIsRebuiltOnlyWhenTheCoalescingTimerFires() {
        place(10, 10);
        open();
        firePreviewTimer();
        PagePainter first = dialog.preview().painter().orElseThrow();
        dialog.blackAndWhite().doClick();
        dialog.paperChoice().setSelectedItem(Paper.A3);
        assertThat(dialog.preview().painter()).containsSame(first);
        firePreviewTimer();
        assertThat(dialog.preview().painter().orElseThrow()).isNotSameAs(first);
    }

    @Test
    void aboveFiftySheetsTheSaveAsksFirst() {
        place(0, 0);
        place(2000, 0);
        open();
        dialog.scaleChoice().setSelectedItem(PdfExportDialog.ScaleChoice.OTHER);
        dialog.percentSpinner().setValue(400);
        long count = dialog.pageLayout().orElseThrow().pageCountExact();
        assertThat(count).isBetween(51L, 1000L);
        confirmAnswer = false;
        chosenFile = Optional.of(directory.resolve("grande.pdf"));
        dialog.saveButton().doClick();
        assertThat(confirmations).containsExactly("Se van a generar " + count + " hojas. ¿Seguir?");
        assertThat(chooserDirectories).as("refused before asking for the file").isEmpty();
        assertThat(exporter.painters).isEmpty();
        confirmAnswer = true;
        dialog.saveButton().doClick();
        assertThat(exporter.painters).hasSize(1);
    }

    @Test
    void aboveAThousandSheetsTheSaveIsOffWithAHint() {
        place(0, 0);
        place(2000, 1000);
        open();
        dialog.scaleChoice().setSelectedItem(PdfExportDialog.ScaleChoice.OTHER);
        dialog.percentSpinner().setValue(400);
        assertThat(dialog.pageLayout().orElseThrow().pageCountExact()).isGreaterThan(1000);
        assertThat(dialog.saveButton().isEnabled()).isFalse();
        assertThat(dialog.hintText()).contains("demasiadas hojas");
        dialog.scaleChoice().setSelectedItem(PdfExportDialog.ScaleChoice.FIT);
        assertThat(dialog.saveButton().isEnabled()).isTrue();
        assertThat(dialog.hintText()).isEmpty();
    }

    @Test
    void cancellingTheFileChoiceOrTheDialogSavesNoPreferences() {
        place(10, 10);
        open();
        dialog.blackAndWhite().doClick();
        dialog.saveButton().doClick();
        assertThat(chooserDirectories).containsExactly(directory);
        assertThat(exporter.painters).isEmpty();
        dialog.cancelButton().doClick();
        assertThat(closes).isEqualTo(1);
        assertThat(preferences.printOptions("x").blackAndWhite()).isFalse();
        assertThat(preferences.printDirectory()).isEmpty();
    }

    @Test
    void aSuccessfulExportSavesThePreferencesAndCloses() {
        place(10, 10);
        open();
        dialog.blackAndWhite().doClick();
        dialog.paperChoice().setSelectedItem(Paper.LETTER);
        Path target = directory.resolve("salida").resolve("circuito.pdf");
        chosenFile = Optional.of(target);
        dialog.saveButton().doClick();
        assertThat(exporter.targets).containsExactly(target);
        assertThat(dialog.exporting()).isTrue();
        assertThat(dialog.saveButton().isEnabled()).isFalse();
        exporter.progress.pageDone(1, 1);
        assertThat(dialog.progressBar().getValue()).isEqualTo(1);
        assertThat(preferences.printOptions("x").blackAndWhite()).as("nothing saved before it is written").isFalse();
        exporter.future.complete(null);
        assertThat(closes).isEqualTo(1);
        assertThat(preferences.printOptions("x").blackAndWhite()).isTrue();
        assertThat(preferences.printOptions("x").paper()).isEqualTo(Paper.LETTER);
        assertThat(preferences.printDirectory()).contains(target.getParent().toAbsolutePath());
    }

    @Test
    void cancelDuringTheExportStopsItAndSavesNothing() {
        place(10, 10);
        open();
        chosenFile = Optional.of(directory.resolve("circuito.pdf"));
        dialog.blackAndWhite().doClick();
        dialog.saveButton().doClick();
        assertThat(exporter.progress.cancelled()).isFalse();
        dialog.cancelButton().doClick();
        assertThat(exporter.progress.cancelled()).isTrue();
        assertThat(closes).as("the dialog waits for the export to stop").isZero();
        exporter.future.completeExceptionally(new CancellationException());
        assertThat(dialog.exporting()).isFalse();
        assertThat(dialog.saveButton().isEnabled()).isTrue();
        assertThat(messages).isEmpty();
        assertThat(preferences.printOptions("x").blackAndWhite()).isFalse();
    }

    @Test
    void aFailedExportIsReportedAndSavesNothing() {
        place(10, 10);
        open();
        chosenFile = Optional.of(directory.resolve("circuito.pdf"));
        dialog.saveButton().doClick();
        exporter.future.completeExceptionally(new IOException("disco lleno"));
        assertThat(messages).containsExactly("No se pudo exportar circuito.pdf: disco lleno");
        assertThat(closes).isZero();
        assertThat(dialog.exporting()).isFalse();
        assertThat(preferences.printDirectory()).isEmpty();
    }

    @Test
    void theExporterGetsTheCircuitAsItWasWhenTheDialogOpened() {
        place(10, 10);
        PagePainter before = new PagePainter(capture(), PrintScopes.area(capture(), new PrintScope.WholeCircuit())
                .orElseThrow(), PrintOptions.defaults(openDocument.displayName()),
                PageLayout.of(PrintScopes.area(capture(), new PrintScope.WholeCircuit()).orElseThrow().gridArea(),
                        PageGeometry.standard(Paper.A4, PageOrientation.PORTRAIT, false),
                        new ScaleMode.FitToOnePage()));
        open();
        place(300, 200);
        chosenFile = Optional.of(directory.resolve("circuito.pdf"));
        dialog.saveButton().doClick();
        assertThat(samePixels(paint(exporter.painters.get(0)), paint(before))).isTrue();
        assertThat(dialog.refreshButton().isEnabled()).as("no new copy while writing").isFalse();
    }

    @Test
    void refreshTakesANewCopyOfTheCircuit() {
        place(10, 10);
        open();
        place(300, 200);
        PageLayout frozen = dialog.pageLayout().orElseThrow();
        dialog.refreshButton().doClick();
        assertThat(dialog.pageLayout().orElseThrow().scale()).isLessThan(frozen.scale());
        chosenFile = Optional.of(directory.resolve("circuito.pdf"));
        dialog.saveButton().doClick();
        assertThat(samePixels(paint(exporter.painters.get(0)), paint(new PagePainter(capture(),
                PrintScopes.area(capture(), new PrintScope.WholeCircuit()).orElseThrow(),
                PrintOptions.defaults(openDocument.displayName()), dialog.pageLayout().orElseThrow())))).isTrue();
    }

    @Test
    void aStaleSnapshotSaysWhyThereAreNoValues() {
        place(10, 10);
        PrintInput stale = PrintInput.capture(new PrintInput.LiveCircuit(openDocument.document(),
                openDocument.registry(), openDocument.netlist(), openDocument.document().revision() + 7,
                openDocument.latestSnapshot(), "c", Optional.empty()), Set.of(), ColorScheme.LIGHT,
                TextFont.DEFAULT, CLOCK);
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(exporter,
                (parent, startDirectory, suggestedName) -> Optional.empty(), (parent, message) -> true,
                (parent, message) -> messages.add(message), action -> new Timer(Integer.MAX_VALUE, null),
                Runnable::run);
        dialog = new PdfExportDialog(stale, () -> stale, preferences, directory, "c", collaborators);
        assertThat(dialog.valuesProblemText()).startsWith("Sin valores:").contains("«Actualizar»");
        dialog.withValues().doClick();
        assertThat(dialog.valuesProblemText()).isEmpty();
    }

    @Test
    void theEnglishStaleMessageNamesTheRefreshButton() {
        Messages.initialize(java.util.Locale.ENGLISH);
        assertThat(Messages.text("print.values.stale")).contains("«" + Messages.text("print.refresh") + "»");
        Messages.initialize(Messages.SPANISH);
        assertThat(Messages.text("print.values.stale")).contains("«" + Messages.text("print.refresh") + "»");
    }

    @Test
    void theChosenFileGetsThePdfExtensionOnce() {
        assertThat(PdfFileChooser.withPdfExtension(Path.of("/tmp/sumador"))).isEqualTo(Path.of("/tmp/sumador.pdf"));
        assertThat(PdfFileChooser.withPdfExtension(Path.of("/tmp/sumador.PDF"))).isEqualTo(Path.of("/tmp/sumador.PDF"));
        assertThat(PdfFileChooser.withPdfExtension(Path.of("sumador.vsim"))).isEqualTo(Path.of("sumador.vsim.pdf"));
    }
}
