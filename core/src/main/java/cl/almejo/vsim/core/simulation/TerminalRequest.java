package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.terminal.TerminalSettings;

import java.util.Objects;

/**
 * What the Terminal window wants to see of the Terminal simulated as {@code componentId} (a part on the circuit
 * itself): its screen, the last {@code historyLines} rows that scrolled off the top, and the bytes received after
 * byte number {@code bytesSince} (0 = all the log keeps). The simulation thread answers with a
 * {@link TerminalWindowFrame} (at most every 16 ms while running); {@link SimulationThread#acknowledgeTerminalBytes}
 * moves the first byte forward without a new request.
 */
public record TerminalRequest(String componentId, int historyLines, long bytesSince) {

    public TerminalRequest {
        Objects.requireNonNull(componentId, "componentId");
        if (historyLines < 0 || historyLines > TerminalSettings.MAXIMUM_SCROLLBACK_LINES || bytesSince < 0) {
            throw new IllegalArgumentException("a terminal request of " + historyLines + " history lines and bytes "
                    + "since " + bytesSince);
        }
    }
}
