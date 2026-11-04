package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.sheets.SheetAnchors;
import cl.almejo.vsim.ui.sheets.SheetsTestAccess;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.Timer;
import java.awt.geom.Rectangle2D;
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
 * The dialog follows the sheets shown on the circuit (sheets on the canvas, spec §0a): «Todo el circuito» is laid out
 * on the anchored lattice, a note says so, «Ver hojas en el circuito» turns the sheets on, and the canvas, the dialog
 * and the paper agree sheet by sheet.
 */
class SheetsInTheDialogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T15:30:00Z"), ZoneOffset.UTC);
    private static final String NOTE = "Se usan las hojas que ves en el circuito.";
    private static final double WIDER_MARGIN = 40;

    @TempDir
    Path directory;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final PdfExportDialogTest.FakeExporter exporter = new PdfExportDialogTest.FakeExporter();
    private final FakePrinterAccess printers = new FakePrinterAccess();
    private Set<ElementId> viewSelection = Set.of();
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

    /** What the canvas would draw: the remembered options at 100 %, A4 portrait. */
    private PrintOptions rememberHundredPercent() {
        PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.Percentage(100), true, false, false, openDocument.displayName());
        preferences.setPrintOptions(options);
        return options;
    }

    /** Like {@link #rememberHundredPercent()}, with «Saltar hojas vacías» on. */
    private PrintOptions rememberHundredPercentSkippingEmptySheets() {
        PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.Percentage(100), true, false, false, openDocument.displayName(), false, true, true,
                true, true, true);
        preferences.setPrintOptions(options);
        return options;
    }

    /** The «Hoja n» numbers of the sheets that go out. */
    private static List<Integer> sheetNumbers(PrintedSheets printed) {
        List<Integer> numbers = new ArrayList<>();
        for (int position = 0; position < printed.count(); position++) {
            numbers.add(printed.layoutIndex(position) + 1);
        }
        return numbers;
    }

    private double stepInGridUnits(PrintOptions options, double marginPoints) {
        PageLayout probe = PageLayout.of(new Rectangle2D.Double(0, 0, 4000, 4000),
                PageGeometry.standard(options, marginPoints), options.scaleMode());
        return (probe.window().getWidth() - probe.overlapPoints()) / (GridPoint.PIXELS_PER_UNIT * probe.scale());
    }

    /** A circuit about 1.8 sheets wide at 100 %: two columns by themselves, three when the lattice starts half a sheet earlier. */
    private void placeAboutTwoSheetsWide(PrintOptions options, double marginPoints) {
        int width = (int) Math.round(stepInGridUnits(options, marginPoints) * 1.6);
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10 + width, 10, Map.of());
    }

    private Rectangle2D wholeArea(PrintOptions options) {
        return TextFonts.withFont(TextFont.DEFAULT, () -> PrintScopes.wholeCircuitArea(openDocument.document(),
                openDocument.registry(), options.includeStickyNotes())).orElseThrow();
    }

    /** An anchor half a sheet to the right of the area's left edge: the lattice then starts half a sheet before it. */
    private GridPoint halfASheetIn(PrintOptions options, double marginPoints) {
        Rectangle2D area = wholeArea(options);
        return new GridPoint((int) Math.floor(area.getX() + stepInGridUnits(options, marginPoints) / 2),
                (int) Math.floor(area.getY()));
    }

    private PrintInput capture(Optional<GridPoint> anchor) {
        return PrintInput.capture(openDocument, viewSelection, ColorScheme.LIGHT, TextFont.DEFAULT, CLOCK, true,
                anchor);
    }

    private PdfExportDialog open(PdfExportDialog.Mode mode, Optional<GridPoint> anchor) {
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(exporter,
                (parent, startDirectory, suggestedName) -> chosenFile, (parent, message) -> true,
                (parent, message) -> { }, action -> new Timer(Integer.MAX_VALUE, null), Runnable::run, printers);
        dialog = new PdfExportDialog(mode, capture(anchor), () -> capture(anchor), preferences, directory, "circuito",
                collaborators);
        dialog.onClose(() -> closes++);
        return dialog;
    }

    private PageLayout automaticLayout(PrintOptions options, double marginPoints) {
        return PageLayout.of(wholeArea(options), PageGeometry.standard(options, marginPoints), options.scaleMode());
    }

    @ParameterizedTest
    @EnumSource(PdfExportDialog.Mode.class)
    void wholeCircuitIsLaidOutOnTheAnchoredSheetsAndTheNoteSaysSo(PdfExportDialog.Mode mode) {
        PrintOptions options = rememberHundredPercent();
        placeAboutTwoSheetsWide(options, PageGeometry.CENTIMETRE_POINTS);
        GridPoint anchor = halfASheetIn(options, PageGeometry.CENTIMETRE_POINTS);
        PageLayout automatic = automaticLayout(options, PageGeometry.CENTIMETRE_POINTS);
        PageLayout expected = SheetPlan.layout(wholeArea(options), PageGeometry.standard(options,
                PageGeometry.CENTIMETRE_POINTS), options.scaleMode(), Optional.of(anchor));
        assertThat(expected.pageCount()).as("the fixture must make the anchor matter")
                .isGreaterThan(automatic.pageCount());

        open(mode, Optional.of(anchor));

        PageLayout shown = dialog.pageLayout().orElseThrow();
        assertThat(shown.columns()).isEqualTo(expected.columns());
        assertThat(shown.pageCount()).isEqualTo(expected.pageCount());
        assertThat(shown.gridArea()).isEqualTo(expected.gridArea());
        assertThat(dialog.sheetNoteText()).isEqualTo(NOTE);
        assertThat(dialog.statusText()).contains(shown.pageCount() + " en total");
    }

    @Test
    void theNoteGoesWhenTheDialogsPaperIsNotTheOneOnTheCanvas() {
        PrintOptions options = rememberHundredPercent();
        placeAboutTwoSheetsWide(options, PageGeometry.CENTIMETRE_POINTS);
        open(PdfExportDialog.Mode.PDF, Optional.of(halfASheetIn(options, PageGeometry.CENTIMETRE_POINTS)));
        assertThat(dialog.sheetNoteText()).isEqualTo(NOTE);

        dialog.paperChoice().setSelectedItem(Paper.A3);
        assertThat(dialog.pageLayout().orElseThrow().tiled()).isTrue();
        assertThat(dialog.sheetNoteText()).as("another paper than the canvas's").isEmpty();

        dialog.paperChoice().setSelectedItem(Paper.A4);
        assertThat(dialog.sheetNoteText()).as("back to the canvas's paper").isEqualTo(NOTE);
    }

    @Test
    void theNoteGoesWhenTheMarginIsNotTheRememberedOne() {
        PrintOptions options = rememberHundredPercent();
        placeAboutTwoSheetsWide(options, PageGeometry.CENTIMETRE_POINTS);
        open(PdfExportDialog.Mode.PDF, Optional.of(halfASheetIn(options, PageGeometry.CENTIMETRE_POINTS)));
        assertThat(dialog.sheetNoteText()).isEqualTo(NOTE);

        preferences.setPrintMarginPoints(WIDER_MARGIN);
        dialog.blackAndWhite().doClick();

        assertThat(dialog.sheetNoteText()).isEmpty();
    }

    @Test
    void theNoteGoesWhenSkippingEmptySheetsIsNotTheCanvasChoice() {
        PrintOptions options = rememberHundredPercent();
        placeAboutTwoSheetsWide(options, PageGeometry.CENTIMETRE_POINTS);
        open(PdfExportDialog.Mode.PDF, Optional.of(halfASheetIn(options, PageGeometry.CENTIMETRE_POINTS)));
        assertThat(dialog.sheetNoteText()).isEqualTo(NOTE);

        dialog.skipEmptySheets().doClick();
        assertThat(dialog.sheetNoteText()).as("the canvas dims the empty sheets, the paper prints them").isEmpty();

        dialog.skipEmptySheets().doClick();
        assertThat(dialog.sheetNoteText()).isEqualTo(NOTE);
    }

    @Test
    void withoutAnAnchorNothingChangesAndThereIsNoNote() {
        PrintOptions options = rememberHundredPercent();
        placeAboutTwoSheetsWide(options, PageGeometry.CENTIMETRE_POINTS);

        open(PdfExportDialog.Mode.PDF, Optional.empty());

        PageLayout automatic = automaticLayout(options, PageGeometry.CENTIMETRE_POINTS);
        assertThat(dialog.pageLayout().orElseThrow().gridArea()).isEqualTo(automatic.gridArea());
        assertThat(dialog.sheetNoteText()).isEmpty();
    }

    @Test
    void oneSheetIgnoresTheAnchorAndShowsNoNote() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());

        open(PdfExportDialog.Mode.PDF, Optional.of(new GridPoint(0, 0)));

        assertThat(dialog.pageLayout().orElseThrow().tiled()).isFalse();
        assertThat(dialog.sheetNoteText()).isEmpty();
    }

    @Test
    void aFrameIgnoresTheAnchorAndTheNoteGoesWithIt() {
        PrintOptions options = rememberHundredPercent();
        placeAboutTwoSheetsWide(options, PageGeometry.CENTIMETRE_POINTS);
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0),
                (int) (stepInGridUnits(options, PageGeometry.CENTIMETRE_POINTS) * 1.5), 30, "Bloque", FrameTint.BLUE,
                false);
        openDocument.execute(new CreateFrameCommand(frame));
        GridPoint anchor = halfASheetIn(options, PageGeometry.CENTIMETRE_POINTS);
        open(PdfExportDialog.Mode.PDF, Optional.of(anchor));
        assertThat(dialog.sheetNoteText()).isEqualTo(NOTE);

        dialog.scopeFrame().doClick();

        PrintArea frameArea = TextFonts.withFont(TextFont.DEFAULT, () -> PrintScopes.area(capture(Optional.empty()),
                new PrintScope.Frame(frame.id()), false)).orElseThrow();
        assertThat(dialog.pageLayout().orElseThrow().gridArea()).isEqualTo(PageLayout.of(frameArea.gridArea(),
                PageGeometry.standard(options, PageGeometry.CENTIMETRE_POINTS), options.scaleMode()).gridArea());
        assertThat(dialog.sheetNoteText()).isEmpty();
    }

    @Test
    void printingSendsTheAnchoredSheetCountToThePrinter() {
        PrintOptions options = rememberHundredPercent();
        placeAboutTwoSheetsWide(options, PageGeometry.CENTIMETRE_POINTS);
        GridPoint anchor = halfASheetIn(options, PageGeometry.CENTIMETRE_POINTS);
        int expected = SheetPlan.layout(wholeArea(options), PageGeometry.standard(options,
                PageGeometry.CENTIMETRE_POINTS), options.scaleMode(), Optional.of(anchor)).pageCount();
        open(PdfExportDialog.Mode.PRINT, Optional.of(anchor));

        dialog.printButton().doClick();

        assertThat(printers.prints).hasSize(1);
        assertThat(printers.prints.get(0).pages().pages()).hasSize(expected);
    }

    @ParameterizedTest
    @EnumSource(PdfExportDialog.Mode.class)
    void theDialogStartsFromTheRememberedMarginSoItAgreesWithTheCanvas(PdfExportDialog.Mode mode) {
        preferences.setPrintMarginPoints(WIDER_MARGIN);
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());

        open(mode, Optional.empty());

        assertThat(dialog.pageLayout().orElseThrow().geometry().marginPoints()).isEqualTo(WIDER_MARGIN);
    }

    @ParameterizedTest
    @EnumSource(PdfExportDialog.Mode.class)
    void showOnTheCircuitRemembersTheOptionsAndMarginTurnsTheSheetsOnAndCloses(PdfExportDialog.Mode mode) {
        preferences.setPrintMarginPoints(WIDER_MARGIN);
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        open(mode, Optional.empty());
        dialog.blackAndWhite().doClick();
        dialog.paperChoice().setSelectedItem(Paper.LETTER);
        assertThat(dialog.showOnCanvasButton().getText()).isEqualTo("Ver hojas en el circuito");
        assertThat(dialog.showOnCanvasButton().isEnabled()).isTrue();
        assertThat(preferences.showSheets()).isFalse();

        dialog.showOnCanvasButton().doClick();

        assertThat(preferences.showSheets()).isTrue();
        assertThat(preferences.printOptions("x").paper()).isEqualTo(Paper.LETTER);
        assertThat(preferences.printOptions("x").blackAndWhite()).isTrue();
        assertThat(preferences.printMarginPoints()).isEqualTo(WIDER_MARGIN);
        assertThat(closes).isEqualTo(1);
        assertThat(exporter.targets).isEmpty();
    }

    @Test
    void showOnTheCircuitIsOffForSelectionAndFrameWithATooltipSayingWhy() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 20, "Bloque",
                FrameTint.BLUE, false);
        openDocument.execute(new CreateFrameCommand(frame));
        open(PdfExportDialog.Mode.PDF, Optional.empty());
        assertThat(dialog.showOnCanvasButton().getToolTipText()).isEqualTo(
                "Recuerda estas opciones y muestra sus hojas sobre el circuito");

        dialog.scopeFrame().doClick();

        assertThat(dialog.showOnCanvasButton().isEnabled()).isFalse();
        assertThat(dialog.showOnCanvasButton().getToolTipText())
                .isEqualTo("En el circuito se muestran las hojas de «Todo el circuito»");
        dialog.scopeWhole().doClick();
        assertThat(dialog.showOnCanvasButton().isEnabled()).isTrue();
    }

    @Test
    void cancellingSavesNothingNorTurnsTheSheetsOn() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        open(PdfExportDialog.Mode.PDF, Optional.empty());
        dialog.paperChoice().setSelectedItem(Paper.LETTER);

        dialog.cancelButton().doClick();

        assertThat(preferences.showSheets()).isFalse();
        assertThat(preferences.printOptions("x").paper()).isEqualTo(Paper.A4);
        assertThat(preferences.printMarginPoints()).isEqualTo(PageGeometry.CENTIMETRE_POINTS);
    }

    /**
     * Canvas = paper: the sheets the canvas draws for the remembered options, margin and anchor are the dialog's
     * sheets, rectangle by rectangle and number by number, in both modes, with and without an anchor.
     */
    @ParameterizedTest
    @EnumSource(PdfExportDialog.Mode.class)
    void canvasEqualsPaperWithAndWithoutAnAnchor(PdfExportDialog.Mode mode) {
        PrintOptions options = rememberHundredPercentSkippingEmptySheets();
        preferences.setPrintMarginPoints(WIDER_MARGIN);
        placeAboutTwoSheetsWide(options, WIDER_MARGIN);
        for (Optional<GridPoint> anchor : List.of(Optional.<GridPoint>empty(),
                Optional.of(halfASheetIn(options, WIDER_MARGIN)))) {
            // what the canvas lays out: remembered options and margin, the document's own area
            PrintOptions remembered = preferences.printOptions(openDocument.displayName());
            PageLayout canvas = SheetPlan.layout(wholeArea(remembered), PageGeometry.standard(remembered,
                    preferences.printMarginPoints()), remembered.scaleMode(), anchor);
            List<Integer> canvasPrinted = sheetNumbers(PrintedSheets.of(SheetGrid.of(canvas),
                    TextFonts.withFont(TextFont.DEFAULT, () -> PrintScopes.wholeCircuitDrawnBounds(
                            openDocument.document(), openDocument.registry(), remembered.includeStickyNotes(),
                            remembered.blackAndWhite())),
                    Optional.empty(), remembered.skipEmptySheets()));

            open(mode, anchor);
            PageLayout paper = dialog.pageLayout().orElseThrow();
            List<Integer> paperPrinted = sheetNumbers(dialog.printedSheets().orElseThrow());
            dialog.dispose();

            assertThat(paperPrinted).as("the same sheets go out").isEqualTo(canvasPrinted);
            if (anchor.isPresent()) {
                assertThat(paperPrinted).as("the sheet between the parts is empty").hasSizeLessThan(
                        paper.pageCount());
            }

            SheetGrid canvasGrid = SheetGrid.of(canvas);
            SheetGrid paperGrid = SheetGrid.of(paper);
            assertThat(paperGrid.tiled()).isTrue();
            assertThat(paperGrid.columns()).isEqualTo(canvasGrid.columns());
            assertThat(paperGrid.rows()).isEqualTo(canvasGrid.rows());
            assertThat(paper.pageCount()).isEqualTo(canvas.pageCount());
            List<Rectangle2D> canvasRectangles = new ArrayList<>();
            List<Rectangle2D> paperRectangles = new ArrayList<>();
            for (int row = 0; row < canvasGrid.rows(); row++) {
                for (int column = 0; column < canvasGrid.columns(); column++) {
                    canvasRectangles.add(canvasGrid.sheetRectangle(column, row));
                    paperRectangles.add(paperGrid.sheetRectangle(column, row));
                    assertThat(paperGrid.sheetNumber(column, row)).isEqualTo(canvasGrid.sheetNumber(column, row));
                }
            }
            assertThat(paperRectangles).isEqualTo(canvasRectangles);
            if (anchor.isPresent()) {
                assertThat(paper.pageCount()).as("the anchor changed the sheets")
                        .isGreaterThan(automaticLayout(remembered, WIDER_MARGIN).pageCount());
            }
        }
    }

    /**
     * Canvas = paper, read from the canvas's own picture (the grid its overlay paints), not rebuilt by hand: with a
     * wider remembered margin and with a stored one narrower than 1 cm, which every layout clamps to 1 cm.
     */
    @ParameterizedTest
    @EnumSource(PdfExportDialog.Mode.class)
    void theDialogAgreesWithTheGridTheCanvasItselfPaints(PdfExportDialog.Mode mode) {
        PrintOptions options = rememberHundredPercentSkippingEmptySheets();
        placeAboutTwoSheetsWide(options, WIDER_MARGIN);
        for (double stored : new double[] {WIDER_MARGIN, 14}) {
            preferences.setPrintMarginPoints(stored);
            SheetAnchors anchors = new SheetAnchors();
            EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                    preferences, new ComponentLibrary(), List.of());
            EditorView view = new EditorView(openDocument, services, UiTestSupport.NO_WINDOW);
            view.canvas().setSize(800, 600);
            try (SheetsTestAccess canvas = SheetsTestAccess.install(anchors, view)) {
                preferences.setShowSheets(true);
                // held half a sheet to the left before they are laid out: the sheet between the parts is empty,
                // at least with 40 pt
                anchors.take(openDocument.document(), halfASheetIn(options,
                        Math.max(PageGeometry.CENTIMETRE_POINTS, stored)));
                SheetGrid canvasGrid = canvas.canvasGrid().orElseThrow();
                assertThat(canvasGrid.tiled()).isTrue();
                open(mode, anchors.anchor(openDocument.document()));
                PageLayout paper = dialog.pageLayout().orElseThrow();
                List<Integer> paperPrinted = sheetNumbers(dialog.printedSheets().orElseThrow());
                dialog.dispose();
                SheetGrid paperGrid = SheetGrid.of(paper);

                assertThat(paperPrinted).as("the same sheets go out, margin " + stored)
                        .isEqualTo(sheetNumbers(canvas.canvasPrintedSheets().orElseThrow()));
                if (stored == WIDER_MARGIN) {
                    assertThat(paperPrinted).as("the sheet between the parts is skipped")
                            .hasSizeLessThan(paper.pageCount());
                }

                assertThat(canvasGrid.layout().geometry().marginPoints()).as("margin " + stored)
                        .isEqualTo(Math.max(PageGeometry.CENTIMETRE_POINTS, stored));
                assertThat(paperGrid.columns()).isEqualTo(canvasGrid.columns());
                assertThat(paperGrid.rows()).isEqualTo(canvasGrid.rows());
                for (int row = 0; row < canvasGrid.rows(); row++) {
                    for (int column = 0; column < canvasGrid.columns(); column++) {
                        assertThat(paperGrid.sheetRectangle(column, row))
                                .isEqualTo(canvasGrid.sheetRectangle(column, row));
                        assertThat(paperGrid.sheetNumber(column, row))
                                .isEqualTo(canvasGrid.sheetNumber(column, row));
                    }
                }
            } finally {
                preferences.setShowSheets(false);
                view.dispose();
            }
        }
    }
}
