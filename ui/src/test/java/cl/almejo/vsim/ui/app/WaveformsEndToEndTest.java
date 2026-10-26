package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.session.AutosaveService;
import cl.almejo.vsim.ui.session.RecentFiles;
import cl.almejo.vsim.ui.session.RecoveryStore;
import cl.almejo.vsim.ui.session.WindowState;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.waveform.ProbeActions;
import cl.almejo.vsim.ui.waveform.WaveformPanel;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** M6-A against B's recovery and recents and C's shortcuts, with every production extension installed. */
class WaveformsEndToEndTest {

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private final List<OpenDocument> opened = new ArrayList<>();
    private LibraryService service;
    private EditorServices services;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, library, registry, Runnable::run);
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
    }

    @AfterEach
    void close() {
        opened.forEach(OpenDocument::close);
        service.close();
    }

    private OpenDocument track(OpenDocument document) {
        opened.add(document);
        return document;
    }

    private static JMenuItem viewItem(EditorView view, String text) {
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no View item " + text);
    }

    @Test
    void channelsSurviveAutosaveAndRecoveryAndShowInTwoViewsOfTheReopenedFileWhoseWindowStateRecentsKeeps()
            throws Exception {
        OpenDocument original = track(OpenDocument.createEmpty(registry));
        Component toggle = place(original, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component gate = place(original, StandardComponentTypes.NOT, 4, -1, Map.of());
        ProbeActions actions = new ProbeActions(original);
        ProbeActions.AddResult added = actions.addSelection(List.of(toggle.id(), gate.id()));
        assertThat(added.addedIds()).hasSize(2);
        Path file = directory.resolve("cadena.vsim");
        original.save(file);
        assertThat(original.isModified()).isFalse();

        // Autosave copies a modified document whose changeCount moved (overview C5): a channel edit is enough.
        RecoveryStore store = new RecoveryStore(directory.resolve("recovery"));
        AutosaveService autosave = new AutosaveService(store, Clock.systemUTC(), Runnable::run);
        try {
            autosave.track(original);
            autosave.tick();
            Path copy = store.contentPath(original.recoveryId());
            assertThat(copy).as("saved and unchanged: no copy").doesNotExist();

            long before = original.changeCount();
            actions.rename(added.addedIds().get(0), "entrada");
            assertThat(original.isModified()).isTrue();
            assertThat(original.changeCount()).isNotEqualTo(before);
            autosave.tick();
            assertThat(copy).exists();

            // Recovering the copy brings the channels back, modified, with the original path.
            OpenDocument recovered = track(OpenDocument.openRecovered(copy, file, registry));
            assertThat(recovered.isModified()).isTrue();
            assertThat(recovered.document().probes()).isEqualTo(original.document().probes());
            assertThat(recovered.document().probes().get(0).name()).isEqualTo("entrada");
        } finally {
            // Shutting down also deletes this run's recovery folder.
            autosave.shutdown();
        }

        // Recientes keeps the window state; reopening the saved file shows its channels in two views.
        RecentFiles recent = new RecentFiles(preferences);
        recent.remember(file, new WindowState(10, 20, 900, 700, false, new ViewState(2.0, 5.0, 6.0)));
        assertThat(recent.stateFor(file)).hasValueSatisfying(state -> assertThat(state.view().zoom()).isEqualTo(2.0));
        OpenDocument reopened = track(OpenDocument.open(file, registry));
        EditorView first = new EditorView(reopened, services, NO_WINDOW);
        EditorView second = new EditorView(reopened, services, NO_WINDOW);
        try {
            for (EditorView view : List.of(first, second)) {
                assertThat(view.bottomDock().isDockShowing()).isTrue();
                WaveformPanel panel = (WaveformPanel) view.bottomDock().content().orElseThrow();
                assertThat(panel.channelNames()).containsExactly("c1.out", "c2.out");
            }
        } finally {
            second.dispose();
            first.dispose();
        }
    }

    @Test
    void theWaveformsToggleIsInTheCatalogAndTakesTheAssignedShortcut() {
        assertThat(ShortcutCatalog.find("waveforms.toggle")).contains(new ShortcutEntry("waveforms.toggle",
                "menu.view.waveforms", MenuId.VIEW, Scope.WINDOW, Optional.empty()));
        List<ShortcutEntry> viewEntries = ShortcutCatalog.entries().stream()
                .filter(entry -> entry.group() == MenuId.VIEW).toList();
        assertThat(viewEntries.get(viewEntries.size() - 2).id()).isEqualTo("waveforms.toggle"); // «Presentar» last

        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0);
        new KeyBindings(preferences).assign("waveforms.toggle", stroke);
        OpenDocument document = track(OpenDocument.createEmpty(registry));
        EditorView view = new EditorView(document, services, NO_WINDOW);
        try {
            assertThat(view.shortcutActions()).containsKey("waveforms.toggle");
            assertThat(viewItem(view, "Formas de onda").getAccelerator()).isEqualTo(stroke);
        } finally {
            view.dispose();
        }
        assertThat(view.shortcutActions()).doesNotContainKey("waveforms.toggle");
    }
}
