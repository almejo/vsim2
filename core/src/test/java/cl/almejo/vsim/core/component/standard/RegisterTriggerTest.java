package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The register's «Disparo»: the two edges and the two latch levels. */
class RegisterTriggerTest {

    private static final int DATA = 0;
    private static final int ENABLE = 1;
    private static final int CLEAR = 2;
    private static final int CLOCK = 3;
    private static final int OUTPUT = 4;
    private static final String[] TRIGGERS = {"falling", "rising", "high", "low"};

    private static BlockHarness register(String trigger) {
        return new BlockHarness(StandardComponentTypes.REGISTER,
                Map.of(StandardComponentTypes.WIDTH, 4, "trigger", trigger)).set(ENABLE, 1).settle();
    }

    private static Value four(long bits) {
        return Value.of(4, bits);
    }

    @Test
    void theTriggerIsTheLastParameterAndFallingIsTheDefault() {
        ParameterSpec parameter = StandardComponentTypes.registry().require(StandardComponentTypes.REGISTER)
                .parameters().getLast();
        assertThat(parameter.name()).isEqualTo("trigger");
        assertThat(parameter.defaultValue()).isEqualTo("falling");
        assertThat(parameter.choices()).containsExactly("falling", "rising", "high", "low");
    }

    @Test
    void aRisingRegisterLoadsOnlyOnZeroToOneAndNotOnTheFallingEdge() {
        BlockHarness harness = register("rising");
        harness.set(DATA, 0x5).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(0));
        harness.set(CLOCK, 1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(5));
        harness.set(DATA, 0x9).settle().set(CLOCK, 0).settle();
        assertThat(harness.output(OUTPUT)).as("the falling edge does nothing").isEqualTo(four(5));
        harness.set(CLOCK, 1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(9));
    }

    @Test
    void aFallingRegisterStillLoadsOnTheFallingEdgeOnly() {
        BlockHarness harness = register("falling");
        harness.set(DATA, 0x5).set(CLOCK, 1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(0));
        harness.set(CLOCK, 0).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(5));
    }

    @Test
    void aHighLatchFollowsEveryDChangeWhileTheClockIsHighAndHoldsWhenItCloses() {
        BlockHarness harness = register("high");
        harness.set(DATA, 0x3).settle();
        assertThat(harness.output(OUTPUT)).as("closed: holds").isEqualTo(four(0));
        harness.set(CLOCK, 1).settle();
        assertThat(harness.output(OUTPUT)).as("opens with the D it sees").isEqualTo(four(3));
        harness.set(DATA, 0x6).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(6));
        harness.set(DATA, 0xC).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(0xC));
        harness.set(CLOCK, 0).settle();
        assertThat(harness.output(OUTPUT)).as("closing keeps the last value").isEqualTo(four(0xC));
        harness.set(DATA, 0x1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(0xC));
    }

    @Test
    void aLowLatchIsOpenWhileTheClockIsLowAndHoldsWhileItIsHigh() {
        BlockHarness harness = register("low");
        harness.set(CLOCK, 1).settle().set(DATA, 0x7).settle();
        assertThat(harness.output(OUTPUT)).as("closed").isEqualTo(four(0));
        harness.set(CLOCK, 0).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(7));
        harness.set(DATA, 0x2).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(2));
        harness.set(DATA, 0x8).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(8));
        harness.set(CLOCK, 1).settle().set(DATA, 0x1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(8));
    }

    @Test
    void aLatchOpensWhenEnableGoesUpWhileTheClockIsAtTheLevel() {
        BlockHarness harness = register("high");
        harness.set(ENABLE, 0).set(CLOCK, 1).set(DATA, 0x4).settle();
        assertThat(harness.output(OUTPUT)).as("EN = 0 holds").isEqualTo(four(0));
        harness.set(DATA, 0x5).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(0));
        harness.set(ENABLE, 1).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(5));
    }

    @Test
    void enableZeroHoldsInEveryMode() {
        for (String trigger : TRIGGERS) {
            BlockHarness harness = register(trigger);
            harness.set(ENABLE, 0).set(DATA, 0x6).settle();
            harness.set(CLOCK, 1).settle().set(DATA, 0x7).settle().set(CLOCK, 0).settle().set(DATA, 0x2).settle();
            assertThat(harness.output(OUTPUT)).as(trigger).isEqualTo(four(0));
        }
    }

    @Test
    void anUnconnectedEnableCountsAsOneInALatch() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.REGISTER,
                Map.of(StandardComponentTypes.WIDTH, 4, "trigger", "high"), ENABLE);
        harness.set(CLOCK, 1).set(DATA, 0x9).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(9));
    }

    @Test
    void clearWinsInEveryModeAndKeepsAnOpenLatchAtZero() {
        for (String trigger : TRIGGERS) {
            BlockHarness harness = register(trigger);
            int open = trigger.equals("low") || trigger.equals("falling") ? 0 : 1;
            harness.set(DATA, 0xA).set(CLOCK, open).settle();
            harness.set(CLOCK, 1 - open).settle().set(CLOCK, open).settle();
            harness.set(CLEAR, 1).settle();
            assertThat(harness.output(OUTPUT)).as(trigger + " cleared").isEqualTo(four(0));
            harness.set(DATA, 0x3).set(CLOCK, 1 - open).settle().set(CLOCK, open).settle();
            assertThat(harness.output(OUTPUT)).as(trigger + " stays cleared").isEqualTo(four(0));
        }
    }

    @Test
    void anUndefinedDInAnOpenLatchStoresZAsTheEdgesDoToday() {
        BlockHarness harness = register("high");
        harness.set(CLOCK, 1).set(DATA, 0x3).settle();
        assertThat(harness.output(OUTPUT)).isEqualTo(four(3));
        harness.release(DATA).settle();
        assertThat(harness.output(OUTPUT).isFullyDefined()).isFalse();
    }
}
