package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JToolBar;
import java.awt.Component;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** Extensions hang their own components in named toolbar slots; the animation point is on for now. */
class ToolbarComponentTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void aSlotComponentGoesWhereItsSlotIsAndTheReturnedRunnableTakesItBack() {
        JButton file = new JButton("file");
        JButton simulation = new JButton("simulation");
        JButton right = new JButton("right");
        List<Component> before = List.of(view.toolBar().getComponents());
        Runnable removeFile = view.addToolbarComponent(file, ToolbarSlot.AFTER_FILE);
        Runnable removeSimulation = view.addToolbarComponent(simulation, ToolbarSlot.AFTER_SIMULATION);
        Runnable removeRight = view.addToolbarComponent(right, ToolbarSlot.RIGHT);
        List<Component> children = List.of(view.toolBar().getComponents());
        assertThat(children.get(children.indexOf(file) - 1)).isInstanceOf(javax.swing.JButton.class)
                .extracting(child -> ((JButton) child).getAction()).isSameAs(view.actions().save());
        assertThat(children.get(children.indexOf(file) + 1)).isInstanceOf(javax.swing.JToolBar.Separator.class);
        assertThat(children.get(children.indexOf(simulation) - 1)).isInstanceOf(javax.swing.JComboBox.class);
        assertThat(children.get(children.indexOf(right) - 1)).isInstanceOf(javax.swing.Box.Filler.class);
        assertThat(children.getLast()).isSameAs(right);
        removeRight.run();
        removeSimulation.run();
        removeFile.run();
        assertThat(List.of(view.toolBar().getComponents())).isEqualTo(before);
    }

    @Test
    void insideASlotTheOrderNumberDecidesNotTheInstallOrder() {
        JButton late = new JButton("late");
        JButton early = new JButton("early");
        JButton alsoLate = new JButton("also late");
        view.addToolbarComponent(late, ToolbarSlot.RIGHT, 2);
        view.addToolbarComponent(early, ToolbarSlot.RIGHT, 1);
        view.addToolbarComponent(alsoLate, ToolbarSlot.RIGHT, 2);
        List<Component> children = List.of(view.toolBar().getComponents());
        assertThat(children.subList(children.size() - 3, children.size())).containsExactly(early, late, alsoLate);
    }

    @Test
    void aHorizontalGlueKeepsTheRightSlotAtTheRightEdgeAndTheRestAtTheLeft() {
        JButton right = new JButton("right");
        view.addToolbarComponent(right, ToolbarSlot.RIGHT);
        JToolBar toolBar = view.toolBar();
        toolBar.setSize(2000, toolBar.getPreferredSize().height);
        toolBar.doLayout();
        assertThat(right.getX() + right.getWidth()).isGreaterThan(2000 - 20);
        Component speed = null;
        for (Component child : toolBar.getComponents()) {
            if (child instanceof javax.swing.JComboBox<?>) {
                speed = child;
            }
        }
        assertThat(speed.getX() + speed.getWidth()).isLessThan(right.getX() - 300);
    }

    @Test
    void animationsAreAlwaysOnUntilTheGlobalSettingExists() {
        assertThat(Animations.enabled(AppPreferences.inMemory())).isTrue();
    }
}
