package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.PassThroughCircuit;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ProblemsControllerTest {

    private OpenDocument openDocument;
    private EditorView view;
    private Component high;
    private Component low;
    private Wire joint;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
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

    private String netOfPin(Component component, int pinIndex) {
        return openDocument.netlist().netForPin(new PinRef(component.id(), pinIndex)).orElseThrow().id().value();
    }

    private SimulationSnapshot currentSnapshot(Map<String, Value> netValues, Map<String, VisibleState> states) {
        return new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, netValues, states, List.of(),
                Optional.empty());
    }

    @Test
    void everyPinNamesItselfAndShowsItsValueOnlyWithACurrentSnapshot() {
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 10, 10, Map.of(StandardComponentTypes.NEGATE, true)); // in (10,12), out (14,12)
        assertThat(view.canvas().getToolTipText(hoverAt(80, 96))).isEqualTo("in");

        view.problems().showSnapshot(currentSnapshot(Map.of(netOfPin(gate, 0), Value.HIGH_IMPEDANCE,
                netOfPin(gate, 1), Value.ONE), Map.of()));
        assertThat(view.canvas().getToolTipText(hoverAt(80, 96))).isEqualTo("in = Z (sin conectar)");
        assertThat(view.canvas().getToolTipText(hoverAt(112, 96))).isEqualTo("out = 1");
        assertThat(view.canvas().getToolTipText(hoverAt(84, 97))).as("within the pin radius").isEqualTo(
                "in = Z (sin conectar)");

        place(openDocument, StandardComponentTypes.LED, 13, 14, Map.of()); // edits make the snapshot stale
        assertThat(view.canvas().getToolTipText(hoverAt(80, 96))).isEqualTo("in");
    }

    @Test
    void aDisplayPinNamesItsBitAndADashedDisplayExplainsTheDash() {
        Component display = place(openDocument, StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 0, Map.of());
        assertThat(view.canvas().getToolTipText(hoverAt(192, 0))).isEqualTo("in0 · bit 0 (el de la derecha)");
        assertThat(view.canvas().getToolTipText(hoverAt(168, 0))).isEqualTo("in3 · bit 3 (el de la izquierda)");

        String id = display.id().value();
        view.problems().showSnapshot(currentSnapshot(Map.of(),
                Map.of(id, new VisibleState.SevenSegmentsState(new Value(4, 0x1, 0x8, false)))));
        assertThat(view.canvas().getToolTipText(hoverAt(176, 24))).isEqualTo("Alguna entrada no está definida (Z)");
        view.problems().showSnapshot(currentSnapshot(Map.of(),
                Map.of(id, new VisibleState.SevenSegmentsState(Value.of(4, 0x5)))));
        assertThat(view.canvas().getToolTipText(hoverAt(176, 24))).as("a readable digit").isNull();
        assertThat(view.canvas().getToolTipText(hoverAt(192, 0))).as("its pins keep their tooltip")
                .isEqualTo("in0 · bit 0 (el de la derecha)");
    }

    @Test
    void theBusInputOfADisplayNamesItsWidth() {
        place(openDocument, StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 0, Map.of()); // bus at (22, 6)
        assertThat(view.canvas().getToolTipText(hoverAt(176, 48))).isEqualTo("bus · 4 bits");
    }

    @Test
    void aDisplayWarningIsListedWithoutAlarmingTheStatusBarAndGoesAwayWithIt() {
        Component display = place(openDocument, StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 20, 0, Map.of());
        ProblemsController problems = view.problems();
        DisplayBusOverride warning = new DisplayBusOverride(netOfPin(display, 4), display.id().value());
        SimulationSnapshot withWarning = new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(), List.of(warning), Optional.empty());
        problems.showSnapshot(withWarning);
        view.statusBar().showSnapshot(withWarning);

        int wiringWarnings = openDocument.connectivityProblems().size();
        assertThat(wiringWarnings).isZero();
        assertThat(problems.panel().problemList().getModel().getSize()).isEqualTo(1 + wiringWarnings);
        assertThat(problems.panel().summaryText()).isEqualTo("1 problema");
        assertThat(Arrays.stream(view.statusBar().getComponents())
                .filter(JLabel.class::isInstance)
                .map(JLabel.class::cast)
                .filter(JLabel::isVisible)
                .map(JLabel::getText))
                .noneMatch(text -> text.startsWith("⚠"));
        assertThat(problems.navigator().next()).contains(warning);
        assertThat(view.canvas().selection().ids()).containsExactly(display.id());

        problems.showSnapshot(currentSnapshot(Map.of(), Map.of()));
        assertThat(problems.navigator().problems()).doesNotContain(warning)
                .containsExactlyElementsOf(openDocument.connectivityProblems());
        assertThat(problems.nextProblem().isEnabled()).isEqualTo(wiringWarnings > 0);
    }

    @Test
    void aShortedPinShowsItsNameAndThenTheDrivers() {
        view.problems().showSnapshot(shortSnapshot(openDocument.netlistToken()));
        assertThat(view.canvas().getToolTipText(hoverAt(32, 8))) // the output of «high» at (4, 1)
                .isEqualTo("out · Interruptor → 1 · Interruptor → 0");
    }

    @Test
    void hoveringAShortedWireShowsItsDrivers() {
        view.problems().showSnapshot(shortSnapshot(openDocument.netlistToken()));
        assertThat(view.canvas().getToolTipText(hoverAt(32, 32))).isEqualTo("Interruptor → 1 · Interruptor → 0");
        assertThat(view.canvas().getToolTipText(hoverAt(200, 200))).isNull();
    }

    @Test
    void theRendererThePulseAndTheTooltipsShareOneHighlightCache() {
        assertThat(view.problems().pulse().highlights()).isSameAs(view.canvas().highlights());
        view.problems().showSnapshot(shortSnapshot(openDocument.netlistToken()));
        view.canvas().getToolTipText(hoverAt(32, 32));
        assertThat(view.canvas().highlights().topLevelNets(openDocument.document(), openDocument.netlist()))
                .as("the tooltip filled the shared memo").isSameAs(view.canvas().highlights()
                        .topLevelNets(openDocument.document(), openDocument.netlist()));
        assertThat(view.canvas().getToolTipText(hoverAt(32, 32))).isEqualTo("Interruptor → 1 · Interruptor → 0");
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

    /** Review M8: dispose takes the canvas off the tooltip manager instead of registering it again. */
    @Test
    void disposeUnregistersTheCanvasFromTheTooltipManager() {
        assertThat(view.canvas().getMouseListeners()).contains(ToolTipManager.sharedInstance());
        view.problems().dispose();
        assertThat(view.canvas().getMouseListeners()).doesNotContain(ToolTipManager.sharedInstance());
        assertThat(view.canvas().getToolTipText(hoverAt(10, 10))).isNull();
    }

    @Test
    void hoveringAWireOfAMismatchedNetNamesItsPinsAndABusShowsItsValue() {
        Component splitter = place(openDocument, StandardComponentTypes.BUS_SPLITTER, 20, 0,
                Map.of(StandardComponentTypes.WIDTH, 4)); // bus pin at (20, 0)
        Wire bus = new Wire(openDocument.document().newWireId(), new GridPoint(10, 0), new GridPoint(20, 0));
        openDocument.execute(new AddWireCommand(bus));
        String netId = openDocument.netlist().netForWire(bus.id()).orElseThrow().id().value();

        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true,
                Map.of(netId, Value.of(4, 0x9)), Map.of(), List.of(), Optional.empty()));
        assertThat(view.canvas().getToolTipText(hoverAt(120, 0))).isEqualTo("Bus de 4 bits = 0x9 (1001)");
        view.problems().showSnapshot(new SimulationSnapshot(2, openDocument.netlistToken(), 10, true,
                Map.of(netId, new Value(4, 0x9, 0x6, false)), Map.of(), List.of(), Optional.empty()));
        assertThat(view.canvas().getToolTipText(hoverAt(120, 0))).isEqualTo("Bus de 4 bits = 0x? (1ZZ1)");
        view.problems().showSnapshot(new SimulationSnapshot(3, openDocument.netlistToken(), 10, true,
                Map.of(netId, Value.of(4, 0x9).withConflict()), Map.of(), List.of(), Optional.empty()));
        assertThat(view.canvas().getToolTipText(hoverAt(120, 0))).isEqualTo("Bus de 4 bits = X");

        WidthMismatch mismatch = new WidthMismatch(netId, List.of(new FlatPinRef(splitter.id().value(), 0),
                new FlatPinRef(high.id().value(), 0)));
        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(), List.of(mismatch), Optional.empty()));
        assertThat(view.canvas().getToolTipText(hoverAt(120, 0)))
                .isEqualTo("⚠ Anchos incompatibles: Separador de bus (4 bits) · Interruptor (1 bit)");
        assertThat(view.statusBar().text()).contains("⚠ 1 ancho incompatible");
        assertThat(view.problems().nextProblem().isEnabled()).isTrue();
    }

    @Test
    void aMismatchReportedUnderAnotherNetIdStillTooltipsItsMergedWire() {
        // bus pin at (20, 1)
        place(openDocument, StandardComponentTypes.BUS_SPLITTER, 20, 1, Map.of(StandardComponentTypes.WIDTH, 4));
        Wire bus = new Wire(openDocument.document().newWireId(), new GridPoint(4, 1), new GridPoint(20, 1));
        openDocument.execute(new AddWireCommand(bus));
        assertThat(openDocument.netlist().netForWire(bus.id()).orElseThrow().widthMismatch()).isTrue();

        WidthMismatch elsewhere = new WidthMismatch("n:p:other#0", List.of(new FlatPinRef(high.id().value(), 0)));
        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(), List.of(elsewhere), Optional.empty()));
        assertThat(view.canvas().getToolTipText(hoverAt(120, 8))).startsWith("⚠ Anchos incompatibles: ");
    }

    @Test
    void theMismatchTextBeforeASnapshotLeavesOutTunnelPins() {
        // bus pin at (20, 1)
        place(openDocument, StandardComponentTypes.BUS_SPLITTER, 20, 1, Map.of(StandardComponentTypes.WIDTH, 4));
        Wire bus = new Wire(openDocument.document().newWireId(), new GridPoint(4, 1), new GridPoint(20, 1));
        openDocument.execute(new AddWireCommand(bus));
        // an entrada whose pin sits on the wire at (12, 1)
        place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 12, 0, Map.of(Tunnels.NAME, "D"));

        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(), List.of(), Optional.empty()));

        // grid (8, 1): on the wire, left of the entrada's tag
        assertThat(view.canvas().getToolTipText(hoverAt(64, 8)))
                .startsWith("⚠ Anchos incompatibles: ")
                .contains("Separador de bus (4 bits)", "Interruptor (1 bit)")
                .doesNotContain("Túnel");
    }

    @Test
    void hoveringEitherSideOfANetMergedThroughATemplateNamesItsDrivers() {
        PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);
        view.problems().showSnapshot(circuit.shortSnapshot(openDocument));

        String leftSide = view.canvas().getToolTipText(hoverAt(56, 160));   // grid (7, 20), on wire left
        String rightSide = view.canvas().getToolTipText(hoverAt(152, 160)); // grid (19, 20), on wire rightAcross

        assertThat(leftSide).isEqualTo("Interruptor → 1 · Interruptor → 0");
        assertThat(rightSide).isEqualTo(leftSide);
    }

    @Test
    void hoveringAPinThatSitsOnAPinOfAnotherWidthNamesBothWidths() {
        // bus pin at (30, 0)
        place(openDocument, StandardComponentTypes.BUS_SPLITTER, 30, 0, Map.of(StandardComponentTypes.WIDTH, 4));
        place(openDocument, StandardComponentTypes.LED, 29, -3, Map.of()); // its pin is at (30, 0): pin on pin, no wire
        assertThat(openDocument.netlist().nets().stream().anyMatch(net -> net.widthMismatch() && net.wires().isEmpty()))
                .isTrue();
        view.problems().showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(),
                Map.of(), List.of(), Optional.empty()));

        assertThat(view.canvas().getToolTipText(hoverAt(240, 0)))
                .as("the pin tooltip of the LED (the last component there), then the problem of its net")
                .startsWith("in · ⚠ Anchos incompatibles: ")
                .contains("Separador de bus (4 bits)")
                .contains("LED (1 bit)");
        assertThat(view.canvas().getToolTipText(hoverAt(256, 16))).isEqualTo("b1"); // the healthy splitter bit b1
    }

    @Test
    void theMergedNetsMemoRefreshesWhenTheNetlistChanges() {
        PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);
        String expected = "Interruptor → 1 · Interruptor → 0";

        // Without the template instance the two sides are separate nets: the memo is rebuilt as "nothing merged".
        openDocument.execute(new RemoveElementsCommand(List.of(circuit.instance().id())));
        String rightNetId = openDocument.netlist().netForWire(circuit.rightAcross().id()).orElseThrow().id().value();
        view.problems().showSnapshot(shortCircuitSnapshot(circuit, rightNetId, 2));
        assertThat(view.canvas().getToolTipText(hoverAt(152, 160))).isEqualTo(expected);

        // Merged again (new token): a stale "nothing merged" memo would not resolve the right side to the root.
        openDocument.history().undo();
        view.problems().showSnapshot(circuit.shortSnapshot(openDocument));
        assertThat(view.canvas().getToolTipText(hoverAt(152, 160))).isEqualTo(expected);
        assertThat(view.canvas().getToolTipText(hoverAt(56, 160))).isEqualTo(expected);
    }

    private SimulationSnapshot shortCircuitSnapshot(PassThroughCircuit circuit, String netId, long sequence) {
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(
                new DriverRef(circuit.high().id().value(), 0, Value.ONE),
                new DriverRef(circuit.low().id().value(), 0, Value.ZERO)), 5);
        return new SimulationSnapshot(sequence, openDocument.netlistToken(), 10, true, Map.of(), Map.of(),
                List.of(shortCircuit), Optional.empty());
    }
}
