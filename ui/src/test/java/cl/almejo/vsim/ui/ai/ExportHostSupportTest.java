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
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
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
        Messages.initialize(Messages.SPANISH);
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName("Sumador.vsim");
        document = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        exports = new ExportHostSupport(open -> List.copyOf(windows), () -> home, Runnable::run,
                SwingUtilities::invokeLater, Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC),
                opener);
        onEditor(() -> place(document, StandardComponentTypes.NAND2, 0, 0, Map.of()));
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
        Component second = onEditor(() -> place(document, StandardComponentTypes.NAND2, 40, 0, Map.of()));

        ExportReceipt all = export(request(ExportRequest.Format.PNG, new ExportRequest.Scope.WholeCircuit()));
        ExportReceipt one = export(request(ExportRequest.Format.PNG,
                new ExportRequest.Scope.Elements(Set.of(second.id().value()))));

        assertThat(one.width()).isLessThan(all.width());
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
