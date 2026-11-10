package cl.almejo.vsim.ui.whatsnew;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The bubbles wait for a window free of presentation mode and of the AI's work. */
class TourGateTest {

    private boolean presenting;
    private boolean aiWorking;
    private final List<Runnable> listeners = new ArrayList<>();
    private int started;

    private final TourGate.Conditions conditions = new TourGate.Conditions() {
        @Override
        public boolean presenting() {
            return presenting;
        }

        @Override
        public boolean aiWorking() {
            return aiWorking;
        }

        @Override
        public Runnable addChangeListener(Runnable listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }
    };

    private final TourGate gate = new TourGate(conditions, () -> started++);

    private void changed() {
        List.copyOf(listeners).forEach(Runnable::run);
    }

    @Test
    void anIdleWindowStartsTheTourAtOnce() {
        gate.windowReady();

        assertThat(started).isEqualTo(1);
        assertThat(listeners).isEmpty();
    }

    @Test
    void presentationModeDefersItUntilItEnds() {
        presenting = true;
        gate.windowReady();
        assertThat(started).isZero();

        changed();
        assertThat(started).isZero();
        presenting = false;
        changed();

        assertThat(started).isEqualTo(1);
        assertThat(listeners).isEmpty();
    }

    @Test
    void theAiWorkingDefersItUntilItStopsAndItStartsOnlyOnce() {
        aiWorking = true;
        gate.windowReady();
        assertThat(started).isZero();

        aiWorking = false;
        changed();
        changed();

        assertThat(started).isEqualTo(1);
    }

    @Test
    void aClosedWindowNeverStartsIt() {
        aiWorking = true;
        gate.windowReady();

        gate.dispose();
        aiWorking = false;
        changed();

        assertThat(started).isZero();
        assertThat(listeners).isEmpty();
    }
}
