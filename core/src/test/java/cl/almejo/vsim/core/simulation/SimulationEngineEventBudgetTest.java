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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link SimulationEngine#runWithEventBudget}: every path the AI reaches stops at its budget (IA.1, A§7). */
class SimulationEngineEventBudgetTest {

    private static final String RINGING_SWITCH = "test-ringing-switch";
    private static final String RINGING_AT_FIVE = "test-ringing-at-five";

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();

    /**
     * An input that, once set to 1, inverts its own output with no delay for ever (a zero-delay ring oscillator the
     * standard parts cannot build: their delays are at least 1).
     */
    private FlatCircuit ringingSwitch() {
        registry.register(new ComponentType(RINGING_SWITCH, ComponentCategory.DEBUG, new GridSize(2, 2),
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
                        if (!ringing) {
                            return;
                        }
                        if (context.input(0).equals(Value.ONE)) {
                            context.drive(1, Value.ZERO, 0);
                        } else if (context.input(0).equals(Value.ZERO)) {
                            context.drive(1, Value.ONE, 0);
                        }
                    }
                }));
        return new FlatCircuit(List.of(new FlatComponent("r", registry.require(RINGING_SWITCH), Map.of())),
                List.of(new FlatNet("loop", List.of(new FlatPinRef("r", 0), new FlatPinRef("r", 1)), 1, false)));
    }

    /** Output wired to its own input: at t = 5 it starts inverting itself with no delay, forever. */
    private FlatCircuit ringingAtFive() {
        registry.register(new ComponentType(RINGING_AT_FIVE, ComponentCategory.DEBUG, new GridSize(2, 2),
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
        return new FlatCircuit(List.of(new FlatComponent("r", registry.require(RINGING_AT_FIVE), Map.of())),
                List.of(new FlatNet("loop", List.of(new FlatPinRef("r", 0), new FlatPinRef("r", 1)), 1, false)));
    }

    private static FlatCircuit clock() {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        return new FlatCircuit(List.of(new FlatComponent("k", standard.require(StandardComponentTypes.CLOCK),
                Map.of("time-up", 1, "time-down", 1))),
                List.of(new FlatNet("clock", List.of(new FlatPinRef("k", 0)), 1, false)));
    }

    @Test
    void settingAnInputOfAZeroDelayRingStopsAtTheBudget() {
        engine.update(ringingSwitch());
        engine.advance(3);

        EventBudgetRun run = engine.runWithEventBudget(1_000, () -> engine.setInput("r", Value.ONE));

        assertThat(run.exceeded()).isTrue();
        assertThat(run.eventsProcessed()).isEqualTo(1_000);
        assertThat(engine.now()).isEqualTo(3);
    }

    @Test
    void aDurationOverAFreeRunningClockStopsAtTheBudgetWithTheTimeReached() {
        engine.update(clock());

        EventBudgetRun run = engine.runWithEventBudget(10, () -> engine.advanceBy(1_000_000));

        assertThat(run.exceeded()).isTrue();
        assertThat(run.eventsProcessed()).isEqualTo(10);
        assertThat(engine.now()).as("stopped where the budget ran out").isBetween(1L, 20L);
    }

    @Test
    void withinTheBudgetTheWorkCompletesAndCountsItsEvents() {
        engine.update(clock());

        EventBudgetRun run = engine.runWithEventBudget(100, () -> engine.advanceBy(10));

        assertThat(run.exceeded()).isFalse();
        assertThat(run.eventsProcessed()).isPositive().isLessThan(100);
        assertThat(engine.now()).isEqualTo(10);
    }

    @Test
    void afterARunTheEngineIsUnlimitedAgain() {
        engine.update(clock());
        engine.runWithEventBudget(5, () -> engine.advanceBy(1_000));

        long before = engine.now();
        engine.advance(1_000);

        assertThat(engine.now()).isEqualTo(before + 1_000);
    }

    @Test
    void theNextEventWithinABudgetIsLimitedByTheBudgetAlone() {
        engine.update(ringingAtFive());

        EventBudgetRun run = engine.runWithEventBudget(200_000, () -> engine.advanceToNextEvent(Integer.MAX_VALUE));

        assertThat(run.exceeded()).as("not the per-step limit of the user's «Avanzar»").isTrue();
        assertThat(run.eventsProcessed()).isEqualTo(200_000);
    }

    @Test
    void aFailureThatIsNotTheBudgetReachesTheCaller() {
        engine.update(clock());
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> engine.runWithEventBudget(10, () -> {
            throw failure;
        })).isSameAs(failure);
        engine.advance(1_000);
        assertThat(engine.now()).isEqualTo(1_000);
    }

    @Test
    void aBudgetMustBePositive() {
        assertThatThrownBy(() -> engine.runWithEventBudget(0, () -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
