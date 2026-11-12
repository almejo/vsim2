package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The command palette lists what the menus and the toolbar hold; a shortcut with an action but no menu item or
 * toolbar button would be invisible in «Acciones» (the user, 2026-11-09). Adding an id to {@link #EXCEPTIONS}
 * needs its reason.
 */
class EveryShortcutIsAnActionRowTest {

    /** Shortcut ids that are not «Acciones» rows on purpose, each with its reason. */
    private static final Map<String, String> EXCEPTIONS = exceptions();

    private static Map<String, String> exceptions() {
        Map<String, String> reasons = new HashMap<>();
        reasons.put("edit.quickInsert", "it opens the palette itself, on «Componentes»; from «Acciones» Tab goes there");
        reasons.put("view.lens", "a key to hold, not to run: the lens shows while it is held and goes on its release;"
                + " its row in «Acciones» is «Lupa fija», which keeps the lens on");
        String nudge = "an arrow-key nudge of the selection: a row for each of the eight would only be noise, and"
                + " the palette gives the focus back to the canvas, so the keys are the way to use them";
        for (String direction : List.of("Left", "Right", "Up", "Down")) {
            reasons.put("edit.move" + direction, nudge);
            reasons.put("edit.move" + direction + "Far", nudge);
        }
        return reasons;
    }

    @TempDir
    Path directory;

    @Test
    void everyShortcutWithAnActionInTheViewIsAnActionsRow() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        AppPreferences preferences = AppPreferences.inMemory();
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        LibraryService service = new LibraryService(preferences, new ComponentLibrary(), registry, Runnable::run);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
        OpenDocument document = OpenDocument.createEmpty(registry);
        EditorView view = new EditorView(document, services, NO_WINDOW);
        try {
            Map<String, Action> actions = ShortcutsExtension.actionsOf(view);
            Set<Object> listed = ActionRows.listedActionsOf(view);
            List<String> missing = new ArrayList<>();
            for (ShortcutEntry entry : ShortcutCatalog.entries()) {
                Action action = actions.get(entry.id());
                if (action == null || EXCEPTIONS.containsKey(entry.id())) {
                    continue;
                }
                if (!listed.contains(action)) {
                    missing.add(entry.id() + " (" + action.getValue(Action.NAME) + ")");
                }
            }
            assertThat(missing).as("shortcuts with an action but no «Acciones» row").isEmpty();
        } finally {
            view.dispose();
            document.close();
            service.close();
        }
    }
}
