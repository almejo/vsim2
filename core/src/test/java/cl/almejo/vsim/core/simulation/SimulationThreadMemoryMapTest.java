package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.component.standard.MemoryAccessLog.MemoryEvent;
import cl.almejo.vsim.core.component.standard.MemoryBehavior;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The memory map frames the simulation thread publishes for the «Mapa de memoria» tab (spec §6). */
class SimulationThreadMemoryMapTest {

    private static final long MILLISECOND = 1_000_000L;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AtomicLong clock = new AtomicLong();
    private final SimulationEngine engine = new SimulationEngine();
    private final SimulationThread thread = new SimulationThread(engine, clock::get);

    /** A RAM "ram" of {@code words} x 8 with A, D, WE and CLK from input pins of the same names (CS unconnected). */
    private FlatCircuit circuit(String words) {
        Map<String, Object> parameters = Map.of(MemoryTypes.WORDS, words);
        ComponentType ram = registry.require(StandardComponentTypes.RAM).specializedFor(parameters);
        List<FlatComponent> components = new ArrayList<>(List.of(new FlatComponent("ram", ram, parameters)));
        List<FlatNet> nets = new ArrayList<>();
        int pinIndex = 0;
        for (String input : List.of("A", "D", "WE", "CS", "CLK", "Q")) {
            int width = ram.pin(pinIndex).width();
            if (input.equals("Q")) {
                nets.add(new FlatNet("q", List.of(new FlatPinRef("ram", pinIndex)), width, false));
            } else if (!input.equals("CS")) {
                Map<String, Object> pinParameters = Map.of("value", "0", InterfacePins.WIDTH, width);
                components.add(new FlatComponent(input, registry.require(InterfacePins.INPUT_PIN)
                        .specializedFor(pinParameters), pinParameters));
                nets.add(new FlatNet("n-" + input, List.of(new FlatPinRef(input, 0),
                        new FlatPinRef("ram", pinIndex)), width, false));
            }
            pinIndex++;
        }
        return new FlatCircuit(components, nets);
    }

    /** Ticks after the publication interval, so a frame may be published. */
    private void tickLater() {
        clock.addAndGet(20 * MILLISECOND);
        thread.tick();
    }

    private void readAt(int addressBits, long address) {
        thread.setInputs(Map.of("A", Value.of(addressBits, address)));
        thread.step(10);
    }

    private void writeAt(int addressBits, long address, int value) {
        thread.setInputs(Map.of("A", Value.of(addressBits, address), "D", Value.of(8, value), "WE", Value.ONE,
                "CLK", Value.ONE));
        thread.step(10);
        thread.setInputs(Map.of("CLK", Value.ZERO));
        thread.step(10);
        thread.setInputs(Map.of("WE", Value.ZERO));
        thread.step(10);
    }

    /** 0x1C read ten times (alternating with 0x1D) and 0x100 written once with 0x5A, in a 512 x 8 RAM. */
    private void readTenTimesAndWriteOnce() {
        thread.update(circuit("512"));
        thread.tick();
        for (int time = 0; time < 10; time++) {
            readAt(9, 0x1C);
            readAt(9, 0x1D);
        }
        writeAt(9, 0x100, 0x5A);
        thread.tick();
    }

    @Test
    void aFrameCountsTheReadsAndWritesOfEachCellAndHoldsItsFirstWord() {
        readTenTimesAndWriteOnce();

        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        tickLater();

        MemoryMapFrame frame = thread.latestMemoryMap().orElseThrow();
        assertThat(frame.request()).isEqualTo(new MemoryMapRequest("ram", 0, 1, 512));
        assertThat(frame.cells()).isEqualTo(512);
        assertThat(frame.readsAt(0x1C)).isEqualTo(10);
        assertThat(frame.writesAt(0x1C)).isZero();
        assertThat(frame.writesAt(0x100)).isEqualTo(1);
        assertThat(frame.valueAt(0x100)).isEqualTo(0x5A);
        assertThat(frame.reads()[0x1C]).isEqualTo(10);
        assertThat(frame.writes()[0x100]).isEqualTo(1);
        assertThat(frame.values()[0x100]).isEqualTo(0x5A);
        assertThat(frame.time()).isEqualTo(thread.latestSnapshot().time());
        assertThat(frame.recent()).as("the accesses before the request are not recent for the map").isEmpty();
    }

