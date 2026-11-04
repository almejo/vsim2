package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.logisim.LogisimProject;
import cl.almejo.vsim.core.io.logisim.LogisimReader;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ExportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class LogisimExportExtensionTest {

    private static final String ITEM = "Circuito de Logisim (.circ) (beta)…";

    @TempDir
    Path folder;

    private final List<String> problems = new ArrayList<>();
    private final List<ImportReport> reports = new ArrayList<>();
    private final List<ReportHost> hosts = new ArrayList<>();
    private final List<String> reportSummaries = new ArrayList<>();
    private Optional<Path> choice = Optional.empty();
    private String suggestedName;

    private final LogisimImportDialogs dialogs = new LogisimImportDialogs() {
        @Override
        public void problem(java.awt.Component parent, String title, String message, String details) {
            problems.add(message);
        }

        @Override
        public void report(java.awt.Component parent, String title, String summary, ImportReport report,
                           ReportHost host) {
            hosts.add(host);
            reportSummaries.add(summary);
            reports.add(report);
        }

        @Override
        public LogisimImportJob.Progress progress(java.awt.Component parent) {
            throw new AssertionError("the export shows no progress");
        }
    };

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT),
                UiTestSupport.NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private LogisimExportExtension extension() {
        return new LogisimExportExtension((parent, directory, suggested) -> {
            suggestedName = suggested;
            return choice;
        }, dialogs, Runnable::run, Runnable::run);
    }

    private JMenuItem exportItem() {
        JMenu exportMenu = ExportMenu.find(view.menu(MenuId.FILE)).orElseThrow();
        for (int index = 0; index < exportMenu.getItemCount(); index++) {
            JMenuItem item = exportMenu.getItem(index);
            if (item != null && ITEM.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no " + ITEM);
    }

    private void place(String typeId, int x, int y, String label) {
        Component component = openDocument.registry().require(typeId).newComponent(
                openDocument.document().newComponentId(), new GridPoint(x, y), Rotation.R0, Map.of(), label);
        openDocument.execute(new AddElementsCommand(List.of(component)));
    }

    @Test
    void theItemFollowsTheOtherExportsIsOffForAnEmptyDocumentAndGoesAwayOnDispose() {
        Runnable disposer = extension().install(view);

        assertThat(texts(ExportMenu.find(view.menu(MenuId.FILE)).orElseThrow())).containsExactly(ITEM);
        assertThat(exportItem().isEnabled()).isFalse();
        assertThat(view.shortcutActions()).containsKey("file.exportLogisim");
        assertThat(ExportIcons.names()).contains(ExportIcons.EXPORT_LOGISIM);
        place(StandardComponentTypes.AND, 10, 10, "");
        assertThat(exportItem().isEnabled()).isTrue();

        disposer.run();
        assertThat(ExportMenu.find(view.menu(MenuId.FILE))).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKey("file.exportLogisim");
    }

    @Test
    void theChosenFileGetsTheCircuitAsALogisimProjectAndNoReportWhenNothingIsLeftOut() throws Exception {
        extension().install(view);
        place(StandardComponentTypes.AND, 10, 10, "");
        Path file = folder.resolve("compuerta.circ");
        choice = Optional.of(file);

        exportItem().doClick();

        assertThat(suggestedName).endsWith(".circ");
        try (InputStream input = Files.newInputStream(file)) {
            LogisimProject project = new LogisimReader().read(input);
            assertThat(project.circuits()).singleElement()
                    .satisfies(circuit -> assertThat(circuit.components()).hasSize(1));
        }
        assertThat(reports).isEmpty();
        assertThat(problems).isEmpty();
        assertThat(openDocument.isModified()).isTrue();
    }

    @Test
    void theMainCircuitIsNamedAfterTheCircuitAndTheFileAfterTheWindow() throws Exception {
        extension().install(view);
        place(StandardComponentTypes.AND, 10, 10, "");
        openDocument.document().setName("Simple Uprog Lrv32im");
        openDocument.showUntitledAs("lrv32im-up");
        Path file = folder.resolve("lrv32im-up.circ");
        choice = Optional.of(file);

        exportItem().doClick();

        assertThat(suggestedName).isEqualTo("lrv32im-up.circ");
        try (InputStream input = Files.newInputStream(file)) {
            LogisimProject project = new LogisimReader().read(input);
            assertThat(project.mainCircuit()).isEqualTo("Simple Uprog Lrv32im");
        }
    }

    @Test
    void aPartLogisimLacksIsReportedByItsName() {
        extension().install(view);
        place(StandardComponentTypes.ALU, 10, 10, "U1");
        choice = Optional.of(folder.resolve("alu.circ"));

        exportItem().doClick();

        assertThat(Files.exists(folder.resolve("alu.circ"))).isTrue();
        assertThat(reports).singleElement().satisfies(report -> {
            ImportReport.KindGroup kind = report.circuits().get(0).kinds().get(0);
            assertThat(kind.title()).isEqualTo("Sin soporte (1)");
            assertThat(kind.entries()).singleElement().satisfies(entry -> {
                assertThat(entry.explanation()).contains("ALU «U1»");
                assertThat(entry.places()).singleElement().satisfies(place -> {
                    assertThat(place.text()).isEqualTo("ALU «U1» (10, 10)");
                    assertThat(place.element()).isPresent();
                    assertThat(hosts.get(0).goTo(place.element().get())).isTrue();
                    assertThat(view.selection().ids()).containsExactly(place.element().get());
                });
            });
        });
        assertThat(reportSummaries.get(0)).contains("alu.circ");
    }

    @Test
    void cancellingTheChooserWritesNothing() {
        extension().install(view);
        place(StandardComponentTypes.AND, 10, 10, "");

        exportItem().doClick();

        assertThat(folder).isEmptyDirectory();
        assertThat(reports).isEmpty();
        assertThat(problems).isEmpty();
    }

    @Test
    void aFileThatCannotBeWrittenTellsTheUser() {
        extension().install(view);
        place(StandardComponentTypes.AND, 10, 10, "");
        choice = Optional.of(folder.resolve("no-existe").resolve("compuerta.circ"));

        exportItem().doClick();

        assertThat(problems).singleElement().asString().contains("compuerta.circ");
        assertThat(reports).isEmpty();
    }
}
