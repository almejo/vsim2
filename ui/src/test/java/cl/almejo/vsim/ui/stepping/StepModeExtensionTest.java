package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class StepModeExtensionTest {

    private OpenDocument openDocument;
    private EditorServices services;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new StepModeExtension()));
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    /** Button texts (no ActionIcons here, so buttons keep their action names), «label» and «---» for the rest. */
    private static List<String> toolBarTexts(JToolBar toolBar) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : toolBar.getComponents()) {
            texts.add(child instanceof StepSizeButton ? "arrow"
                    : child instanceof AbstractButton button ? button.getText()
                    : child instanceof JLabel ? "label" : "---");
        }
        return texts;
    }

    @Test
    void theItemsAndTheButtonsFollowTheFixedStep() {
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            assertThat(texts(view.menu(MenuId.SIMULATION))).containsExactly("Iniciar", "Pausar", "Iniciar o pausar", "Reiniciar",
                    "Avanzar 10 t", "Tamaño del avance…", "Paso a paso", "Avanzar al próximo evento",
                    "Avanzar al próximo flanco de reloj", "Avanzar un ciclo de reloj", "---",
                    "Pausar al detectar cortocircuito");
            assertThat(view.menu(MenuId.SIMULATION).getMenuComponent(6)).isInstanceOf(JCheckBoxMenuItem.class);
            int fixedStep = toolBarTexts(view.toolBar()).indexOf("Avanzar 10 t");
            assertThat(toolBarTexts(view.toolBar()).subList(fixedStep, fixedStep + 8)).containsExactly(
                    "Avanzar 10 t", "arrow", "---", "Paso a paso", "Avanzar al próximo evento",
                    "Avanzar al próximo flanco de reloj", "Avanzar un ciclo de reloj", "label");
            assertThat(view.shortcutActions()).containsKeys("simulation.stepSize", "simulation.stepMode",
                    "simulation.advance", "simulation.advanceToClockEdge", "simulation.advanceClockCycle");
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
            JCheckBoxMenuItem item = (JCheckBoxMenuItem) view.menu(MenuId.SIMULATION).getMenuComponent(6);
            assertThat(item.isSelected()).isTrue();
        }

        second.dispose();
        assertThat(openDocument.stepModeListenerCount()).isEqualTo(listeners + 1);
        assertThat(texts(second.menu(MenuId.SIMULATION))).startsWith("Iniciar", "Pausar", "Iniciar o pausar", "Reiniciar", "Avanzar 10 t")
                .doesNotContain("Tamaño del avance…", "Paso a paso", "Avanzar al próximo evento",
                        "Avanzar al próximo flanco de reloj", "Avanzar un ciclo de reloj");
        assertThat(second.toolBar().getComponentCount()).isEqualTo(toolBarItems - 7);
        assertThat(second.shortcutActions()).isEmpty();
        first.dispose();
        assertThat(openDocument.stepModeListenerCount()).isEqualTo(listeners);
        assertThat(openDocument.changeListenerCount()).isEqualTo(changeListeners);
    }
}
