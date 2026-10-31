package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.simulation.TerminalWindowFrame;
import cl.almejo.vsim.core.terminal.ByteLog;

import java.util.List;
import java.util.Objects;

/**
 * A new frame of a Terminal for one reader: the frame, the bytes this reader had not seen yet and whether they start
 * its stream over (the first frame, a «Reiniciar»).
 */
public record TerminalUpdate(TerminalWindowFrame frame, List<ByteLog.Entry> newBytes, boolean startedOver) {

    public TerminalUpdate {
        Objects.requireNonNull(frame, "frame");
        newBytes = List.copyOf(newBytes);
    }
}
