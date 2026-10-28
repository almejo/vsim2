package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.LastRead;
import cl.almejo.vsim.core.component.VisibleState.LastWrite;
import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.component.VisibleState.MemoryState;
import cl.almejo.vsim.ui.paint.MemoryLights;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Each light of a memory is lit by its own accesses since the frame before, and stays lit a little while. */
class MemoryActivityTest {

    private static final String RAM = "ram1";
    private static final long MILLISECOND = 1_000_000L;
    private static final List<Long> WINDOW = List.of(0L, 0L, 0L, 0L, 0L);

    private long now = 1_000 * MILLISECOND;
    private final MemoryActivity activity = new MemoryActivity(() -> now);

    /** A RAM that has counted {@code reads} reads (the last at {@code readAddress}) and {@code writes} writes. */
    private static MemoryState state(MemoryAccess last, long reads, long readAddress, long writes, long writeAddress) {
        long lastAddress = last == MemoryAccess.WRITE ? writeAddress : readAddress;
        LastRead read = reads == 0 ? LastRead.NONE : new LastRead(reads, reads * 10, readAddress);
        LastWrite write = writes == 0 ? LastWrite.NONE
                : new LastWrite(writes, writes * 10 + 5, writeAddress, 0, WINDOW, 0);
        return new MemoryState(true, last, lastAddress, 0, WINDOW, 0, read, write);
    }

    @Test
    void theFirstFrameLightsNothing() {
        assertThat(activity.lights(RAM, state(MemoryAccess.READ, 3, 2, 1, 1), 100)).isEqualTo(MemoryLights.IDLE);
    }

    @Test
    void aWriteFollowedByAReadInTheSameFrameLightsBoth() {
        activity.lights(RAM, state(MemoryAccess.READ, 1, 0, 0, -1), 100);
        // the counter wrote at 1 and moved on to 2, which was read: the last access is the read
        MemoryLights lights = activity.lights(RAM, state(MemoryAccess.READ, 2, 2, 1, 1), 200);
        assertThat(lights).isEqualTo(new MemoryLights(true, 2, true, 1));
    }

    @Test
    void whileRunningEachLightStaysOnAtLeastTwoHundredMillisecondsThenGoesOut() {
        activity.lights(RAM, state(MemoryAccess.READ, 1, 0, 0, -1), 100);
        activity.lights(RAM, state(MemoryAccess.WRITE, 1, 0, 1, 1), 200);
        now += 150 * MILLISECOND;
        assertThat(activity.lights(RAM, state(MemoryAccess.WRITE, 1, 0, 1, 1), 300).writing())
                .as("held: nothing new, but only 150 ms").isTrue();
        now += 60 * MILLISECOND;
        MemoryLights later = activity.lights(RAM, state(MemoryAccess.WRITE, 1, 0, 1, 1), 400);
        assertThat(later.writing()).as("210 ms and the time moved on").isFalse();
        assertThat(later.reading()).isFalse();
    }

    @Test
    void whilePausedALightStaysOnUntilTheNextStep() {
        activity.lights(RAM, state(MemoryAccess.READ, 1, 0, 0, -1), 100);
        activity.lights(RAM, state(MemoryAccess.WRITE, 1, 0, 1, 1), 200);
        now += 5_000 * MILLISECOND;
        assertThat(activity.lights(RAM, state(MemoryAccess.WRITE, 1, 0, 1, 1), 200).writing())
                .as("paused: the simulation time did not move").isTrue();
        assertThat(activity.lights(RAM, state(MemoryAccess.WRITE, 1, 0, 1, 1), 250).writing())
                .as("the next step did not write").isFalse();
    }

    @Test
    void aRestoreForgetsTheWriteAndARestartedCountStillCountsAsNew() {
        activity.lights(RAM, state(MemoryAccess.READ, 1, 0, 0, -1), 100);
        activity.lights(RAM, state(MemoryAccess.WRITE, 1, 0, 1, 1), 200);
        MemoryState restored = new MemoryState(true, MemoryAccess.NONE, -1, 0, WINDOW, 0, LastRead.NONE,
                new LastWrite(1, -1, -1, 0, List.of(), 0));
        assertThat(activity.lights(RAM, restored, 200)).isEqualTo(MemoryLights.IDLE);
        now += 1_000 * MILLISECOND;
        // a new engine counts from 1 again: the write at a new time is a new write
        MemoryState again = new MemoryState(true, MemoryAccess.WRITE, 3, 0, WINDOW, 0, LastRead.NONE,
                new LastWrite(1, 7, 3, 0, WINDOW, 0));
        assertThat(activity.lights(RAM, again, 300)).isEqualTo(new MemoryLights(false, -1, true, 3));
    }

    @Test
    void aStateThatIsNotAMemoryLightsNothing() {
        assertThat(activity.lights(RAM, VisibleState.NONE, 100)).isEqualTo(MemoryLights.IDLE);
    }
}
