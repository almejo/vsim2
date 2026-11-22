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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine's limits (an event past the largest time, too many events at one time, an edge step that never reaches
 * its edge) pause the simulation with a {@link PauseNotice} in the user's words, never an English failure.
 */
class SimulationLimitTest {

    private static final String FAR = "test-far";
    private static final String RINGING = "test-limit-ringing";

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final AtomicLong nanos = new AtomicLong();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);

    /** At t = 5 it schedules an event as far as the largest time from there: past it. */
    private FlatCircuit far() {
        registry.register(new ComponentType(FAR, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    @Override
                    public void initialize(BehaviorContext context) {
                        context.schedule(5, () -> context.schedule(Long.MAX_VALUE, () -> { }));
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                    }
                }));
        return new FlatCircuit(List.of(new FlatComponent("f", registry.require(FAR), Map.of())),
                List.of(new FlatNet("out", List.of(new FlatPinRef("f", 0)), 1, false)));
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
    void anEventPastTheLargestTimePausesWithANoticeNotAFailure() {
        thread.update(far());
        thread.tick();

        thread.advanceToNextEvent();
        thread.tick();
        thread.tick();

        assertThat(thread.latestSnapshot().failure()).isEmpty();
        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().time()).as("the snapshot shows where it stopped").isEqualTo(5);
        PauseNotice notice = thread.latestPauseNotice().orElseThrow();
        assertThat(notice.messageKey()).isEqualTo(SimulationLimitException.TIME_OVERFLOW);
        assertThat(notice.time()).isEqualTo(5);
        assertThat(notice.fromPart()).isFalse();
    }

    @Test
    void anEventPastTheLargestTimeWhileRunningPausesWithTheNotice() {
        thread.update(far());
        thread.tick();
        thread.resume();
        thread.tick();
        nanos.addAndGet(20_000_000L);
        thread.tick();
        thread.tick();

        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().failure()).isEmpty();
        assertThat(thread.latestPauseNotice()).map(PauseNotice::messageKey)
                .contains(SimulationLimitException.TIME_OVERFLOW);
    }

    @Test
    void aZeroDelayLoopPausesWithANotice() {
        thread.update(ringing());
        thread.tick();

        thread.advanceToNextEvent();
        thread.tick();

        assertThat(thread.latestSnapshot().failure()).isEmpty();
        assertThat(thread.latestSnapshot().running()).isFalse();
        PauseNotice notice = thread.latestPauseNotice().orElseThrow();
        assertThat(notice.messageKey()).isEqualTo(SimulationLimitException.TOO_MANY_EVENTS);
        assertThat(notice.time()).isEqualTo(5);
        assertThat(thread.latestStep()).isEmpty();
    }

    @Test
    void theAiStepThatReachesALimitSaysItInItsReceipt() {
        thread.update(far());
        thread.tick();

        CompletableFuture<RequestReport> report = thread.stepWithin(10, 1_000);
        thread.tick();

        assertThat(report).isDone();
        assertThat(report.join().status()).isEqualTo(RequestReport.Status.FAILED);
        assertThat(report.join().snapshot().failure()).isEmpty();
        assertThat(report.join().lastPause()).map(PauseNotice::messageKey)
                .contains(SimulationLimitException.TIME_OVERFLOW);
    }

    @Test
    void anEdgeStepOutOfBudgetStopsWhereItGotAndSaysSo() {
        SimulationEngine engine = new SimulationEngine();
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        // k falls at 2 000; a 1 / 1 clock shorted by a source (left out of the edge, SIM-4) fills every unit before it
        engine.update(new FlatCircuit(List.of(
                new FlatComponent("k", standard.require(StandardComponentTypes.CLOCK),
                        Map.of("time-up", 1_000, "time-down", 1_000)),
                new FlatComponent("fast", standard.require(StandardComponentTypes.CLOCK),
                        Map.of("time-up", 1, "time-down", 1)),
                new FlatComponent("one", registry.require(TestComponentTypes.SOURCE), Map.of("value", "1"))),
                List.of(new FlatNet("clock", List.of(new FlatPinRef("k", 0)), 1, false),
                        new FlatNet("shorted", List.of(new FlatPinRef("fast", 0), new FlatPinRef("one", 0)), 1,
                                false))));

        StepResult step = engine.advanceToNextClockEdge(ClockEdgeMode.FALLING, 10,
                SimulationEngine.STEP_EVENT_BUDGET);

        assertThat(step.time()).isEqualTo(10);
        assertThat(step.edge()).isEmpty();
        assertThat(engine.now()).isEqualTo(10);
        PauseNotice notice = engine.takePauseRequest().orElseThrow();
        assertThat(notice.messageKey()).isEqualTo(SimulationEngine.CLOCK_EDGE_NOT_REACHED_MESSAGE);
        assertThat(notice.time()).isEqualTo(10);
        assertThat(notice.fromPart()).isFalse();
    }

    @Test
    void aPartsNoticeComesFromAPart() {
        assertThat(new PauseNotice("k", "clock.edge.shorted", List.of(), 0, 1).fromPart()).isTrue();
        assertThat(new PauseNotice("", SimulationLimitException.TOO_MANY_EVENTS, List.of(), 0, 1).fromPart())
                .isFalse();
    }
}
