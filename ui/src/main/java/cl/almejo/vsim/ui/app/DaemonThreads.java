package cl.almejo.vsim.ui.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The background threads the editor's work runs on, off the EDT. */
public final class DaemonThreads {

    private DaemonThreads() {
    }

    /** One daemon thread named {@code name}: it never keeps the application from closing. */
    public static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name(name).daemon().factory());
    }
}
