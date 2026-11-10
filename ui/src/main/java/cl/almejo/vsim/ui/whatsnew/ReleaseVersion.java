package cl.almejo.vsim.ui.whatsnew;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A release of VSim as numbers separated by dots («0.8», «1.0»), compared number by number so «0.10» comes after
 * «0.9». A build outside Gradle says «unknown» ({@code VsimVersion.UNKNOWN}) and has none.
 */
record ReleaseVersion(List<Integer> numbers) implements Comparable<ReleaseVersion> {

    private static final Pattern DOTTED_NUMBERS = Pattern.compile("\\d+(\\.\\d+)*");

    ReleaseVersion {
        numbers = List.copyOf(numbers);
        if (numbers.isEmpty()) {
            throw new IllegalArgumentException("a version has at least one number");
        }
    }

    /** {@code text} as a version, or empty when it is not one («unknown», «0.8-beta»). */
    static Optional<ReleaseVersion> parse(String text) {
        if (text == null || !DOTTED_NUMBERS.matcher(text.strip()).matches()) {
            return Optional.empty();
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : text.strip().split("\\.")) {
            try {
                numbers.add(Integer.parseInt(part));
            } catch (NumberFormatException exception) {
                return Optional.empty(); // a number too long for an int is no version of ours
            }
        }
        return Optional.of(new ReleaseVersion(numbers));
    }

    /**
     * The release after this one: the last number plus one («0.7» → «0.8»), as VSim goes up by 0.1 each release. A
     * development build between two releases still says the last one, so it shows the news being written for the next.
     */
    ReleaseVersion next() {
        List<Integer> next = new ArrayList<>(numbers);
        next.set(next.size() - 1, next.get(next.size() - 1) + 1);
        return new ReleaseVersion(next);
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int length = Math.max(numbers.size(), other.numbers.size());
        for (int index = 0; index < length; index++) {
            int compared = Integer.compare(numberAt(index), other.numberAt(index));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private int numberAt(int index) {
        return index < numbers.size() ? numbers.get(index) : 0;
    }

    @Override
    public String toString() {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining("."));
    }
}