    @Test
    void cellsOfManyWordsSumTheirCountsAndKeepTheFirstWord() {
        readTenTimesAndWriteOnce();

        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 32, 16)));
        tickLater();

        MemoryMapFrame frame = thread.latestMemoryMap().orElseThrow();
        assertThat(frame.readsAt(0)).as("0x1C and 0x1D are in the first cell").isGreaterThanOrEqualTo(20);
        assertThat(frame.writesAt(8)).isEqualTo(1);
        assertThat(frame.valueAt(8)).isEqualTo(0x5A);
    }

    @Test
    void theRecentAccessesAreTheOnesSinceTheLastFrame() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        tickLater();

        readAt(9, 0x30);
        tickLater();

        MemoryMapFrame frame = thread.latestMemoryMap().orElseThrow();
        assertThat(frame.recent()).extracting(MemoryEvent::address, MemoryEvent::access)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0x30L, MemoryAccess.READ));
        readAt(9, 0x31);
        tickLater();
        assertThat(thread.latestMemoryMap().orElseThrow().recent()).as("drained once")
                .extracting(MemoryEvent::address).containsExactly(0x31L);
    }

    @Test
    void whileRunningFramesArePublishedAtMostEverySixteenMilliseconds() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        thread.resume();
        tickLater();
        MemoryMapFrame first = thread.latestMemoryMap().orElseThrow();

        thread.setInputs(Map.of("A", Value.of(9, 0x30)));
        clock.addAndGet(5 * MILLISECOND);
        thread.tick();
        assertThat(thread.latestMemoryMap()).containsSame(first);

        tickLater();
        assertThat(thread.latestMemoryMap().orElseThrow()).isNotSameAs(first);
        assertThat(thread.latestMemoryMap().orElseThrow().readsAt(0x30)).isEqualTo(1);
    }

    @Test
    void whilePausedAStepIsPublishedWithItsSnapshotAtOnce() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        tickLater();

        readAt(9, 0x30);
        clock.addAndGet(MILLISECOND);
        thread.tick();

        assertThat(thread.latestMemoryMap().orElseThrow().readsAt(0x30)).isEqualTo(1);
        assertThat(thread.latestMemoryMap().orElseThrow().time()).isEqualTo(thread.latestSnapshot().time());
    }

    @Test
    void nothingChangedPublishesNoNewFrameWhilePaused() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        tickLater();
        MemoryMapFrame first = thread.latestMemoryMap().orElseThrow();

        tickLater();
        tickLater();

        assertThat(thread.latestMemoryMap()).containsSame(first);
    }

    @Test
    void framesStopWhenTheRequestIsEmpty() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        tickLater();
        assertThat(thread.latestMemoryMap()).isPresent();

        thread.setMemoryMapRequest(Optional.empty());
        tickLater();
        thread.resume();
        tickLater();
        readAt(9, 0x40);
        tickLater();

        assertThat(thread.latestMemoryMap()).isEmpty();
    }

    @Test
    void aRequestForSomethingThatIsNotAMemoryPublishesNothing() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("A", 0, 1, 16)));
        tickLater();
        assertThat(thread.latestMemoryMap()).isEmpty();
    }

    @Test
    void aRestartClearsTheCounts() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        tickLater();

        thread.restart(circuit("512"), Map.of());
        tickLater();

        MemoryMapFrame frame = thread.latestMemoryMap().orElseThrow();
        assertThat(frame.readsAt(0x1C)).isZero();
        assertThat(frame.writesAt(0x100)).isZero();
        assertThat(frame.valueAt(0x100)).isZero();
    }

    @Test
    void aFrameOfABigMemoryIsBuiltQuickly() {
        thread.update(circuit("16777216"));
        thread.tick();
        for (long address = 0; address < 64; address++) {
            readAt(24, address * 262_139L);
        }
        writeAt(24, 0xABCDEF, 0x77);
        thread.tick();
        MemoryBehavior memory = engine.memory("ram").orElseThrow();
        MemoryMapRequest request = new MemoryMapRequest("ram", 0, 4096, 4096);

        for (int warmUp = 0; warmUp < 30; warmUp++) {
            MemoryMapFrame.capture(memory, request, List.of(), 0);
        }
        int runs = 50;
        long start = System.nanoTime();
        MemoryMapFrame frame = null;
        for (int run = 0; run < runs; run++) {
            frame = MemoryMapFrame.capture(memory, request, List.of(), 0);
        }
        long averageNanos = (System.nanoTime() - start) / runs;

        assertThat(averageNanos).as("average build time in ns").isLessThan(5 * MILLISECOND);
        assertThat(frame.writesAt((int) (0xABCDEFL / 4096))).isEqualTo(1);
        assertThat(frame.valueAt((int) (0xABCDEFL / 4096))).as("the first word of that cell is 0").isZero();
    }

    @Test
    void theFrameKeepsItsOwnCopies() {
        readTenTimesAndWriteOnce();
        thread.setMemoryMapRequest(Optional.of(new MemoryMapRequest("ram", 0, 1, 512)));
        tickLater();
        MemoryMapFrame frame = thread.latestMemoryMap().orElseThrow();

        frame.reads()[0x1C] = 0;
        frame.writes()[0x100] = 0;
        frame.values()[0x100] = 0;

        assertThat(frame.readsAt(0x1C)).isEqualTo(10);
        assertThat(frame.writesAt(0x100)).isEqualTo(1);
        assertThat(frame.valueAt(0x100)).isEqualTo(0x5A);
    }

    @Test
    void aRequestIsChecked() {
        assertThatThrownBy(() -> new MemoryMapRequest("ram", -1, 1, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryMapRequest("ram", 0, 0, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryMapRequest("ram", 0, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryMapRequest("ram", 0, 1, MemoryMapRequest.MAXIMUM_CELLS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryMapRequest(null, 0, 1, 4)).isInstanceOf(NullPointerException.class);
    }
}
