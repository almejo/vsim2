package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.simulation.TerminalWindowFrame;
import cl.almejo.vsim.core.terminal.ByteLog;

import java.util.List;

/**
 * What one reader of a Terminal's frames has seen of its bytes, so each frame gives it only the new ones. A frame of
 * another screen ({@link TerminalWindowFrame#owner()}) is either a rebuilt part, whose byte log goes on (the last byte
 * seen is still there, with the same time and value), or a «Reiniciar», whose log starts over from byte 1: then the
 * reading {@link Reading#startedOver() starts over} (the byte monitor empties, «Guardar salida» truncates its file).
 * The first frame read also starts over. Not thread-safe: one reader, one thread.
 */
final class ByteStream {

    private static final long NONE = 0;

    /** The bytes a frame brought that the reader had not seen, and whether they start the stream over. */
    record Reading(List<ByteLog.Entry> newBytes, boolean startedOver) {

        Reading {
            newBytes = List.copyOf(newBytes);
        }
    }

    private long owner = NONE;
    private ByteLog.Entry lastSeen;

    /** The new bytes of {@code frame}. */
    Reading read(TerminalWindowFrame frame) {
        List<ByteLog.Entry> bytes = frame.bytes();
        boolean startedOver = owner == NONE || frame.owner() != owner && !continues(bytes);
        owner = frame.owner();
        List<ByteLog.Entry> fresh = startedOver ? bytes : after(bytes);
        if (startedOver) {
            lastSeen = null;
        }
        if (!fresh.isEmpty()) {
            lastSeen = fresh.getLast();
        }
        return new Reading(fresh, startedOver);
    }

    /** Whether the bytes of another screen still hold the last byte seen, as a rebuilt part's log does. */
    private boolean continues(List<ByteLog.Entry> bytes) {
        if (lastSeen == null) {
            return false;
        }
        return bytes.stream().anyMatch(entry -> entry.equals(lastSeen));
    }

    private List<ByteLog.Entry> after(List<ByteLog.Entry> bytes) {
        long last = lastSeen == null ? 0 : lastSeen.sequence();
        return bytes.stream().filter(entry -> entry.sequence() > last).toList();
    }
}
