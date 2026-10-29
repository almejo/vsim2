package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What a component's {@code drive} schedules: an event only when the pin may end different from how it is. */
class ComponentInstanceDriveTest {

    private static final String ALWAYS_LOW = "test-always-low";
    private static final String PULSE = "test-pulse";

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();

    private FlatComponent component(String id, String typeId, Map<String, Object> parameters) {
        return new FlatComponent(id, registry.require(typeId), parameters);
    }

    private static FlatNet net(String id, FlatPinRef... pins) {
        return new FlatNet(id, List.of(pins), 1, false);
    }

    private static FlatPinRef pin(String componentId, int pinIndex) {
        return new FlatPinRef(componentId, pinIndex);
    }

    /** Source s (0) on net a → component {@code typeId} as "c", whose output is net b. */
    private FlatCircuit sourceInto(String typeId) {
        return new FlatCircuit(List.of(component("s", TestComponentTypes.SOURCE, Map.of("value", "0")),
                component("c", typeId, Map.of())),
                List.of(net("a", pin("s", 0), pin("c", 0)), net("b", pin("c", 1))));
    }

    private Value valueOf(String netId) {
        return engine.snapshot().netValues().get(netId);
    }

    /** Drives 0 with delay 1 at start and again whenever its input changes, whatever the input is. */
    private void registerAlwaysLow() {
        registry.register(new ComponentType(ALWAYS_LOW, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    @Override
                    public void initialize(BehaviorContext context) {
                        context.drive(1, Value.ZERO, 1);
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                        context.drive(1, Value.ZERO, 1);
                    }
                }));
    }

    /** On every input change: 1 after 2 units, then back to 0 after 3 units. */
    private void registerPulse() {
        registry.register(new ComponentType(PULSE, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    @Override
                    public void initialize(BehaviorContext context) {
                        context.drive(1, Value.ZERO, 0);
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                        context.drive(1, Value.ONE, 2);
                        context.drive(1, Value.ZERO, 3);
                    }
                }));
    }

    @Test
    void aDriveThatLeavesThePinAsItIsSchedulesNothing() {
        registerAlwaysLow();
        engine.update(sourceInto(ALWAYS_LOW));
        engine.advance(5);
        assertThat(valueOf("b")).isEqualTo(Value.ZERO);

        StepResult step = engine.interactTracked("s");

        assertThat(step.changedNets()).contains("a");
        assertThat(valueOf("a")).isEqualTo(Value.ONE);
        assertThat(engine.nextEventTime()).as("driving 0 on a pin already at 0 is not an event").isEmpty();
        assertThat(engine.pendingComponents()).isEmpty();
        engine.advance(10);
        assertThat(valueOf("b")).isEqualTo(Value.ZERO);
    }

    @Test
    void aDriveBehindAPendingOneKeepsItsPlace() {
        registerPulse();
        engine.update(sourceInto(PULSE));
        engine.advance(5);
        assertThat(valueOf("b")).isEqualTo(Value.ZERO);

        engine.interactTracked("s");

        assertThat(engine.nextEventTime()).hasValue(7);
        StepResult rise = engine.advanceToNextEvent();
        assertThat(rise.changedNets()).contains("b");
        assertThat(valueOf("b")).isEqualTo(Value.ONE);
        assertThat(engine.nextEventTime()).as("the drive back to 0 waits behind the drive to 1").hasValue(8);
        StepResult fall = engine.advanceToNextEvent();
        assertThat(fall.changedNets()).contains("b");
        assertThat(valueOf("b")).isEqualTo(Value.ZERO);
        assertThat(engine.pendingComponents()).isEmpty();
    }

    @Test
    void aSourceThatStartsAtHighImpedanceHasNothingToDo() {
        engine.update(new FlatCircuit(List.of(component("s", TestComponentTypes.SOURCE, Map.of("value", "Z"))),
                List.of(net("a", pin("s", 0)))));

        assertThat(valueOf("a")).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(engine.nextEventTime()).isEmpty();
    }
}
