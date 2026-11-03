package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Timer;
import java.awt.print.PrinterException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The dialog of «Imprimir…»: the printer dialog's answer decides between printing the sheets shown and redoing the
 * preview on the printer's paper first (spec 2026-10-19 §4). A {@link FakePrinterAccess} plays the printer.
 */
class PrintModeDialogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T15:30:00Z"), ZoneOffset.UTC);
    private static final double CENTIMETRE = PageGeometry.CENTIMETRE_POINTS;
    private static final PrintSetup A4_PORTRAIT = new PrintSetup(Paper.A4, PageOrientation.PORTRAIT, CENTIMETRE);

    @TempDir
    Path directory;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final PdfExportDialogTest.FakeExporter exporter = new PdfExportDialogTest.FakeExporter();
    private final FakePrinterAccess printers = new FakePrinterAccess();
    private final List<String> messages = new ArrayList<>();
    private Optional<Path> chosenFile = Optional.empty();
    private int closes;
    private PdfExportDialog dialog;

    @AfterEach
    void close() {
        if (dialog != null) {
            dialog.dispose();
        }
        openDocument.close();
    }

    private PrintInput capture() {
        return PrintInput.capture(openDocument, Set.of(), ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK);
    }

    private PdfExportDialog open(PdfExportDialog.Mode mode) {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(exporter,
                (parent, startDirectory, suggestedName) -> chosenFile, (parent, message) -> true,
                (parent, message) -> messages.add(message), action -> new Timer(Integer.MAX_VALUE, null),
                Runnable::run, printers);
        dialog = new PdfExportDialog(mode, capture(), this::capture, preferences, directory, "circuito",
                collaborators);
        dialog.onClose(() -> closes++);
        return dialog;
    }

    private PdfExportDialog openToPrint() {
        return open(PdfExportDialog.Mode.PRINT);
    }

    private PageGeometry shownGeometry() {
        return dialog.pageLayout().orElseThrow().geometry();
    }

    @Test
    void toPrintTheDefaultButtonIsPrintAndSavePdfIsTheSecondChoice() {
        openToPrint();

        assertThat(dialog.defaultButton()).isSameAs(dialog.printButton());
        assertThat(dialog.printButton().getText()).isEqualTo("Imprimir…");
        assertThat(dialog.printButton().isEnabled()).isTrue();
        assertThat(dialog.saveButton().getParent()).isSameAs(dialog);
        assertThat(dialog.title()).isEqualTo("Imprimir");
    }

    @Test
    void toExportAPdfThereIsNoPrintButton() {
        open(PdfExportDialog.Mode.PDF);

        assertThat(dialog.defaultButton()).isSameAs(dialog.saveButton());
        assertThat(dialog.printButton().getParent()).isNull();
        assertThat(dialog.title()).isEqualTo("Exportar a PDF");
        assertThat(printers.lookups).as("no printer is looked up for a PDF").isEmpty();
    }

    @Test
    void thePrinterDialogStartsWithTheShownPaperAndPrintsTheShownSheets() {
        openToPrint();
        int shown = dialog.pageLayout().orElseThrow().pageCount();

        dialog.printButton().doClick();

        assertThat(printers.requested).containsExactly(A4_PORTRAIT);
        assertThat(printers.prints).hasSize(1);
        FakePrinterAccess.PrintCall call = printers.prints.get(0);
        assertThat(call.job()).isSameAs(FakePrinterAccess.JOB);
        assertThat(call.setup()).isEqualTo(A4_PORTRAIT);
        assertThat(call.pages().pages()).hasSize(shown);
        assertThat(dialog.exporting()).isTrue();
        assertThat(dialog.printButton().isEnabled()).isFalse();
        assertThat(dialog.saveButton().isEnabled()).isFalse();
    }

    @Test
    void anotherPaperInThePrinterDialogRedoesThePreviewAndPrintsNothing() {
        openToPrint();
        printers.accepting(new PrintSetup(Paper.LETTER, PageOrientation.LANDSCAPE, CENTIMETRE));

        dialog.printButton().doClick();

        assertThat(printers.prints).isEmpty();
        assertThat(dialog.paperChoice().getSelectedItem()).isEqualTo(Paper.LETTER);
        assertThat(dialog.landscape().isSelected()).isTrue();
        assertThat(shownGeometry().paper()).isEqualTo(Paper.LETTER);
        assertThat(shownGeometry().orientation()).isEqualTo(PageOrientation.LANDSCAPE);
        assertThat(dialog.hintText()).isEqualTo("La impresora usa Carta (21,6 × 27,9 cm), horizontal, con márgenes"
                + " de 1 cm; revisa la vista previa y vuelve a pulsar «Imprimir…».");
        assertThat(dialog.exporting()).isFalse();
    }

    @Test
    void choosingAnotherPaperByHandDropsThePrintersHint() {
        openToPrint();
        printers.accepting(new PrintSetup(Paper.LETTER, PageOrientation.LANDSCAPE, CENTIMETRE));
        dialog.printButton().doClick();

        dialog.portrait().doClick();

        assertThat(dialog.hintText()).as("the printer's paper is no longer the one shown").isEmpty();
    }

    @Test
    void theHintStaysWhileThePrintersPaperIsShown() {
        openToPrint();
        printers.accepting(new PrintSetup(Paper.LETTER, PageOrientation.LANDSCAPE, CENTIMETRE));
        dialog.printButton().doClick();

        dialog.blackAndWhite().doClick();

        assertThat(dialog.hintText()).startsWith("La impresora usa Carta");
    }

    @Test
    void thePrinterDialogGetsTheCircuitsNameForTheQueue() {
        openToPrint();

        dialog.printButton().doClick();

        assertThat(printers.jobNames).containsExactly("circuito");
    }

    @Test
    void aPdfNotesThatPaperGoesThroughImprimir() {
        assertThat(Messages.text("print.note")).isEqualTo("Para imprimir en papel, usa Archivo ▸ Imprimir….");
    }

    @Test
    void theSecondPressAfterTheChangeAsksForTheNewPaperAndPrints() {
        openToPrint();
        PrintSetup letter = new PrintSetup(Paper.LETTER, PageOrientation.LANDSCAPE, CENTIMETRE);
        printers.accepting(letter);
        dialog.printButton().doClick();

        dialog.printButton().doClick();

        assertThat(printers.requested).containsExactly(A4_PORTRAIT, letter);
        assertThat(printers.prints).hasSize(1);
        assertThat(printers.prints.get(0).setup()).isEqualTo(letter);
        assertThat(printers.prints.get(0).pages().pages()).hasSize(dialog.pageLayout().orElseThrow().pageCount());
    }

    @Test
    void aHardwareMarginUnderACentimetreChangesNothing() {
        openToPrint();
        // what SystemPrinterAccess makes of 0.6 cm: the centimetre
        printers.accepting(A4_PORTRAIT);

        dialog.printButton().doClick();

        assertThat(printers.prints).hasSize(1);
        assertThat(shownGeometry().marginPoints()).isCloseTo(CENTIMETRE, within(1e-9));
    }

    @Test
    void aWiderHardwareMarginRedoesTheLayoutAndThePdfKeepsIt() {
        openToPrint();
        double wide = 15 * 72 / 25.4;
        PrintSetup wideMargin = new PrintSetup(Paper.A4, PageOrientation.PORTRAIT, wide);
        printers.accepting(wideMargin);

        dialog.printButton().doClick();

        assertThat(printers.prints).isEmpty();
        assertThat(shownGeometry().marginPoints()).isCloseTo(wide, within(1e-9));
        assertThat(dialog.hintText()).contains("con márgenes de 1,5 cm");

        chosenFile = Optional.of(directory.resolve("circuito.pdf"));
        dialog.saveButton().doClick();
        assertThat(exporter.painters).hasSize(1);
        assertThat(exporter.painters.get(0).pages().get(0).size().widthPoints())
                .isCloseTo(Paper.A4.widthPoints(), within(1e-9));
        assertThat(shownGeometry().marginPoints()).as("the PDF is laid out like the paper").isCloseTo(wide,
                within(1e-9));
    }

    @Test
    void aPaperVsimDoesNotKnowIsNamedAndNothingIsPrinted() {
        openToPrint();
        printers.answer(new PrinterChoice.UnknownPaper("na-legal (216 × 356 mm)"));

        dialog.printButton().doClick();

        assertThat(printers.prints).isEmpty();
        assertThat(dialog.paperChoice().getSelectedItem()).isEqualTo(Paper.A4);
        assertThat(dialog.hintText()).isEqualTo("Esta impresora usa un papel que VSim no conoce (na-legal (216 × 356"
                + " mm)); elige A4, Carta, Oficio o A3 en su diálogo.");
    }

    @Test
    void closingThePrinterDialogDoesNothing() {
        openToPrint();
        printers.answer(new PrinterChoice.Declined());

        dialog.printButton().doClick();

        assertThat(printers.prints).isEmpty();
        assertThat(dialog.hintText()).isEmpty();
        assertThat(dialog.exporting()).isFalse();
        assertThat(closes).isZero();
    }

    @Test
    void withoutPrintersThePrintButtonIsOffAndSaysWhy() {
        printers.printersInstalled(false);
        openToPrint();

        assertThat(dialog.printButton().isEnabled()).isFalse();
        assertThat(dialog.printButton().getToolTipText()).isEqualTo("No hay impresoras instaladas");
        assertThat(dialog.saveButton().isEnabled()).as("the PDF stays available").isTrue();
        dialog.print();
        assertThat(printers.requested).isEmpty();
    }

    @Test
    void untilTheLookupAnswersThePrintButtonStaysOn() {
        printers.slowLookups();
        openToPrint();
        assertThat(dialog.printButton().isEnabled()).isTrue();

        printers.lookups.get(0).complete(false);

        assertThat(dialog.printButton().isEnabled()).isFalse();
    }

    @Test
    void aSuccessfulPrintRemembersTheOptionsAndCloses() {
        openToPrint();
        dialog.blackAndWhite().doClick();
        printers.accepting(new PrintSetup(Paper.OFICIO, PageOrientation.PORTRAIT, CENTIMETRE));
        dialog.printButton().doClick();
        dialog.printButton().doClick();
        FakePrinterAccess.PrintCall call = printers.prints.get(0);
        call.progress().pageDone(1, 1);
        assertThat(dialog.progressBar().getValue()).isEqualTo(1);
        assertThat(preferences.printOptions("x").blackAndWhite()).as("nothing saved before it printed").isFalse();

        call.result().complete(null);

        assertThat(closes).isEqualTo(1);
        assertThat(preferences.printOptions("x").blackAndWhite()).isTrue();
        assertThat(preferences.printOptions("x").paper()).as("the printer's paper").isEqualTo(Paper.OFICIO);
        assertThat(preferences.printDirectory()).as("no folder: nothing was saved").isEmpty();
    }

    @Test
    void aFailedPrintIsReportedInPlainWordsAndTheDialogStaysUsable() {
        openToPrint();
        dialog.printButton().doClick();

        printers.prints.get(0).result().completeExceptionally(new PrinterException("lpr: printer offline"));

        assertThat(messages).containsExactly("No se pudo imprimir. Revisa que la impresora esté encendida y"
                + " conectada, y vuelve a intentarlo.");
        assertThat(closes).isZero();
        assertThat(dialog.exporting()).isFalse();
        assertThat(dialog.printButton().isEnabled()).isTrue();
        assertThat(dialog.saveButton().isEnabled()).isTrue();
        assertThat(dialog.progressBar().isVisible()).isFalse();
    }

    @Test
    void cancelDuringThePrintStopsItQuietly() {
        openToPrint();
        dialog.printButton().doClick();
        FakePrinterAccess.PrintCall call = printers.prints.get(0);
        assertThat(call.progress().cancelled()).isFalse();

        dialog.cancelButton().doClick();
        assertThat(call.progress().cancelled()).isTrue();
        assertThat(closes).as("the dialog waits for the printer to stop").isZero();
        call.result().completeExceptionally(new CancellationException());

        assertThat(messages).isEmpty();
        assertThat(dialog.exporting()).isFalse();
        assertThat(dialog.printButton().isEnabled()).isTrue();
        assertThat(preferences.printOptions("x").paper()).isEqualTo(Paper.A4);
    }
}
