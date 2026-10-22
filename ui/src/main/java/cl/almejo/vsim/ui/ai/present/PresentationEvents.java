package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.present.PresentationListener;

import java.util.Objects;
import java.util.function.Function;

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

    /**
     * {@code listener} runs whenever VSim serves a call of the AI (off the editor thread), so «Claude está
     * pensando…» goes at the next call (IA.3), until the returned runnable takes it back. Nothing by default.
     */
    default Runnable addActivityListener(Runnable listener) {
        return () -> {
        };
    }

    static PresentationEvents of(AutomationApi api) {
        return of(api, listener -> () -> {
        });
    }

    /** @param requests registers a listener of every call VSim serves and returns its removal */
    static PresentationEvents of(AutomationApi api, Function<Runnable, Runnable> requests) {
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(requests, "requests");
        return new PresentationEvents() {
            @Override
            public Runnable addActivityListener(Runnable listener) {
                return requests.apply(listener);
            }

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
