package cl.almejo.vsim.core.io.logisim;

import java.util.Comparator;

/**
 * The Logisim version that saved a file ({@code <project source="2.7.1">}): a few defaults changed between versions
 * (a multiplexer's enable input since 2.6.3.220, the splitter's look since 2.6.3.202), so the reader asks it.
 */
public record LogisimVersion(int major, int minor, int release, int revision) implements Comparable<LogisimVersion> {

    /** The last Logisim 2.7 release; a file that does not say its version is read as this one, as Logisim does. */
    public static final LogisimVersion LATEST = new LogisimVersion(2, 7, 1, 0);

    private static final Comparator<LogisimVersion> ORDER = Comparator.comparingInt(LogisimVersion::major)
            .thenComparingInt(LogisimVersion::minor).thenComparingInt(LogisimVersion::release)
            .thenComparingInt(LogisimVersion::revision);

    /** {@code 2.7.1} or {@code 2.6.3.220}; blank or unreadable text is {@link #LATEST}. */
    public static LogisimVersion parse(String text) {
        if (text == null || text.isBlank()) {
            return LATEST;
        }
        String[] parts = text.strip().split("\\.");
        int[] numbers = new int[4];
        try {
            for (int index = 0; index < Math.min(parts.length, numbers.length); index++) {
                numbers[index] = Integer.parseInt(parts[index].strip());
            }
        } catch (NumberFormatException exception) {
            return LATEST;
        }
        return new LogisimVersion(numbers[0], numbers[1], numbers[2], numbers[3]);
    }

    /** Whether this version is {@code other} or newer. */
    public boolean isAtLeast(LogisimVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(LogisimVersion other) {
        return ORDER.compare(this, other);
    }
}
