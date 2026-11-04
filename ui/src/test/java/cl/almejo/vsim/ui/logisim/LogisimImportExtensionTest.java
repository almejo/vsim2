package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class LogisimImportExtensionTest {

    private static final String PROJECT_WITH_AN_UNKNOWN_LIBRARY = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <project source="2.7.1" version="1.0">
              <lib desc="#Wiring" name="0"/>
              <lib desc="file#mine.jar" name="9"/>
              <main name="main"/>
              <circuit name="main">
                <comp lib="0" loc="(100,100)" name="Pin"/>
                <comp lib="9" loc="(200,100)" name="Thing"/>
              </circuit>
            </project>
            """;

    private static final String NEGATED_GATE = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <project source="2.7.1" version="1.0">
              <lib desc="#Wiring" name="0"/>
              <lib desc="#Gates" name="1"/>
              <main name="main"/>
              <circuit name="main">
                <comp lib="1" loc="(200,100)" name="AND Gate">
                  <a name="negate0" val="true"/>
                </comp>
              </circuit>
            </project>
            """;

    private static final String CLEAN_PROJECT = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <project source="2.7.1" version="1.0">
              <lib desc="#Wiring" name="0"/>
              <main name="Mi Sumador"/>
              <circuit name="Mi Sumador">
                <comp lib="0" loc="(100,100)" name="Pin"/>
              </circuit>
            </project>
            """;

    @TempDir
    Path folder;

    private final List<CircuitDocument> untitled = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final List<String> problemDetails = new ArrayList<>();
    private final List<ImportReport> reports = new ArrayList<>();
    private final List<ReportHost> hosts = new ArrayList<>();
    private final List<EditorView> shownViews = new ArrayList<>();
    private final List<OpenDocument> shownDocuments = new ArrayList<>();
    private final List<String> reportSummaries = new ArrayList<>();
    private final List<Path> remembered = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private Optional<Path> choice = Optional.empty();
    private Path chooserStartedIn;

    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public void openUntitled(CircuitDocument document) {
            opened.add("window");
            untitled.add(document);
        }

        @Override
        public Optional<EditorView> showUntitled(CircuitDocument document, String windowName) {
            openUntitled(document);
            OpenDocument shown = new OpenDocument(document, StandardComponentTypes.registry(), null, false);
            shown.showUntitledAs(windowName);
            shownDocuments.add(shown);
            EditorView shownView = new EditorView(shown, EditorServices.of(PainterRegistry.standard(),
                    ColorScheme.LIGHT), this);
            shownViews.add(shownView);
            return Optional.of(shownView);
        }
    };

    private final LogisimImportDialogs dialogs = new LogisimImportDialogs() {
        @Override
        public void problem(Component parent, String title, String message, String details) {
            problems.add(message);
            problemDetails.add(details);
        }

        @Override
        public void report(Component parent, String title, String summary, ImportReport report, ReportHost host) {
            opened.add("report");
            hosts.add(host);
            reportSummaries.add(summary);
            reports.add(report);
        }

        @Override
        public LogisimImportJob.Progress progress(Component parent) {
            return new LogisimImportJob.Progress() {
                @Override public void show(Runnable cancel) { }
                @Override public void hide() { }
            };
        }
    };

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT), window);
    }

    @AfterEach
    void close() {
        shownViews.forEach(EditorView::dispose);
        shownDocuments.forEach(OpenDocument::close);
        view.dispose();
        openDocument.close();
    }

    private LogisimImportExtension extension() {
        return new LogisimImportExtension(() -> folder, remembered::add, (parent, directory) -> {
            chooserStartedIn = directory;
            return choice;
        }, component -> Optional.of(window), dialogs, Runnable::run, Runnable::run,
                (milliseconds, action) -> () -> { });
    }

    private JMenuItem importItem() {
        JMenu importMenu = ImportMenu.find(view.menu(MenuId.FILE)).orElseThrow();
        return importMenu.getItem(importMenu.getItemCount() - 1);
    }

    private Path circ(String name, String content) throws IOException {
        return Files.writeString(folder.resolve(name), content);
    }

    @Test
    void theItemIsInTheImportSubmenuAndGoesAwayOnDispose() {
        Runnable disposer = extension().install(view);

        assertThat(texts(ImportMenu.find(view.menu(MenuId.FILE)).orElseThrow()))
                .containsExactly("Circuito de Logisim (.circ) (beta)…");
        disposer.run();
        assertThat(ImportMenu.find(view.menu(MenuId.FILE))).isEmpty();
    }

    @Test
    void cancellingTheChooserDoesNothing() {
        extension().install(view);

        importItem().doClick();

        assertThat(chooserStartedIn).isEqualTo(folder);
        assertThat(untitled).isEmpty();
        assertThat(problems).isEmpty();
        assertThat(remembered).isEmpty();
    }

    @Test
    void theChosenFileOpensAsAnUnsavedDocumentNamedAfterItAndItsFolderIsRemembered() throws IOException {
        extension().install(view);
        Path file = circ("mi circuito.circ", CLEAN_PROJECT);
        choice = Optional.of(file);

        importItem().doClick();

        assertThat(untitled).hasSize(1);
        // The window and the file it suggests follow the .circ; the circuit keeps Logisim's main circuit name.
        assertThat(shownDocuments.get(0).displayName()).isEqualTo("mi circuito");
        assertThat(shownDocuments.get(0).suggestedFileName()).isEqualTo("mi circuito.vsim");
        assertThat(untitled.get(0).name()).isEqualTo("Mi Sumador");
        assertThat(untitled.get(0).components()).hasSize(1);
        assertThat(remembered).containsExactly(file);
        assertThat(reports).isEmpty();
    }

    @Test
    void whenThereAreNotesTheReportShowsAfterTheDocumentOpens() throws IOException {
        extension().install(view);
        choice = Optional.of(circ("con-avisos.circ", PROJECT_WITH_AN_UNKNOWN_LIBRARY));

        importItem().doClick();

        assertThat(opened).containsExactly("window", "report");
        assertThat(reports.get(0).isEmpty()).isFalse();
        assertThat(reportSummaries.get(0)).contains("con-avisos");
    }

    @Test
    void aLineThatNamesAPartGoesToItInTheNewDocumentsWindow() throws IOException {
        extension().install(view);
        choice = Optional.of(circ("negada.circ", NEGATED_GATE));

        importItem().doClick();

        ImportReport.Place place = reports.get(0).circuits().stream().flatMap(circuit -> circuit.kinds().stream())
                .flatMap(kind -> kind.entries().stream()).flatMap(entry -> entry.places().stream())
                .filter(candidate -> candidate.element().isPresent()).findFirst().orElseThrow();
        ReportHost host = hosts.get(0);
        assertThat(place.text()).startsWith("AND");
        assertThat(host.goTo(place.element().get())).isTrue();
        assertThat(shownViews.get(0).selection().ids()).containsExactly(place.element().get());
        assertThat(host.goTo(new cl.almejo.vsim.core.document.ElementId("nope"))).isFalse();
    }

    @Test
    void theReportWindowSizeIsRememberedInThePreferences() throws IOException {
        extension().install(view);
        choice = Optional.of(circ("con-avisos.circ", PROJECT_WITH_AN_UNKNOWN_LIBRARY));
        importItem().doClick();

        ReportHost host = hosts.get(0);
        assertThat(host.size()).isEmpty();
        host.rememberSize(new java.awt.Dimension(900, 700));
        assertThat(host.size()).contains(new java.awt.Dimension(900, 700));
    }

    @Test
    void aFileThatIsNotXmlGetsItsOwnReasonAndOpensNothing() throws IOException {
        extension().install(view);
        choice = Optional.of(circ("texto.circ", "esto no es xml"));

        importItem().doClick();

        assertThat(untitled).isEmpty();
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("texto.circ").contains("no es un archivo XML");
        assertThat(problemDetails.get(0)).contains("not XML");
    }

    @Test
    void anXmlFileThatIsNotALogisimProjectGetsItsOwnReason() throws IOException {
        extension().install(view);
        choice = Optional.of(circ("otro.circ", "<?xml version=\"1.0\"?><otra-cosa/>"));

        importItem().doClick();

        assertThat(untitled).isEmpty();
        assertThat(problems.get(0)).contains("otro.circ").contains("no es un proyecto de Logisim");
    }

    @Test
    void aMissingFileIsAReadProblem() {
        extension().install(view);
        choice = Optional.of(folder.resolve("no-esta.circ"));

        importItem().doClick();

        assertThat(untitled).isEmpty();
        assertThat(problems.get(0)).contains("no-esta.circ").contains("No se pudo leer");
    }
}
