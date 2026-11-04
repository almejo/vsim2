package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.Timer;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Saltar hojas vacías» in the dialog, in both modes: a check box next to the joining guides, on by default and only
 * with several sheets; the status line says how many sheets go out; the PDF, the printer and the progress get only
 * those; it is remembered like the other options; off, everything is as it always was.
 */
class SkipEmptySheetsInTheDialogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T15:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final PdfExportDialogTest.FakeExporter exporter = new PdfExportDialogTest.FakeExporter();
    private final FakePrinterAccess printers = new FakePrinterAccess();
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
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(exporter,
                (parent, startDirectory, suggestedName) -> Optional.of(directory.resolve("hojas.pdf")),
                (parent, message) -> true, (parent, message) -> { }, action -> new Timer(Integer.MAX_VALUE, null),
                Runnable::run, printers);
        dialog = new PdfExportDialog(mode, capture(), this::capture, preferences, directory, "circuito",
                collaborators);
        return dialog;
    }

    /** Two switches in opposite corners: at 100 % several A4 sheets, the ones between them empty. */
    private void cornersOnly() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 200, 150, Map.of());
    }

    private void atOneHundredPercent() {
        dialog.scaleChoice().setSelectedItem(PdfExportDialog.ScaleChoice.HUNDRED);
    }

    private int printedCount() {
        return dialog.printedSheets().orElseThrow().count();
    }

    @ParameterizedTest
    @EnumSource(PdfExportDialog.Mode.class)
    void theCheckBoxIsOnByDefaultAndShowsOnlyWithSeveralSheets(PdfExportDialog.Mode mode) {
        cornersOnly();
        open(mode);
        assertThat(dialog.skipEmptySheets().getText()).isEqualTo("Saltar hojas vacías");
        assertThat(dialog.skipEmptySheets().isSelected()).as("on by default").isTrue();
        assertThat(dialog.skipEmptySheets().isVisible()).as("one sheet, nothing to skip").isFalse();

        atOneHundredPercent();

        assertThat(dialog.pageLayout().orElseThrow().tiled()).isTrue();
        assertThat(dialog.skipEmptySheets().isVisible()).isTrue();
        assertThat(dialog.options().skipEmptySheets()).isTrue();
    }

    @Test
    void theStatusLineSaysHowManySheetsGoOut() {
        cornersOnly();
        open(PdfExportDialog.Mode.PDF);
        atOneHundredPercent();
        PageLayout layout = dialog.pageLayout().orElseThrow();
        String grid = layout.columns() + " × " + layout.rows() + " hojas · " + layout.pageCount()
                + " en total · escala 100 %";
        assertThat(printedCount()).as("the fixture has empty sheets").isLessThan(layout.pageCount());

        assertThat(dialog.statusText()).isEqualTo(grid + " · se imprimen " + printedCount());

        dialog.skipEmptySheets().doClick();

        assertThat(dialog.options().skipEmptySheets()).isFalse();
        assertThat(dialog.statusText()).as("off: as it always was").isEqualTo(grid);
        assertThat(printedCount()).isEqualTo(layout.pageCount());
    }

    @Test
    void thePdfGetsOnlyThePrintedSheetsAndTheProgressCountsThem() {
        cornersOnly();
        open(PdfExportDialog.Mode.PDF);
        atOneHundredPercent();
        int printed = printedCount();

        dialog.saveButton().doClick();

        assertThat(exporter.painters).hasSize(1);
        assertThat(exporter.painters.get(0).pages()).hasSize(printed);
        assertThat(dialog.progressBar().getMaximum()).isEqualTo(printed);
    }

    @Test
    void thePrinterGetsOnlyThePrintedSheetsAndTheProgressCountsThem() {
        cornersOnly();
        open(PdfExportDialog.Mode.PRINT);
        atOneHundredPercent();
        int printed = printedCount();

        dialog.printButton().doClick();

        assertThat(printers.prints).hasSize(1);
        assertThat(printers.prints.get(0).pages().pages()).hasSize(printed);
        assertThat(dialog.progressBar().getMaximum()).isEqualTo(printed);
    }

    @Test
    void onlyThePrintedSheetsCountTowardTheConfirmation() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 2000, 0, Map.of());
        List<String> confirmations = new ArrayList<>();
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(exporter,
                (parent, startDirectory, suggestedName) -> Optional.of(directory.resolve("tira.pdf")),
                (parent, message) -> confirmations.add(message), (parent, message) -> { },
                action -> new Timer(Integer.MAX_VALUE, null), Runnable::run, printers);
        dialog = new PdfExportDialog(PdfExportDialog.Mode.PDF, capture(), this::capture, preferences, directory,
                "circuito", collaborators);
        dialog.scaleChoice().setSelectedItem(PdfExportDialog.ScaleChoice.OTHER);
        dialog.percentSpinner().setValue(400);
        assertThat(dialog.pageLayout().orElseThrow().pageCountExact()).as("the fixture: a long strip")
                .isGreaterThan(PdfExport.CONFIRM_ABOVE_PAGES);

        dialog.saveButton().doClick();

        assertThat(confirmations).as("only the two switches' sheets go out").isEmpty();
        assertThat(exporter.painters).hasSize(1);
        assertThat(exporter.painters.get(0).pages()).hasSize(printedCount());
    }

    @Test
    void withTheOptionOffEverySheetGoesOut() {
        cornersOnly();
        open(PdfExportDialog.Mode.PDF);
        atOneHundredPercent();
        dialog.skipEmptySheets().doClick();

        dialog.saveButton().doClick();

        assertThat(exporter.painters.get(0).pages()).hasSize(dialog.pageLayout().orElseThrow().pageCount());
    }

    @Test
    void theOptionIsRememberedAfterASavedExport() {
        cornersOnly();
        assertThat(preferences.printOptions("x").skipEmptySheets()).as("on by default").isTrue();
        open(PdfExportDialog.Mode.PDF);
        atOneHundredPercent();
        dialog.skipEmptySheets().doClick();

        dialog.saveButton().doClick();
        exporter.future.complete(null);

        assertThat(preferences.printOptions("x").skipEmptySheets()).isFalse();
        dialog.dispose();
        open(PdfExportDialog.Mode.PDF);
        assertThat(dialog.skipEmptySheets().isSelected()).isFalse();
    }
}
