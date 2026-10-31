package cl.almejo.vsim.core.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The bytes typed and not read yet (a Teclado's queue, later a UART's receive buffer), with Logisim's editing
 * cursor: typed bytes go in at the cursor, which sits after them; the arrows and Inicio/Fin move it and Supr deletes
 * the byte after it; the reader takes bytes from the head, and the cursor follows the byte it was on. Bounded:
 * {@link #insert} takes a whole character or nothing. Not thread-safe: a behaviour keeps one and only the simulation
 * thread touches it.
 */
public final class KeyQueue {

    private static final int BYTE_MASK = 0xFF;

    private final int capacity;
    private final ArrayList<Integer> bytes = new ArrayList<>();
    private int cursor;
    private long version;

    /** An empty queue that holds at most {@code capacity} bytes. */
    public KeyQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int size() {
        return bytes.size();
    }

    public boolean isEmpty() {
        return bytes.isEmpty();
    }

    /** The cursor's place, 0 (before the head) to {@link #size()} (after the last byte). */
    public int cursor() {
        return cursor;
    }

    /** Grows on every change of the contents or the cursor, so a view can tell whether it is out of date. */
    public long version() {
        return version;
    }

    /** The head byte (0 to 255), or 0 when the queue is empty. */
    public int peek() {
        return bytes.isEmpty() ? 0 : bytes.get(0);
    }

    /** Takes the head byte off; false when there is none. */
    public boolean pop() {
        if (bytes.isEmpty()) {
            return false;
        }
        bytes.remove(0);
        cursor = Math.max(0, cursor - 1);
        version++;
        return true;
    }

    /**
     * Puts {@code characterBytes} in at the cursor, all of them or none: false (changing nothing) when they do not
     * fit.
     */
    public boolean insert(byte[] characterBytes) {
        Objects.requireNonNull(characterBytes, "characterBytes");
        if (characterBytes.length == 0 || bytes.size() + characterBytes.length > capacity) {
            return false;
        }
        for (byte value : characterBytes) {
            bytes.add(cursor++, value & BYTE_MASK);
        }
        version++;
        return true;
    }

    /** Empties the queue. */
    public void clear() {
        if (bytes.isEmpty() && cursor == 0) {
            return;
        }
        bytes.clear();
        cursor = 0;
        version++;
    }

    /** Moves the cursor or deletes the byte after it, as Logisim's Keyboard does with its keys, or empties the queue. */
    public void edit(TypingEdit edit) {
        Objects.requireNonNull(edit, "edit");
        int cursorBefore = cursor;
        int sizeBefore = bytes.size();
        switch (edit) {
            case CURSOR_LEFT -> cursor = Math.max(0, cursor - 1);
            case CURSOR_RIGHT -> cursor = Math.min(bytes.size(), cursor + 1);
            case CURSOR_HOME -> cursor = 0;
            case CURSOR_END -> cursor = bytes.size();
            case DELETE -> {
                if (cursor < bytes.size()) {
                    bytes.remove(cursor);
                }
            }
            case CLEAR -> {
                bytes.clear();
                cursor = 0;
            }
        }
        if (cursor != cursorBefore || bytes.size() != sizeBefore) {
            version++;
        }
    }

    /** The first {@code limit} bytes, as a new list. */
    public List<Integer> head(int limit) {
        return List.copyOf(bytes.subList(0, Math.min(limit, bytes.size())));
    }

    /**
     * A queue of {@code newCapacity} with what this one holds: the bytes that fit, the cursor kept inside. This queue
     * is left as it is.
     */
    public KeyQueue resizedTo(int newCapacity) {
        KeyQueue resized = new KeyQueue(newCapacity);
        resized.bytes.addAll(bytes.subList(0, Math.min(bytes.size(), newCapacity)));
        resized.cursor = Math.min(cursor, resized.bytes.size());
        resized.version = version + 1;
        return resized;
    }
}
