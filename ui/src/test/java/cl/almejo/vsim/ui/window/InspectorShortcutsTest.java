package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** Alt+3 … Alt+6 and Ver ▸ Propiedades … Mapa de memoria: each one is the section's toggle. */
class InspectorShortcutsTest {

    private static final List<String> IDS = List.of(InspectorPanel.PROPERTIES, InspectorPanel.PROBLEMS,
            InspectorPanel.NOTES, InspectorPanel.MEMORY_MAP);

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(),
                new cl.almejo.vsim.core.library.ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        for (String id : IDS) {
            if (view.inspector().title(id).isEmpty()) {
                view.inspector().addTab(id, id, InspectorPanel.PROPERTIES_ICON, new JPanel());
                view.inspector().showShortcut(id, view.inspectorToggleAction(id));
            }
        }
        view.inspector().close();
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private void fire(String id) {
        view.inspectorToggleAction(id).actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void firingOnAClosedPanelOpensThatSection() {
        for (String id : IDS) {
            view.inspector().close();
            fire(id);
            assertThat(view.inspector().isOpen()).isTrue();
            assertThat(view.inspector().selectedTab()).contains(id);
        }
    }

    @Test
    void firingOnTheOpenSectionClosesThePanel() {
        fire(InspectorPanel.NOTES);
        fire(InspectorPanel.NOTES);

        assertThat(view.inspector().isOpen()).isFalse();
    }

    @Test
    void firingAnotherSectionSwitchesToIt() {
        fire(InspectorPanel.NOTES);
        fire(InspectorPanel.PROBLEMS);

        assertThat(view.inspector().isOpen()).isTrue();
        assertThat(view.inspector().selectedTab()).contains(InspectorPanel.PROBLEMS);
    }

    @Test
    void theViewMenuListsThemRightAfterThePaletteWithTheirNames() {
        JMenu viewMenu = view.menu(MenuId.VIEW);

        List<String> texts = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            texts.add(viewMenu.getItem(index) == null ? "---" : viewMenu.getItem(index).getText());
        }
        assertThat(texts).containsExactly("Árbol del circuito", "Paleta", "Propiedades", "Problemas", "Notas",
                "Mapa de memoria", "---", "Selección");
    }

    @Test
    void theKeysAreAltThreeToAltSixAndTheStripTooltipNamesThem() {
        ShortcutsExtension shortcuts = new ShortcutsExtension();
        Runnable uninstall = shortcuts.install(view);
        try {
            int[] digits = {KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5, KeyEvent.VK_6};
            for (int index = 0; index < IDS.size(); index++) {
                Action action = view.inspectorToggleAction(IDS.get(index));
                assertThat(action.getValue(Action.ACCELERATOR_KEY)).isEqualTo(
                        javax.swing.KeyStroke.getKeyStroke(digits[index], InputEvent.ALT_DOWN_MASK));
            }
            assertThat(((InspectorStrip) view.inspector().strip()).tooltip(InspectorPanel.PROPERTIES)).contains("Alt+3");
        } finally {
            uninstall.run();
        }
    }
}
