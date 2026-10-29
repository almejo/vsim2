package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.command.SetSwitchValueCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A click on a Switch while simulating changes its «Valor» (decided 2026-10-28): an edit that marks the file modified,
 * a run of clicks on one switch is one undo step, and the undo puts the old value back in the running simulation.
 */
class SwitchClickEditTest {

    private OpenDocument document;

    @BeforeEach
    void create() {
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        document.close();
    }

    private Object valueOf(Component component) {
        return current(component).parameters().get(StandardComponentTypes.VALUE);
    }

    private Component current(Component component) {
        return document.document().component(component.id()).orElseThrow();
    }

    private Value liveValueOf(Component component) {
        return ((VisibleState.SwitchState) awaitSimulation(document).componentState(component.id().value())).value();
    }

    private Component wideSwitch(String value) {
        return place(document, StandardComponentTypes.SWITCH, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 4, StandardComponentTypes.VALUE, value));
    }

    @Test
    void aClickChangesTheValueAndOneUndoPutsItBackInTheFileAndTheSimulation() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        document.history().markSaved();
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ONE);

        document.interact(toggle.id());

        assertThat(valueOf(toggle)).isEqualTo("0");
        assertThat(document.isModified()).isTrue();
        assertThat(document.history().undoDescription()).contains(SetSwitchValueCommand.CLICK_DESCRIPTION);
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ZERO);

        document.history().undo();

        assertThat(valueOf(toggle)).isEqualTo("1");
        assertThat(document.isModified()).isFalse();
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ONE);
    }

    @Test
    void threeBitClicksAreOneUndoStep() {
        Component bits = wideSwitch("0b0101");

        document.interactBit(bits.id(), 0);
        document.interactBit(bits.id(), 1);
        document.interactBit(bits.id(), 2);

        assertThat(valueOf(bits)).isEqualTo("0b10");
        assertThat(liveValueOf(bits)).isEqualTo(Value.of(4, 0b0010));
        document.history().undo();
        assertThat(valueOf(bits)).isEqualTo("0b101");
        assertThat(liveValueOf(bits)).isEqualTo(Value.of(4, 0b0101));
        assertThat(document.history().undoDescription()).contains("command.add");
    }

    @Test
    void aHighImpedanceSwitchClickedGoesToOne() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0,
                Map.of(StandardComponentTypes.VALUE, "Z"));

        document.interact(toggle.id());

        assertThat(valueOf(toggle)).isEqualTo("1");
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ONE);
    }

    @Test
    void anotherPropertyChangedAfterAClickKeepsTheClickedValue() {
        Component bits = wideSwitch("0b0101");
        document.interactBit(bits.id(), 3);
        document.execute(new SetLabelCommand(bits.id(), "A"));
        assertThat(liveValueOf(bits)).isEqualTo(Value.of(4, 0b1101));

        ComponentType type = document.registry().typeOf(current(bits));
        Map<String, Object> parameters = new HashMap<>(current(bits).parameters());
        parameters.put(MultiBitLayout.SIDE, MultiBitLayout.BOTTOM);
        document.execute(new ChangeParametersCommand(bits.id(), type, parameters));

        assertThat(valueOf(bits)).isEqualTo("0b1101");
        assertThat(liveValueOf(bits)).isEqualTo(Value.of(4, 0b1101));
    }

    @Test
    void aPausedClickIsOneStepWithItsNetsAndFiresOnce() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component gate = place(document, StandardComponentTypes.BUFFER, 4, -1,
                Map.of(StandardComponentTypes.NEGATE, true));
        waitUntil(() -> document.latestSnapshot().token() == document.netlistToken()
                && document.latestSnapshot().running() && document.latestNextEventTime().isEmpty());
        document.setStepMode(true);
        long before = awaitSimulation(document).time();

        document.interact(toggle.id());

        awaitSimulation(document);
        StepReport click = document.latestStep().orElseThrow();
        assertThat(click.result().changedNets()).contains(netOf(toggle, 0));
        assertThat(valueOf(toggle)).isEqualTo("0");
        assertThat(document.latestNextEventTime()).as("only the gate waits").hasValue(before + 1);
        document.advanceToNextEvent();
        awaitSimulation(document);
        assertThat(document.latestStep().orElseThrow().result().changedNets()).contains(netOf(gate, 1));
        assertThat(document.latestNextEventTime()).isEmpty();
    }

    @Test
    void aClickDoesNotMakeTheAisPreparedEditsStale() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        long version = document.contentVersion();
        long changes = document.changeCount();

        document.interact(toggle.id());
        document.history().undo();

        assertThat(document.contentVersion()).isEqualTo(version);
        assertThat(document.changeCount()).isGreaterThan(changes);
    }

    @Test
    void clicksAndTheirUndoKeepTheStructureRevisionAndOtherEditsMoveIt() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        long structure = document.structureRevision();

        document.interact(toggle.id());
        document.interact(toggle.id());
        document.interact(toggle.id());
        document.history().undo();
        assertThat(document.structureRevision()).isEqualTo(structure);
        assertThat(document.netlistToken()).isEqualTo(structure);
        assertThat(document.document().revision()).isGreaterThan(structure);

        document.execute(new SetLabelCommand(toggle.id(), "A"));
        assertThat(document.structureRevision()).isEqualTo(document.netlistToken()).isGreaterThan(structure);
    }

    @Test
    void aClickKeepsTheNetlistAndItsTokenSoWiresNeverLoseTheirValue() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        awaitSimulation(document);
        Netlist netlist = document.netlist();
        long token = document.netlistToken();

        document.interact(toggle.id());

        assertThat(document.netlist()).isSameAs(netlist);
        assertThat(document.netlistToken()).isEqualTo(token);
        assertThat(document.latestSnapshot().token()).as("the snapshot before the click still matches").isEqualTo(token);
        assertThat(awaitSimulation(document).token()).isEqualTo(token);
        document.history().undo();
        assertThat(document.netlistToken()).isEqualTo(token);
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ONE);
    }

    @Test
    void aRestartAfterAClickKeepsTheTokenAndTheClickedValue() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        document.interact(toggle.id());

        document.restart();

        assertThat(awaitSimulation(document).token()).isEqualTo(document.netlistToken());
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ZERO);
    }

    @Test
    void aClickRightAfterAnUndoStartsFromTheValueTheUndoPutBack() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        awaitSimulation(document);

        document.interact(toggle.id());
        document.history().undo();
        document.interact(toggle.id());

        assertThat(valueOf(toggle)).isEqualTo("0");
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ZERO);
    }

    @Test
    void aClickThatGivesBackTheStoredValueLeavesNoUndoStep() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        document.history().markSaved();
        document.setInputs(Map.of(toggle.id(), Value.ZERO));
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ZERO);

        document.interact(toggle.id());

        assertThat(liveValueOf(toggle)).isEqualTo(Value.ONE);
        assertThat(valueOf(toggle)).isEqualTo("1");
        assertThat(document.isModified()).isFalse();
        assertThat(document.history().undoDescription()).contains("command.add");
    }

    @Test
    void aPausedUndoOfAClickIsPublishedAsAStep() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        waitUntil(() -> document.latestSnapshot().token() == document.netlistToken()
                && document.latestSnapshot().running());
        document.setStepMode(true);
        document.interact(toggle.id());
        awaitSimulation(document);
        long clickStep = document.latestStep().orElseThrow().sequence();

        document.history().undo();

        awaitSimulation(document);
        StepReport undone = document.latestStep().orElseThrow();
        assertThat(undone.sequence()).isGreaterThan(clickStep);
        assertThat(undone.result().changedNets()).contains(netOf(toggle, 0));
        assertThat(liveValueOf(toggle)).isEqualTo(Value.ONE);
    }

    @Test
    void aClickOnAnInputPinIsStillOnlyAnInputEvent() {
        Component pin = place(document, InterfacePins.INPUT_PIN, 0, 0, Map.of());
        document.history().markSaved();

        document.interact(pin.id());

        assertThat(document.isModified()).isFalse();
    }

    private String netOf(Component component, int pinIndex) {
        return document.netlist().netForPin(new PinRef(component.id(), pinIndex)).orElseThrow().id().value();
    }
}
