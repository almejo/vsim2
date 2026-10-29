package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A click is an edit of the Switch's «Valor» (2026-10-28): the edit that records it must not build the switch again
 * nor drive its value a second time, and an undo hands the old value to the running switch.
 */
class SwitchValueParameterTest {

    private final ComponentType standardSwitch = StandardComponentTypes.registry()
            .require(StandardComponentTypes.SWITCH);
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger valuesDriven = new AtomicInteger();
    /** The standard one-bit switch, counting the behaviours built and what they drive when handed a new value. */
    private final ComponentType countingSwitch = new ComponentType(StandardComponentTypes.SWITCH,
            ComponentCategory.INPUT_OUTPUT, standardSwitch.size(), List.of(standardSwitch.pin(0)),
            standardSwitch.parameters(), parameters -> {
                created.incrementAndGet();
                return new CountingBehavior(standardSwitch.createBehavior(parameters));
            });
    private final SimulationEngine engine = new SimulationEngine();

    private FlatCircuit circuit(Map<String, Object> parameters) {
        return new FlatCircuit(List.of(new FlatComponent("s", countingSwitch, parameters)),
                List.of(new FlatNet("n", List.of(new FlatPinRef("s", 0)), 1, false)));
    }

    private static Map<String, Object> value(String text) {
        return Map.of(StandardComponentTypes.VALUE, text);
    }

    @Test
    void aNewValueKeepsTheRunningSwitchAndDrivesItNow() {
        engine.update(circuit(value("1")));
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ONE);

        engine.update(circuit(value("0")));

        assertThat(created).hasValue(1);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
        assertThat(engine.nextEventTime()).isEmpty();
    }

    @Test
    void theValueAClickAlreadyDrivesIsNotDrivenAgain() {
        engine.update(circuit(value("1")));
        engine.interact("s");

        engine.update(circuit(value("0")));

        assertThat(created).hasValue(1);
        assertThat(valuesDriven).as("the click reached the engine first").hasValue(0);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
    }

    @Test
    void anUndoOfAClickDrivesTheOldValueAgain() {
        engine.update(circuit(value("1")));
        engine.interact("s");
        engine.update(circuit(value("0")));

        engine.update(circuit(value("1")));

        assertThat(created).hasValue(1);
        assertThat(valuesDriven).hasValue(1);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ONE);
    }

    @Test
    void anotherParameterStillBuildsTheSwitchAgainWithItsValue() {
        engine.update(circuit(value("0")));

        engine.update(circuit(Map.of(StandardComponentTypes.VALUE, "0",
                MultiBitLayout.SIDE, MultiBitLayout.BOTTOM)));

        assertThat(created).hasValue(2);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
    }

    /** Review M-2: «Números» only says how the value reads, so changing it keeps the value the switch drives now. */
    @Test
    void theNumbersOnlyChangeHowTheValueReadsSoTheRunningSwitchStays() {
        engine.update(circuit(value("1")));
        engine.interact("s");

        engine.update(circuit(Map.of(StandardComponentTypes.VALUE, "1",
                StandardComponentTypes.SIGNEDNESS, StandardComponentTypes.SIGNEDNESS_SIGNED)));

        assertThat(created).hasValue(1);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
    }

    @Test
    void handedLiveValuesDriveTheNewValueKeepingTheCircuitAndItsToken() {
        engine.update(new FlatCircuit(7, circuit(value("1")).components(), circuit(value("1")).nets()));

        assertThat(engine.takeLiveValues(Map.of("s", value("0")))).isTrue();

        assertThat(created).hasValue(1);
        assertThat(engine.snapshot().token()).isEqualTo(7);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
    }

    @Test
    void aHandedValueTheSwitchDrivesAlreadyChangesNothingAndUnknownIdsAreSkipped() {
        engine.update(circuit(value("1")));
        engine.interact("s");

        assertThat(engine.takeLiveValues(Map.of("s", value("0"), "gone", value("1")))).isFalse();

        assertThat(valuesDriven).hasValue(0);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
    }

    @Test
    void handedLiveValuesTrackedReportTheNetsTheyChangedAndNothingWhenNothingChanged() {
        engine.update(circuit(value("1")));

        assertThat(engine.takeLiveValuesTracked(Map.of("s", value("0"))).orElseThrow().changedNets())
                .contains("n");
        assertThat(engine.takeLiveValuesTracked(Map.of("s", value("0")))).isEmpty();
    }

    @Test
    void onlyTheSwitchValueIsALiveValue() {
        assertThat(standardSwitch.parameters()).filteredOn(ParameterSpec::liveValue)
                .extracting(ParameterSpec::name).containsExactly(StandardComponentTypes.VALUE);
        assertThat(StandardComponentTypes.registry().require(StandardComponentTypes.CONSTANT).parameters())
                .noneMatch(ParameterSpec::liveValue);
    }

    /** Hands every call to the switch's own behaviour, counting what it drives when handed a new value. */
    private final class CountingBehavior implements Behavior {
        private final Behavior delegate;

        private CountingBehavior(Behavior delegate) {
            this.delegate = delegate;
        }

        @Override
        public void initialize(BehaviorContext context) {
            delegate.initialize(context);
        }

        @Override
        public void inputChanged(BehaviorContext context, int pinIndex) {
            delegate.inputChanged(context, pinIndex);
        }

        @Override
        public VisibleState visibleState() {
            return delegate.visibleState();
        }

        @Override
        public boolean isInteractive() {
            return true;
        }

        @Override
        public void interact(BehaviorContext context) {
            delegate.interact(context);
        }

        @Override
        public void takeLiveValue(BehaviorContext context, Map<String, Object> parameters) {
            delegate.takeLiveValue(new BehaviorContext() {
                @Override
                public Value input(int pinIndex) {
                    return context.input(pinIndex);
                }

                @Override
                public void drive(int pinIndex, Value value, long delay) {
                    valuesDriven.incrementAndGet();
                    context.drive(pinIndex, value, delay);
                }

                @Override
                public void schedule(long delay, Runnable action) {
                    context.schedule(delay, action);
                }

                @Override
                public long now() {
                    return context.now();
                }
            }, parameters);
        }
    }
}
