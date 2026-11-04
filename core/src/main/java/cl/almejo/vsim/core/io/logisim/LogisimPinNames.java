package cl.almejo.vsim.core.io.logisim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The names of a circuit's interface pins in VSim, which must be unique ignoring case and not blank. The rule is the
 * one of «Crear componente desde selección» ({@code DefinitionBuilder.pinNames}): the stripped label; a repeated
 * label gets the first free {@code label2}, {@code label3}, …; a blank input is {@code in} plus its place among the
 * inputs top to bottom ({@code in1}, …) and a blank output {@code out} plus its place, or the first free number after
 * it. Labels are claimed first, so a blank pin never takes a label's name.
 */
public final class LogisimPinNames {

    private static final String INPUT_PREFIX = "in";
    private static final String OUTPUT_PREFIX = "out";
    private static final Comparator<LogisimInterfacePin> TOP_TO_BOTTOM = Comparator
            .comparingInt((LogisimInterfacePin pin) -> pin.location().y())
            .thenComparingInt(pin -> pin.location().x());

    private LogisimPinNames() {
    }

    /** One name per pin of {@code pins}, in the same order. */
    public static List<String> unique(List<LogisimInterfacePin> pins) {
        List<Integer> inputs = side(pins, false);
        List<Integer> outputs = side(pins, true);
        List<Integer> order = new ArrayList<>(inputs);
        order.addAll(outputs);
        Set<String> reserved = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        pins.forEach(pin -> reserved.add(pin.label().strip()));
        reserved.remove("");
        Set<String> taken = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String[] names = new String[pins.size()];
        for (int index : order) {
            String label = pins.get(index).label().strip();
            if (!label.isEmpty()) {
                names[index] = taken.add(label) ? label : firstFree(label, 2, reserved, taken);
            }
        }
        numberBlanks(pins, inputs, INPUT_PREFIX, names, reserved, taken);
        numberBlanks(pins, outputs, OUTPUT_PREFIX, names, reserved, taken);
        return List.of(names);
    }

    /** The indexes of the inputs (or outputs) of {@code pins}, top to bottom, then left to right. */
    private static List<Integer> side(List<LogisimInterfacePin> pins, boolean outputs) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < pins.size(); index++) {
            if (pins.get(index).output() == outputs) {
                indexes.add(index);
            }
        }
        indexes.sort(Comparator.comparing(pins::get, TOP_TO_BOTTOM));
        return indexes;
    }

    private static void numberBlanks(List<LogisimInterfacePin> pins, List<Integer> side, String prefix,
                                     String[] names, Set<String> reserved, Set<String> taken) {
        for (int place = 0; place < side.size(); place++) {
            int index = side.get(place);
            if (pins.get(index).label().isBlank()) {
                names[index] = firstFree(prefix, place + 1, reserved, taken);
            }
        }
    }

    /** The first {@code base + number} (from {@code firstNumber} up) neither reserved nor taken; marks it taken. */
    private static String firstFree(String base, int firstNumber, Set<String> reserved, Set<String> taken) {
        for (int number = firstNumber; ; number++) {
            String candidate = base + number;
            if (!reserved.contains(candidate) && !taken.contains(candidate)) {
                taken.add(candidate);
                return candidate;
            }
        }
    }
}
