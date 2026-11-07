package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.PlaceRef;

import java.util.Optional;

/**
 * Where each running task of the AI works now: the place its robot marks as working on the tabs (round E, 5q). The
 * «Presentar» panel of a task shows on that place's tab. EDT only.
 */
public interface TaskPlaces {

    /** The place task {@code taskId} works in now; empty while it holds none (it touched no place yet, or ended). */
    Optional<PlaceRef> placeOf(String taskId);

    /** {@code listener} runs whenever a task's place may have changed; the returned runnable takes it back. */
    Runnable addListener(Runnable listener);

    /** No task works anywhere (tests, and an application without the AI's marks). */
    TaskPlaces NONE = new TaskPlaces() {
        @Override
        public Optional<PlaceRef> placeOf(String taskId) {
            return Optional.empty();
        }

        @Override
        public Runnable addListener(Runnable listener) {
            return () -> {
            };
        }
    };
}
