package cl.almejo.vsim.core.memory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The words of a memory: immutable, sparse (only pages holding a non-zero word exist) and up to 64 bits wide. An
 * address that was never set reads as 0. The canonical text form ({@link #encode()}) is what the circuit file keeps.
 */
public final class MemoryContents {

    static final int PAGE_BITS = 8;
    static final int PAGE_SIZE = 1 << PAGE_BITS;
    private static final MemoryContents EMPTY = new MemoryContents(new TreeMap<>());

    /** Receives each non-zero word, in ascending address order. */
    public interface LongLongConsumer {
        void accept(long address, long value);
    }

    /** Pages by page index; never mutated, never holds an all-zero page. */
    private final SortedMap<Long, long[]> pages;
    private final long count;
    private final long highestAddress;

    /** Takes ownership of {@code pages}; their arrays are never written again (see {@link MutableMemoryContents}). */
    MemoryContents(SortedMap<Long, long[]> pages) {
        this.pages = pages;
        long nonZero = 0;
        long highest = -1;
        for (Map.Entry<Long, long[]> entry : pages.entrySet()) {
            long[] page = entry.getValue();
            for (int offset = 0; offset < PAGE_SIZE; offset++) {
                if (page[offset] != 0) {
                    nonZero++;
                    highest = (entry.getKey() << PAGE_BITS) + offset;
                }
            }
        }
        this.count = nonZero;
        this.highestAddress = highest;
    }

    public static MemoryContents empty() {
        return EMPTY;
    }

    public long word(long address) {
        long[] page = pages.get(address >>> PAGE_BITS);
        return page == null ? 0 : page[(int) (address & (PAGE_SIZE - 1))];
    }

    public MemoryContents with(long address, long value) {
        requireAddress(address);
        if (word(address) == value) {
            return this;
        }
        MutableMemoryContents mutable = new MutableMemoryContents(this);
        mutable.set(address, value);
        return mutable.snapshot();
    }

    public MemoryContents withAll(SortedMap<Long, Long> words) {
        MutableMemoryContents mutable = new MutableMemoryContents(this);
        for (Map.Entry<Long, Long> entry : words.entrySet()) {
            requireAddress(entry.getKey());
            mutable.set(entry.getKey(), entry.getValue());
        }
        return mutable.snapshot();
    }

    /** The highest address holding a non-zero word, or -1 when empty. */
    public long highestAddress() {
        return highestAddress;
    }

    /** How many words are non-zero. */
    public long count() {
        return count;
    }

    /** Bits of the widest stored value (0 when empty). */
    public int widestBits() {
        long combined = 0;
        for (long[] page : pages.values()) {
            for (long value : page) {
                combined |= value;
            }
        }
        return Long.SIZE - Long.numberOfLeadingZeros(combined);
    }

    public void forEachNonZero(LongLongConsumer consumer) {
        for (Map.Entry<Long, long[]> entry : pages.entrySet()) {
            long base = entry.getKey() << PAGE_BITS;
            long[] page = entry.getValue();
            for (int offset = 0; offset < PAGE_SIZE; offset++) {
                if (page[offset] != 0) {
                    consumer.accept(base + offset, page[offset]);
                }
            }
        }
    }

    /**
     * The canonical text: runs of consecutive non-zero words, each run as {@code @<hex address> <hex word>...},
     * upper-case hex, zeros omitted. Empty contents encode to the empty string.
     */
    public String encode() {
        StringBuilder text = new StringBuilder();
        long[] nextAddress = {-1};
        forEachNonZero((address, value) -> {
            if (address != nextAddress[0]) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append('@').append(Long.toHexString(address).toUpperCase());
            }
            text.append(' ').append(Long.toHexString(value).toUpperCase());
            nextAddress[0] = address + 1;
        });
        return text.toString();
    }

    /** The inverse of {@link #encode()}; throws IllegalArgumentException for text that cannot be read. */
    public static MemoryContents decode(String text) {
        MutableMemoryContents mutable = new MutableMemoryContents();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return EMPTY;
        }
        long address = 0;
        boolean addressKnown = false;
        for (String token : trimmed.split("\\s+")) {
            try {
                if (token.startsWith("@")) {
                    address = Long.parseLong(token.substring(1), 16);
                    if (address < 0) {
                        throw new IllegalArgumentException("negative address in " + token);
                    }
                    addressKnown = true;
                } else {
                    if (!addressKnown) {
                        throw new IllegalArgumentException("a word before any @address: " + token);
                    }
                    mutable.set(address, Long.parseUnsignedLong(token, 16));
                    address++;
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("not hexadecimal: " + token, exception);
            }
        }
        return mutable.snapshot();
    }

    /**
     * The pages themselves, for {@link MutableMemoryContents} to share: it never writes a page it did not create or
     * copy, so these stay unchanged.
     */
    SortedMap<Long, long[]> sharedPages() {
        return Collections.unmodifiableSortedMap(pages);
    }

    private static void requireAddress(long address) {
        if (address < 0) {
            throw new IllegalArgumentException("negative address " + address);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemoryContents that) || that.count != count
                || !that.pages.keySet().equals(pages.keySet())) {
            return false;
        }
        for (Map.Entry<Long, long[]> entry : pages.entrySet()) {
            if (!Arrays.equals(entry.getValue(), that.pages.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        for (Map.Entry<Long, long[]> entry : pages.entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + Arrays.hashCode(entry.getValue());
        }
        return hash;
    }

    @Override
    public String toString() {
        return "MemoryContents[" + count + " words]";
    }
}
