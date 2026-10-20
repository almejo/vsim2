package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.ExportImageFeatures;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
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

class ExportPdfFeaturesTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<PdfExportDialog> presented = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;
    private ExportImageFeatures imageFeatures;
    private ExportPdfFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        imageFeatures = new ExportImageFeatures(view, (parent, start, name, scale, background) -> Optional.empty(),
                (parent, message) -> { });
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(
                new PdfExportDialogTest.FakeExporter(), (parent, start, name) -> Optional.empty(),
                (parent, message) -> true, (parent, message) -> { },
                action -> new Timer(Integer.MAX_VALUE, null), Runnable::run);
        features = new ExportPdfFeatures(view, collaborators, (parent, dialog) -> presented.add(dialog), CLOCK);
    }

    @AfterEach
    void close() {
        presented.forEach(PdfExportDialog::dispose);
        features.dispose();
        imageFeatures.dispose();
        view.dispose();
        openDocument.close();
    }

    private static void perform(Action action) {
        action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    @Test
    void theItemSitsRightAfterExportImageAndIsOffOnAnEmptyDocument() {
        List<String> texts = texts(view.menu(MenuId.FILE));
        assertThat(texts.indexOf("Exportar a PDF…")).isEqualTo(texts.indexOf("Exportar imagen…") + 1);
        assertThat(features.action().isEnabled()).isFalse();
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        assertThat(features.action().isEnabled()).isTrue();
        openDocument.history().undo();
        assertThat(features.action().isEnabled()).isFalse();
        perform(features.action());
        assertThat(presented).isEmpty();
    }

    @Test
    void theItemHasTheExportPdfIcon() {
        assertThat(features.action().getValue(Action.SMALL_ICON))
                .isSameAs(Icons.get(ExportIcons.EXPORT_PDF, Icons.MENU_SIZE));
        assertThat(ExportIcons.names()).contains(ExportIcons.EXPORT_PDF);
    }

    @Test
    void theShortcutIsRegisteredAndTakenBackOnDispose() {
        assertThat(view.shortcutActions().get("file.exportPdf")).isSameAs(features.action());
        int listeners = openDocument.changeListenerCount();
        features.dispose();
        assertThat(view.shortcutActions()).doesNotContainKey("file.exportPdf");
        assertThat(texts(view.menu(MenuId.FILE))).doesNotContain("Exportar a PDF…");
        assertThat(openDocument.changeListenerCount()).isEqualTo(listeners - 1);
    }

    @Test
    void theDialogGetsTheViewsSelectionAndDoesNotTouchTheDocument() {
        Component component = UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        view.canvas().selection().select(component.id());
        long revision = openDocument.document().revision();
        boolean modified = openDocument.isModified();
        perform(features.action());
        assertThat(presented).hasSize(1);
        assertThat(presented.get(0).scopeSelection().isEnabled()).isTrue();
        assertThat(openDocument.document().revision()).isEqualTo(revision);
        assertThat(openDocument.isModified()).isEqualTo(modified);
        assertThat(view.canvas().selection().ids()).isEqualTo(Set.of(component.id()));
    }

    @Test
    void theFileChooserStartsInTheRememberedPdfFolder() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        assertThat(features.startDirectory()).isEqualTo(Path.of(System.getProperty("user.home")));
        preferences.setPrintDirectory(directory);
        assertThat(features.startDirectory()).isEqualTo(directory.toAbsolutePath());
    }
}
