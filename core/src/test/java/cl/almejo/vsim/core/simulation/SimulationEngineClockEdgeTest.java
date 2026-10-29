package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Avanzar al próximo flanco de reloj» stops at an edge of either kind and «Avanzar un ciclo de reloj» at the next
 * falling one; each step names the edge it stopped at.
 */
class SimulationEngineClockEdgeTest {

    private final SimulationEngine engine = new SimulationEngine();

    /** A standard clock: 0 for {@code timeDown}, then 1 for {@code timeUp}, and so on. */
    private static FlatComponent clock(String id, int timeDown, int timeUp) {
        return new FlatComponent(id, StandardComponentTypes.registry().require(StandardComponentTypes.CLOCK),
                Map.of("time-up", timeUp, "time-down", timeDown));
    }

    private static FlatNet net(String id, String componentId) {
        return new FlatNet(id, List.of(new FlatPinRef(componentId, 0)), 1, false);
    }

    private void oneClock() {
        engine.update(new FlatCircuit(List.of(clock("k", 1_000, 1_000)), List.of(net("clock", "k"))));
    }

    /** k1 (1 000 / 1 000) rises at 1 000, falls at 2 000 and rises at 3 000; k2 (1 500 / 1 500) falls at 3 000. */
    private void twoClocksOfDifferentPeriods() {
        engine.update(new FlatCircuit(List.of(clock("k1", 1_000, 1_000), clock("k2", 1_500, 1_500)),
                List.of(net("fast", "k1"), net("slow", "k2"))));
    }

    private List<String> stops(ClockEdgeMode mode, int count) {
        List<String> stops = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            StepResult step = engine.advanceToNextClockEdge(mode);
            stops.add(step.time() + " " + step.edge().orElseThrow());
        }
        return stops;
    }

    @Test
    void withOneClockEveryEdgeStopsAndTheKindAlternates() {
        oneClock();

        assertThat(stops(ClockEdgeMode.ANY, 4))
                .containsExactly("1000 RISING", "2000 FALLING", "3000 RISING", "4000 FALLING");
    }

    @Test
    void aFullCycleStopsOnlyAtTheFallingEdges() {
        oneClock();

        assertThat(stops(ClockEdgeMode.FALLING, 2)).containsExactly("2000 FALLING", "4000 FALLING");
    }

    @Test
    void anEdgeStepStartsWhereThePreviousOneStoppedAndCarriesTheNetsOfTheEdgeTime() {
        oneClock();
        engine.advance(5);

        StepResult rising = engine.advanceToNextClockEdge(ClockEdgeMode.ANY);

        assertThat(rising.fromTime()).isEqualTo(5);
        assertThat(rising.time()).isEqualTo(1_000);
        assertThat(rising.changedNets()).containsExactly("clock");
        assertThat(rising.nextEventTime()).hasValue(2_000);
    }

    @Test
    void withTwoClocksTheNextEdgeOfEitherStops() {
        twoClocksOfDifferentPeriods();

        assertThat(stops(ClockEdgeMode.ANY, 4))
                .containsExactly("1000 RISING", "1500 RISING", "2000 FALLING", "3000 BOTH");
    }

    @Test
    void twoClocksChangingOppositeWaysAtTheSameTimeAreBothEdges() {
        engine.update(new FlatCircuit(List.of(clock("k1", 1_000, 1_000), clock("k2", 500, 500)),
                List.of(net("fast", "k2"), net("slow", "k1"))));
        engine.advanceToNextClockEdge(ClockEdgeMode.ANY); // k2 rises at 500

        StepResult step = engine.advanceToNextClockEdge(ClockEdgeMode.ANY);

        assertThat(step.time()).isEqualTo(1_000);
        assertThat(step.edge()).contains(ClockEdge.BOTH);
    }

    @Test
    void aFullCycleWithTwoClocksNamesARisingEdgeAtTheSameTimeToo() {
        twoClocksOfDifferentPeriods();

        assertThat(stops(ClockEdgeMode.FALLING, 2)).containsExactly("2000 FALLING", "3000 BOTH");
    }

    @Test
    void withoutAClockNothingRunsAndNoEdgeIsNamed() {
        engine.update(new FlatCircuit(List.of(new FlatComponent("s",
                TestComponentTypes.registry().require(TestComponentTypes.SOURCE), Map.of("value", "0"))),
                List.of(net("a", "s"))));
        engine.advance(5);

        for (ClockEdgeMode mode : ClockEdgeMode.values()) {
            StepResult step = engine.advanceToNextClockEdge(mode);
            assertThat(step.isIdle()).as(mode.name()).isTrue();
            assertThat(step.edge()).as(mode.name()).isEmpty();
        }
    }

    @Test
    void anOrdinaryStepNamesNoEdge() {
        oneClock();

        assertThat(engine.advanceToNextEvent().edge()).as("the clock rose, but not in an edge step").isEmpty();
        assertThat(new StepResult(0, 0, Set.of(), OptionalLong.empty()).edge())
                .isEqualTo(Optional.empty());
    }
}
