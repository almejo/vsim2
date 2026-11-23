package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.LogicValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One column of a {@link MachineTest}: the cycle before one clock edge. {@code inputs} gives the inputs during that
 * cycle by input pin id (an absent pin is 0); {@code expected} gives what the student expects of each output just
 * before the edge, by output pin id (an absent pin is «no importa»). Keyed by the pins' stable ids, so renaming,
 * reordering or showing the pins as a bus costs nothing. Kept canonical: an input at 0 is left out, so two columns
 * that mean the same are equal.
 */
public record TestColumn(Map<String, Boolean> inputs, Map<String, Boolean> expected) {

    /** A column with every input 0 and nothing expected. */
    public static final TestColumn EMPTY = new TestColumn(Map.of(), Map.of());

    public TestColumn {
        Map<String, Boolean> ones = new LinkedHashMap<>();
        Objects.requireNonNull(inputs, "inputs").forEach((pinId, value) -> {
            if (Objects.requireNonNull(value, "input value")) {
                ones.put(pinId, true);
            }
        });
        inputs = Map.copyOf(ones);
        expected = Map.copyOf(expected);
    }

    /** The input {@code pinId} in this column; 0 when it was never set. */
    public boolean input(String pinId) {
        return inputs.getOrDefault(pinId, false);
    }

    /** What is expected of the output {@code pinId}; {@link LogicValue#DONT_CARE} when nothing is. */
    public LogicValue expected(String pinId) {
        Boolean value = expected.get(pinId);
        if (value == null) {
            return LogicValue.DONT_CARE;
        }
        return value ? LogicValue.ONE : LogicValue.ZERO;
    }

    /** This column with the input {@code pinId} at {@code value}. */
    public TestColumn withInput(String pinId, boolean value) {
        Map<String, Boolean> changed = new LinkedHashMap<>(inputs);
        changed.put(Objects.requireNonNull(pinId, "pinId"), value);
        return new TestColumn(changed, expected);
    }

    /** This column expecting {@code value} of the output {@code pinId} ({@link LogicValue#DONT_CARE} clears it). */
    public TestColumn withExpected(String pinId, LogicValue value) {
        Objects.requireNonNull(pinId, "pinId");
        Map<String, Boolean> changed = new LinkedHashMap<>(expected);
        switch (Objects.requireNonNull(value, "value")) {
            case ZERO -> changed.put(pinId, false);
            case ONE -> changed.put(pinId, true);
            case DONT_CARE -> changed.remove(pinId);
        }
        return new TestColumn(inputs, changed);
    }

    /** This column with only the inputs whose pin id {@code keepInput} accepts and the expected values likewise. */
    TestColumn retaining(Predicate<String> keepInput, Predicate<String> keepOutput) {
        Map<String, Boolean> keptInputs = new LinkedHashMap<>();
        inputs.forEach((pinId, value) -> {
            if (keepInput.test(pinId)) {
                keptInputs.put(pinId, value);
            }
        });
        Map<String, Boolean> keptExpected = new LinkedHashMap<>();
        expected.forEach((pinId, value) -> {
            if (keepOutput.test(pinId)) {
                keptExpected.put(pinId, value);
            }
        });
        return new TestColumn(keptInputs, keptExpected);
    }

    /** A column with this one's inputs and nothing expected: what «+ columna» adds after it (holding an input). */
    TestColumn holdingInputs() {
        return new TestColumn(inputs, Map.of());
    }
}
