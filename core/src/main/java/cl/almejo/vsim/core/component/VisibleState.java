package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.value.Value;

import java.util.List;

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
     */
    record MemoryState(boolean enabled, MemoryAccess lastAccess, long lastAddress, long firstWindowAddress,
                       List<Long> window, long undefinedWindowWords) implements VisibleState {
        public MemoryState {
            window = List.copyOf(window);
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
    }
}
