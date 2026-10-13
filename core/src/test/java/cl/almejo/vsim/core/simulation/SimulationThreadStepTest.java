package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** «Avanzar» through the simulation thread (M6.1-B). */
class SimulationThreadStepTest {

    private static final String RINGING = "test-ringing";

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final AtomicLong nanos = new AtomicLong();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);

    /** Source s (0) → inverter n1 (delay 1) → inverter n2 (delay 1): nets a, b and c. */
    private FlatCircuit inverterChain() {
        ComponentType source = registry.require(TestComponentTypes.SOURCE);
        ComponentType inverter = registry.require(TestComponentTypes.INVERTER);
        return new FlatCircuit(List.of(new FlatComponent("s", source, Map.of("value", "0")),
                new FlatComponent("n1", inverter, Map.of("delay", 1)),
                new FlatComponent("n2", inverter, Map.of("delay", 1))),
                List.of(new FlatNet("a", List.of(new FlatPinRef("s", 0), new FlatPinRef("n1", 0)), 1, false),
                        new FlatNet("b", List.of(new FlatPinRef("n1", 1), new FlatPinRef("n2", 0)), 1, false),
                        new FlatNet("c", List.of(new FlatPinRef("n2", 1)), 1, false)));
    }

    /** Output wired to its own input: at t = 5 it starts inverting itself with no delay, forever. */
    private FlatCircuit ringing() {
        registry.register(new ComponentType(RINGING, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    @Override
                    public void initialize(BehaviorContext context) {
                        context.schedule(5, () -> context.drive(1, Value.ONE, 0));
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                        context.drive(1, context.input(0).equals(Value.ONE) ? Value.ZERO : Value.ONE, 0);
                    }
                }));
        return new FlatCircuit(List.of(new FlatComponent("r", registry.require(RINGING), Map.of())),
                List.of(new FlatNet("loop", List.of(new FlatPinRef("r", 0), new FlatPinRef("r", 1)), 1, false)));
    }

    @Test
    void pausedClicksAndAdvancesPublishNumberedStepsAndTheNextEventTime() {
        assertThat(thread.latestStep()).isEmpty();
        thread.update(inverterChain());
        thread.tick();
        assertThat(thread.latestNextEventTime()).as("refreshed after an update").hasValue(1);

        thread.step(10);
        thread.tick();
        assertThat(thread.latestNextEventTime()).as("refreshed after Paso").isEmpty();
        assertThat(thread.latestStep()).as("Paso publishes no step").isEmpty();

        thread.interact("s");
        thread.tick();
        assertThat(thread.latestStep()).contains(
                new StepReport(1, new StepResult(10, 10, Set.of("a"), OptionalLong.of(11))));
        assertThat(thread.latestNextEventTime()).as("refreshed after a click").hasValue(11);

        thread.advanceToNextEvent();
        thread.tick();
        assertThat(thread.latestStep()).contains(
                new StepReport(2, new StepResult(10, 11, Set.of("b"), OptionalLong.of(12))));
        assertThat(thread.latestSnapshot().time()).isEqualTo(11);
        assertThat(thread.latestNextEventTime()).hasValue(12);
    }

    @Test
    void advanceAndClicksPublishNoStepWhileRunning() {
        thread.update(inverterChain());
        thread.resume();
        thread.tick();

        thread.advanceToNextEvent();
        thread.interact("s");
        thread.tick();

        assertThat(thread.latestStep()).isEmpty();
        assertThat(thread.latestSnapshot().time()).as("the advance was ignored").isZero();
    }

    @Test
    void aZeroDelayLoopFailsPausedAndPublishesNoStep() {
        thread.update(ringing());
        thread.tick();

        thread.advanceToNextEvent();
        thread.tick();

        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().failure()).contains("IllegalStateException: too many events at t = 5");
        assertThat(thread.latestStep()).isEmpty();
        assertThat(thread.latestNextEventTime()).hasValue(5);
    }

    @Test
    void pendingComponentsArePublishedWhilePausedAndEmptyWhileRunning() {
        assertThat(thread.latestPendingComponents()).isEmpty();
        thread.update(inverterChain());
        thread.tick();
        assertThat(thread.latestPendingComponents()).as("both inverters settle at t = 1").containsExactlyInAnyOrder(
                "n1", "n2");

        thread.advanceToNextEvent();
        thread.tick();
        assertThat(thread.latestPendingComponents()).containsExactly("n2");

        thread.resume();
        thread.tick();
        assertThat(thread.latestNextEventTime()).hasValue(2);
        assertThat(thread.latestPendingComponents()).as("running").isEmpty();
    }

    @Test
    void theNextClockEdgeIsPublishedAsAStepAndIgnoredWhileRunning() {
        thread.update(new FlatCircuit(List.of(new FlatComponent("k",
                StandardComponentTypes.registry().require(StandardComponentTypes.CLOCK),
                Map.of("time-up", 3, "time-down", 2))),
                List.of(new FlatNet("clock", List.of(new FlatPinRef("k", 0)), 1, false))));
        thread.tick();

        thread.advanceToNextClockEdge();
        thread.tick();
        assertThat(thread.latestStep()).contains(
                new StepReport(1, new StepResult(0, 5, Set.of("clock"), OptionalLong.of(7))));
        assertThat(thread.latestPendingComponents()).containsExactly("k");

        thread.resume();
        thread.advanceToNextClockEdge();
        thread.tick();
        assertThat(thread.latestStep().orElseThrow().sequence()).as("ignored while running").isEqualTo(1);
    }
}
