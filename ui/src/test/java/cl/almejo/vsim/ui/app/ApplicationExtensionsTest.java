package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
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

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The production extensions (Application.editorExtensions) installed together on two views of one document. */
class ApplicationExtensionsTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final ComponentLibrary library = new ComponentLibrary();
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private LibraryService service;
    private EditorServices services;
    private OpenDocument openDocument;

    /** Listener counts on every shared object an extension registers with, in a fixed order. */
    private record Counts(int library, int libraryProblems, int preferences, int appearance, int document) {
        Counts plus(Counts other, int times) {
            return new Counts(library + times * other.library, libraryProblems + times * other.libraryProblems,
                    preferences + times * other.preferences, appearance + times * other.appearance,
                    document + times * other.document);
        }

        Counts minus(Counts other) {
            return plus(other, -1);
        }
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        service = new LibraryService(preferences, library, registry, Runnable::run);
        services = new EditorServices(PainterRegistry.standard(), appearance, preferences, library,
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
        openDocument = OpenDocument.createEmpty(registry);
    }

    @AfterEach
    void close() {
        openDocument.close();
        service.close();
    }

    private Counts counts() {
        return new Counts(library.listenerCount(), service.problemsListenerCount(), preferences.listenerCount(),
                appearance.listenerCount(), openDocument.changeListenerCount());
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    @Test
    void theFileMenuHasEveryExtensionsEntryInPlace() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.FILE))).containsExactly("Nuevo", "Abrir…", "Guardar",
                    "Guardar como…", "---", "Importar componente…", "---", "Preferencias…", "---",
                    "Cerrar ventana", "Salir");
            assertThat(texts(view.menu(MenuId.EDIT))).containsSubsequence("Deshacer", "Rehacer", "Cortar", "Copiar",
                    "Pegar", "Crear componente desde selección…", "Actualizar componente…");
        } finally {
            view.dispose();
        }
    }

    @Test
    void theShortcutsExtensionBindsTheWindowMenuAcceleratorsAndCanvasKeys() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.WINDOW))).containsExactly("Nueva vista");
            assertThat(view.actions().save().getValue(javax.swing.Action.ACCELERATOR_KEY))
                    .isEqualTo(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                            java.awt.event.InputEvent.CTRL_DOWN_MASK));
            assertThat(view.canvas().getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0)))
                    .isEqualTo("shortcut.edit.delete");
        } finally {
            view.dispose();
        }
    }

    @Test
    void disposingEachOfTwoViewsTakesBackExactlyItsListeners() {
        Counts baseline = counts();

        EditorView first = new EditorView(openDocument, services, NO_WINDOW);
        Counts perView = counts().minus(baseline);
        EditorView second = new EditorView(openDocument, services, NO_WINDOW);

        assertThat(perView.library()).as("the extensions follow the library").isPositive();
        assertThat(perView.libraryProblems()).isPositive();
        assertThat(perView.document()).isPositive();
        assertThat(counts()).isEqualTo(baseline.plus(perView, 2));

        second.dispose();
        assertThat(counts()).isEqualTo(baseline.plus(perView, 1));
        first.dispose();
        assertThat(counts()).isEqualTo(baseline);
    }
}
