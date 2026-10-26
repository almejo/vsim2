package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.NewViewPlacement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** M6-C with the production extensions: two views of one document, configurable shortcuts and Nueva vista. */
class EditingExtrasEndToEndTest {

    private static final int CONTROL = InputEvent.CTRL_DOWN_MASK;
    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private final List<EditorView> views = new ArrayList<>();
    private LibraryService service;
    private EditorServices services;
    private OpenDocument openDocument;

    @BeforeEach
    void create() {
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, library, registry, Runnable::run);
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                library, Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
        openDocument = OpenDocument.createEmpty(registry);
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        openDocument.close();
        service.close();
    }

    private EditorView newView() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        view.canvas().setSize(800, 600);
        views.add(view);
        return view;
    }

    @Test
    void everyCatalogEntryHasAnActionInAProductionView() {
        EditorView view = newView();
        assertThat(ShortcutsExtension.actionsOf(view).keySet())
                .containsAll(ShortcutCatalog.entries().stream().map(ShortcutEntry::id).toList());
    }

    @Test
    void aChangedShortcutAppliesAtOnceInBothViewsAndInViewsOpenedLater() {
        EditorView first = newView();
        EditorView second = newView();
        KeyStroke controlG = KeyStroke.getKeyStroke(KeyEvent.VK_G, CONTROL);
        KeyStroke letterT = KeyStroke.getKeyStroke(KeyEvent.VK_T, 0);
        KeyStroke newViewStroke = KeyStroke.getKeyStroke(KeyEvent.VK_N, CONTROL | InputEvent.SHIFT_DOWN_MASK);
        KeyBindings bindings = new KeyBindings(preferences);
        bindings.assign("file.save", controlG);
        bindings.assign("edit.rotateClockwise", letterT);
        bindings.assign("window.newView", newViewStroke);
        for (EditorView view : List.of(first, second)) {
            assertThat(view.actions().save().getValue(Action.ACCELERATOR_KEY)).isEqualTo(controlG);
            assertThat(view.actions().newView().getValue(Action.ACCELERATOR_KEY)).isEqualTo(newViewStroke);
            InputMap keys = view.canvas().getInputMap(JComponent.WHEN_FOCUSED);
            assertThat(keys.get(letterT)).isEqualTo("shortcut.edit.rotateClockwise");
            assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0))).isNull();
        }
        EditorView later = newView(); // what the next start reads from the same stored preferences
        assertThat(later.actions().save().getValue(Action.ACCELERATOR_KEY)).isEqualTo(controlG);
        assertThat(new KeyBindings(preferences).strokeFor("window.newView")).contains(newViewStroke);
    }

    @Test
    void aViewStateRoundTripsThroughNewViewPlacement() {
        EditorView source = newView();
        EditorView copy = newView();
        source.canvas().applyViewState(new ViewState(2.0, 10.5, -4.0));
        NewViewPlacement placement = NewViewPlacement.after(new Point(100, 80), source.canvas().viewState());
        copy.canvas().applyViewState(placement.viewState());
        assertThat(copy.canvas().viewState().zoom()).isCloseTo(2.0, within(1e-9));
        assertThat(copy.canvas().viewState().centerX()).isCloseTo(10.5, within(1e-9));
        assertThat(copy.canvas().viewState().centerY()).isCloseTo(-4.0, within(1e-9));
        assertThat(placement.location()).isEqualTo(new Point(130, 110));
    }

    @Test
    void closingBothViewsLeavesNoPreferencesListenerBehind() {
        int baseline = preferences.listenerCount();
        EditorView first = newView();
        EditorView second = newView();
        assertThat(preferences.listenerCount()).isGreaterThan(baseline);
        views.clear();
        second.dispose();
        first.dispose();
        assertThat(preferences.listenerCount()).isEqualTo(baseline);
    }
}
