package cl.almejo.vsim.core.memory;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * The mutable, sparse twin of {@link MemoryContents}, for behaviours that write words while simulating. Pages are
 * copied on write: building one from contents and taking a {@link #snapshot()} share the pages, and only a page that
 * is written afterwards is copied, so a big memory with a few written words snapshots in time proportional to its
 * page count.
 */
public final class MutableMemoryContents {

    private final TreeMap<Long, long[]> pages;
    /** The pages this object may write in place: created or copied here since the last snapshot. */
    private final Set<Long> ownedPages = new HashSet<>();

    public MutableMemoryContents() {
        this.pages = new TreeMap<>();
    }

    public MutableMemoryContents(MemoryContents initial) {
        this.pages = new TreeMap<>(initial.sharedPages());
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
            ownedPages.add(pageIndex);
        } else if (page[offset] == value) {
            return;
        } else if (!ownedPages.contains(pageIndex)) {
            page = page.clone();
            pages.put(pageIndex, page);
            ownedPages.add(pageIndex);
        }
        page[offset] = value;
        if (value == 0 && isZero(page)) {
            pages.remove(pageIndex);
            ownedPages.remove(pageIndex);
        }
    }

    /** An immutable view of the current words; later writes here copy the pages they touch first. */
    public MemoryContents snapshot() {
        ownedPages.clear();
        return new MemoryContents(new TreeMap<>(pages));
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
