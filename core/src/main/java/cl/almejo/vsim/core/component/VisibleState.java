package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.terminal.GlyphCache;
import cl.almejo.vsim.core.terminal.TerminalFrame;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Objects;

public sealed interface VisibleState {

    VisibleState NONE = new None();

    record None() implements VisibleState {
    }

    record LedState(Value value) implements VisibleState {
    }

    record SwitchState(Value value) implements VisibleState {
    }

    /** The value a constant drives, for its painter. */
    record ConstantState(Value value) implements VisibleState {
    }

    record ClockState(Value value) implements VisibleState {
    }

    /** Value with one bit per display input; pin 0 is the least significant bit. */
    record SevenSegmentsState(Value value) implements VisibleState {
    }

    /** The operation an ALU block is doing now: its OP code (0..7), or -1 while OP is undefined. */
    record AluState(int operation) implements VisibleState {
    }

    /** The value a register or counter block holds now (all Z after loading an undefined D). */
    record StoredValueState(Value value) implements VisibleState {
    }

    /**
     * A Teclado's queue: {@code version} grows with every change of it, {@code queue} holds the first
     * {@link #SHOWN_BYTES} bytes (0 to 255) not read yet, {@code size} all of them and {@code cursor} the typing
     * cursor (0 to {@code size}).
     */
    record KeyboardState(long version, List<Integer> queue, int size, int cursor) implements VisibleState {

        /** How many bytes of the queue a state carries. */
        public static final int SHOWN_BYTES = 64;

        public KeyboardState {
            queue = List.copyOf(queue);
        }
    }

    /**
     * A Terminal as its painter shows it: an immutable {@code frame} of its screen, the screen's {@code version},
     * whether it draws with an external character ROM and the {@code glyphs} read from it so far (spec §8.8; empty
     * with the internal generator). {@code owner} is a number unique to the behaviour that published it, so a screen
     * started over («Reiniciar», a rebuilt part) never looks like an older one with the same version. Equality and hash
     * use {@code owner}, {@code version}, {@code externalGlyphs} and the glyphs' generation only, never the cells or
     * the bitmaps: the canvas compares the states of every part on every frame, and a version is enough to know the
     * screen changed.
     */
    record TerminalState(long owner, long version, TerminalFrame frame, boolean externalGlyphs, GlyphCache glyphs)
            implements VisibleState {

        public TerminalState {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(glyphs, "glyphs");
        }

        /** A state without glyphs from a character ROM. */
        public TerminalState(long owner, long version, TerminalFrame frame, boolean externalGlyphs) {
            this(owner, version, frame, externalGlyphs, GlyphCache.EMPTY);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TerminalState state && owner == state.owner && version == state.version
                    && externalGlyphs == state.externalGlyphs
                    && glyphs.generation() == state.glyphs.generation();
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, version, externalGlyphs, glyphs.generation());
        }
    }

    record TimeDiagramState(List<TimeDiagramSample> samples) implements VisibleState {
        public TimeDiagramState {
            samples = List.copyOf(samples);
        }
    }

    /** What a memory did last: nothing yet (or since its contents were restored), a read or a write. */
    enum MemoryAccess { NONE, READ, WRITE }

    /**
     * A RAM or ROM as its painter shows it: whether it is selected ({@code CS} = 1), its last access and that access's
     * address ({@code -1} before the first one), and the content window: the words from {@code firstWindowAddress} on
     * (five, or every word of a smaller memory), placed around the last address and kept inside the memory. A word the
     * simulation stored as undefined (all Z) is 0 in {@code window} and has its bit set in {@code undefinedWindowWords}
     * (bit {@code row} for {@code window.get(row)}), so the painter shows it as «Z».
     *
     * <p>{@code lastRead} and {@code lastWrite} tell the last read and the last write apart from the last access, each
     * with a count that only grows, so a view that compares them with the state it showed before can tell which
     * accesses happened in between (a counter that moves the address right after each write would otherwise hide
     * every write behind the next read).
     */
    record MemoryState(boolean enabled, MemoryAccess lastAccess, long lastAddress, long firstWindowAddress,
                       List<Long> window, long undefinedWindowWords, LastRead lastRead, LastWrite lastWrite)
            implements VisibleState {
        public MemoryState {
            window = List.copyOf(window);
            Objects.requireNonNull(lastRead, "lastRead");
            Objects.requireNonNull(lastWrite, "lastWrite");
        }

        /** A state without the read and write tallies (none so far). */
        public MemoryState(boolean enabled, MemoryAccess lastAccess, long lastAddress, long firstWindowAddress,
                           List<Long> window, long undefinedWindowWords) {
            this(enabled, lastAccess, lastAddress, firstWindowAddress, window, undefinedWindowWords, LastRead.NONE,
                    LastWrite.NONE);
        }

        /** A state whose window words are all defined. */
        public MemoryState(boolean enabled, MemoryAccess lastAccess, long lastAddress, long firstWindowAddress,
                           List<Long> window) {
            this(enabled, lastAccess, lastAddress, firstWindowAddress, window, 0);
        }

        /** Whether the word in window row {@code row} was written undefined. */
        public boolean undefinedAt(int row) {
            return row >= 0 && row < Long.SIZE && (undefinedWindowWords >>> row & 1L) != 0;
        }

        /** Whether the window holds the word at {@code address}. */
        public boolean windowHolds(long address) {
            return address >= firstWindowAddress && address < firstWindowAddress + window.size();
        }

        /**
         * This state as if its last write were its last access: the write, its address and the window around it.
         * Itself when there is no write to show.
         */
        public MemoryState showingWrite() {
            if (lastWrite.address() < 0) {
                return this;
            }
            return new MemoryState(enabled, MemoryAccess.WRITE, lastWrite.address(), lastWrite.firstWindowAddress(),
                    lastWrite.window(), lastWrite.undefinedWindowWords(), lastRead, lastWrite);
        }
    }

    /**
     * A memory's last read: how many reads it has counted in all ({@code count}, it only grows, also across a
     * restore), and the simulation time and address of the last one ({@code -1} before the first one and since a
     * restore).
     */
    record LastRead(long count, long time, long address) {

        /** No read yet. */
        public static final LastRead NONE = new LastRead(0, -1, -1);
    }

    /**
     * A memory's last write, like {@link LastRead}, plus the content window around its address as it is now (like
     * {@link MemoryState}'s), so a view can show the written word even when the last access was a read far from it.
     */
    record LastWrite(long count, long time, long address, long firstWindowAddress, List<Long> window,
                     long undefinedWindowWords) {

        /** No write yet. */
        public static final LastWrite NONE = new LastWrite(0, -1, -1, 0, List.of(), 0);

        public LastWrite {
            window = List.copyOf(window);
        }
    }
}
