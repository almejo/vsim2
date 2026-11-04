package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The groups being edited in the «Grupos» window (spec §2.3): pins of «tramos» that may be wrong for a while (a bit in
 * two pins, an empty pin) while the user moves bits around. The window shows {@link #problems()} and offers Aceptar
 * only when there are none, with {@link #groups()}. One pin is always selected: a click on the bit strip moves the bit
 * there ({@link #toggleBit}).
 */
final class GroupsDraft {

    static final String BIT_OUT_OF_RANGE = "groups.bitOutOfRange";
    static final String BIT_IN_TWO_PINS = "groups.bitInTwoPins";
    static final String BITS_IN_TWO_PINS = "groups.bitsInTwoPins";
    static final String EMPTY_PIN = "groups.emptyPin";
    static final String TOO_MANY_PINS = "groups.tooManyPins";

    private static final String PIN_SEPARATOR = "|";
    private static final String RANGE_SEPARATOR = ",";
    /** A range of the text: one bit or two joined by a dash, at most two digits each (BitGroups' own limit). */
    private static final Pattern RANGE = Pattern.compile("\\s*(\\d{1,2})\\s*(?:-\\s*(\\d{1,2})\\s*)?");

    private final int width;
    private final List<List<BitRange>> pins = new ArrayList<>();
    private int selectedPin;

    private GroupsDraft(int width) {
        this.width = width;
    }

    /** A draft of {@code groups}, its first pin selected. */
    static GroupsDraft of(BitGroups groups) {
        GroupsDraft draft = new GroupsDraft(groups.width());
        draft.setPinsFrom(groups);
        return draft;
    }

    int width() {
        return width;
    }

    int pinCount() {
        return pins.size();
    }

    /** The ranges of pin {@code pin} as they are edited. */
    List<BitRange> ranges(int pin) {
        return List.copyOf(pins.get(pin));
    }

    /** The bus bits of pin {@code pin}, ascending and once each. */
    List<Integer> bitsOf(int pin) {
        TreeSet<Integer> bits = new TreeSet<>();
        for (BitRange range : pins.get(pin)) {
            IntStream.rangeClosed(range.low(), range.high()).forEach(bits::add);
        }
        return List.copyOf(bits);
    }

    /** Pin {@code pin}'s bits as text ({@code 7-11,25-31}), {@code ""} when it has none. */
    String label(int pin) {
        return BitGroups.label(bitsOf(pin));
    }

    int selectedPin() {
        return selectedPin;
    }

    void select(int pin) {
        selectedPin = Math.clamp(pin, 0, pins.size() - 1);
    }

    /** Whether a pin may be removed: the part keeps at least one. */
    boolean canRemovePin() {
        return pins.size() > 1;
    }

    /** A new last pin with the first unused bit (the highest bit when every bit is used), selected. */
    void addPin() {
        pins.add(new ArrayList<>(List.of(BitRange.of(firstUnusedBit()))));
        selectedPin = pins.size() - 1;
    }

    /** Removes pin {@code pin}, whose bits become unused; never the last pin. */
    void removePin(int pin) {
        if (!canRemovePin()) {
            return;
        }
        pins.remove(pin);
        select(selectedPin > pin ? selectedPin - 1 : selectedPin);
    }

    /** A new range of the first unused bit at the end of pin {@code pin}. */
    void addRange(int pin) {
        pins.get(pin).add(BitRange.of(firstUnusedBit()));
    }

    /** Removes range {@code index} of pin {@code pin}; a pin keeps at least one range. */
    void removeRange(int pin, int index) {
        if (pins.get(pin).size() > 1) {
            pins.get(pin).remove(index);
        }
    }

    /** Range {@code index} of pin {@code pin} becomes {@code range}, its ends kept inside the bus. */
    void setRange(int pin, int index, BitRange range) {
        pins.get(pin).set(index, new BitRange(insideTheBus(range.first()), insideTheBus(range.last())));
    }

    /**
     * A click on bit {@code bit} of the strip: it leaves the selected pin when it was there, otherwise it moves there
     * from whatever pins had it. The pins it touches are rewritten as their merged ranges.
     */
    void toggleBit(int bit) {
        if (bitsOf(selectedPin).contains(bit)) {
            setBits(selectedPin, bitsOf(selectedPin).stream().filter(other -> other != bit).toList());
            return;
        }
        for (int pin : ownersOf(bit)) {
            setBits(pin, bitsOf(pin).stream().filter(other -> other != bit).toList());
        }
        List<Integer> bits = new ArrayList<>(bitsOf(selectedPin));
        bits.add(bit);
        setBits(selectedPin, bits.stream().sorted().toList());
    }

    /** The pins (from 0) that have bit {@code bit}: none for an unused bit, two or more for a conflict. */
    List<Integer> ownersOf(int bit) {
        return IntStream.range(0, pins.size()).filter(pin -> bitsOf(pin).contains(bit)).boxed().toList();
    }

    /** The bus bits in no pin, ascending. */
    List<Integer> unusedBits() {
        TreeSet<Integer> unused = IntStream.range(0, width).boxed().collect(Collectors.toCollection(TreeSet::new));
        for (int pin = 0; pin < pins.size(); pin++) {
            bitsOf(pin).forEach(unused::remove);
        }
        return List.copyOf(unused);
    }

    /**
     * The groups as text, one pin per {@code |} and its ranges merged: {@code 7-11,25-31|15-19}. One bit per pin is
     * written out ({@code 0|1|2|3}), unlike the stored canonical text, so the user sees every pin.
     */
    String text() {
        return IntStream.range(0, pins.size()).mapToObj(this::label).collect(Collectors.joining(PIN_SEPARATOR));
    }

    /**
     * Reads {@code text} ({@code 7-11, 25-31 | 15-19}) as the new pins, the first one selected; blank text is one bit
     * per pin. A range written from its high end ({@code 11-7}) is read low – high. Returns false and changes nothing
     * when the text cannot be read; bits beyond the bus, empty pins and bits in two pins are read and then shown as
     * {@link #problems()}.
     */
    boolean readText(String text) {
        if (text.isBlank()) {
            setPinsFrom(BitGroups.onePerPin(width));
            return true;
        }
        List<List<BitRange>> read = new ArrayList<>();
        for (String pinText : text.split(Pattern.quote(PIN_SEPARATOR), -1)) {
            Optional<List<BitRange>> ranges = readPin(pinText);
            if (ranges.isEmpty()) {
                return false;
            }
            read.add(ranges.get());
        }
        pins.clear();
        read.forEach(ranges -> pins.add(new ArrayList<>(ranges)));
        selectedPin = 0;
        return true;
    }

    /**
     * What keeps these pins from being groups, in the order the window lists them: too many pins, then each pin
     * without bits or with a bit beyond the bus, then the bits in two pins (told together). The keys are
     * {@link BitGroups}' own, plus {@link #BITS_IN_TWO_PINS} for several bits at once.
     */
    List<ParameterProblem> problems() {
        List<ParameterProblem> problems = new ArrayList<>();
        if (pins.size() > BitGroups.MAXIMUM_PINS) {
            problems.add(problem(TOO_MANY_PINS, BitGroups.MAXIMUM_PINS));
        }
        for (int pin = 0; pin < pins.size(); pin++) {
            List<Integer> bits = bitsOf(pin);
            if (bits.isEmpty()) {
                problems.add(problem(EMPTY_PIN, pin + 1));
            } else if (bits.getLast() >= width) {
                problems.add(problem(BIT_OUT_OF_RANGE, bits.getLast(), width));
            }
        }
        List<Integer> shared = sharedBits();
        if (shared.size() == 1) {
            problems.add(problem(BIT_IN_TWO_PINS, shared.getFirst()));
        } else if (shared.size() > 1) {
            problems.add(problem(BITS_IN_TWO_PINS, BitGroups.label(shared)));
        }
        return problems;
    }

    /** The groups these pins are, empty while there is a {@linkplain #problems() problem}. */
    Optional<BitGroups> groups() {
        return problems().isEmpty() ? Optional.of(BitGroups.parse(text(), width)) : Optional.empty();
    }

    private void setPinsFrom(BitGroups groups) {
        pins.clear();
        for (int pin = 0; pin < groups.pinCount(); pin++) {
            pins.add(rangesOf(groups.bitsOf(pin)));
        }
        selectedPin = 0;
    }

    private void setBits(int pin, List<Integer> ascendingBits) {
        pins.set(pin, rangesOf(ascendingBits));
    }

    /** Ascending bits as merged ranges: 7, 8, 9, 25 is 7-9 and 25. */
    private static List<BitRange> rangesOf(List<Integer> ascendingBits) {
        List<BitRange> ranges = new ArrayList<>();
        for (int bit : ascendingBits) {
            if (!ranges.isEmpty() && ranges.getLast().high() == bit - 1) {
                ranges.set(ranges.size() - 1, new BitRange(ranges.getLast().low(), bit));
            } else {
                ranges.add(BitRange.of(bit));
            }
        }
        return ranges;
    }

    private List<Integer> sharedBits() {
        TreeSet<Integer> seen = new TreeSet<>();
        TreeSet<Integer> shared = new TreeSet<>();
        for (int pin = 0; pin < pins.size(); pin++) {
            for (int bit : bitsOf(pin)) {
                if (!seen.add(bit)) {
                    shared.add(bit);
                }
            }
        }
        return List.copyOf(shared);
    }

    private int firstUnusedBit() {
        List<Integer> unused = unusedBits();
        return unused.isEmpty() ? width - 1 : unused.getFirst();
    }

    private int insideTheBus(int bit) {
        return Math.clamp(bit, 0, width - 1);
    }

    /** The ranges of one pin's text, none for a blank pin, empty when a range cannot be read. */
    private static Optional<List<BitRange>> readPin(String pinText) {
        if (pinText.isBlank()) {
            return Optional.of(List.of());
        }
        List<BitRange> ranges = new ArrayList<>();
        for (String rangeText : pinText.split(RANGE_SEPARATOR, -1)) {
            Matcher matcher = RANGE.matcher(rangeText);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            int first = Integer.parseInt(matcher.group(1));
            int last = matcher.group(2) == null ? first : Integer.parseInt(matcher.group(2));
            // written from either end (11-7), the table shows it low – high, as the text does after reading
            ranges.add(new BitRange(Math.min(first, last), Math.max(first, last)));
        }
        return Optional.of(ranges);
    }

    private static ParameterProblem problem(String key, Object... arguments) {
        return new ParameterProblem(StandardComponentTypes.GROUPS, key, List.of(arguments));
    }
}
