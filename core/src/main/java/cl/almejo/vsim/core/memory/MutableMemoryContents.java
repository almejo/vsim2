package cl.almejo.vsim.core.memory;

import java.util.TreeMap;

/** The mutable, sparse twin of {@link MemoryContents}, for behaviours that write words while simulating. */
public final class MutableMemoryContents {

    private final TreeMap<Long, long[]> pages;

    public MutableMemoryContents() {
        this.pages = new TreeMap<>();
    }

    public MutableMemoryContents(MemoryContents initial) {
        this.pages = new TreeMap<>(initial.copyOfPages());
    }

    public long word(long address) {
        long[] page = pages.get(address >>> MemoryContents.PAGE_BITS);
        return page == null ? 0 : page[(int) (address & (MemoryContents.PAGE_SIZE - 1))];
    }

    public void set(long address, long value) {
        if (address < 0) {
            throw new IllegalArgumentException("negative address " + address);
        }
        long pageIndex = address >>> MemoryContents.PAGE_BITS;
        int offset = (int) (address & (MemoryContents.PAGE_SIZE - 1));
        long[] page = pages.get(pageIndex);
        if (page == null) {
            if (value == 0) {
                return;
            }
            page = new long[MemoryContents.PAGE_SIZE];
            pages.put(pageIndex, page);
        }
        page[offset] = value;
        if (value == 0 && isZero(page)) {
            pages.remove(pageIndex);
        }
    }

    /** An immutable copy of the current words. */
    public MemoryContents snapshot() {
        TreeMap<Long, long[]> copy = new TreeMap<>();
        for (var entry : pages.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return new MemoryContents(copy);
    }

    private static boolean isZero(long[] page) {
        for (long value : page) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
