package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListenersTest {

    @Test
    void firesInOrderAndTheRemoverTakesTheListenerBack() {
        Listeners<Runnable> listeners = new Listeners<>();
        List<String> calls = new ArrayList<>();
        Runnable removeFirst = listeners.add(() -> calls.add("first"));
        listeners.add(() -> calls.add("second"));
        listeners.fire(Runnable::run);
        removeFirst.run();
        listeners.fire(Runnable::run);
        assertThat(calls).containsExactly("first", "second", "second");
        assertThat(listeners.size()).isEqualTo(1);
    }

    @Test
    void removeTakesTheListenerBack() {
        Listeners<Runnable> listeners = new Listeners<>();
        Runnable listener = () -> { };
        listeners.add(listener);
        listeners.remove(listener);
        assertThat(listeners.size()).isZero();
    }

    @Test
    void aListenerAddedWhileFiringHearsOnlyTheNextFire() {
        Listeners<Runnable> listeners = new Listeners<>();
        List<String> calls = new ArrayList<>();
        listeners.add(() -> {
            calls.add("outer");
            listeners.add(() -> calls.add("late"));
        });
        listeners.fire(Runnable::run);
        assertThat(calls).containsExactly("outer");
    }

    @Test
    void fireStopsAtTheFirstFailureButFireAllRunsEveryoneAndCollects() {
        Listeners<Runnable> listeners = new Listeners<>();
        List<String> calls = new ArrayList<>();
        listeners.add(() -> {
            throw new IllegalStateException("one");
        });
        listeners.add(() -> calls.add("after"));
        assertThatThrownBy(() -> listeners.fire(Runnable::run)).hasMessage("one");
        assertThat(calls).isEmpty();
        List<RuntimeException> failures = listeners.fireAll(Runnable::run);
        assertThat(calls).containsExactly("after");
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).hasMessage("one");
    }
}
