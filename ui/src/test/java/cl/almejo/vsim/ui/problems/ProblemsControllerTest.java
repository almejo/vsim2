package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ProblemsControllerTest {

    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private OpenDocument openDocument;
    private EditorView view;
    private Component high;
    private Component low;
    private Wire joint;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, window);
        high = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        low = place(openDocument, StandardComponentTypes.SWITCH, 0, 6, Map.of("value", "0"));
        joint = new Wire(openDocument.document().newWireId(), new GridPoint(4, 1), new GridPoint(4, 7));
        openDocument.execute(new AddWireCommand(joint));
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private SimulationSnapshot shortSnapshot(long token) {
        String netId = openDocument.netlist().netForWire(joint.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE),
                new DriverRef(low.id().value(), 0, Value.ZERO)), 5);
        return new SimulationSnapshot(1, token, 10, true, Map.of(), Map.of(), List.of(shortCircuit), Optional.empty());
    }

    private static void perform(Action action) {
        action.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
    }

    private MouseEvent hoverAt(int x, int y) {
        return new MouseEvent(view.canvas(), MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false);
    }

    private JCheckBoxMenuItem pauseItem() {
        JMenu simulation = view.menu(MenuId.SIMULATION);
        for (int index = 0; index < simulation.getItemCount(); index++) {
            if (simulation.getItem(index) instanceof JCheckBoxMenuItem item) {
                return item;
            }
        }
        throw new AssertionError("no pause option in the Simulation menu");
    }

    private static List<String> itemTexts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            if (item != null) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    private static List<String> tabTitles(java.awt.Component root) {
        List<String> titles = new ArrayList<>();
        if (root instanceof JTabbedPane tabs) {
            for (int index = 0; index < tabs.getTabCount(); index++) {
                titles.add(tabs.getTitleAt(index));
            }
        }
        if (root instanceof Container container) {
            for (java.awt.Component child : container.getComponents()) {
                titles.addAll(tabTitles(child));
            }
        }
        return titles;
    }

    @Test
    void installsTheProblemsTabTheViewItemsAndThePauseOption() {
        assertThat(tabTitles(view)).contains("Problemas");
        assertThat(itemTexts(view.menu(MenuId.VIEW))).contains("Siguiente problema", "Problema anterior");
        JCheckBoxMenuItem pause = pauseItem();
        assertThat(pause.getText()).isEqualTo("Pausar al detectar cortocircuito");
        assertThat(pause.isSelected()).isFalse();
    }

    @Test
    void f8AndShiftF8AreCanvasBindings() {
        var keys = view.canvas().getInputMap(JComponent.WHEN_FOCUSED);
        assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0))).isEqualTo("nextProblem");
        assertThat(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_F8, InputEvent.SHIFT_DOWN_MASK)))
                .isEqualTo("previousProblem");
        assertThat(view.canvas().getActionMap().get("nextProblem")).isSameAs(view.problems().nextProblem());
        assertThat(view.canvas().getActionMap().get("previousProblem")).isSameAs(view.problems().previousProblem());
    }

    @Test
    void nextProblemIsEnabledOnlyWithProblemsAndNavigates() {
        ProblemsController problems = view.problems();
        assertThat(problems.nextProblem().isEnabled()).isFalse();
        problems.showSnapshot(shortSnapshot(openDocument.netlistToken()));
        assertThat(problems.nextProblem().isEnabled()).isTrue();
        assertThat(problems.panel().problemList().getModel().getSize()).isEqualTo(1);
        perform(problems.nextProblem());
        assertThat(view.canvas().selection().ids()).containsExactly(joint.id());
        assertThat(problems.blink().isBlinking()).isTrue();
        problems.showSnapshot(SimulationSnapshot.EMPTY);
        assertThat(problems.nextProblem().isEnabled()).isFalse();
        assertThat(problems.previousProblem().isEnabled()).isFalse();
    }

    @Test
    void clickingTheStatusBarWarningGoesToTheNextProblem() {
        SimulationSnapshot snapshot = shortSnapshot(openDocument.netlistToken());
        view.problems().showSnapshot(snapshot);
        view.statusBar().showSnapshot(snapshot);
        JLabel warning = Arrays.stream(view.statusBar().getComponents())
                .filter(JLabel.class::isInstance)
                .map(JLabel.class::cast)
                .filter(label -> label.getText().startsWith("⚠"))
                .findFirst().orElseThrow();
        MouseEvent click = new MouseEvent(warning, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : warning.getMouseListeners()) {
            listener.mouseClicked(click);
        }
        assertThat(view.canvas().selection().ids()).containsExactly(joint.id());
    }

    @Test
    void hoveringAShortedWireShowsItsDrivers() {
        view.problems().showSnapshot(shortSnapshot(openDocument.netlistToken()));
        assertThat(view.canvas().getToolTipText(hoverAt(32, 32))).isEqualTo("Interruptor → 1 · Interruptor → 0");
        assertThat(view.canvas().getToolTipText(hoverAt(200, 200))).isNull();
    }

    @Test
    void aStaleSnapshotShowsNoTooltip() {
        view.problems().showSnapshot(shortSnapshot(openDocument.netlistToken() - 1));
        assertThat(view.canvas().getToolTipText(hoverAt(32, 32))).isNull();
    }

    @Test
    void thePauseOptionDrivesTheDocumentAndFollowsIt() {
        JCheckBoxMenuItem pause = pauseItem();
        pause.doClick(0);
        assertThat(openDocument.pauseOnShortCircuit()).isTrue();
        pause.doClick(0);
        assertThat(openDocument.pauseOnShortCircuit()).isFalse();

        openDocument.setPauseOnShortCircuit(true);
        JMenu simulation = view.menu(MenuId.SIMULATION);
        for (MenuListener listener : simulation.getMenuListeners()) {
            listener.menuSelected(new MenuEvent(simulation));
        }
        assertThat(pause.isSelected()).isTrue();
    }

    @Test
    void thePulseRunsOnlyWhileShortsExistAndDisposeStopsIt() {
        ProblemsController problems = view.problems();
        problems.showSnapshot(shortSnapshot(openDocument.netlistToken()));
        assertThat(problems.pulse().isPulsing()).isTrue();
        problems.showSnapshot(SimulationSnapshot.EMPTY);
        assertThat(problems.pulse().isPulsing()).isFalse();
        problems.showSnapshot(shortSnapshot(openDocument.netlistToken()));
        view.dispose();
        assertThat(problems.pulse().isPulsing()).isFalse();
    }

    @Test
    void aStaleSnapshotChangesNeitherThePanelNorTheNavigator() {
        ProblemsController problems = view.problems();
        problems.showSnapshot(shortSnapshot(openDocument.netlistToken() - 1));
        assertThat(problems.panel().problemList().getModel().getSize()).isZero();
        assertThat(problems.navigator().problems()).isEmpty();
        assertThat(problems.nextProblem().isEnabled()).isFalse();
        assertThat(problems.pulse().isPulsing()).isFalse();

        problems.showSnapshot(shortSnapshot(openDocument.netlistToken()));
        problems.showSnapshot(staleWithoutProblems());
        assertThat(problems.panel().problemList().getModel().getSize()).isEqualTo(1);
        assertThat(problems.navigator().problems()).hasSize(1);
        assertThat(problems.nextProblem().isEnabled()).isTrue();
    }

    private SimulationSnapshot staleWithoutProblems() {
        return new SimulationSnapshot(1, openDocument.netlistToken() - 1, 10, true, Map.of(), Map.of(), List.of(),
                Optional.empty());
    }

    @Test
    void disposeRemovesEverythingTheControllerRegistered() {
        ProblemsController problems = view.problems();
        int viewItems = view.menu(MenuId.VIEW).getItemCount();
        int simulationItems = view.menu(MenuId.SIMULATION).getItemCount();
        int simulationListeners = view.menu(MenuId.SIMULATION).getMenuListeners().length;
        problems.dispose();
        assertThat(view.menu(MenuId.VIEW).getItemCount()).isLessThan(viewItems);
        assertThat(view.menu(MenuId.SIMULATION).getItemCount()).isLessThan(simulationItems);
        assertThat(view.menu(MenuId.SIMULATION).getMenuListeners()).hasSize(simulationListeners - 1);
        assertThat(view.canvas().getActionMap().get("nextProblem")).isNull();
        assertThat(view.canvas().getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0))).isNull();
    }
}
