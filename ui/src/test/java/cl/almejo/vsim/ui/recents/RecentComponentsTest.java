package cl.almejo.vsim.ui.recents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RecentComponentsTest {

    private final RecentComponents recents = new RecentComponents();

    @Test
    void keepsTheFiveLastDistinctComponentsMostRecentFirst() {
        for (String typeId : List.of("and2", "or2", "led", "and2", "xor2", "not", "clock", "switch")) {
            recents.used(typeId);
        }

        assertThat(recents.typeIds()).containsExactly("switch", "clock", "not", "xor2", "and2");
    }

    @Test
    void usingOneAgainMovesItToTheTopAndTellsTheListenersOnlyWhenTheListChanges() {
        AtomicInteger changes = new AtomicInteger();
        Runnable remove = recents.addListener(changes::incrementAndGet);
        recents.used("and2");
        recents.used("led");
        recents.used("and2");
        recents.used("and2");

        assertThat(recents.typeIds()).containsExactly("and2", "led");
        assertThat(changes).hasValue(3);
        remove.run();
        assertThat(recents.listenerCount()).isZero();
    }

    @Test
    void startsEmpty() {
        assertThat(recents.typeIds()).isEmpty();
    }
}
