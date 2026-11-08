package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportImageExtension;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
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

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.JRootPane;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/** Archivo ▸ Exportar ▸ «Imagen de la ventana…»: a PNG of the window at the screen's scale, where the user chose. */
class WindowExportFeaturesTest {

    private static final int WINDOW_WIDTH = 700;
    private static final int WINDOW_HEIGHT = 500;

    @TempDir
    Path folder;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> messages = new ArrayList<>();
    private final List<Path> offeredFolders = new ArrayList<>();
    private final List<String> suggestedNames = new ArrayList<>();
    private Optional<Path> choice = Optional.empty();
    private OpenDocument openDocument;
    private EditorView view;
    private boolean viewDisposed;

    @BeforeEach
    void aViewInAWindow() {
        Messages.initialize(Messages.SPANISH);
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName("Sumador");
        openDocument = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(new ExportImageExtension(), extension()));
        view = new EditorView(openDocument, services, UiTestSupport.NO_WINDOW);
        JRootPane root = new JRootPane();
        root.setJMenuBar(view.menuBar());
        root.setContentPane(view);
        root.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        layOut(root);
    }

    @AfterEach
    void close() {
        if (!viewDisposed) {
            view.dispose();
        }
        openDocument.close();
    }

    private WindowExportExtension extension() {
        return new WindowExportExtension((Component parent, Path directory, String suggestedName) -> {
            offeredFolders.add(directory);
            suggestedNames.add(suggestedName);
            return choice;
        }, (parent, message) -> messages.add(message));
    }

    private Action action() {
        return view.shortcutActions().get(WindowExportFeatures.SHORTCUT_ID);
    }

    @Test
    void theItemComesRightAfterImagenInTheExportMenuAndWorksOnAnEmptyCircuit() {
        assertThat(texts(ExportMenu.find(view.menu(MenuId.FILE)).orElseThrow()))
                .containsExactly("Imagen…", "Imagen de la ventana…");
        assertThat(openDocument.document().isEmpty()).isTrue();
        assertThat(action().isEnabled()).isTrue();
    }

    @Test
    void theChosenFileGetsAPngOfTheWindowAndItsFolderIsRemembered() throws Exception {
        preferences.setExportDirectory(folder);
        choice = Optional.of(folder.resolve("ventana.png"));

        perform(action());

        assertThat(offeredFolders).containsExactly(folder);
        assertThat(suggestedNames).containsExactly("Sumador.png");
        BufferedImage written = ImageIO.read(folder.resolve("ventana.png").toFile());
        // no screen in the tests: the scale is 1
        assertThat(written.getWidth()).isEqualTo(WINDOW_WIDTH);
        assertThat(written.getHeight()).isEqualTo(WINDOW_HEIGHT);
        assertThat(preferences.exportDirectory()).contains(folder);
        assertThat(messages).isEmpty();
    }

    @Test
    void cancellingTheChooserWritesNothing() throws Exception {
        perform(action());

        try (Stream<Path> files = Files.list(folder)) {
            assertThat(files).isEmpty();
        }
        assertThat(messages).isEmpty();
    }

    @Test
    void aFileThatCannotBeWrittenIsReportedInASentence() {
        choice = Optional.of(folder.resolve("no-such-folder").resolve("ventana.png"));

        perform(action());

        assertThat(messages).singleElement().asString().startsWith("No se pudo exportar ventana.png");
    }

    @Test
    void disposingTakesTheItemAndTheShortcutBack() {
        view.dispose();
        viewDisposed = true;

        assertThat(view.shortcutActions()).doesNotContainKey(WindowExportFeatures.SHORTCUT_ID);
    }
}
