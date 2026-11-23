package cl.almejo.vsim.ui.machine;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** What is selected in a machine's diagram: states and arrows by id, told to its listeners on every change. */
class MachineSelectionTest {

    private final MachineSelection selection = new MachineSelection();
    private final AtomicInteger changes = new AtomicInteger();

    @Test
    void choosingAStateReplacesWhatWasSelectedAndTellsTheListeners() {
        selection.addListener(changes::incrementAndGet);
        selection.set(Set.of("s0"), Set.of("t1"));

        selection.selectState("s1");

        assertThat(selection.stateIds()).containsExactly("s1");
        assertThat(selection.transitionIds()).isEmpty();
        assertThat(changes).hasValue(2);
    }

    @Test
    void choosingTheSameAgainTellsNobody() {
        selection.selectTransition("t2");
        selection.addListener(changes::incrementAndGet);

        selection.selectTransition("t2");

        assertThat(changes).hasValue(0);
        assertThat(selection.containsTransition("t2")).isTrue();
    }

    @Test
    void whatLeftTheMachineIsDropped() {
        selection.set(Set.of("s0", "s9"), Set.of("t1", "t99"));

        selection.retainExisting(MachineFixtures.detector());

        assertThat(selection.stateIds()).containsExactly("s0");
        assertThat(selection.transitionIds()).containsExactly("t1");
    }

    @Test
    void aRemovedListenerHearsNothingAndClearingEmpties() {
        Runnable remove = selection.addListener(changes::incrementAndGet);
        remove.run();
        selection.selectState("s0");

        selection.clear();

        assertThat(selection.isEmpty()).isTrue();
        assertThat(changes).hasValue(0);
    }
}
