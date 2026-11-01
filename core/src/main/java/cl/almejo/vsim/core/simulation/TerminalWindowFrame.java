package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.standard.TerminalScreenPart;
import cl.almejo.vsim.core.terminal.ByteLog;
import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalCell;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.core.terminal.TerminalScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One picture of a Terminal for its window (spec §9), answering a {@link TerminalRequest}: the {@code owner} of the
 * screen ({@link cl.almejo.vsim.core.component.VisibleState.TerminalState#owner()}: another one means a rebuilt part
 * or a «Reiniciar»), the screen, the history rows asked for (oldest first), the received bytes not acknowledged yet
 * (oldest first; see {@link SimulationThread#acknowledgeTerminalBytes}), the number of the last byte received (it
 * goes back to 0 after «Reiniciar»), the epoch of the byte log ({@link ByteLog#epoch()}: another one means «Reiniciar», the
 * same one a rebuilt part, whose numbering goes on), the
 * transcript, the simulation time and the glyphs read from its character ROM (empty without one). Immutable: the
 * history rows are the screen's own, shared and never written (a row never changes once it scrolled off; see
 * {@link TerminalScreen#history}), and {@link #history()} gives copies (read single cells with {@link #historyCell},
 * which allocates nothing).
 */
public record TerminalWindowFrame(TerminalRequest request, long owner, TerminalFrame frame,
                                  List<TerminalCell[]> history,
                                  List<ByteLog.Entry> bytes, long lastByteSequence, long byteEpoch, String transcript,
                                  long time, GlyphCache glyphs) {

    public TerminalWindowFrame {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(transcript, "transcript");
        Objects.requireNonNull(glyphs, "glyphs");
        history = List.copyOf(history);
        bytes = List.copyOf(bytes);
    }

    /** A frame of a Terminal without glyphs from a character ROM. */
    public TerminalWindowFrame(TerminalRequest request, long owner, TerminalFrame frame, List<TerminalCell[]> history,
                               List<ByteLog.Entry> bytes, long lastByteSequence, long byteEpoch, String transcript,
                               long time) {
        this(request, owner, frame, history, bytes, lastByteSequence, byteEpoch, transcript, time, GlyphCache.EMPTY);
    }

    /**
     * The frame of {@code terminal} for {@code request}, on the simulation thread, with the bytes after
     * {@code bytesSince}. Costs the history asked for and the bytes not acknowledged yet, never more than the
     * screen's limits (10 000 lines, the byte log's 10 000 entries).
     */
    static TerminalWindowFrame capture(TerminalScreenPart terminal, TerminalRequest request, long bytesSince,
                                       long time) {
        TerminalScreen screen = terminal.screen();
        return new TerminalWindowFrame(request, terminal.owner(), screen.frame(),
                screen.history(request.historyLines()),
                screen.byteLog().since(bytesSince), screen.byteLog().lastSequence(), screen.byteLog().epoch(), screen.transcript(), time,
                terminal.glyphs());
    }

    /** Fresh copies of the history rows, oldest first; prefer {@link #historyCell} when drawing. */
    @Override
    public List<TerminalCell[]> history() {
        return copies(history);
    }

    /** How many history rows the frame holds. */
    public int historyLines() {
        return history.size();
    }

    /** The cell at {@code column} of history row {@code line} (0 = the oldest), without copying. */
    public TerminalCell historyCell(int line, int column) {
        return history.get(line)[column];
    }

    /** The length of history row {@code line}. */
    public int historyColumns(int line) {
        return history.get(line).length;
    }

    private static List<TerminalCell[]> copies(List<TerminalCell[]> rows) {
        List<TerminalCell[]> copies = new ArrayList<>(rows.size());
        rows.forEach(row -> copies.add(row.clone()));
        return List.copyOf(copies);
    }

    @Override
    public boolean equals(Object other) {
        return other == this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "TerminalWindowFrame[" + request + ", version " + frame.version() + ", " + bytes.size() + " bytes, t = "
                + time + "]";
    }
}
