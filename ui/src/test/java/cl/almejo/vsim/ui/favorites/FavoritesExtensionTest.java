package cl.almejo.vsim.ui.favorites;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

class FavoritesExtensionTest {

    private OpenDocument document;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(document, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        document.close();
    }

    @Test
    void theFavoritesWorkWithoutQuickInsertAndDisposingTakesTheListenerBack() {
        int listeners = view.services().preferences().listenerCount();
        Runnable uninstall = new FavoritesExtension().install(view);

        view.services().preferences().setPaletteFavorites(List.of("led"));

        assertThat(view.palette().visibleSections()).startsWith("Favoritos");
        assertThat(view.palette().visibleTypeIds()).startsWith("led");
        assertThat(view.shortcutActions()).doesNotContainKey("edit.quickInsert");
        uninstall.run();
        assertThat(view.services().preferences().listenerCount()).isEqualTo(listeners);
    }
}
