package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
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

import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JToolBar;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StepModeExtensionTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private OpenDocument openDocument;
    private EditorServices services;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new StepModeExtension()));
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    /** Button texts (no ActionIcons here, so buttons keep their action names), «label» and «---» for the rest. */
    private static List<String> toolBarTexts(JToolBar toolBar) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : toolBar.getComponents()) {
            texts.add(child instanceof AbstractButton button ? button.getText()
                    : child instanceof JLabel ? "label" : "---");
        }
        return texts;
    }

    @Test
    void theItemsAndTheButtonsFollowTheFixedStep() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.SIMULATION))).containsExactly("Iniciar", "Pausar", "Avanzar 10 t",
                    "Paso a paso", "Avanzar al próximo evento", "Avanzar al próximo flanco de reloj", "---",
                    "Pausar al detectar cortocircuito");
            assertThat(view.menu(MenuId.SIMULATION).getMenuComponent(3)).isInstanceOf(JCheckBoxMenuItem.class);
            assertThat(toolBarTexts(view.toolBar())).containsSubsequence("Avanzar 10 t", "---", "Paso a paso",
                    "Avanzar al próximo evento", "Avanzar al próximo flanco de reloj", "label");
            int fixedStep = toolBarTexts(view.toolBar()).indexOf("Avanzar 10 t");
            assertThat(toolBarTexts(view.toolBar()).subList(fixedStep, fixedStep + 6)).containsExactly(
                    "Avanzar 10 t", "---", "Paso a paso", "Avanzar al próximo evento",
                    "Avanzar al próximo flanco de reloj", "label");
            assertThat(view.shortcutActions()).containsKeys("simulation.stepMode", "simulation.advance",
                    "simulation.advanceToClockEdge");
        } finally {
            view.dispose();
        }
    }

    @Test
    void twoViewsShareTheModeAndEachTakesBackWhatItAdded() {
        int listeners = openDocument.stepModeListenerCount();
        int changeListeners = openDocument.changeListenerCount();
        EditorView first = new EditorView(openDocument, services, NO_WINDOW);
        EditorView second = new EditorView(openDocument, services, NO_WINDOW);
        int toolBarItems = second.toolBar().getComponentCount();
        assertThat(openDocument.stepModeListenerCount()).isEqualTo(listeners + 2);

        openDocument.setStepMode(true);
        for (EditorView view : List.of(first, second)) {
            JCheckBoxMenuItem item = (JCheckBoxMenuItem) view.menu(MenuId.SIMULATION).getMenuComponent(3);
            assertThat(item.isSelected()).isTrue();
        }

        second.dispose();
        assertThat(openDocument.stepModeListenerCount()).isEqualTo(listeners + 1);
        assertThat(texts(second.menu(MenuId.SIMULATION))).startsWith("Iniciar", "Pausar", "Avanzar 10 t")
                .doesNotContain("Paso a paso", "Avanzar al próximo evento", "Avanzar al próximo flanco de reloj");
        assertThat(second.toolBar().getComponentCount()).isEqualTo(toolBarItems - 5);
        assertThat(second.shortcutActions()).isEmpty();
        first.dispose();
        assertThat(openDocument.stepModeListenerCount()).isEqualTo(listeners);
        assertThat(openDocument.changeListenerCount()).isEqualTo(changeListeners);
    }
}
