package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.present.PresentationListener;

import java.util.Objects;

/**
 * What the «Presentar» panel hears (spec §5): the presented task's plan and steps, and when tasks begin and end. Both
 * streams are called off the editor thread; the automation layer gives them ({@link #of}), tests give a fake.
 */
public interface PresentationEvents {

    /** Events that never come (an extension that nobody turns on). */
    PresentationEvents NONE = new PresentationEvents() {
        @Override
        public Runnable addPresentationListener(PresentationListener listener) {
            return () -> {
            };
        }

        @Override
        public Runnable addTaskListener(AutomationListener listener) {
            return () -> {
            };
        }
    };

    /** {@code listener} hears every presented task until the returned runnable takes it back. */
    Runnable addPresentationListener(PresentationListener listener);

    /** {@code listener} hears every task begin and end until the returned runnable takes it back. */
    Runnable addTaskListener(AutomationListener listener);

    static PresentationEvents of(AutomationApi api) {
        Objects.requireNonNull(api, "api");
        return new PresentationEvents() {
            @Override
            public Runnable addPresentationListener(PresentationListener listener) {
                return api.addPresentationListener(listener);
            }

            @Override
            public Runnable addTaskListener(AutomationListener listener) {
                return api.addListener(listener);
            }
        };
    }
}
