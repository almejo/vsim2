package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How simulated time moves (SIM-2 of the 2026-11-20 bug hunt): at «Máxima» it jumps from event to event and stays put
 * when nothing is pending, like Logisim's; and it never passes {@code Long.MAX_VALUE} nor wraps to a negative time.
 */
class SimulatedTimeTest {

    private static final long MILLISECOND = 1_000_000L;
    private static final int CLOCK_HALF_PERIOD = 1_000_000;

    private final ComponentTypeRegistry testRegistry = TestComponentTypes.registry();
    /** A clock whose every look costs one millisecond, so a tick at «Máxima» runs about ten slices. */
    private final AtomicLong steppingNanos = new AtomicLong();

    private SimulationThread unlimitedThread(FlatCircuit circuit) {
        SimulationThread thread = new SimulationThread(new SimulationEngine(),
                () -> steppingNanos.addAndGet(MILLISECOND));
        thread.update(circuit);
        thread.setSpeed(Double.POSITIVE_INFINITY);
        thread.resume();
        return thread;
    }

    private FlatCircuit singleSource() {
        ComponentType source = testRegistry.require(TestComponentTypes.SOURCE);
        return new FlatCircuit(List.of(new FlatComponent("s", source, Map.of("value", "0"))),
                List.of(new FlatNet("out", List.of(new FlatPinRef("s", 0)), 1, false)));
    }

    private FlatCircuit slowClock() {
        Map<String, Object> parameters = Map.of("time-up", CLOCK_HALF_PERIOD, "time-down", CLOCK_HALF_PERIOD);
        ComponentType clock = StandardComponentTypes.registry().require(StandardComponentTypes.CLOCK)
                .specializedFor(parameters);
        return new FlatCircuit(List.of(new FlatComponent("clock", clock, parameters)),
                List.of(new FlatNet("out", List.of(new FlatPinRef("clock", 0)), 1, false)));
    }

    @Test
    void anIdleCircuitAtUnlimitedSpeedKeepsItsTime() {
        try (SimulationThread thread = unlimitedThread(singleSource())) {
            thread.tick();
            long settled = thread.latestSnapshot().time();
            for (int tick = 0; tick < 50; tick++) {
                thread.tick();
            }

            assertThat(thread.latestSnapshot().time()).as("nothing is pending, so no time passes").isEqualTo(settled);
            assertThat(thread.latestSnapshot().running()).isTrue();
            assertThat(thread.latestSnapshot().failure()).isEmpty();
        }
    }

    @Test
    void aClockedCircuitAtUnlimitedSpeedJumpsFromEdgeToEdge() {
        try (SimulationThread thread = unlimitedThread(slowClock())) {
            thread.tick();
            long before = thread.latestSnapshot().time();
            for (int tick = 0; tick < 5; tick++) {
                thread.tick();
            }
            long after = thread.latestSnapshot().time();

            assertThat(after).as("it still advances").isGreaterThan(before);
            assertThat(after % CLOCK_HALF_PERIOD).as("time stops at the clock's edges only").isZero();
        }
    }

    @Test
    void advancingNearTheLargestTimeStopsThereInsteadOfWrapping() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(FlatCircuit.EMPTY);
        engine.runUntil(Long.MAX_VALUE - 500_000_000L);

        engine.advanceUntilPause(1_000_000_000L);
        assertThat(engine.now()).isEqualTo(Long.MAX_VALUE);
        engine.advance(1_000_000_000L);
        assertThat(engine.now()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void aFiniteSpeedNearTheLargestTimeNeverGoesNegative() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(FlatCircuit.EMPTY);
        engine.runUntil(Long.MAX_VALUE - 1_000);
        AtomicLong nanos = new AtomicLong();
        try (SimulationThread thread = new SimulationThread(engine, nanos::get)) {
            thread.setSpeed(1_000);
            thread.resume();
            thread.tick();
            for (int tick = 0; tick < 5; tick++) {
                nanos.addAndGet(5 * MILLISECOND);
                thread.tick();
            }

            assertThat(thread.latestSnapshot().time()).isEqualTo(Long.MAX_VALUE);
            assertThat(thread.latestSnapshot().failure()).isEmpty();
        }
    }
}
