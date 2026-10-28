package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The counters behind the memory map: exact per word in small memories, per block in big ones. */
class MemoryAccessLogTest {

    @Test
    void aSmallMemoryCountsEveryWord() {
        MemoryAccessLog log = new MemoryAccessLog(256);
        log.read(3, 10);
        log.read(3, 11);
        log.write(4, 12);
        assertThat(log.wordsPerCounter()).isEqualTo(1);
        assertThat(log.reads(3)).isEqualTo(2);
        assertThat(log.reads(4)).isZero();
        assertThat(log.writes(4)).isEqualTo(1);
        assertThat(log.readsPerCell(0, 4, 2)).containsExactly(2, 0);
        assertThat(log.writesPerCell(0, 4, 2)).containsExactly(0, 1);
        assertThat(log.drainRecent()).containsExactly(new MemoryAccessLog.MemoryEvent(3, MemoryAccess.READ, 10),
                new MemoryAccessLog.MemoryEvent(3, MemoryAccess.READ, 11),
                new MemoryAccessLog.MemoryEvent(4, MemoryAccess.WRITE, 12));
    }

    @Test
    void aSixteenMegawordMemoryCountsPerBlockAndStaysSmall() {
        MemoryAccessLog log = new MemoryAccessLog(16_777_216);
        assertThat(log.wordsPerCounter()).isEqualTo(256);
        for (long address = 0; address < 16_777_216; address += 4096) {
            log.read(address, 0);
        }
        log.read(255, 0);
        assertThat(log.reads(0)).as("the block of words 0..255").isEqualTo(2);
        assertThat(log.reads(256)).isZero();
        long[] perCell = log.readsPerCell(0, 1 << 20, 16);
        assertThat(perCell).containsOnly(256L, 257L);
        assertThat(perCell[0]).isEqualTo(257);
    }

    @Test
    void onlyTheLatestAccessesWaitForTheDrain() {
        MemoryAccessLog log = new MemoryAccessLog(16);
        for (int access = 0; access < MemoryAccessLog.MAXIMUM_RECENT + 10; access++) {
            log.write(access % 16, access);
        }
        assertThat(log.drainRecent()).hasSize(MemoryAccessLog.MAXIMUM_RECENT)
                .first().extracting(MemoryAccessLog.MemoryEvent::time).isEqualTo(10L);
        assertThat(log.drainRecent()).isEmpty();
    }
}
