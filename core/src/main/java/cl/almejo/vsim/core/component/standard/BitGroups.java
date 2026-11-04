package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ParameterProblem;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * How a «Separador de bus» / «Unión de bus» groups the bits of its bus into pins: one group of bus bits per pin, in pin
 * order. Bits in no group are unused (the Separador ignores them, the Unión leaves them Z). Inside a pin the bits go
 * from the lowest bus bit to the highest, Logisim's rule too: for the RISC-V {@code sw} immediate {@code 7-11,25-31},
 * bus bits 7-11 are pin bits 0-4 and bus bits 25-31 are pin bits 5-11.
 *
 * <p>The text form (the {@link StandardComponentTypes#GROUPS} parameter) separates the pins with {@code |} and gives
 * each pin a comma-separated list of ranges, e.g. {@code 7-11,25-31|15-19|20-24}. A range may be written from either
 * end ({@code 11-7} is {@code 7-11}, as Logisim and Verilog write {@code [11:7]}): it is a set of bits, and inside the
 * pin they go from the lowest anyway. The «Grupos» window reads its «desde – hasta» the same way. The canonical text
 * sorts and merges the ranges of each pin and is empty for one bit per pin in bus order, today's splitter, so parts
 * without groups keep their files and their pin names ({@code b0}..{@code b7}).
 */
public final class BitGroups {

    /** The most pins a part has: as many as the bits of the widest bus (32). */
    public static final int MAXIMUM_PINS = 32;

    /** The widest bus a grouped part takes; the bus parts' own «Ancho» limit. */
    public static final int MAXIMUM_WIDTH = 32;

    /** The problem keys of {@link #problem}, in the UI bundle; the AI gets them in words. */
    public static final String BIT_OUT_OF_RANGE = "groups.bitOutOfRange";
    public static final String BIT_IN_TWO_PINS = "groups.bitInTwoPins";
    public static final String EMPTY_PIN = "groups.emptyPin";
    public static final String TOO_MANY_PINS = "groups.tooManyPins";
    public static final String INVALID_TEXT = "groups.invalidText";

    private static final String PIN_SEPARATOR = "|";
    private static final String RANGE_SEPARATOR = ",";
    private static final String RANGE_DASH = "-";
    private static final String PIN_NAME_PREFIX = "b";
    /**
     * A bit number has at most two digits (bits 0..31, so 99 is reported as out of range); longer numbers are unreadable
     * text, which also keeps a range such as {@code 0-999999999} from building a huge set of bits.
     */
    private static final int MAXIMUM_DIGITS = 2;

    private final int width;
    private final List<List<Integer>> pins;

    private BitGroups(int width, List<List<Integer>> pins) {
        this.width = width;
        this.pins = pins.stream().map(List::copyOf).toList();
    }

    /**
     * The groups {@code text} gives on a bus of {@code width} bits; empty text is one bit per pin.
     *
     * @throws IllegalArgumentException with the {@link #problem} key in its message when the text is not valid
     */
    public static BitGroups parse(String text, int width) {
        Objects.requireNonNull(text, "text");
        checkWidth(width);
        Reading reading = read(text, width);
        reading.problem().ifPresent(problem -> {
            throw new IllegalArgumentException("groups " + text + ": " + problem.messageKey() + " "
                    + problem.arguments());
        });
        return reading.groups();
    }

    /**
     * What is wrong with {@code text} on a bus of {@code width} bits, empty when nothing is: unreadable text, a bit
     * ≥ the width, a bit in two pins, an empty pin or more than {@link #MAXIMUM_PINS} pins. The message keys live in
     * the UI bundle ({@code groups.*}); the arguments are the bit and width, the bit, the pin's position (from 1) and
     * the limit.
     *
     * @throws IllegalArgumentException when {@code width} is not a bus width (1 to 32), as {@link #parse} does
     */
    public static Optional<ParameterProblem> problem(String text, int width) {
        Objects.requireNonNull(text, "text");
        checkWidth(width);
        return read(text, width).problem();
    }

    /** One pin per bit, in bus order: the part as it was before groups (canonical text {@code ""}). */
    public static BitGroups onePerPin(int width) {
        return uniform(width, 1);
    }

    /**
     * Equal contiguous pins of {@code bitsPerPin} bits from bit 0 up, the last one with what remains:
     * {@code uniform(8, 4)} is {@code 0-3|4-7}, {@code uniform(8, 3)} is {@code 0-2|3-5|6-7}.
     */
    public static BitGroups uniform(int width, int bitsPerPin) {
        checkWidth(width);
        if (bitsPerPin < 1 || bitsPerPin > width) {
            throw new IllegalArgumentException("bits per pin must be between 1 and " + width + ": " + bitsPerPin);
        }
        List<List<Integer>> pins = new ArrayList<>();
        for (int first = 0; first < width; first += bitsPerPin) {
            pins.add(IntStream.range(first, Math.min(first + bitsPerPin, width)).boxed().toList());
        }
        return new BitGroups(width, pins);
    }

    /**
     * {@code text} in canonical form for any width (the parameter's stored value): ranges sorted and merged, pins in
     * their order. A text of one bit per pin stays as written, since only the width can tell it means «one bit per
     * pin»; the pins it gives are the same as the empty text's.
     *
     * @throws IllegalArgumentException when the text has a problem on the widest bus
     */
    public static String normalize(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            return "";
        }
        return parse(text, MAXIMUM_WIDTH).groupsText();
    }

    /** The width of the bus these groups split. */
    public int width() {
        return width;
    }

    /** The text of these groups, {@code ""} when they are one bit per pin in bus order. */
    public String canonical() {
        return equals(onePerPin(width)) ? "" : groupsText();
    }

    public int pinCount() {
        return pins.size();
    }

    /** The bus bits of pin {@code pin}, ascending: pin bit {@code k} is the {@code k}-th of them. */
    public List<Integer> bitsOf(int pin) {
        return pins.get(pin);
    }

    /** How many bits pin {@code pin} carries. */
    public int widthOf(int pin) {
        return pins.get(pin).size();
    }

    /** Pin {@code pin}'s bits as text: {@code 5}, {@code 0-3}, {@code 7-11,25-31}. */
    public String label(int pin) {
        return label(pins.get(pin));
    }

    /** The name of pin {@code pin}: {@code b} and its label, so a one-bit pin keeps today's name ({@code b5}). */
    public String pinName(int pin) {
        return PIN_NAME_PREFIX + label(pin);
    }

    /**
     * The bits per pin when these groups are {@linkplain #uniform uniform} («Bits por pin» in Propiedades), 0 when they
     * are custom.
     */
    public int uniformSize() {
        int size = widthOf(0);
        return equals(uniform(width, size)) ? size : 0;
    }

    /**
     * The bits of the last pin of {@linkplain #uniformSize() equal pins} when it carries fewer than the others, the rest
     * of a width that is not a multiple of «Bits por pin» ({@code 0-6|7}: 1); 0 when every pin is equal or the groups
     * are custom. Propiedades says it, so «2 pines de 7 bits» does not hide the 1-bit pin.
     */
    public int lastPinRemainder() {
        int size = uniformSize();
        int last = widthOf(pinCount() - 1);
        return size > 0 && last < size ? last : 0;
    }

    /** The bus bits in no pin, ascending. */
    public List<Integer> unusedBits() {
        BitSet used = new BitSet(width);
        pins.forEach(bits -> bits.forEach(used::set));
        return IntStream.range(0, width).filter(bit -> !used.get(bit)).boxed().toList();
    }

    /**
     * These groups on a bus of {@code newWidth} bits («Ancho» changed): uniform groups stay uniform with the same bits
     * per pin (at most the new width); custom ones lose the bits ≥ the new width and the pins left empty, and become
     * one bit per pin when no pin is left.
     */
    public BitGroups resized(int newWidth) {
        checkWidth(newWidth);
        int size = uniformSize();
        if (size > 0) {
            return uniform(newWidth, Math.min(size, newWidth));
        }
        List<List<Integer>> kept = pins.stream()
                .map(bits -> bits.stream().filter(bit -> bit < newWidth).toList())
                .filter(bits -> !bits.isEmpty())
                .toList();
        return kept.isEmpty() ? onePerPin(newWidth) : new BitGroups(newWidth, kept);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BitGroups groups && groups.width == width && groups.pins.equals(pins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, pins);
    }

    @Override
    public String toString() {
        return width + ":" + groupsText();
    }

    private String groupsText() {
        return pins.stream().map(BitGroups::label).collect(Collectors.joining(PIN_SEPARATOR));
    }

    /**
     * Ascending bits as ranges: {@code 7-11,25-31}, {@code ""} for none. The «Grupos» window labels its pins (and the
     * unused bits) this way while they are being edited, before they are valid groups.
     */
    public static String label(List<Integer> bits) {
        List<String> ranges = new ArrayList<>();
        int index = 0;
        while (index < bits.size()) {
            int first = bits.get(index);
            int last = first;
            while (index + 1 < bits.size() && bits.get(index + 1) == last + 1) {
                index++;
                last = bits.get(index);
            }
            ranges.add(first == last ? String.valueOf(first) : first + RANGE_DASH + last);
            index++;
        }
        return String.join(RANGE_SEPARATOR, ranges);
    }

    private static void checkWidth(int width) {
        if (width < 1 || width > MAXIMUM_WIDTH) {
            throw new IllegalArgumentException("width must be between 1 and " + MAXIMUM_WIDTH + ": " + width);
        }
    }

    /** The groups a text gives, or the first problem found reading it. */
    private record Reading(BitGroups groups, Optional<ParameterProblem> problem) {

        static Reading of(BitGroups groups) {
            return new Reading(groups, Optional.empty());
        }

        static Reading failed(String key, Object... arguments) {
            return new Reading(null, Optional.of(new ParameterProblem(StandardComponentTypes.GROUPS, key,
                    List.of(arguments))));
        }
    }

    private static Reading read(String text, int width) {
        if (text.isBlank()) {
            return Reading.of(onePerPin(width));
        }
        String[] pinTexts = text.split("\\" + PIN_SEPARATOR, -1);
        if (pinTexts.length > MAXIMUM_PINS) {
            return Reading.failed(TOO_MANY_PINS, MAXIMUM_PINS);
        }
        BitSet taken = new BitSet(width);
        List<List<Integer>> pins = new ArrayList<>();
        for (int pin = 0; pin < pinTexts.length; pin++) {
            Optional<BitSet> bits = readPin(pinTexts[pin]);
            if (bits.isEmpty()) {
                return Reading.failed(INVALID_TEXT);
            }
            Optional<Reading> problem = problemOf(bits.get(), pin, width, taken);
            if (problem.isPresent()) {
                return problem.get();
            }
            taken.or(bits.get());
            pins.add(bits.get().stream().boxed().toList());
        }
        return Reading.of(new BitGroups(width, pins));
    }

    /** What is wrong with pin {@code pin}'s {@code bits} on a bus of {@code width} bits with {@code taken} in use. */
    private static Optional<Reading> problemOf(BitSet bits, int pin, int width, BitSet taken) {
        if (bits.isEmpty()) {
            return Optional.of(Reading.failed(EMPTY_PIN, pin + 1));
        }
        if (bits.length() > width) {
            return Optional.of(Reading.failed(BIT_OUT_OF_RANGE, bits.length() - 1, width));
        }
        BitSet shared = (BitSet) bits.clone();
        shared.and(taken);
        return shared.isEmpty() ? Optional.empty() : Optional.of(Reading.failed(BIT_IN_TWO_PINS, shared.nextSetBit(0)));
    }

    /**
     * The bits of one pin's text ({@code 7-11, 25-31}, a range from either end), an empty set for a blank pin, empty
     * when unreadable.
     */
    private static Optional<BitSet> readPin(String pinText) {
        BitSet bits = new BitSet();
        if (pinText.isBlank()) {
            return Optional.of(bits);
        }
        for (String rangeText : pinText.split(RANGE_SEPARATOR, -1)) {
            String[] ends = rangeText.strip().split(RANGE_DASH, -1);
            if (ends.length > 2) {
                return Optional.empty();
            }
            int first = bitNumber(ends[0]);
            int last = ends.length == 2 ? bitNumber(ends[1]) : first;
            if (first < 0 || last < 0) {
                return Optional.empty();
            }
            bits.set(Math.min(first, last), Math.max(first, last) + 1);
        }
        return Optional.of(bits);
    }

    /** The bit {@code text} names, -1 when it is not a number of at most {@link #MAXIMUM_DIGITS} digits. */
    private static int bitNumber(String text) {
        String digits = text.strip();
        if (digits.isEmpty() || digits.length() > MAXIMUM_DIGITS || !digits.chars().allMatch(character -> character >= '0' && character <= '9')) {
            return -1;
        }
        return Integer.parseInt(digits);
    }
}
