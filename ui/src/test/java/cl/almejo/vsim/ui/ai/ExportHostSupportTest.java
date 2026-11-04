package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.export.ExportReceipt;
import cl.almejo.vsim.automation.export.ExportRequest;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.print.PageGeometry;
import cl.almejo.vsim.ui.print.PageLayout;
import cl.almejo.vsim.ui.print.PageOrientation;
import cl.almejo.vsim.ui.print.Paper;
import cl.almejo.vsim.ui.print.PrintOptions;
import cl.almejo.vsim.ui.print.PrintScope;
import cl.almejo.vsim.ui.print.PrintScopes;
import cl.almejo.vsim.ui.print.ScaleMode;
import cl.almejo.vsim.ui.print.SheetPlan;
import cl.almejo.vsim.ui.sheets.SheetAnchors;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.onEditor;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The AI's exports (spec §7): the menus' code paths, only into the export folder, never over a file. */
class ExportHostSupportTest {

    private static final DocumentId ID = new DocumentId("circuit-1");

    @TempDir
    Path home;
    @TempDir
    Path remembered;

    private final List<ShownWindow> windows = new ArrayList<>();
    private final List<EditorView> views = new ArrayList<>();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final FakeOpener opener = new FakeOpener();
    private final SheetAnchors sheetAnchors = new SheetAnchors();
    private OpenDocument document;
    private ExportHostSupport exports;

    /** Records the files it was asked to show; {@code works} and {@code available} say how the desktop behaves. */
    private static final class FakeOpener implements FolderOpener {
        final List<Path> opened = new ArrayList<>();
        boolean available = true;
        boolean works = true;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean open(Path file) {
            opened.add(file);
            return works;
        }
    }

