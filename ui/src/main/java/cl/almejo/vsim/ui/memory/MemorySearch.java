package cl.almejo.vsim.ui.memory;

import java.util.Arrays;
import java.util.OptionalLong;

/**
 * The words of a memory holding one value, found without visiting every address: for a non-zero value it keeps the
 * sorted addresses that hold it; for zero it keeps the sorted non-zero addresses and treats every other address as a
 * match (so a 16M-word memory that is mostly unset costs only its stored words). Immutable.
 */
final class MemorySearch {

    private final long words;
    private final boolean zero;
    /** The matching addresses (non-zero search) or the non-zero ones (zero search), ascending. */
    private final long[] addresses;

    /** {@code addresses} must be ascending, below {@code words} and without repeats. */
    MemorySearch(long words, boolean zero, long[] addresses) {
        this.words = words;
        this.zero = zero;
        this.addresses = addresses;
    }

    long count() {
        return zero ? words - addresses.length : addresses.length;
    }

    /** The first match after {@code address}, wrapping to the first one; empty when nothing matches. */
    OptionalLong next(long address) {
        if (count() == 0) {
            return OptionalLong.empty();
        }
        long candidate = firstMatchFrom(address + 1);
        return OptionalLong.of(candidate >= 0 ? candidate : firstMatchFrom(0));
    }

    /** The last match before {@code address}, wrapping to the last one; empty when nothing matches. */
    OptionalLong previous(long address) {
        if (count() == 0) {
            return OptionalLong.empty();
        }
        long candidate = lastMatchUpTo(address - 1);
        return OptionalLong.of(candidate >= 0 ? candidate : lastMatchUpTo(words - 1));
    }

    /** The 1-based position of {@code address} among the matches, 0 when it is not one. */
    long rank(long address) {
        int index = Arrays.binarySearch(addresses, address);
        if (zero) {
            return index >= 0 || address < 0 || address >= words ? 0 : address + 1 - insertionPoint(index);
        }
        return index >= 0 ? index + 1 : 0;
    }

    private static int insertionPoint(int searchResult) {
        return searchResult >= 0 ? searchResult : -searchResult - 1;
    }

    /** The first match at or after {@code from}, or -1. */
    private long firstMatchFrom(long from) {
        if (from >= words) {
            return -1;
        }
        if (!zero) {
            int index = insertionPoint(Arrays.binarySearch(addresses, from));
            return index < addresses.length ? addresses[index] : -1;
        }
        long candidate = from;
        int index = insertionPoint(Arrays.binarySearch(addresses, candidate));
        while (index < addresses.length && addresses[index] == candidate) {
            candidate++;
            index++;
        }
        return candidate < words ? candidate : -1;
    }

    /** The last match at or before {@code from}, or -1. */
    private long lastMatchUpTo(long from) {
        if (from < 0) {
            return -1;
        }
        long start = Math.min(from, words - 1);
        if (!zero) {
            int index = Arrays.binarySearch(addresses, start);
            int position = index >= 0 ? index : -index - 2;
            return position >= 0 ? addresses[position] : -1;
        }
        long candidate = start;
        int index = Arrays.binarySearch(addresses, candidate);
        while (index >= 0 && candidate >= 0) {
            candidate--;
            index--;
            if (index < 0 || addresses[index] != candidate) {
                break;
            }
        }
        return candidate;
    }
}
