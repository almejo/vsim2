package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

class PreferencesFeatureTest {

    private final List<Component> openedFrom = new ArrayList<>();
    private final List<AppPreferences> openedWith = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT),
                NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private static List<String> entries(JMenu menu) {
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < menu.getMenuComponentCount(); index++) {
            Component component = menu.getMenuComponent(index);
            entries.add(component instanceof JMenuItem item ? item.getText()
                    : component instanceof JSeparator ? "—" : component.getClass().getSimpleName());
        }
        return entries;
    }

    @Test
    void addsPreferencesBeforeTheCloseGroupAndRemovesItOnDispose() {
        JMenu file = view.menu(MenuId.FILE);
        List<String> before = entries(file);
        Runnable disposer = new PreferencesFeature((parent, preferences, tab) -> {
            openedFrom.add(parent);
            openedWith.add(preferences);
        }).install(view);

        List<String> after = entries(file);
        int close = after.indexOf(Messages.text("action.closeTab"));
        assertThat(after.subList(close - 3, close)).containsExactly("—", "Preferencias…", "—");
        assertThat(after).hasSize(before.size() + 2);

        disposer.run();
        assertThat(entries(file)).isEqualTo(before);
    }

    @Test
    void theItemOpensTheDialogOverThisViewWithTheSharedPreferences() {
        JMenu file = view.menu(MenuId.FILE);
        Runnable disposer = new PreferencesFeature((parent, preferences, tab) -> {
            openedFrom.add(parent);
            openedWith.add(preferences);
        }).install(view);
        try {
            for (int index = 0; index < file.getMenuComponentCount(); index++) {
                if (file.getMenuComponent(index) instanceof JMenuItem item
                        && "Preferencias…".equals(item.getText())) {
                    item.doClick(0);
                }
            }
            assertThat(openedFrom).containsExactly(view);
            assertThat(openedWith).containsExactly(view.services().preferences());
        } finally {
            disposer.run();
        }
    }

    @Test
    void preferencesIsOfferedToTheShortcutEditorUntilDisposed() {
        Runnable disposer = new PreferencesFeature((parent, preferences, tab) -> {
        }).install(view);
        JMenu file = view.menu(MenuId.FILE);
        JMenuItem item = null;
        for (int index = 0; index < file.getItemCount(); index++) {
            if (file.getItem(index) != null && "Preferencias…".equals(file.getItem(index).getText())) {
                item = file.getItem(index);
            }
        }
        assertThat(item).isNotNull();
        assertThat(view.shortcutActions().get("file.preferences")).isSameAs(item.getAction());
        disposer.run();
        assertThat(view.shortcutActions()).doesNotContainKey("file.preferences");
    }

    @Test
    void theMenuItemOpensTheLastGroupSeenAndEachPaletteEntryOpensItsOwn() {
        List<Optional<String>> openedGroups = new ArrayList<>();
        Runnable disposer = new PreferencesFeature((parent, preferences, group) -> openedGroups.add(group))
                .install(view);
        try {
            Action preferences = view.shortcutActions().get("file.preferences");
            perform(preferences);
            List<?> entries = (List<?>) preferences.getValue(Actions.PALETTE_ENTRIES);

            assertThat(entries).extracting(entry -> ((Action) entry).getValue(Action.NAME))
                    .containsExactly("Preferencias ▸ General", "Preferencias ▸ Aspecto",
                            "Preferencias ▸ Accesibilidad", "Preferencias ▸ Archivos",
                            "Preferencias ▸ Atajos de teclado", "Preferencias ▸ IA");
            assertThat(entries).extracting(entry -> ((Action) entry).getValue(Actions.PALETTE_ENTRY_ID))
                    .containsExactlyElementsOf(PreferencesDialog.GROUP_IDS);
            for (Object entry : entries) {
                perform((Action) entry);
            }
            assertThat(openedGroups).containsExactly(Optional.empty(), Optional.of("general"),
                    Optional.of("appearance"), Optional.of("accessibility"), Optional.of("files"),
                    Optional.of("shortcuts"), Optional.of("ai"));
        } finally {
            disposer.run();
        }
    }
}
