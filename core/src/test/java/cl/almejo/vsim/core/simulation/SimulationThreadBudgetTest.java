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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The requests with an event budget and one receipt each (IA.1 Task 4, A§7). */
class SimulationThreadBudgetTest {

    private static final String RINGING_SWITCH = "test-ringing-switch";
    private static final String RINGING_AT_FIVE = "test-ringing-at-five";

    private final ComponentTypeRegistry testRegistry = TestComponentTypes.registry();
    private final ComponentTypeRegistry standard = StandardComponentTypes.registry();
    private final AtomicLong nanos = new AtomicLong();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);

    private FlatComponent component(String id, String typeId, Map<String, Object> parameters) {
        ComponentType type = standard.require(typeId).specializedFor(parameters);
        return new FlatComponent(id, type, type.resolveParameters(parameters));
    }

    /** Switches "a" (net "na", read by NOT "gate" whose output is "out") and "b" (net "nb"). */
    private FlatCircuit switches() {
        return new FlatCircuit(List.of(component("a", StandardComponentTypes.SWITCH, Map.of()),
                component("b", StandardComponentTypes.SWITCH, Map.of()),
                component("gate", StandardComponentTypes.BUFFER, Map.of(StandardComponentTypes.NEGATE, true))),
                List.of(new FlatNet("na", List.of(new FlatPinRef("a", 0), new FlatPinRef("gate", 0)), 1, false),
                        new FlatNet("nb", List.of(new FlatPinRef("b", 0)), 1, false),
                        new FlatNet("out", List.of(new FlatPinRef("gate", 1)), 1, false)));
    }

    private FlatCircuit clock() {
        return new FlatCircuit(List.of(component("k", StandardComponentTypes.CLOCK,
                Map.of("time-up", 3, "time-down", 2))),
                List.of(new FlatNet("clock", List.of(new FlatPinRef("k", 0)), 1, false)));
    }

    /** An input that, once set to 1, inverts its own output with no delay for ever. */
    private FlatCircuit ringingSwitch() {
        testRegistry.register(new ComponentType(RINGING_SWITCH, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    private boolean ringing;

                    @Override
                    public void initialize(BehaviorContext context) {
                        context.drive(1, Value.ZERO, 0);
                    }

                    @Override
                    public boolean setValue(BehaviorContext context, Value value) {
                        ringing = value.equals(Value.ONE);
                        context.drive(1, value, 0);
                        return true;
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                        if (ringing) {
                            context.drive(1, context.input(0).equals(Value.ONE) ? Value.ZERO : Value.ONE, 0);
                        }
                    }
                }));
        return new FlatCircuit(List.of(new FlatComponent("r", testRegistry.require(RINGING_SWITCH), Map.of())),
                List.of(new FlatNet("loop", List.of(new FlatPinRef("r", 0), new FlatPinRef("r", 1)), 1, false)));
    }

    /** Output wired to its own input: at t = 5 it starts inverting itself with no delay, forever. */
    private FlatCircuit ringingAtFive() {
        testRegistry.register(new ComponentType(RINGING_AT_FIVE, ComponentCategory.DEBUG, new GridSize(2, 2),
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
        return new FlatCircuit(List.of(new FlatComponent("r", testRegistry.require(RINGING_AT_FIVE), Map.of())),
                List.of(new FlatNet("loop", List.of(new FlatPinRef("r", 0), new FlatPinRef("r", 1)), 1, false)));
    }

    private RequestReport applied(CompletableFuture<RequestReport> request) {
        assertThat(request).as("queued, not applied by the caller").isNotDone();
        thread.tick();
        assertThat(request).isDone();
        return request.join();
    }

    @Test
    void everyInputIsReportedAcceptedOrRejectedInTheOrderAsked() {
        thread.update(switches());
        thread.tick();

        RequestReport report = applied(thread.setInputsWithin(List.of(
                new InputAssignment("ghost", Value.ONE),
                new InputAssignment("b", Value.ZERO),
                new InputAssignment("gate", Value.ONE),
                new InputAssignment("a", Value.of(4, 3))), 1_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.COMPLETED);
        assertThat(report.inputs()).extracting(InputOutcome::componentId).containsExactly("ghost", "b", "gate", "a");
        assertThat(report.inputs()).extracting(InputOutcome::accepted).containsExactly(false, true, false, false);
        assertThat(report.inputs().get(0).reason()).contains("no component ghost");
        assertThat(report.inputs().get(2).reason()).contains("not an input");
        assertThat(report.snapshot().netValue("nb")).isEqualTo(Value.ZERO);
        assertThat(report.snapshot().netValue("na")).as("a rejected value changes nothing").isEqualTo(Value.ONE);
        assertThat(report.eventsProcessed()).isPositive();
    }

    @Test
    void aZeroDelayRingUnderSetInputsStopsAtTheBudgetAndPauses() {
        thread.update(ringingSwitch());
        thread.resume();
        thread.tick();

        RequestReport report = applied(thread.setInputsWithin(List.of(new InputAssignment("r", Value.ONE)), 5_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.BUDGET_EXCEEDED);
        assertThat(report.eventsProcessed()).isEqualTo(5_000);
        assertThat(report.snapshot().running()).as("the leftover events must not run away").isFalse();
        assertThat(report.snapshot().failure()).isEmpty();
        assertThat(report.inputs()).extracting(InputOutcome::accepted).containsExactly(true);

        thread.tick();
        assertThat(thread.latestSnapshot().running()).isFalse();
        assertThat(thread.latestSnapshot().failure()).isEmpty();
    }

    @Test
    void aZeroDelayRingUnderTheNextEventStopsAtTheBudgetNotAsAFailure() {
        thread.update(ringingAtFive());
        thread.tick();

        RequestReport report = applied(thread.advanceToNextEventWithin(1, 150_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.BUDGET_EXCEEDED);
        assertThat(report.eventsProcessed()).isEqualTo(150_000);
        assertThat(report.snapshot().time()).isEqualTo(5);
        assertThat(report.snapshot().failure()).isEmpty();
        assertThat(report.fromTime()).isZero();
    }

    @Test
    void aDurationOverAFreeRunningClockStopsAtTheBudgetWithTheTimeReached() {
        thread.update(clock());
        thread.tick();

        RequestReport report = applied(thread.stepWithin(1_000_000, 20));

        assertThat(report.status()).isEqualTo(RequestReport.Status.BUDGET_EXCEEDED);
        assertThat(report.eventsProcessed()).isEqualTo(20);
        assertThat(report.snapshot().time()).isBetween(1L, 100L);
        assertThat(report.nextEventTime()).isPresent();
        assertThat(thread.latestStep().orElseThrow().result().time()).isEqualTo(report.snapshot().time());
    }

    @Test
    void aDurationWithinTheBudgetGoesTheWholeWay() {
        thread.update(clock());
        thread.tick();

        RequestReport report = applied(thread.stepWithin(50, 1_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.COMPLETED);
        assertThat(report.snapshot().time()).isEqualTo(50);
        assertThat(report.fromTime()).isZero();
    }

    @Test
    void aTimeThatWouldOverflowIsRefusedAndNothingRuns() {
        thread.update(clock());
        thread.tick();
        applied(thread.stepWithin(10, 1_000));

        RequestReport report = applied(thread.stepWithin(Long.MAX_VALUE - 5, 1_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.REFUSED_TIME_OVERFLOW);
        assertThat(report.snapshot().time()).isEqualTo(10);
        assertThat(report.eventsProcessed()).isZero();
    }

    @Test
    void steppingWhileRunningIsRefusedAndNothingRuns() {
        thread.update(clock());
        thread.resume();
        thread.tick();

        RequestReport report = applied(thread.advanceToNextClockEdgeWithin(ClockEdgeMode.FALLING, 1, 1_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.REFUSED_WHILE_RUNNING);
        assertThat(report.eventsProcessed()).isZero();
        assertThat(report.snapshot().running()).isTrue();
    }

    @Test
    void theNextClockEdgeStopsRightAfterTheFallingEdge() {
        thread.update(clock());
        thread.tick();

        RequestReport report = applied(thread.advanceToNextClockEdgeWithin(ClockEdgeMode.FALLING, 1, 1_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.COMPLETED);
        assertThat(report.snapshot().netValue("clock")).isEqualTo(Value.ZERO);
        assertThat(report.snapshot().time()).isPositive();
    }

    @Test
    void severalClockEdgesAreOneStepFromTheFirstStartToTheLastEdge() {
        thread.update(clock());
        thread.tick();
        RequestReport one = applied(thread.advanceToNextClockEdgeWithin(ClockEdgeMode.FALLING, 1, 1_000));

        RequestReport three = applied(thread.advanceToNextClockEdgeWithin(ClockEdgeMode.FALLING, 3, 1_000));

        long period = 5; // time-up 3 + time-down 2
        assertThat(three.fromTime()).isEqualTo(one.snapshot().time());
        assertThat(three.snapshot().time()).isEqualTo(one.snapshot().time() + 3 * period);
        assertThat(thread.latestStep().orElseThrow().result().fromTime()).isEqualTo(three.fromTime());
    }

    @Test
    void halfCyclesAlternateAndTheReportNamesTheLastEdge() {
        thread.update(clock()); // rises at 2, falls at 5, rises at 7
        thread.tick();

        RequestReport rising = applied(thread.advanceToNextClockEdgeWithin(ClockEdgeMode.ANY, 1, 1_000));
        RequestReport twoMore = applied(thread.advanceToNextClockEdgeWithin(ClockEdgeMode.ANY, 2, 1_000));

        assertThat(rising.snapshot().time()).isEqualTo(2);
        assertThat(rising.clockEdge()).contains(ClockEdge.RISING);
        assertThat(twoMore.snapshot().time()).isEqualTo(7);
        assertThat(twoMore.clockEdge()).contains(ClockEdge.RISING);
        assertThat(thread.latestStep().orElseThrow().result().edge()).contains(ClockEdge.RISING);
    }

    @Test
    void aStepThatIsNotAnEdgeStepNamesNoEdge() {
        thread.update(clock());
        thread.tick();

        RequestReport report = applied(thread.advanceToNextEventWithin(1, 1_000));

        assertThat(report.clockEdge()).isEmpty();
    }

    @Test
    void severalEventsStopEarlyOnceNothingIsPending() {
        thread.update(switches());
        thread.tick();
        applied(thread.setInputsWithin(List.of(new InputAssignment("a", Value.ZERO)), 1_000));

        RequestReport report = applied(thread.advanceToNextEventWithin(50, 1_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.COMPLETED);
        assertThat(report.nextEventTime()).isEmpty();
        assertThat(report.snapshot().netValue("out")).isEqualTo(Value.ONE);
    }

    @Test
    void theReportSnapshotIsTheStateRightAfterTheRequest() {
        FlatCircuit circuit = switches();
        thread.update(new FlatCircuit(7, circuit.components(), circuit.nets()));
        thread.tick();

        RequestReport report = applied(thread.report());
        thread.tick();

        assertThat(report.status()).isEqualTo(RequestReport.Status.COMPLETED);
        assertThat(report.snapshot().netValues()).isEqualTo(thread.latestSnapshot().netValues());
        assertThat(report.snapshot().token()).isEqualTo(7);
        assertThat(report.eventsProcessed()).isZero();
    }

    @Test
    void aRestartWithinABudgetStartsAgainAtTimeZero() {
        thread.update(clock());
        thread.tick();
        applied(thread.stepWithin(40, 1_000));

        RequestReport report = applied(thread.restartWithin(clock(), Map.of(), 1_000));

        assertThat(report.status()).isEqualTo(RequestReport.Status.COMPLETED);
        assertThat(report.snapshot().time()).isZero();
    }

    @Test
    void aBudgetMustBePositive() {
        assertThatThrownBy(() -> thread.stepWithin(10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> thread.stepWithin(0, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRunningThreadStaysResponsiveAfterARunawayRequest() throws Exception {
        try (SimulationThread running = new SimulationThread(new SimulationEngine())) {
            running.update(ringingAtFive());
            running.start();

            RequestReport report = running.advanceToNextEventWithin(1, 100_000).get(10, TimeUnit.SECONDS);
            running.pause();
            SimulationSnapshot afterPause = running.published().get(10, TimeUnit.SECONDS);

            assertThat(report.status()).isEqualTo(RequestReport.Status.BUDGET_EXCEEDED);
            assertThat(afterPause.running()).isFalse();
        }
    }
}
