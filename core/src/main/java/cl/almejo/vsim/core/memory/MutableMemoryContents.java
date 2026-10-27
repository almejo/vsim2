package cl.almejo.vsim.core.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.LongStream;

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

    /**
     * Sets every word from {@code first} to {@code last} (both included) to {@code value}, a page at a time: a whole
     * page in the range is replaced (or dropped, for 0) without visiting its words, so filling a 16M-word memory with
     * 0 takes no time and leaves it empty.
     */
    public void fill(long first, long last, long value) {
        if (first < 0 || last < first) {
            throw new IllegalArgumentException("bad range " + first + ".." + last);
        }
        long firstPage = first >>> MemoryContents.PAGE_BITS;
        long lastPage = last >>> MemoryContents.PAGE_BITS;
        for (long pageIndex : pageIndexesToVisit(firstPage, lastPage, value)) {
            long pageStart = pageIndex << MemoryContents.PAGE_BITS;
            long from = Math.max(first, pageStart);
            long to = Math.min(last, pageStart + MemoryContents.PAGE_SIZE - 1);
            if (from == pageStart && to == pageStart + MemoryContents.PAGE_SIZE - 1) {
                replaceWholePage(pageIndex, value);
            } else {
                for (long address = from; address <= to; address++) {
                    set(address, value);
                }
            }
        }
    }

    /** Zero only has to visit the pages that exist; any other value visits every page of the range. */
    private Iterable<Long> pageIndexesToVisit(long firstPage, long lastPage, long value) {
        if (value == 0) {
            return new ArrayList<>(pages.subMap(firstPage, true, lastPage, true).keySet());
        }
        return () -> LongStream.rangeClosed(firstPage, lastPage).iterator();
    }

    private void replaceWholePage(long pageIndex, long value) {
        if (value == 0) {
            pages.remove(pageIndex);
            ownedPages.remove(pageIndex);
            return;
        }
        long[] page = new long[MemoryContents.PAGE_SIZE];
        Arrays.fill(page, value);
        pages.put(pageIndex, page);
        ownedPages.add(pageIndex);
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
