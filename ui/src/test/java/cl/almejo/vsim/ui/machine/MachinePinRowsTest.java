package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.RemovedPinWire;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachinePinEdits.Side;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The machine's pin list in its Propiedades (spec 2026-11-14 §1 «Pins come from the names», hito 3 task 6): rename
 * everywhere in one undo step with the wires kept, add an unused pin, remove only an unused one, a click on the uses
 * lights them, reorder by dragging or with Alt+↑ / Alt+↓, «Como un bus», «Flanco» and the limits.
 */
class MachinePinRowsTest {

    /** A switch starts at 1 by default; the clock and the inputs start at 0. */
    private static final Map<String, Object> SWITCH_OFF = Map.of("value", "0");

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private EditorServices services;
    private OpenDocument document;
    private OpenDocument place;
    private Component piece;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                new ComponentLibrary(), List.of(new MachineModeExtension()));
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        if (place != null) {
            place.close();
        }
        document.close();
    }

    /** The detector with {@code machine} placed once in the circuit, its tab open on that copy; the tab's view. */
    private EditorView tabOf(StateMachine machine) {
        ComponentDefinition definition = MachineDefinitions.regenerated(MachineFixtures.definition(machine));
        document.execute(new PutDefinitionsCommand(List.of(definition)));
        piece = UiTestSupport.place(document, SubcircuitTypes.typeIdOf(definition.id()), 20, 0, Map.of());
        return openTab();
    }

    private EditorView openTab() {
        place = document.openBody(MachineFixtures.DEFINITION_ID, Optional.of(piece.id().value()));
        EditorView view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        return view;
    }

    private static MachinePinRows pins(EditorView view) {
        return MachineMode.of(view).orElseThrow().inspectorRows().pinRows().orElseThrow();
    }

    private StateMachine machine() {
        return document.document().definition(MachineFixtures.DEFINITION_ID).flatMap(ComponentDefinition::machine)
                .orElseThrow();
    }

    private ComponentDefinition definition() {
        return document.document().definition(MachineFixtures.DEFINITION_ID).orElseThrow();
    }

    private GridPoint pinLocation(String pinName) {
        return document.registry().typeOf(piece).pinLocation(piece, definition().pinIndexOf(pinName).orElseThrow());
    }

    /** A switch at 0 wired to {@code pinName} of the piece from 4 units to its left; returns the switch. */
    private Component switchOn(String pinName) {
        GridPoint pin = pinLocation(pinName);
        GridPoint end = pin.translate(-4, 0);
        ComponentType switchType = document.registry().require(StandardComponentTypes.SWITCH);
        Component probe = switchType.newComponent(new ElementId("probe"), GridPoint.ORIGIN, Rotation.R0, SWITCH_OFF,
                "");
        GridPoint offset = switchType.pinLocation(probe, 0);
        Component placed = UiTestSupport.place(document, StandardComponentTypes.SWITCH, end.x() - offset.x(),
                end.y() - offset.y(), SWITCH_OFF);
        UiTestSupport.addWire(document, pin.x(), pin.y(), end.x(), end.y());
        return placed;
    }

    private boolean aWireEndsAt(GridPoint point) {
        return document.document().wires().stream()
                .anyMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
    }

    private static StateMachine detectorWithTwoInputs() {
        StateMachine detector = MachineFixtures.detector();
        return detector.withTransitions(detector.transitions().stream()
                .map(transition -> transition.id().equals("t6") ? transition.withCondition("y") : transition)
                .toList()).withPinNames(List.of("x", "y"), List.of("Z"));
    }

    private static void type(JTextField field, String text) {
        field.setText(text);
        field.postActionEvent();
    }

    private static void pressKey(JComponent target, int keyCode, int modifiers) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyCode, modifiers);
        Object key = target.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        assertThat(key).as("a binding for " + stroke).isNotNull();
        target.getActionMap().get(key).actionPerformed(new ActionEvent(target, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void renamingAnInputFromTheListRenamesItEverywhereKeepsTheWireAndUndoesInOneStep() {
        EditorView view = tabOf(MachineFixtures.detector());
        switchOn("x");
        GridPoint wiredPin = pinLocation("x");
        MachinePinTable inputs = pins(view).table(Side.INPUT);

        type(inputs.nameField("x").orElseThrow(), "a");

        assertThat(machine().inputNames()).containsExactly("a");
        assertThat(machine().transitions()).extracting(MachineTransition::condition)
                .containsExactly("a", "a'", "a", "a", "", "");
        assertThat(pinLocation("a")).isEqualTo(wiredPin);
        assertThat(aWireEndsAt(pinLocation("a"))).as("the wire stays on the renamed pin").isTrue();
        assertThat(document.insideProblems()).isEmpty();
        assertThat(pins(view).table(Side.INPUT).nameField("a")).isPresent();

        perform(view.actions().undo());

        assertThat(machine().inputNames()).containsExactly("x");
        assertThat(machine().transitions().get(1).condition()).isEqualTo("x'");
        assertThat(aWireEndsAt(pinLocation("x"))).isTrue();
        assertThat(pins(view).table(Side.INPUT).nameField("x")).isPresent();
    }

    @Test
    void aTakenReservedOrWrongNameIsRefusedInlineAndChangesNothing() {
        EditorView view = tabOf(MachineFixtures.detector());
        MachinePinTable inputs = pins(view).table(Side.INPUT);

        type(inputs.nameField("x").orElseThrow(), "Z");
        assertThat(machine().inputNames()).containsExactly("x");
        assertThat(inputs.problemText()).contains("Ya hay una salida Z");

        type(inputs.nameField("x").orElseThrow(), "CLK");
        assertThat(inputs.problemText()).contains("CLK es de todas las máquinas");

        type(inputs.nameField("x").orElseThrow(), "2x");
        assertThat(inputs.problemText())
                .hasValueSatisfying(text -> assertThat(text).startsWith("«2x» no es un nombre"));
        assertThat(machine().inputNames()).containsExactly("x");
    }

    @Test
    void theNamesOfClockAndResetAreTheReadableMutedGreyNotTheDisabledOne() {
        EditorView view = tabOf(MachineFixtures.detector());
        MachinePinTable inputs = pins(view).table(Side.INPUT);

        assertThat(inputs.fixedNameColor("RST")).isEqualTo(cl.almejo.vsim.ui.theme.ReadableColors.mutedText());
        assertThat(inputs.fixedNameColor("CLK")).isEqualTo(cl.almejo.vsim.ui.theme.ReadableColors.mutedText());
        assertThat(inputs.fixedNameColor("x")).isNull();
    }

    @Test
    void theNoArrowTextIsDrawnWithTheReadableWarningColour() {
        EditorView view = tabOf(MachineFixtures.detector());
        MachinePinTable inputs = pins(view).table(Side.INPUT);
        inputs.addButton().doClick();
        type(inputs.newNameField().orElseThrow(), "b");
        inputs = pins(view).table(Side.INPUT);

        assertThat(inputs.usesLabelColor("b")).isEqualTo(cl.almejo.vsim.ui.theme.ReadableColors.warningText());
    }

    @Test
    void anAddedPinIsUnusedInWordsAndOnlyItHasARemoveButton() {
        EditorView view = tabOf(MachineFixtures.detector());
        MachinePinTable inputs = pins(view).table(Side.INPUT);

        inputs.addButton().doClick();
        type(inputs.newNameField().orElseThrow(), "b");

        assertThat(machine().inputNames()).containsExactly("x", "b");
        inputs = pins(view).table(Side.INPUT);
        assertThat(inputs.usesText("b")).isEqualTo("en ninguna flecha");
        assertThat(inputs.usesText("x")).isEqualTo("en 4 flechas");
        assertThat(inputs.removeButton("b")).isPresent();
        assertThat(inputs.removeButton("x")).as("a used pin cannot be removed").isEmpty();
        assertThat(inputs.usesLink("x").orElseThrow().getToolTipText()).contains("x se usa en 4 flechas")
                .as("a tooltip, with the term of the list and no word that only fits a plural")
                .doesNotEndWith(".").contains("«en ninguna flecha»").doesNotContain("verlas", "sin usar");
        assertThat(inputs.counterText()).isEqualTo("2 de 16");

        inputs.removeButton("b").orElseThrow().doClick();

        assertThat(machine().inputNames()).containsExactly("x");
        perform(view.actions().undo());
        assertThat(machine().inputNames()).containsExactly("x", "b");
    }

    @Test
    void clockAndResetAreShownAtTheEndAsAlwaysThere() {
        EditorView view = tabOf(MachineFixtures.detector());

        assertThat(pins(view).table(Side.INPUT).rowNames()).containsExactly("x", "RST", "CLK");
        assertThat(pins(view).table(Side.INPUT).usesText("CLK")).isEqualTo("siempre");
        assertThat(pins(view).table(Side.INPUT).nameField("CLK")).isEmpty();
        assertThat(pins(view).table(Side.OUTPUT).rowNames()).containsExactly("Z");
        assertThat(pins(view).table(Side.OUTPUT).usesText("Z")).isEqualTo("en 1 estado");
    }

    @Test
    void aClickOnTheUsesSelectsThoseArrowsAndLightsThemAtOnce() {
        EditorView view = tabOf(MachineFixtures.detector());
        MachineMode mode = MachineMode.of(view).orElseThrow();
        mode.canvas().setBounds(0, 0, 900, 600);

        pins(view).table(Side.INPUT).usesLink("x").orElseThrow().doClick();

        assertThat(mode.selection().transitionIds()).as("the arrows it names are chosen")
                .containsExactlyInAnyOrder("t1", "t2", "t3", "t4");
        assertThat(mode.selection().stateIds()).isEmpty();
        assertThat(mode.canvas().shownUses()).hasValueSatisfying(uses ->
                assertThat(uses.transitionIds()).containsExactlyInAnyOrder("t1", "t2", "t3", "t4"));
        assertThat(mode.canvas().isBlinking()).isTrue();
        assertThat(mode.canvas().isFlashLit()).as("lit from the first moment").isTrue();
    }

    @Test
    void withoutAnimationsTheUsesAreLitForAMomentWithoutBlinking() {
        preferences.setAnimations(false);
        EditorView view = tabOf(MachineFixtures.detector());
        MachineMode mode = MachineMode.of(view).orElseThrow();

        pins(view).table(Side.INPUT).usesLink("x").orElseThrow().doClick();

        assertThat(mode.selection().transitionIds()).containsExactlyInAnyOrder("t1", "t2", "t3", "t4");
        assertThat(mode.canvas().isBlinking()).isFalse();
        assertThat(mode.canvas().isLightingUses()).isTrue();
        assertThat(mode.canvas().shownUses()).isPresent();
    }

    @Test
    void altDownMovesAnInputBelowTheNextAndEachSwitchStaysOnItsOwnPin() {
        EditorView view = tabOf(detectorWithTwoInputs());
        Component xSwitch = switchOn("x");
        Component ySwitch = switchOn("y");

        pressKey(pins(view).table(Side.INPUT).nameField("x").orElseThrow(), KeyEvent.VK_DOWN,
                InputEvent.ALT_DOWN_MASK);

        assertThat(machine().inputNames()).containsExactly("y", "x");
        assertThat(pins(view).table(Side.INPUT).rowNames()).startsWith("y", "x");
        assertSwitchesOnTheirPins(xSwitch, ySwitch);

        pressKey(pins(view).table(Side.INPUT).nameField("x").orElseThrow(), KeyEvent.VK_UP,
                InputEvent.ALT_DOWN_MASK);
        assertThat(machine().inputNames()).containsExactly("x", "y");
        assertSwitchesOnTheirPins(xSwitch, ySwitch);
        perform(view.actions().undo());
        assertThat(machine().inputNames()).containsExactly("y", "x");
        assertSwitchesOnTheirPins(xSwitch, ySwitch);
        perform(view.actions().undo());
        assertThat(machine().inputNames()).containsExactly("x", "y");
        assertSwitchesOnTheirPins(xSwitch, ySwitch);
        perform(view.actions().redo());
        assertThat(machine().inputNames()).containsExactly("y", "x");
        assertSwitchesOnTheirPins(xSwitch, ySwitch);
    }

    /** The switch placed on x is on the net of the piece's pin named x, and the one placed on y on y's. */
    private void assertSwitchesOnTheirPins(Component xSwitch, Component ySwitch) {
        assertThat(netOf(piecePin("x"))).as("the switch wired to x").contains(new PinRef(xSwitch.id(), 0));
        assertThat(netOf(piecePin("y"))).as("the switch wired to y").contains(new PinRef(ySwitch.id(), 0));
    }

    private PinRef piecePin(String pinName) {
        return new PinRef(piece.id(), definition().pinIndexOf(pinName).orElseThrow());
    }

    private Set<PinRef> netOf(PinRef pin) {
        return Connectivity.of(document.document().wires(), document.document(), document.registry()).pinNets()
                .stream().filter(net -> net.contains(pin)).findFirst().orElse(Set.of(pin));
    }

    @Test
    void draggingARowByItsHandleReordersThePinsAndEachSwitchStaysOnItsOwnPin() {
        EditorView view = tabOf(detectorWithTwoInputs());
        Component xSwitch = switchOn("x");
        Component ySwitch = switchOn("y");
        MachinePinTable inputs = pins(view).table(Side.INPUT);
        inputs.setSize(300, 400);
        UiTestSupport.layOut(inputs);
        JComponent handle = inputs.handle("x").orElseThrow();
        JTextField target = inputs.nameField("y").orElseThrow();
        Rectangle targetBounds = SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), handle);
        Point below = new Point(2, targetBounds.y + targetBounds.height - 1);

        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_PRESSED, new Point(2, 2)));
        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_DRAGGED, below));
        handle.dispatchEvent(mouse(handle, MouseEvent.MOUSE_RELEASED, below));

        assertThat(machine().inputNames()).containsExactly("y", "x");
        assertSwitchesOnTheirPins(xSwitch, ySwitch);
        perform(view.actions().undo());
        assertThat(machine().inputNames()).containsExactly("x", "y");
        assertSwitchesOnTheirPins(xSwitch, ySwitch);
    }

    private static MouseEvent mouse(JComponent source, int id, Point point) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        return new MouseEvent(source, id, System.currentTimeMillis(), modifiers, point.x, point.y, 1, false,
                MouseEvent.BUTTON1);
    }

    @Test
    void turningTheInputsIntoABusShowsTheBitsAndTellsWhichWiresCameLoose() {
        EditorView view = tabOf(detectorWithTwoInputs());
        switchOn("x");
        switchOn("y");
        MachinePinTable inputs = pins(view).table(Side.INPUT);
        assertThat(inputs.busText()).isEmpty();

        inputs.busBox().doClick();

        assertThat(machine().inputsAsBus()).isTrue();
        assertThat(definition().pinIndexOf("in")).isPresent();
        inputs = pins(view).table(Side.INPUT);
        assertThat(inputs.busText()).isEqualTo("in[1:0]");
        assertThat(inputs.firstColumn()).startsWith("0", "1");
        assertThat(view.statusBar().text())
                .contains("Los cables de x, y quedaron sueltos (Deshacer los vuelve a conectar)");

        perform(view.actions().undo());

        assertThat(machine().inputsAsBus()).isFalse();
        assertThat(pins(view).table(Side.INPUT).busBox().isSelected()).isFalse();
        assertThat(aWireEndsAt(pinLocation("x"))).isTrue();
    }

    @Test
    void anEditOfAnotherCopyOfTheMachineNeverRepeatsTheCircuitsLastNotice() {
        EditorView view = tabOf(detectorWithTwoInputs());
        switchOn("x");
        switchOn("y");
        pins(view).table(Side.INPUT).busBox().doClick();
        assertThat(view.statusBar().text()).contains("quedaron sueltos");
        view.showNotice("Otra cosa");
        MachineCanvas canvas = MachineMode.of(view).orElseThrow().canvas();
        FakeMachineSource draft = new FakeMachineSource(canvas.machine().orElseThrow());
        canvas.useSource(draft);

        pins(view).table(Side.INPUT).busBox().doClick(); // in the draft: the circuit's last step is still the bus

        assertThat(draft.edits()).isEqualTo(1);
        assertThat(machine().inputsAsBus()).as("the circuit keeps its bus").isTrue();
        assertThat(view.statusBar().text()).doesNotContain("suelto");
    }

    @Test
    void theLooseWiresNoticeAgreesWithTheNumberOfWiresItTellsAbout() {
        RemovedPinWire first = new RemovedPinWire(Optional.empty(), new ElementId("c1"), "x", new GridPoint(0, 2));
        RemovedPinWire second = new RemovedPinWire(Optional.empty(), new ElementId("c2"), "x", new GridPoint(0, 12));

        assertThat(MachinePinRows.looseWiresNotice(List.of(first)))
                .contains("El cable de x quedó suelto (Deshacer lo vuelve a conectar)");
        assertThat(MachinePinRows.looseWiresNotice(List.of(first, second))).as("two copies wired on x")
                .contains("Los cables de x quedaron sueltos (Deshacer los vuelve a conectar)");
    }

    @Test
    void aPieceThatMustKeepItsOldShapeIsToldOnTheStatusLine() {
        EditorView view = tabOf(detectorWithTwoInputs());
        for (String pinName : List.of("x", "y")) {
            GridPoint pin = pinLocation(pinName);
            UiTestSupport.addWire(document, pin.x(), pin.y(), pin.x() - 1, pin.y());
        }

        pins(view).table(Side.INPUT).busBox().doClick();

        assertThat(document.document().component(piece.id()).orElseThrow().appearance())
                .as("one-unit stubs cannot be pulled back: RST would touch y's").isPresent();
        assertThat(view.statusBar().text()).contains("Los cables de x, y quedaron sueltos")
                .contains("el pin in quedó en otro lugar (su lugar estaba ocupado)");
    }

    @Test
    void aBusOfOneNameIsWrittenWithItsOnlyBit() {
        EditorView view = tabOf(MachineFixtures.detector());

        pins(view).table(Side.OUTPUT).busBox().doClick();

        assertThat(pins(view).table(Side.OUTPUT).busText()).isEqualTo("out[0]");
        assertThat(view.statusBar().text()).as("no wire was on Z").doesNotContain("suelto");
    }

    @Test
    void flancoSubidaMakesTheMachineStepOnTheRisingEdgeInOneUndoStep() {
        EditorView view = tabOf(MachineFixtures.detector());
        Component input = switchOn("x");
        Component clock = switchOn(MachineLimits.CLOCK);

        pins(view).edgeBox().setSelectedIndex(1);

        assertThat(machine().edge()).isEqualTo(StateMachine.EDGE_RISING);
        perform(view.actions().undo());
        assertThat(machine().edge()).isEqualTo(StateMachine.EDGE_FALLING);
        assertThat(pins(view).edgeBox().getSelectedIndex()).isZero();
        perform(view.actions().redo());
        assertThat(pins(view).edgeBox().getSelectedIndex()).isOne();
        MachineCanvas canvas = MachineMode.of(view).orElseThrow().canvas();
        document.interact(input.id());
        document.interact(clock.id());
        UiTestSupport.awaitSimulation(document);
        canvas.showLatestState();
        assertThat(canvas.activeState()).as("one rising edge with x = 1").hasValueSatisfying(state ->
                assertThat(state.stateId()).isEqualTo("s1"));
    }

    @Test
    void atSixteenInputsAddIsDisabledWithItsReason() {
        List<String> sixteen = IntStream.range(0, MachineLimits.MAXIMUM_INPUTS).mapToObj(index -> "i" + index)
                .toList();
        EditorView view = tabOf(MachineFixtures.detector().withPinNames(sixteen, List.of("Z")));
        MachinePinTable inputs = pins(view).table(Side.INPUT);

        assertThat(inputs.counterText()).isEqualTo("16 de 16");
        assertThat(inputs.addButton().isEnabled()).isFalse();
        assertThat(inputs.addButton().getToolTipText())
                .isEqualTo("No caben más entradas: una máquina tiene como mucho 16");
        assertThat(pins(view).table(Side.OUTPUT).addButton().isEnabled()).isTrue();
        assertThat(MachineMode.of(view).orElseThrow().inspectorRows().statesText()).contains("3 de 64");
    }

    @Test
    void textTypedInAPinFieldIsKeptWhenTheMachineChangesElsewhere() {
        EditorView view = tabOf(detectorWithTwoInputs());
        JTextField field = pins(view).table(Side.INPUT).nameField("y").orElseThrow();
        field.setText("ye");

        document.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID,
                machine().withEdge(StateMachine.EDGE_RISING)));

        assertThat(pins(view).table(Side.INPUT).nameField("y").orElseThrow().getText()).isEqualTo("ye");
    }

    @Test
    void anUnusedPinRenamedOntoAUsedOneTakesItsPlaceAndKeepsItsWire() {
        EditorView view = tabOf(MachineFixtures.detector().withPinNames(List.of("x", "b"), List.of("Z")));
        switchOn("b");

        type(pins(view).table(Side.INPUT).nameField("b").orElseThrow(), "x");

        assertThat(machine().inputNames()).containsExactly("x");
        assertThat(aWireEndsAt(pinLocation("x"))).as("b's wire went with it").isTrue();
        assertThat(machine().transitions().get(0).condition()).isEqualTo("x");
    }
}
