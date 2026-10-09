package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesFeatureTest {

    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private final List<Component> openedFrom = new ArrayList<>();
    private final List<AppPreferences> openedWith = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT), window);
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
        Runnable disposer = new PreferencesFeature((parent, preferences) -> {
            openedFrom.add(parent);
            openedWith.add(preferences);
        }).install(view);

        List<String> after = entries(file);
        int close = after.indexOf(Messages.text("action.close"));
        assertThat(after.subList(close - 3, close)).containsExactly("—", "Preferencias…", "—");
        assertThat(after).hasSize(before.size() + 2);

        disposer.run();
        assertThat(entries(file)).isEqualTo(before);
    }

    @Test
    void theItemOpensTheDialogOverThisViewWithTheSharedPreferences() {
        JMenu file = view.menu(MenuId.FILE);
        Runnable disposer = new PreferencesFeature((parent, preferences) -> {
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
}
