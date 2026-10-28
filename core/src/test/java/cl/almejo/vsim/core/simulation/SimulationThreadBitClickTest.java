package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** A bit of a multi-bit Switch clicked through the simulation thread: paused it is a step, running it is not. */
class SimulationThreadBitClickTest {

    private final AtomicLong nanos = new AtomicLong();
    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), nanos::get);

    private FlatCircuit eightBitSwitch() {
        ComponentType switchType = StandardComponentTypes.registry().require(StandardComponentTypes.SWITCH);
        Map<String, Object> parameters = Map.of("width", 8, "value", "0b00001010");
        return new FlatCircuit(List.of(new FlatComponent("s", switchType.specializedFor(parameters), parameters)),
                List.of(new FlatNet("n", List.of(new FlatPinRef("s", 0)), 8, false)));
    }

    @Test
    void aBitClickedWhilePausedFlipsThatBitAndPublishesAStepWithTheNet() {
        thread.update(eightBitSwitch());
        thread.tick();

        thread.interactBit("s", 7);
        thread.tick();

        assertThat(thread.latestSnapshot().netValue("n")).isEqualTo(Value.of(8, 0b10001010));
        assertThat(thread.latestStep().orElseThrow().result().changedNets()).isEqualTo(Set.of("n"));
        assertThat(thread.latestStep().orElseThrow().sequence()).isEqualTo(1);
    }

    @Test
    void aBitClickedWhileRunningFlipsThatBitAndPublishesNoStep() {
        thread.update(eightBitSwitch());
        thread.resume();
        thread.tick();

        thread.interactBit("s", 0);
        thread.tick();

        assertThat(thread.latestSnapshot().netValue("n")).isEqualTo(Value.of(8, 0b00001011));
        assertThat(thread.latestStep()).isEmpty();
    }
}
