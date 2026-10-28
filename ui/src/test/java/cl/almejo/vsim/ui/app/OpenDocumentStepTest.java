package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class OpenDocumentStepTest {

    private OpenDocument document;

    @BeforeEach
    void create() {
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        document.close();
    }

    private String netOf(Component component, int pinIndex) {
        return document.netlist().netForPin(new PinRef(component.id(), pinIndex)).orElseThrow().id().value();
    }

    /** Lets the running simulation settle the circuit (pausing at once would leave the gates' events pending). */
    private void settleThenEnterStepMode() {
        waitUntil(() -> document.latestSnapshot().token() == document.netlistToken()
                && document.latestSnapshot().running() && document.latestNextEventTime().isEmpty());
        document.setStepMode(true);
        assertThat(awaitSimulation(document).running()).isFalse();
        assertThat(document.latestNextEventTime()).isEmpty();
    }

    @Test
    void stepModePausesIsSharedAndIniciarLeavesIt() {
        AtomicInteger notifications = new AtomicInteger();
        Runnable listener = notifications::incrementAndGet;
        document.addStepModeListener(listener);
        waitUntil(() -> document.latestSnapshot().running());
        assertThat(document.stepMode()).isFalse();

        document.setStepMode(true);
        document.setStepMode(true);
        assertThat(document.stepMode()).isTrue();
        assertThat(notifications).as("only real changes are announced").hasValue(1);
        assertThat(awaitSimulation(document).running()).isFalse();

        document.resume();
        assertThat(document.stepMode()).isFalse();
        assertThat(notifications).hasValue(2);
        assertThat(awaitSimulation(document).running()).isTrue();

        document.setStepMode(true);
        document.setStepMode(false);
        assertThat(notifications).hasValue(4);
        assertThat(document.stepModeListenerCount()).isEqualTo(1);
        document.removeStepModeListener(listener);
        assertThat(document.stepModeListenerCount()).isZero();
    }

    @Test
    void aPausedClickAndAnAdvanceArePublishedAsNumberedSteps() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());   // out (4,1), value 1
        Component gate = place(document, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));       // (4,1) → (8,1)
        settleThenEnterStepMode();
        long before = document.latestSnapshot().time();

        document.interact(toggle.id());
        awaitSimulation(document);
        StepReport click = document.latestStep().orElseThrow();
        assertThat(click.result().changedNets()).contains(netOf(toggle, 0));
        assertThat(click.result().time()).isEqualTo(before);
        assertThat(document.latestNextEventTime()).hasValue(before + 1);

        document.advanceToNextEvent();
        SimulationSnapshot advanced = awaitSimulation(document);
        StepReport advance = document.latestStep().orElseThrow();
        assertThat(advance.sequence()).isEqualTo(click.sequence() + 1);
        assertThat(advance.result().fromTime()).isEqualTo(before);
        assertThat(advance.result().time()).isEqualTo(before + 1);
        assertThat(advance.result().changedNets()).contains(netOf(gate, 1));
        assertThat(advanced.running()).isFalse();
        assertThat(advanced.time()).isEqualTo(before + 1);
        assertThat(document.latestNextEventTime()).isEmpty();
    }

    @Test
    void hasClockFollowsTheCircuit() {
        assertThat(document.hasClock()).isFalse();

        place(document, StandardComponentTypes.CLOCK, 0, 0, Map.of());
        assertThat(document.hasClock()).isTrue();

        document.history().undo();
        assertThat(document.hasClock()).isFalse();
    }

    @Test
    void restartIsNotADocumentChangeAndKeepsTheSwitchesAndTheMode() {
        Component toggle = place(document, StandardComponentTypes.SWITCH, 0, 0, Map.of());   // value 1
        place(document, StandardComponentTypes.BUFFER, 4, -1, Map.of(StandardComponentTypes.NEGATE, true));
        settleThenEnterStepMode();
        assertThat(document.latestSnapshot().time()).as("the NOT gate settled after t = 0").isPositive();
        document.interact(toggle.id());
        awaitSimulation(document);
        boolean modified = document.isModified();
        long changes = document.changeCount();
        Object undo = document.history().undoDescription();

        document.restart();

        assertThat(awaitSimulation(document).time()).isZero();
        assertThat(document.latestSnapshot().componentState(toggle.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ZERO));
        assertThat(document.latestSnapshot().running()).isFalse();
        assertThat(document.stepMode()).isTrue();
        assertThat(document.isModified()).isEqualTo(modified);
        assertThat(document.changeCount()).isEqualTo(changes);
        assertThat(document.history().undoDescription()).isEqualTo(undo);
    }
}
