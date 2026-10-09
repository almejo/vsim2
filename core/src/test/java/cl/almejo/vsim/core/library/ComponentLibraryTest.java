package cl.almejo.vsim.core.library;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentLibraryTest {

    private final ComponentLibrary library = new ComponentLibrary();
    private final AtomicInteger notifications = new AtomicInteger();
    private final DefinitionBundle inverter = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
    private final DefinitionBundle doubleInverter = DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
            id -> Optional.of(ExampleDefinitions.inverter()));

    @Test
    void entriesAreSortedByNameAndChangesNotifyOnce() {
        library.addListener(notifications::incrementAndGet);
        library.put(inverter);
        library.put(doubleInverter);
        assertThat(library.entries()).containsExactly(doubleInverter, inverter);
        assertThat(library.ids()).containsExactlyInAnyOrder("inverter", "double-inverter");
        assertThat(notifications).hasValue(2);

        library.put(inverter);
        assertThat(notifications).hasValue(2);

        DefinitionBundle renamed = new DefinitionBundle(ExampleDefinitions.inverter().withName("Abc"), List.of());
        library.put(renamed);
        assertThat(library.find("inverter")).contains(renamed);
        assertThat(library.entries()).first().isEqualTo(renamed);
        assertThat(notifications).hasValue(3);
    }

    @Test
    void removeAndReplaceAllNotifyOnlyOnRealChanges() {
        library.put(inverter);
        library.addListener(notifications::incrementAndGet);
        assertThat(library.remove("missing")).isFalse();
        assertThat(notifications).hasValue(0);
        library.replaceAll(List.of(inverter));
        assertThat(notifications).hasValue(0);
        library.replaceAll(List.of(doubleInverter));
        assertThat(library.ids()).containsExactly("double-inverter");
        assertThat(notifications).hasValue(1);
        assertThat(library.remove("double-inverter")).isTrue();
        assertThat(library.entries()).isEmpty();
        assertThat(notifications).hasValue(2);
    }

    @Test
    void removedListenersHearNothing() {
        Runnable listener = notifications::incrementAndGet;
        library.addListener(listener);
        assertThat(library.listenerCount()).isEqualTo(1);
        library.removeListener(listener);
        assertThat(library.listenerCount()).isZero();
        library.put(inverter);
        assertThat(notifications).hasValue(0);
    }

    @Test
    void aThrowingListenerDoesNotStopTheOthersNorTheCaller() {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
        AtomicInteger reported = new AtomicInteger();
        thread.setUncaughtExceptionHandler((failedThread, exception) -> reported.incrementAndGet());
        try {
            library.addListener(() -> {
                throw new IllegalStateException("boom");
            });
            library.addListener(notifications::incrementAndGet);
            library.put(inverter);
            assertThat(notifications).hasValue(1);
            assertThat(reported).hasValue(1);
            assertThat(library.find("inverter")).contains(inverter);
        } finally {
            thread.setUncaughtExceptionHandler(previous);
        }
    }

    @Test
    void aListenerMayReadAndChangeTheLibraryFromItsCallback() {
        List<Integer> sizes = new java.util.ArrayList<>();
        library.addListener(() -> {
            sizes.add(library.entries().size());
            if (library.find("double-inverter").isEmpty()) {
                library.put(doubleInverter);
            }
        });
        library.put(inverter);
        assertThat(library.ids()).containsExactlyInAnyOrder("inverter", "double-inverter");
        assertThat(sizes).containsExactly(1, 2);
    }

    @Test
    void aNullListenerIsRejected() {
        org.assertj.core.api.Assertions.assertThatNullPointerException().isThrownBy(() -> library.addListener(null));
    }
}
