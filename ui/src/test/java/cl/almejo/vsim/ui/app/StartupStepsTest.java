package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupStepsTest {

    private final List<String> windows = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    @Test
    void anOpeningThatThrowsDoesNotStopTheOthers() {
        StartupSteps.openEach(List.of("first", "broken", "third"), name -> {
            if (name.equals("broken")) {
                throw new IllegalStateException("no window for you");
            }
            windows.add(name);
        });

        assertThat(windows).containsExactly("first", "third");
    }

    @Test
    void theTailAlwaysLeavesAWindowAndStartsAutosave() {
        StartupSteps.openEach(List.of("broken"), name -> {
            throw new IllegalStateException("no window for you");
        });
        StartupSteps.finish(() -> !windows.isEmpty(), () -> windows.add("empty document"),
                () -> events.add("components"), () -> events.add("autosave"));

        assertThat(windows).containsExactly("empty document");
        assertThat(events).containsExactly("components", "autosave");
    }

    @Test
    void noEmptyDocumentWhenSomethingOpened() {
        windows.add("circuit");
        StartupSteps.finish(() -> !windows.isEmpty(), () -> windows.add("empty document"),
                () -> events.add("components"), () -> events.add("autosave"));

        assertThat(windows).containsExactly("circuit");
        assertThat(events).containsExactly("components", "autosave");
    }

    @Test
    void autosaveStartsEvenWhenTheEmptyDocumentOrTheComponentsFail() {
        StartupSteps.finish(() -> false, () -> {
            throw new IllegalStateException("no empty document");
        }, () -> {
            throw new IllegalStateException("no import");
        }, () -> events.add("autosave"));

        assertThat(events).containsExactly("autosave");
    }

    @Test
    void autosaveStartsWhenAnEarlierStepThrows() {
        try {
            StartupSteps.run(() -> {
                throw new IllegalStateException("plan failed");
            }, () -> !windows.isEmpty(), () -> windows.add("empty document"), () -> events.add("components"),
                    () -> events.add("autosave"));
        } catch (IllegalStateException expected) {
            events.add("rethrown");
        }

        assertThat(windows).containsExactly("empty document");
        assertThat(events).containsExactly("components", "autosave", "rethrown");
    }
}
