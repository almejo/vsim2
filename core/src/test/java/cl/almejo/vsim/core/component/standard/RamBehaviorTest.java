package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.component.VisibleState.MemoryState;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The RAM's behaviour (spec §2): combinational read, write on the falling clock edge or while WE = 1. */
class RamBehaviorTest {

    private static Map<String, Object> ram(int words, int width) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(MemoryTypes.WORDS, String.valueOf(words));
        parameters.put(MemoryTypes.WIDTH, width);
        return parameters;
    }

    private static Map<String, Object> with(Map<String, Object> parameters, String name, Object value) {
        Map<String, Object> copy = new HashMap<>(parameters);
        copy.put(name, value);
        return copy;
    }

    private static int pin(BlockHarness harness, String name) {
        return MemoryTypesTest.pinIndex(harness.type(), name);
    }

    /** A 16 x 8 RAM with every input driven: CS = 1, WE = 0, A = 0, D = 0, CLK = 0. */
    private static BlockHarness selected(Map<String, Object> parameters, int... unconnected) {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.RAM, parameters, unconnected);
        if (!contains(unconnected, pin(harness, "CS"))) {
            harness.set(pin(harness, "CS"), 1);
        }
        return harness.settle();
    }

    private static boolean contains(int[] pins, int pinIndex) {
        for (int candidate : pins) {
            if (candidate == pinIndex) {
                return true;
            }
        }
        return false;
    }

    /** Writes {@code value} at {@code address} with a full clock cycle (WE back to 0 afterwards). */
    private static void write(BlockHarness harness, long address, long value) {
        harness.set(pin(harness, "A"), address).set(pin(harness, "D"), value).set(pin(harness, "WE"), 1)
                .set(pin(harness, "CLK"), 1).settle().set(pin(harness, "CLK"), 0).settle()
                .set(pin(harness, "WE"), 0).settle();
    }

    private static Value read(BlockHarness harness, long address) {
        harness.set(pin(harness, "A"), address).settle();
        return harness.output(pin(harness, "Q"));
    }

    @Test
    void readingIsCombinationalAfterTheDelay() {
        Map<String, Object> parameters = with(with(ram(16, 8), MemoryTypes.CONTENTS, "@3 7F"), "delay", 5);
        BlockHarness harness = selected(parameters);
        harness.set(pin(harness, "A"), 3).advance(4);
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0));
        harness.advance(1);
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x7F));
    }

    @Test
    void writesOnTheFallingEdgeOnlyWithWeAndCsAtOneSamplingAtTheEdge() {
        BlockHarness harness = selected(ram(16, 8));
        harness.set(pin(harness, "A"), 2).set(pin(harness, "D"), 0x11).set(pin(harness, "WE"), 1).settle();
        harness.set(pin(harness, "CLK"), 1).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("rising edge writes nothing").isEqualTo(Value.of(8, 0));
        harness.set(pin(harness, "D"), 0x22).settle();
        harness.set(pin(harness, "CLK"), 0).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("D sampled at the falling edge").isEqualTo(Value.of(8, 0x22));

        harness.set(pin(harness, "WE"), 0).set(pin(harness, "D"), 0x33).settle();
        harness.set(pin(harness, "CLK"), 1).settle().set(pin(harness, "CLK"), 0).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("WE = 0 never writes").isEqualTo(Value.of(8, 0x22));

        harness.set(pin(harness, "WE"), 1).set(pin(harness, "CS"), 0).settle();
        harness.set(pin(harness, "CLK"), 1).settle().set(pin(harness, "CLK"), 0).settle();
        harness.set(pin(harness, "CS"), 1).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("CS = 0 never writes").isEqualTo(Value.of(8, 0x22));
    }

    @Test
    void levelWritingStoresWhileWeIsOne() {
        BlockHarness harness = selected(with(ram(16, 8), MemoryTypes.WRITE, MemoryTypes.WRITE_LEVEL));
        assertThat(harness.type().pins()).extracting(pinSpec -> pinSpec.name()).doesNotContain("CLK");
        harness.set(pin(harness, "A"), 5).set(pin(harness, "D"), 0x44).set(pin(harness, "WE"), 1).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x44));
        harness.set(pin(harness, "D"), 0x45).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x45));
        harness.set(pin(harness, "WE"), 0).settle().set(pin(harness, "D"), 0x46).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x45));
    }

    @Test
    void anUnconnectedCsReadsAndWrites() {
        BlockHarness probe = new BlockHarness(StandardComponentTypes.RAM, ram(16, 8));
        BlockHarness harness = selected(with(ram(16, 8), MemoryTypes.CONTENTS, "@1 9"), pin(probe, "CS"));
        assertThat(read(harness, 1)).isEqualTo(Value.of(8, 9));
        write(harness, 1, 0xA);
        assertThat(read(harness, 1)).isEqualTo(Value.of(8, 0xA));
    }

    @Test
    void csAtZeroReleasesQ() {
        BlockHarness harness = selected(with(ram(16, 8), MemoryTypes.CONTENTS, "@0 1"));
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 1));
        harness.set(pin(harness, "CS"), 0).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.highImpedance(8));
        assertThat(((MemoryState) harness.state()).enabled()).isFalse();
        harness.conflict(pin(harness, "CS")).settle();
        assertThat(harness.output(pin(harness, "Q"))).as("a conflict on CS").isEqualTo(Value.highImpedance(8));
    }

    @Test
    void anUnconnectedWeNeverWrites() {
        BlockHarness probe = new BlockHarness(StandardComponentTypes.RAM, ram(16, 8));
        BlockHarness harness = selected(ram(16, 8), pin(probe, "WE"));
        harness.set(pin(harness, "D"), 0x55);
        harness.set(pin(harness, "CLK"), 1).settle().set(pin(harness, "CLK"), 0).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0));
    }

    @Test
    void anAddressWithZReadsAllZAndWritesNothing() {
        BlockHarness harness = selected(with(ram(16, 8), MemoryTypes.CONTENTS, "@0 1 2"));
        harness.release(pin(harness, "A")).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.highImpedance(8));
        harness.set(pin(harness, "D"), 0x77).set(pin(harness, "WE"), 1);
        harness.set(pin(harness, "CLK"), 1).settle().set(pin(harness, "CLK"), 0).settle();
        harness.set(pin(harness, "WE"), 0).settle();
        assertThat(read(harness, 0)).isEqualTo(Value.of(8, 1));
        assertThat(read(harness, 1)).isEqualTo(Value.of(8, 2));
    }

    @Test
    void anUndefinedDStoresAllZ() {
        BlockHarness harness = selected(with(ram(16, 8), MemoryTypes.CONTENTS, "@4 5"));
        harness.set(pin(harness, "A"), 4).release(pin(harness, "D")).set(pin(harness, "WE"), 1);
        harness.set(pin(harness, "CLK"), 1).settle().set(pin(harness, "CLK"), 0).settle();
        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.highImpedance(8));
        write(harness, 4, 6);
        assertThat(read(harness, 4)).isEqualTo(Value.of(8, 6));
    }

    @Test
    void wordsBeyondTheSizeAreNotAddressedButSurvive() {
        String contents = "@3 1 @10 2 @11 1FF";
        Map<String, Object> parameters = with(ram(16, 8), MemoryTypes.CONTENTS, contents);
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM);
        assertThat(type.resolveParameters(parameters).get(MemoryTypes.CONTENTS))
                .as("the parameter keeps every word").isEqualTo("@3 1 @10 2 1FF");

        MemoryBehavior behavior = (MemoryBehavior) type.createBehavior(parameters);
        assertThat(behavior.liveContents()).isEqualTo(MemoryContents.decode("@3 1"));

        MemoryBehavior wide = (MemoryBehavior) type.createBehavior(with(ram(32, 4), MemoryTypes.CONTENTS, contents));
        assertThat(wide.liveContents()).as("bits beyond the width are not seen")
                .isEqualTo(MemoryContents.decode("@3 1 @10 2 F"));
    }

    @Test
    void aSixtyFourBitWordRoundTrips() {
        BlockHarness harness = selected(ram(4, 64));
        write(harness, 3, 0xFEDC_BA98_7654_3210L);
        assertThat(read(harness, 3)).isEqualTo(Value.of(64, 0xFEDC_BA98_7654_3210L));
    }

    @Test
    void restoringPutsBackTheInitialWordsOnlyInThatMemory() {
        BlockHarness harness = selected(with(ram(16, 8), MemoryTypes.CONTENTS, "@2 22"));
        write(harness, 2, 0x99);
        write(harness, 7, 0x77);
        assertThat(read(harness, 2)).isEqualTo(Value.of(8, 0x99));
        long before = harness.now();

        harness.restoreMemory();
        harness.settle();

        assertThat(harness.output(pin(harness, "Q"))).isEqualTo(Value.of(8, 0x22));
        assertThat(read(harness, 7)).isEqualTo(Value.of(8, 0));
        assertThat(harness.now()).as("time goes on").isGreaterThan(before);
    }

    @Test
    void theVisibleStateReportsTheLastAccessAndTheWindow() {
        BlockHarness harness = selected(with(ram(16, 8), MemoryTypes.CONTENTS, "@0 10 11 12 13 14 15 16"));
        harness.set(pin(harness, "A"), 5).settle();
        assertThat(harness.state()).isEqualTo(new MemoryState(true, MemoryAccess.READ, 5, 3,
                List.of(0x13L, 0x14L, 0x15L, 0x16L, 0L)));

        harness.set(pin(harness, "A"), 15).set(pin(harness, "D"), 0xEE).set(pin(harness, "WE"), 1)
                .set(pin(harness, "CLK"), 1).settle().set(pin(harness, "CLK"), 0).settle();
        assertThat(harness.state()).isEqualTo(new MemoryState(true, MemoryAccess.WRITE, 15, 11,
                List.of(0L, 0L, 0L, 0L, 0xEEL)));
        harness.set(pin(harness, "WE"), 0).settle();
        assertThat(((MemoryState) harness.state()).lastAccess()).as("the first evaluation after a write reads")
                .isEqualTo(MemoryAccess.READ);

        harness.set(pin(harness, "A"), 0).settle();
        MemoryState first = (MemoryState) harness.state();
        assertThat(first.lastAccess()).isEqualTo(MemoryAccess.READ);
        assertThat(first.firstWindowAddress()).as("kept inside the memory").isZero();
    }

    @Test
    void readsAndWritesAreCounted() {
        BlockHarness harness = selected(ram(16, 8));
        harness.set(pin(harness, "A"), 1).settle().set(pin(harness, "A"), 2).settle().set(pin(harness, "A"), 1)
                .settle();
        harness.set(pin(harness, "D"), 3).settle();
        write(harness, 4, 9);
        MemoryAccessLog log = harness.memory().accessLog();
        assertThat(log.reads(1)).isEqualTo(2);
        assertThat(log.reads(2)).isEqualTo(1);
        assertThat(log.writes(4)).isEqualTo(1);
        assertThat(log.drainRecent()).extracting(MemoryAccessLog.MemoryEvent::access)
                .contains(MemoryAccess.READ, MemoryAccess.WRITE);
        assertThat(log.drainRecent()).isEmpty();
    }

    @Test
    void aHugeRamWithAFewWordsIsCheap() {
        Map<String, Object> parameters = with(ram(16_777_216, 64), MemoryTypes.CONTENTS, "@0 1 @800000 2 @FFFFFF 3");
        ComponentType type = StandardComponentTypes.registry().require(StandardComponentTypes.RAM);
        type.createBehavior(parameters);
        long start = System.nanoTime();
        MemoryBehavior behavior = (MemoryBehavior) type.createBehavior(parameters);
        long creation = System.nanoTime() - start;
        assertThat(creation).as("creating the behaviour, ns").isLessThan(50_000_000L);

        for (int warmUp = 0; warmUp < 10_000; warmUp++) {
            behavior.visibleState();
        }
        int calls = 10_000;
        start = System.nanoTime();
        VisibleState state = VisibleState.NONE;
        for (int call = 0; call < calls; call++) {
            state = behavior.visibleState();
        }
        long average = (System.nanoTime() - start) / calls;
        assertThat(state).isInstanceOf(MemoryState.class);
        assertThat(average).as("visibleState(), ns per call").isLessThan(1_000L);
    }
}
