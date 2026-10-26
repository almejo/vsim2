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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class FixedStepFeaturesTest {

    private static final KeyStroke F6 = KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0);

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<Long> asked = new ArrayList<>();
    private OptionalLong answer = OptionalLong.empty();
    private final StepSizeChooser chooser = (parent, current) -> {
        asked.add(current);
        return answer;
    };
    private EditorServices services;
    private OpenDocument openDocument;
    private EditorView view;
    private FixedStepFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        features = new FixedStepFeatures(view, chooser);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private static JMenuItem item(StepSizeButton button, String text) {
        return Arrays.stream(button.menu().getComponents())
                .filter(JMenuItem.class::isInstance)
                .map(JMenuItem.class::cast)
                .filter(candidate -> candidate.getText().equals(text))
                .findFirst().orElseThrow();
    }

    private static List<JSplitPane> splitPanes(Container container) {
        List<JSplitPane> found = new ArrayList<>();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof JSplitPane split) {
                found.add(split);
            }
            if (child instanceof Container nested) {
                found.addAll(splitPanes(nested));
            }
        }
        return found;
    }

    @Test
    void theArrowAndTheMenuItemFollowTheFixedStep() {
        assertThat(view.actions().step().getValue(Action.NAME)).isEqualTo("Avanzar 10 t");
        assertThat(texts(view.menu(MenuId.SIMULATION))).startsWith("Iniciar", "Pausar", "Iniciar o pausar", "Reiniciar", "Avanzar 10 t",
                "Tamaño del avance…");
        List<java.awt.Component> toolBar = Arrays.asList(view.toolBar().getComponents());
        int step = -1;
        for (int index = 0; index < toolBar.size(); index++) {
            if (toolBar.get(index) instanceof AbstractButton button && button.getAction() == view.actions().step()) {
                step = index;
            }
        }
        assertThat(toolBar.get(step + 1)).isSameAs(features.arrow());
        assertThat(view.shortcutActions()).containsKey("simulation.stepSize");
    }

    @Test
    void aChosenSizeRenamesTheFixedStepInEveryViewAndIsRemembered() {
        EditorView second = new EditorView(openDocument, services, NO_WINDOW);
        FixedStepFeatures secondFeatures = new FixedStepFeatures(second, chooser);
        try {
            item(features.arrow(), "100").doClick();

            assertThat(preferences.stepUnits()).isEqualTo(100);
            for (EditorView each : List.of(view, second)) {
                Action step = each.actions().step();
                assertThat(step.getValue(Action.NAME)).isEqualTo("Avanzar 100 t");
                assertThat(step.getValue(Action.SHORT_DESCRIPTION))
                        .isEqualTo("Avanza la simulación 100 unidades de tiempo");
            }
        } finally {
            secondFeatures.dispose();
            second.dispose();
        }
        EditorView later = new EditorView(openDocument, services, NO_WINDOW); // what the next start reads
        FixedStepFeatures laterFeatures = new FixedStepFeatures(later, chooser);
        try {
            assertThat(later.actions().step().getValue(Action.NAME)).isEqualTo("Avanzar 100 t");
        } finally {
            laterFeatures.dispose();
            later.dispose();
        }
    }

    @Test
    void otherAndTheMenuItemAskWithTheCurrentSizeAndACancelChangesNothing() {
        answer = OptionalLong.of(1_000);
        item(features.arrow(), "Otro…").doClick();
        assertThat(asked).containsExactly(10L);
        assertThat(view.actions().step().getValue(Action.NAME)).as("grouped like times").isEqualTo("Avanzar 1 000 t");

        answer = OptionalLong.empty();
        perform(view.shortcutActions().get("simulation.stepSize"));

        assertThat(asked).containsExactly(10L, 1_000L);
        assertThat(preferences.stepUnits()).isEqualTo(1_000);
        assertThat(view.shortcutActions().get("simulation.stepSize").getValue(Action.NAME))
                .isEqualTo("Tamaño del avance…");
    }

    @Test
    void theFixedStepRunsTheChosenUnits() {
        preferences.setStepUnits(100);
        openDocument.pause();
        waitUntil(() -> !openDocument.latestSnapshot().running());
        long before = openDocument.latestSnapshot().time();

        perform(view.actions().step());

        waitUntil(() -> openDocument.latestSnapshot().time() == before + 100);
    }

    @Test
    void f6ReachesTheMenuFromInsideTheSplitPanes() {
        List<JSplitPane> splits = splitPanes(view);
        assertThat(splits).isNotEmpty();
        for (JSplitPane split : splits) {
            assertThat(split.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(F6)).isEqualTo("none");
        }

        features.dispose();

        for (JSplitPane split : splits) {
            assertThat(split.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(F6))
                    .as("the split pane's own key again").isNotEqualTo("none");
        }
    }

    @Test
    void disposeTakesEverythingBack() {
        preferences.setStepUnits(100);
        int listeners = preferences.listenerCount();
        int toolBarItems = view.toolBar().getComponentCount();
        int menuItems = view.menu(MenuId.SIMULATION).getMenuComponentCount();

        features.dispose();
        features.dispose();

        assertThat(preferences.listenerCount()).isEqualTo(listeners - 1);
        assertThat(view.toolBar().getComponentCount()).isEqualTo(toolBarItems - 1);
        assertThat(view.menu(MenuId.SIMULATION).getMenuComponentCount()).isEqualTo(menuItems - 1);
        assertThat(view.shortcutActions()).doesNotContainKey("simulation.stepSize");
        assertThat(view.actions().step().getValue(Action.NAME)).isEqualTo("Avanzar 10 t");
        preferences.setStepUnits(1_000);
        assertThat(view.actions().step().getValue(Action.NAME)).isEqualTo("Avanzar 10 t");
    }
}
