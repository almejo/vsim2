package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.core.terminal.TerminalScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A one-shot reading of a Terminal for a reader that is not the window (the AI): the screen, the text of the last
 * {@code history} rows that scrolled off the top (oldest first), the last received bytes (oldest first), the
 * transcript and the simulation time. Immutable and made of plain text and values, so it can leave the simulation
 * thread as it is.
 */
public record TerminalReading(TerminalFrame frame, List<String> history, List<ByteLog.Entry> bytes, String transcript,
                              long time) {

    public TerminalReading {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(transcript, "transcript");
        history = List.copyOf(history);
        bytes = List.copyOf(bytes);
    }

    /**
     * The reading of {@code screen} with the last {@code historyLines} history rows and the last {@code byteCount}
     * bytes, at simulation time {@code time}. Call it on the thread that owns the screen.
     */
    public static TerminalReading of(TerminalScreen screen, int historyLines, int byteCount, long time) {
        List<String> rows = new ArrayList<>();
        for (TerminalCell[] row : screen.history(historyLines)) {
            StringBuilder text = new StringBuilder(row.length);
            for (TerminalCell cell : row) {
                text.appendCodePoint(cell.codePoint());
            }
            rows.add(text.toString().stripTrailing());
        }
        ByteLog log = screen.byteLog();
        return new TerminalReading(screen.frame(), rows, log.since(Math.max(0, log.lastSequence() - byteCount)),
                screen.transcript(), time);
    }
}
