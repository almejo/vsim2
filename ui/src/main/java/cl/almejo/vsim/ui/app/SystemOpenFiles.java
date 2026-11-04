package cl.almejo.vsim.ui.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Files the operating system asks VSim to open (macOS sends them as an event, not as arguments). The event can come
 * before the {@link Application} has finished starting, so the files wait here until {@link #attach} gives the
 * receiver; after that they go straight to it. Used from the EDT; synchronized anyway.
 */
public final class SystemOpenFiles {

    private final List<Path> waiting = new ArrayList<>();
    private Consumer<List<Path>> receiver;

    public synchronized void receive(List<Path> files) {
        if (files.isEmpty()) {
            return;
        }
        if (receiver == null) {
            waiting.addAll(files);
        } else {
            receiver.accept(List.copyOf(files));
        }
    }

    /** From now on the files go to {@code newReceiver}; the ones that were waiting are delivered at once, together. */
    public synchronized void attach(Consumer<List<Path>> newReceiver) {
        receiver = newReceiver;
        if (!waiting.isEmpty()) {
            List<Path> pending = List.copyOf(waiting);
            waiting.clear();
            newReceiver.accept(pending);
        }
    }
}
