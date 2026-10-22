package cl.almejo.vsim.automation;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * What the editor gives the AI: the editor implements it on its own thread (the EDT) and the automation layer calls it
 * from the server's threads. Each later task adds its methods to its own part ({@link ReadHost}, {@link EditHost},
 * {@link SimulationHost}, {@link SettingsHost}, {@link ExportHost}) so they can be written side by side.
 */
public interface AutomationHost extends ReadHost, EditHost, SimulationHost, SettingsHost, ExportHost,
        PresentationHost {

    /**
     * Runs {@code work} on the editor thread and completes the future off it with what {@code work} returned, or
     * exceptionally with what it threw. Never wait for the future on the editor thread: that would deadlock.
     */
    <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work);
}