    @BeforeEach
    void aCircuitInAWindow() throws Exception {
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName("Sumador.vsim");
        document = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        exports = new ExportHostSupport(open -> List.copyOf(windows), () -> home, Runnable::run,
                SwingUtilities::invokeLater, Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC),
                opener, sheetAnchors);
        onEditor(() -> place(document, StandardComponentTypes.AND, 0, 0, Map.of(StandardComponentTypes.NEGATE, true)));
        AtomicReference<EditorView> created = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            EditorServices services = new EditorServices(PainterRegistry.standard(),
                    new Appearance(ColorScheme.LIGHT), preferences, new ComponentLibrary(), List.of());
            created.set(new EditorView(document, services, NO_WINDOW));
        });
        views.add(created.get());
        windows.add(new ShownWindow(created.get(), created.get(), "", List.of()));
    }

    @AfterEach
    void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> views.forEach(EditorView::dispose));
        document.close();
    }

    @Test
    void aPngGoesIntoTheRememberedFolderUnderTheCircuitsNameAndTheWindowSaysSo() throws Exception {
        preferences.setExportDirectory(remembered);

        ExportReceipt receipt = export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));

        assertThat(receipt.fileName()).isEqualTo("Sumador.png");
        assertThat(Path.of(receipt.path())).isEqualTo(remembered.resolve("Sumador.png").toAbsolutePath());
        assertThat(ImageIO.read(Path.of(receipt.path()).toFile()).getWidth()).isEqualTo(receipt.width());
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(views.getFirst().statusBar().text()).contains("La IA guardó Sumador.png en " + remembered);
    }

    @Test
    void theNoticeOffersAbrirCarpetaWhichShowsTheFileInItsFolder() throws Exception {
        preferences.setExportDirectory(remembered);

        ExportReceipt receipt = export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));
        SwingUtilities.invokeAndWait(() -> { });

        javax.swing.JButton openFolder = views.getFirst().statusBar().noticeAction();
        assertThat(openFolder.isVisible()).isTrue();
        assertThat(openFolder.getText()).isEqualTo("Abrir carpeta");
        SwingUtilities.invokeAndWait(openFolder::doClick);
        assertThat(opener.opened).containsExactly(Path.of(receipt.path()));
    }

    @Test
    void whenTheFolderCannotBeOpenedTheNoticeSaysWhereTheFileIs() throws Exception {
        preferences.setExportDirectory(remembered);
        opener.works = false;

        export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> views.getFirst().statusBar().noticeAction().doClick());

        assertThat(views.getFirst().statusBar().text()).contains("No pude abrir la carpeta " + remembered);
    }

    @Test
    void withoutADesktopToOpenFoldersTheNoticeHasNoButton() throws Exception {
        preferences.setExportDirectory(remembered);
        opener.available = false;

        export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(views.getFirst().statusBar().noticeAction().isVisible()).isFalse();
        assertThat(views.getFirst().statusBar().text()).contains("La IA guardó Sumador.png");
    }

    @Test
    void aNameThatIsTakenGetsASuffixAndTheFileThereIsNeverTouched() throws Exception {
        preferences.setExportDirectory(remembered);
        Files.writeString(remembered.resolve("Sumador.png"), "the user's own file");

        ExportReceipt second = export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));
        ExportReceipt third = export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));

        assertThat(second.fileName()).isEqualTo("Sumador-2.png");
        assertThat(third.fileName()).isEqualTo("Sumador-3.png");
        assertThat(Files.readString(remembered.resolve("Sumador.png"))).isEqualTo("the user's own file");
    }

    @Test
    void withoutARememberedFolderTheExportGoesToTheVsimFolderOfTheHome() throws Exception {
        ExportReceipt receipt = export(request(ExportRequest.Format.SVG, new ExportRequest.Scope.WholeCircuit()));

        assertThat(Path.of(receipt.folder())).isEqualTo(home.resolve("VSim").toAbsolutePath());
        assertThat(Files.readString(Path.of(receipt.path()))).contains("<svg");
        assertThat(preferences.exportDirectory()).isEmpty();
    }

    @Test
    void aPdfUsesThePdfFolderAndTheMenusOptions() throws Exception {
        preferences.setPrintDirectory(remembered);
        Frame frame = onEditor(() -> {
            Frame created = new Frame(document.document().newFrameId(), new GridPoint(-2, -4), 20, 16, "Bloque",
                    FrameTint.BLUE, false);
            document.execute(new AddElementsCommand(List.of(created)));
            return created;
        });

        ExportReceipt receipt = export(new ExportRequest(ID, ExportRequest.Format.PDF,
                new ExportRequest.Scope.Frame(frame.id().value()), Optional.of("bloque"),
                ExportRequest.ImageOptions.DEFAULT,
                new ExportRequest.PdfOptions("LETTER", "LANDSCAPE", 0, true, true, true, "Mi bloque")));

        assertThat(receipt.fileName()).isEqualTo("bloque.pdf");
        assertThat(receipt.pages()).isEqualTo(1);
        assertThat(Files.readString(Path.of(receipt.path()), java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    /** The sheets of the canvas are the sheets of the AI's PDF of the whole circuit (and of nothing else). */
    @Test
    void aPdfOfTheWholeCircuitUsesTheSheetsShownOnTheCircuit() throws Exception {
        preferences.setPrintDirectory(remembered);
        onEditor(() -> place(document, StandardComponentTypes.AND, 95, 0, Map.of()));
        ExportRequest.PdfOptions hundredPercent = new ExportRequest.PdfOptions("A4", "PORTRAIT", 100, true, false,
                false, "");
        ExportRequest request = new ExportRequest(ID, ExportRequest.Format.PDF, new ExportRequest.Scope.WholeCircuit(),
                Optional.empty(), ExportRequest.ImageOptions.DEFAULT, hundredPercent);
        PrintOptions options = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4, PageOrientation.PORTRAIT,
                new ScaleMode.Percentage(100), true, false, false, "Sumador.vsim");
        preferences.setPrintOptions(skipping(options, false)); // every sheet of the lattice counts here
        PageGeometry geometry = PageGeometry.standard(options, PageGeometry.CENTIMETRE_POINTS);
        Rectangle2D area = onEditor(() -> PrintScopes.wholeCircuitArea(document.document(), document.registry(),
                false)).orElseThrow();
        PageLayout automatic = PageLayout.of(area, geometry, options.scaleMode());
        double step = (automatic.window().getWidth() - automatic.overlapPoints())
                / (GridPoint.PIXELS_PER_UNIT * automatic.scale());
        GridPoint anchor = new GridPoint((int) Math.floor(area.getX() + step / 2), (int) Math.floor(area.getY()));
        PageLayout anchored = SheetPlan.layout(area, geometry, options.scaleMode(), Optional.of(anchor));
        assertThat(anchored.pageCount()).as("the fixture must make the anchor matter")
                .isNotEqualTo(automatic.pageCount());

        assertThat(export(request).pages()).as("no sheets shown: the automatic layout")
                .isEqualTo(automatic.pageCount());
        sheetAnchors.take(document.document(), anchor);
        assertThat(export(request).pages()).as("sheets shown: their lattice").isEqualTo(anchored.pageCount());
    }

    /** «Saltar hojas vacías» as the user left it in the dialog, for the whole circuit like the sheets shown. */
    @Test
    void aPdfOfTheWholeCircuitSkipsTheEmptySheetsAsTheUserRemembered() throws Exception {
        preferences.setPrintDirectory(remembered);
        onEditor(() -> place(document, StandardComponentTypes.AND, 200, 0, Map.of()));
        ExportRequest request = new ExportRequest(ID, ExportRequest.Format.PDF,
                new ExportRequest.Scope.WholeCircuit(), Optional.empty(), ExportRequest.ImageOptions.DEFAULT,
                new ExportRequest.PdfOptions("A4", "PORTRAIT", 100, true, false, false, ""));
        PrintOptions hundredPercent = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4,
                PageOrientation.PORTRAIT, new ScaleMode.Percentage(100), true, false, false, "Sumador.vsim");
        Rectangle2D area = onEditor(() -> PrintScopes.wholeCircuitArea(document.document(), document.registry(),
                false)).orElseThrow();
        int every = PageLayout.of(area, PageGeometry.standard(hundredPercent, PageGeometry.CENTIMETRE_POINTS),
                hundredPercent.scaleMode()).pageCount();
        assertThat(every).as("the fixture: sheets between the two gates").isGreaterThan(2);

        preferences.setPrintOptions(skipping(hundredPercent, true));
        assertThat(export(request).pages()).as("only the gates' sheets").isEqualTo(2);
        preferences.setPrintOptions(skipping(hundredPercent, false));
        assertThat(export(request).pages()).as("off: every sheet").isEqualTo(every);
    }

    /** The option as the user left it applies to every scope, as the dialog applies it (a selection here). */
    @Test
    void aPdfOfASelectionSkipsTheEmptySheetsToo() throws Exception {
        preferences.setPrintDirectory(remembered);
        Set<String> both = onEditor(() -> {
            Component left = place(document, StandardComponentTypes.AND, 0, 0, Map.of());
            Component right = place(document, StandardComponentTypes.AND, 200, 0, Map.of());
            return Set.of(left.id().value(), right.id().value());
        });
        ExportRequest request = new ExportRequest(ID, ExportRequest.Format.PDF,
                new ExportRequest.Scope.Elements(both), Optional.empty(), ExportRequest.ImageOptions.DEFAULT,
                new ExportRequest.PdfOptions("A4", "PORTRAIT", 100, true, false, false, ""));
        PrintOptions hundredPercent = new PrintOptions(new PrintScope.Selection(Set.of()), Paper.A4,
                PageOrientation.PORTRAIT, new ScaleMode.Percentage(100), true, false, false, "Sumador.vsim");

        preferences.setPrintOptions(skipping(hundredPercent, false));
        int every = export(request).pages();
        assertThat(every).as("the fixture: sheets between the two gates").isGreaterThan(2);
        preferences.setPrintOptions(skipping(hundredPercent, true));
        assertThat(export(request).pages()).as("only the gates' sheets").isEqualTo(2);
    }

    /** The refusal above 1 000 sheets counts the sheets that go out, as the dialog does. */
    @Test
    void theThousandSheetRefusalCountsTheSheetsThatGoOut() throws Exception {
        preferences.setPrintDirectory(remembered);
        onEditor(() -> {
            place(document, StandardComponentTypes.AND, 0, 0, Map.of());
            return place(document, StandardComponentTypes.AND, 2000, 1000, Map.of());
        });
        ExportRequest request = new ExportRequest(ID, ExportRequest.Format.PDF,
                new ExportRequest.Scope.WholeCircuit(), Optional.empty(), ExportRequest.ImageOptions.DEFAULT,
                new ExportRequest.PdfOptions("A4", "PORTRAIT", 400, true, false, false, ""));
        PrintOptions fourHundred = new PrintOptions(new PrintScope.WholeCircuit(), Paper.A4,
                PageOrientation.PORTRAIT, new ScaleMode.Percentage(400), true, false, false, "Sumador.vsim");

        preferences.setPrintOptions(skipping(fourHundred, false));
        assertThatThrownBy(() -> export(request)).isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        preferences.setPrintOptions(skipping(fourHundred, true));
        assertThat(export(request).pages()).as("only the corners go out").isBetween(1, 10);
    }

    private static PrintOptions skipping(PrintOptions base, boolean skipEmptySheets) {
        return new PrintOptions(base.scope(), base.paper(), base.orientation(), base.scaleMode(), base.withValues(),
                base.blackAndWhite(), base.headerFooter(), base.title(), base.includeStickyNotes(),
                base.sheetGuides(), base.showDate(), base.showFileName(), base.showSheetNumber(), skipEmptySheets);
    }

    @Test
    void anUnknownFrameOrElementOrAnEmptyScopeIsRefusedAndNothingIsWritten() throws Exception {
        preferences.setExportDirectory(remembered);

        assertRefused(request(ExportRequest.Format.PNG, new ExportRequest.Scope.Frame("f9")));
        assertRefused(request(ExportRequest.Format.PDF, new ExportRequest.Scope.Elements(Set.of("c99"))));
        try (var listed = Files.list(remembered)) {
            assertThat(listed).isEmpty();
        }
    }

    @Test
    void theAiNeverChoosesTheFolderThroughTheName() {
        assertThat(ExportHostSupport.baseName("../../etc/passwd")).doesNotContain("/").doesNotStartWith(".");
        assertThat(ExportHostSupport.baseName("C:\\temp\\x.vsim")).doesNotContain("\\").doesNotContain(":");
        assertThat(ExportHostSupport.baseName("  ")).isEqualTo("circuito");
        assertThat(ExportHostSupport.baseName("Latch SR.png")).isEqualTo("Latch SR");
    }

    @Test
    void aNameIsCutWholeWithinWhatFileSystemsTakeAndNeverAReservedOne() {
        String emoji = "\uD83D\uDE00";
        String cut = ExportHostSupport.baseName(emoji.repeat(100));
        assertThat(cut.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(ExportHostSupport.MAXIMUM_NAME_BYTES);
        assertThat(cut).isEqualTo(emoji.repeat(cut.length() / 2));
        assertThat(ExportHostSupport.baseName("a".repeat(300))).hasSize(ExportHostSupport.MAXIMUM_NAME_LENGTH);
        assertThat(ExportHostSupport.baseName("x\uD800y")).isEqualTo("xy");
        assertThat(ExportHostSupport.baseName("Sumador. . ")).isEqualTo("Sumador");
        assertThat(ExportHostSupport.baseName("CON")).isEqualTo("_CON");
        assertThat(ExportHostSupport.baseName("lpt1.txt")).isEqualTo("_lpt1.txt");
        assertThat(ExportHostSupport.baseName("console")).isEqualTo("console");
    }

    @Test
    void theOnlyElementsExportedAreTheOnesNamed() throws Exception {
        preferences.setExportDirectory(remembered);
        Component second = onEditor(() -> place(document, StandardComponentTypes.AND, 40, 0, Map.of(StandardComponentTypes.NEGATE, true)));

        ExportReceipt all = export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));
        ExportReceipt one = export(request(ExportRequest.Format.PNG,
                new ExportRequest.Scope.Elements(Set.of(second.id().value()))));

        assertThat(one.width()).isLessThan(all.width());
    }

    /** Sticky notes stay out of the AI's exports unless it asks, as in the menus: a far-away note changes the size. */
    @Test
    void stickyNotesAreLeftOutOfTheImageUnlessTheRequestAsksForThem() throws Exception {
        preferences.setExportDirectory(remembered);
        onEditor(() -> {
            document.execute(new AddElementsCommand(List.of(new StickyNote(document.document().newStickyNoteId(),
                    new GridPoint(80, 60), 8, 6, StickyColor.YELLOW, new StickyText("lejos"), false))));
            return null;
        });

        ExportReceipt without = export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));
        ExportReceipt with = export(new ExportRequest(ID, ExportRequest.Format.PNG,
                new ExportRequest.Scope.WholeCircuit(), Optional.empty(), ExportRequest.ImageOptions.DEFAULT,
                ExportRequest.PdfOptions.DEFAULT, true));

        assertThat(with.width()).isGreaterThan(without.width());
        assertThat(with.height()).isGreaterThan(without.height());
    }

    private ExportRequest request(ExportRequest.Format format, ExportRequest.Scope scope) {
        return new ExportRequest(ID, format, scope, Optional.empty(), ExportRequest.ImageOptions.DEFAULT,
                ExportRequest.PdfOptions.DEFAULT);
    }

    private ExportReceipt export(ExportRequest request) throws Exception {
        ExportHostSupport.Prepared prepared = onEditor(() -> exports.prepare(ID, new DocumentVersion(
                document.contentVersion()), document, request));
        return exports.write(prepared);
    }

    private void assertRefused(ExportRequest request) {
        assertThatThrownBy(() -> export(request)).isInstanceOfSatisfying(AutomationException.class,
                refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }
}
