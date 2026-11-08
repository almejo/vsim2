package cl.almejo.vsim.ui.app;

import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.util.ArrayList;
import java.util.List;

/** Key hooks kept in lists instead of the focus manager: tests hand them keys and count what stays installed. */
public final class RecordingKeyDispatchers implements KeyDispatchers {

    private final List<KeyEventDispatcher> dispatchers;
    private final List<KeyEventPostProcessor> postProcessors = new ArrayList<>();

    public RecordingKeyDispatchers() {
        this(new ArrayList<>());
    }

    /** Records the dispatchers in {@code dispatchers} (a test's own list). */
    public RecordingKeyDispatchers(List<KeyEventDispatcher> dispatchers) {
        this.dispatchers = dispatchers;
    }

    @Override
    public void add(KeyEventDispatcher dispatcher) {
        dispatchers.add(dispatcher);
    }

    @Override
    public void remove(KeyEventDispatcher dispatcher) {
        dispatchers.remove(dispatcher);
    }

    @Override
    public void addPostProcessor(KeyEventPostProcessor postProcessor) {
        postProcessors.add(postProcessor);
    }

    @Override
    public void removePostProcessor(KeyEventPostProcessor postProcessor) {
        postProcessors.remove(postProcessor);
    }

    public List<KeyEventDispatcher> dispatchers() {
        return List.copyOf(dispatchers);
    }

    public List<KeyEventPostProcessor> postProcessors() {
        return List.copyOf(postProcessors);
    }
}
