package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A pin with drives in flight (VSim 1's rule, {@link RuntimeNet#resolve}): it yields to the settled pins only while
 * it is really letting go of the net, so a bus handoff shows no false short and a part slower than its input shows
 * the short it makes.
 */
class DrivesInFlightTest {

    private static final int SAMPLED_TIMES = 40;
    /** The time the feedback circuits run. */
    private static final int FEEDBACK_TIME = 200;
    /**
     * More events than a gate toggled every unit and its clock can make in {@link #FEEDBACK_TIME}: a few per unit. A
     * zero-time loop of re-resolutions runs into it at once (it once grew the queue until the heap ran out).
     */
    private static final int FEEDBACK_EVENT_BOUND = 2_000;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();

    private FlatComponent component(String id, String typeId, Map<String, Object> parameters) {
        ComponentType type = registry.require(typeId).specializedFor(parameters);
        return new FlatComponent(id, type, parameters);
    }

    private FlatComponent clock(int halfPeriod) {
        return component("clock", StandardComponentTypes.CLOCK, Map.of("time-up", halfPeriod, "time-down", halfPeriod));
    }

    private FlatComponent constant(String id, String value) {
        return component(id, StandardComponentTypes.CONSTANT, Map.of("width", 1, "value", value));
    }

    /** How many of the times 1..{@link #SAMPLED_TIMES} the net {@code netId} is in conflict, with a short listed. */
    private int timesInConflict(String netId) {
        int conflicts = 0;
        for (int time = 1; time <= SAMPLED_TIMES; time++) {
            engine.runUntil(time);
            SimulationSnapshot snapshot = engine.snapshot();
            boolean shortListed = snapshot.problems().stream()
                    .anyMatch(problem -> problem instanceof ShortCircuit shortCircuit
                            && shortCircuit.netId().equals(netId));
            if (snapshot.netValue(netId).conflict()) {
                assertThat(shortListed).as("a conflict at t = %d is listed as a short", time).isTrue();
                conflicts++;
            }
        }
        return conflicts;
    }

    /**
     * The probe of the 2026-11-20 bug hunt (SIM-1) and its review: with any delay the buffer drives 0 against the
     * Constante's 1 about half of the time, also when its delay is half a clock period off phase (3, 5 and 7 on a
     * clock of 1/1, 3 and 6 on one of 2/2), where its last drive in flight always agrees while it drives the 0.
     */
    @ParameterizedTest(name = "clock {0}/{0}, delay {1}")
    @MethodSource("clocksAndDelays")
    void aBufferSlowerThanItsClockShowsTheShortWithAConstant(int halfPeriod, int delay) {
        FlatComponent buffer = component("buffer", StandardComponentTypes.BUFFER, Map.of("delay", delay));
        engine.update(new FlatCircuit(List.of(clock(halfPeriod), buffer, constant("constant", "1")), List.of(
                new FlatNet("clock-net", List.of(new FlatPinRef("clock", 0), new FlatPinRef("buffer", 0)), 1, false),
                new FlatNet("shared", List.of(new FlatPinRef("buffer", 1), new FlatPinRef("constant", 0)), 1, false))));

        assertThat(timesInConflict("shared"))
                .as("once its first drive arrives, the buffer drives 0 half of the time against the constant's 1")
                .isBetween((SAMPLED_TIMES - delay) / 2 - 1, SAMPLED_TIMES / 2 + 1);
    }

    /**
     * A bus with two tri-states taking turns: «a» (1) is enabled by the clock through a buffer and «b» (0) by the
     * clock through a NOT, both of delay 1, so each turn starts as the other one ends. «b» has delay 2: with
     * {@code delayOfA} 2 both changes arrive together, with 1 or 3 one tri-state still drives while the other one's
     * change is on its way.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void aBusHandoffBetweenTwoTriStatesShowsNoShort(int delayOfA) {
        FlatComponent triStateA = component("a", StandardComponentTypes.TRI_STATE, Map.of("delay", delayOfA));
        FlatComponent triStateB = component("b", StandardComponentTypes.TRI_STATE, Map.of("delay", 2));
        FlatComponent buffer = component("buffer", StandardComponentTypes.BUFFER, Map.of("delay", 1));
        FlatComponent inverter = component("not", StandardComponentTypes.BUFFER,
                Map.of("delay", 1, StandardComponentTypes.NEGATE, true));
        engine.update(new FlatCircuit(List.of(clock(5), triStateA, triStateB, buffer, inverter, constant("one", "1"),
                constant("zero", "0")), List.of(
                new FlatNet("clock-net", List.of(new FlatPinRef("clock", 0), new FlatPinRef("buffer", 0),
                        new FlatPinRef("not", 0)), 1, false),
                new FlatNet("enable-a", List.of(new FlatPinRef("buffer", 1), new FlatPinRef("a", 2)), 1, false),
                new FlatNet("enable-b", List.of(new FlatPinRef("not", 1), new FlatPinRef("b", 2)), 1, false),
                new FlatNet("data-a", List.of(new FlatPinRef("one", 0), new FlatPinRef("a", 0)), 1, false),
                new FlatNet("data-b", List.of(new FlatPinRef("zero", 0), new FlatPinRef("b", 0)), 1, false),
                new FlatNet("bus", List.of(new FlatPinRef("a", 1), new FlatPinRef("b", 1)), 1, false))));

        assertThat(timesInConflict("bus")).isZero();
        assertThat(engine.snapshot().problems()).noneMatch(problem -> problem instanceof ShortCircuit);
    }

    @Test
    void theBusHandoffCarriesBothValues() {
        aBusHandoffBetweenTwoTriStatesShowsNoShort(2);
        engine.runUntil(SAMPLED_TIMES + 4);
        Value whileAHasTheBus = engine.netValue("bus");
        engine.runUntil(SAMPLED_TIMES + 9);
        Value whileBHasTheBus = engine.netValue("bus");

        assertThat(List.of(whileAHasTheBus, whileBHasTheBus)).containsExactlyInAnyOrder(Value.ONE, Value.ZERO);
    }

    /** Clocks of 1/1 and 2/2, each with every buffer delay 1..7. */
    static List<Arguments> clocksAndDelays() {
        List<Arguments> cases = new ArrayList<>();
        for (int halfPeriod = 1; halfPeriod <= 2; halfPeriod++) {
            for (int delay = 1; delay <= 7; delay++) {
                cases.add(Arguments.of(halfPeriod, delay));
            }
        }
        return cases;
    }

    /** Every gate, negated or not, with every delay 1..4, clock 1/1..3/3 and Constante 0 or 1. */
    static List<Arguments> feedbackCircuits() {
        List<Arguments> circuits = new ArrayList<>();
        for (String gate : List.of(StandardComponentTypes.AND, StandardComponentTypes.OR, StandardComponentTypes.XOR)) {
            for (boolean negate : new boolean[]{false, true}) {
                for (int delay = 1; delay <= 4; delay++) {
                    for (int halfPeriod = 1; halfPeriod <= 3; halfPeriod++) {
                        for (String constantValue : List.of("0", "1")) {
                            circuits.add(Arguments.of(gate, negate, delay, halfPeriod, constantValue));
                        }
                    }
                }
            }
        }
        return circuits;
    }

    /**
     * A gate whose output and one input share a net with a Constante, the other input on a clock: the gate's drives
     * in flight change course while the net they decide on feeds back into it. Re-resolving that net must not loop
     * at one time (the 2026-11-20 review: a NAND like this ran out of memory in TidyEquivalenceTest).
     */
    @ParameterizedTest(name = "{0} negate {1}, delay {2}, clock {3}/{3}, Constante {4}")
    @MethodSource("feedbackCircuits")
    void aGateFeedingBackIntoItsShortedNetRunsABoundedNumberOfEvents(String gate, boolean negate, int delay,
                                                                      int halfPeriod, String constantValue) {
        FlatComponent gateComponent = component("gate", gate,
                Map.of("delay", delay, StandardComponentTypes.NEGATE, negate));
        engine.update(new FlatCircuit(List.of(clock(halfPeriod), gateComponent, constant("constant", constantValue)),
                List.of(new FlatNet("clock-net", List.of(new FlatPinRef("clock", 0), new FlatPinRef("gate", 0)), 1,
                                false),
                        new FlatNet("shared", List.of(new FlatPinRef("gate", 1), new FlatPinRef("gate", 2),
                                new FlatPinRef("constant", 0)), 1, false))));

        EventBudgetRun run = engine.runWithEventBudget(FEEDBACK_EVENT_BOUND, () -> engine.runUntil(FEEDBACK_TIME));

        assertThat(run.exceeded()).as("stopped by the bound at t = %d", engine.now()).isFalse();
        assertThat(engine.now()).isEqualTo(FEEDBACK_TIME);
    }
}
