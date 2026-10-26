package cl.almejo.vsim.ui.print;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.ExportImageFeatures;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
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

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class ExportPdfFeaturesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<PdfExportDialog> presented = new ArrayList<>();
    private final List<java.awt.Component> parents = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorFixture fixture;
    private EditorView view;
    private ExportImageFeatures imageFeatures;
    private ExportPdfFeatures features;

    @BeforeEach
    void create() {
        fixture = EditorFixture.create(preferences);
        openDocument = fixture.openDocument();
        view = fixture.view();
        imageFeatures = new ExportImageFeatures(view, (parent, start, name, scale, background) -> Optional.empty(),
                (parent, message) -> { });
        PdfExportDialog.Collaborators collaborators = new PdfExportDialog.Collaborators(
                new PdfExportDialogTest.FakeExporter(), (parent, start, name) -> Optional.empty(),
                (parent, message) -> true, (parent, message) -> { },
                action -> new Timer(Integer.MAX_VALUE, null), Runnable::run);
        features = new ExportPdfFeatures(view, collaborators, (parent, dialog) -> {
            parents.add(parent);
            presented.add(dialog);
        }, CLOCK);
    }

    @AfterEach
    void close() {
        presented.forEach(PdfExportDialog::dispose);
        features.dispose();
        imageFeatures.dispose();
        fixture.close();
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
    void fromAnotherPaneOfASplitWindowTheDialogGetsTheWindowsSelection() {
        Component component = UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        view.canvasPanes().split(Orientation.HORIZONTAL, 0.5);
        view.canvasPanes().split(Orientation.VERTICAL, 0.5);
        CircuitCanvas bottomRight = view.panes().get(3);
        view.canvasPanes().activate(bottomRight);
        bottomRight.selection().select(component.id());
        perform(features.action());
        assertThat(presented).hasSize(1);
        assertThat(presented.get(0).scopeSelection().isEnabled()).isTrue();
        assertThat(parents).containsExactly(view);
    }

    @Test
    void theFileChooserStartsInTheRememberedPdfFolder() {
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 10, 10, Map.of());
        assertThat(features.startDirectory()).isEqualTo(Path.of(System.getProperty("user.home")));
        preferences.setPrintDirectory(directory);
        assertThat(features.startDirectory()).isEqualTo(directory.toAbsolutePath());
    }
}
