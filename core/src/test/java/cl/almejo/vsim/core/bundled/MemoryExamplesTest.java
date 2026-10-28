package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.component.VisibleState.MemoryState;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «Programa en ROM» is wired and explained cleanly and its display walks through the ROM's words. */
class MemoryExamplesTest {

    private static final int[] FIBONACCI = {0x00, 0x01, 0x01, 0x02, 0x03, 0x05, 0x08, 0x0D, 0x15, 0x22, 0x37, 0x59,
            0x90, 0xE9, 0x79, 0x62};

    @Test
    void theRomExampleIsWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(MemoryExamples.romProgram());
    }

    @Test
    void theRomHoldsSixteenWordsOfEightBitsWithTheProgram() {
        Component rom = MemoryExamples.romProgram().document().components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.ROM)).findFirst().orElseThrow();
        assertThat(rom.parameters()).containsEntry(MemoryTypes.WORDS, "16").containsEntry(MemoryTypes.WIDTH, 8);
        MemoryContents contents = MemoryContents.decode((String) rom.parameters().get(MemoryTypes.CONTENTS));
        for (int address = 0; address < FIBONACCI.length; address++) {
            assertThat(contents.word(address)).as("word %d", address).isEqualTo(FIBONACCI[address]);
        }
    }

    @Test
    void theDisplayShowsTheNextWordOnEveryFallingEdgeAndWrapsAroundAfterSixteen() {
        // Clock 500/500, low first: falling edges at 1000, 2000, 3000, …
        ExampleSimulation simulation = new ExampleSimulation(MemoryExamples.romProgram(), Map.of());
        simulation.runUntil(100);
        assertThat(simulation.display(0)).as("address 0").isEqualTo(Value.of(8, FIBONACCI[0]));
        for (int edge = 1; edge <= 17; edge++) {
            simulation.runUntil(1000L * edge + 100);
            assertThat(simulation.display(0)).as("after %d edges", edge)
                    .isEqualTo(Value.of(8, FIBONACCI[edge % 16]));
        }
    }

    @Test
    void theRamFillExampleIsWiredAndExplainedCleanly() {
        ExampleChecks.assertExample(MemoryExamples.ramFill());
    }

    private static long word(ExampleSimulation simulation, int address) {
        Component ram = simulation.component("Datos", StandardComponentTypes.RAM);
        return simulation.engine().memory(ram.id().value()).orElseThrow().liveWord(address);
    }

    @Test
    void theRamStartsEmpty() {
        ExampleSimulation simulation = new ExampleSimulation(MemoryExamples.ramFill(), Map.of());
        simulation.runUntil(100);
        for (int address = 0; address < 16; address++) {
            assertThat(word(simulation, address)).as("word %d", address).isZero();
        }
    }

    @Test
    void aFrameAfterTheWriteReportsBothTheWriteAndTheReadOfTheNextAddressThatFollowedIt() {
        // The edge at 19000 (second pass) writes 1 at address 2; the address counter moves to 3 a few units later and the RAM reads it, all
        // before a frame at 19100. The last access is that read, but the state tells the write and the read apart.
        ExampleSimulation simulation = new ExampleSimulation(MemoryExamples.ramFill(), Map.of());
        Component ram = simulation.component("Datos", StandardComponentTypes.RAM);
        simulation.runUntil(18100);
        MemoryState before = (MemoryState) simulation.engine().snapshot().componentState(ram.id().value());
        simulation.runUntil(19100);
        MemoryState state = (MemoryState) simulation.engine().snapshot().componentState(ram.id().value());

        assertThat(state.lastAccess()).isEqualTo(MemoryAccess.READ);
        assertThat(state.lastAddress()).isEqualTo(3);
        assertThat(state.lastWrite().count()).as("one more write since the previous frame")
                .isEqualTo(before.lastWrite().count() + 1);
        assertThat(state.lastWrite().address()).isEqualTo(2);
        assertThat(state.lastWrite().time()).isEqualTo(19000);
        assertThat(state.lastRead().count()).as("and a read after it").isGreaterThan(before.lastRead().count());
        assertThat(state.lastRead().address()).isEqualTo(3);
        assertThat(state.lastRead().time()).isGreaterThan(19000);

        MemoryState written = state.showingWrite();
        assertThat(written.lastAccess()).isEqualTo(MemoryAccess.WRITE);
        assertThat(written.lastAddress()).isEqualTo(2);
        int row = (int) (2 - written.firstWindowAddress());
        assertThat(written.window().get(row)).as("the written word in its window").isEqualTo(1);
        assertThat(written.lastWrite()).isEqualTo(state.lastWrite());
    }

    @Test
    void everyFallingEdgeWritesThePassNumberAtTheAddressOfThatEdgeAndPassesWrapAfterFF() {
        // Clock 500/500, low first: falling edges at 1000, 2000, … Edge n (from 1) writes address (n-1) % 16 with the
        // pass number (n-1) / 16 % 256; the counters change a delay later, so the RAM samples this edge's values.
        ExampleSimulation simulation = new ExampleSimulation(MemoryExamples.ramFill(), Map.of());
        int edges = 16 * 258;
        for (int edge = 1; edge <= edges; edge++) {
            simulation.runUntil(1000L * edge + 100);
            int pass = (edge - 1) / 16 % 256;
            int written = (edge - 1) % 16;
            // The words not reached yet in this pass hold the previous pass (all 0 before the first write).
            int previous = edge <= 16 ? 0 : (pass + 255) % 256;
            for (int address = 0; address < 16; address++) {
                assertThat(word(simulation, address)).as("edge %d, word %d", edge, address)
                        .isEqualTo(address <= written ? pass : previous);
            }
        }
    }
}
