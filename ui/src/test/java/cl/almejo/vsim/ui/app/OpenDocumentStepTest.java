package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.StepReport;
import cl.almejo.vsim.core.simulation.StepResult;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class OpenDocumentStepTest {

    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        document.close();
    }

    private String netOf(Component component, int pinIndex) {
        return document.netlist().netForPin(new PinRef(component.id(), pinIndex)).orElseThrow().id().value();
    }

    private void waitUntilPausedAndStable() {
        waitUntil(() -> !document.latestSnapshot().running() && document.latestNextEventTime().isEmpty());
    }

    /** Lets the running simulation settle the circuit (pausing at once would leave the gates' events pending). */
    private void settleThenEnterStepMode() {
        waitUntil(() -> document.latestSnapshot().token() == document.netlistToken()
                && document.latestSnapshot().running() && document.latestNextEventTime().isEmpty());
        document.setStepMode(true);
        waitUntilPausedAndStable();
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
        waitUntil(() -> !document.latestSnapshot().running());

        document.resume();
        assertThat(document.stepMode()).isFalse();
        assertThat(notifications).hasValue(2);
        waitUntil(() -> document.latestSnapshot().running());

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
        Component gate = place(document, StandardComponentTypes.NOT, 4, -1, Map.of());       // (4,1) → (8,1)
        settleThenEnterStepMode();
        long before = document.latestSnapshot().time();

        document.interact(toggle.id());
        waitUntil(() -> document.latestStep().isPresent());
        StepReport click = document.latestStep().orElseThrow();
        assertThat(click.result().changedNets()).contains(netOf(toggle, 0));
        assertThat(click.result().time()).isEqualTo(before);
        waitUntil(() -> document.latestNextEventTime().isPresent());
        assertThat(document.latestNextEventTime()).hasValue(before + 1);

        document.advanceToNextEvent();
        waitUntil(() -> document.latestStep().orElseThrow().sequence() == click.sequence() + 1);
        StepResult advance = document.latestStep().orElseThrow().result();
        assertThat(advance.fromTime()).isEqualTo(before);
        assertThat(advance.time()).isEqualTo(before + 1);
        assertThat(advance.changedNets()).contains(netOf(gate, 1));
        waitUntilPausedAndStable();
        assertThat(document.latestSnapshot().time()).isEqualTo(before + 1);
    }

    @Test
    void hasClockFollowsTheCircuit() {
        assertThat(document.hasClock()).isFalse();

        place(document, StandardComponentTypes.CLOCK, 0, 0, Map.of());
        assertThat(document.hasClock()).isTrue();

        document.history().undo();
        assertThat(document.hasClock()).isFalse();
    }
}
