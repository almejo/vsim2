package cl.almejo.vsim.ui.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEndingTest {

    private final List<String> events = new ArrayList<>();
    private final SessionEnding ending = new SessionEnding(() -> events.add("save"), () -> events.add("shutdown"));

    @Test
    void exitSavesOnceAndTheLastWindowShutsDown() {
        ending.exiting();
        ending.windowClosing(false);
        ending.windowClosed(false);
        ending.windowClosing(true);
        ending.windowClosed(true);

        assertThat(events).containsExactly("save", "shutdown");
    }

    @Test
    void closingTheLastWindowSavesTheSessionOnce() {
        ending.windowClosing(false);
        ending.windowClosed(false);
        assertThat(events).isEmpty();

        ending.windowClosing(true);
        ending.windowClosed(true);

        assertThat(events).containsExactly("save", "shutdown");
    }

    @Test
    void aSecondExitOrLastWindowDoesNotSaveOrShutDownAgain() {
        ending.exiting();
        ending.exiting();
        ending.windowClosing(true);
        ending.windowClosed(true);
        ending.windowClosed(true);

        assertThat(events).containsExactly("save", "shutdown");
    }
}
