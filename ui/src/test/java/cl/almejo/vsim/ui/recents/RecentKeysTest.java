package cl.almejo.vsim.ui.recents;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RecentKeysTest {

    @Test
    void theLastUsedGoesFirstTheOldestBeyondTheMaximumDropsAndListenersHearOnlyChanges() {
        RecentKeys recent = new RecentKeys(2);
        AtomicInteger changes = new AtomicInteger();
        Runnable removal = recent.addListener(changes::incrementAndGet);

        recent.used("a");
        recent.used("b");
        recent.used("b");
        recent.used("c");
        recent.used("b");

        assertThat(recent.keys()).containsExactly("b", "c");
        assertThat(changes.get()).as("using the first one again changes nothing").isEqualTo(4);
        removal.run();
        assertThat(recent.listenerCount()).isZero();
    }
}
